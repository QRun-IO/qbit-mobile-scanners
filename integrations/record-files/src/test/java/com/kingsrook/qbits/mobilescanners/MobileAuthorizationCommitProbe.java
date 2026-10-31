/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.lang.management.ManagementFactory;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileCaptureIdentityProvider;
import com.kingsrook.qbits.recordfiles.RecordFilesStore;
import com.kingsrook.qbits.recordfiles.api.CaptureIdentity;
import com.kingsrook.qbits.recordfiles.api.CaptureIdentityProvider;
import com.kingsrook.qbits.recordfiles.audit.AuditTrail;
import com.kingsrook.qbits.recordfiles.audit.RecordFilesEvent;
import com.kingsrook.qbits.recordfiles.audit.RecordFilesEventType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Observe real commits inside the real scanner guard; no replacement lock.
 *******************************************************************************/
final class MobileAuthorizationCommitProbe implements CaptureIdentityProvider
{
   private final MobileCaptureIdentityProvider delegate = new MobileCaptureIdentityProvider();
   private final AtomicBoolean observed = new AtomicBoolean();
   private final AtomicBoolean pendingCommit = new AtomicBoolean();
   private final AtomicBoolean verifiedCommit = new AtomicBoolean();
   private final AtomicReference<Throwable> terminationFailure = new AtomicReference<>();
   private final CountDownLatch attempted = new CountDownLatch(1);
   private final Thread terminator;
   private final String jdbc;
   private final String eventColumn;
   private final RecordFilesEventType eventType;
   private final boolean failBeforeCommit;

   /***************************************************************************
    ** Use actual administrator approval/session termination under its own context.
    ***************************************************************************/
   MobileAuthorizationCommitProbe(QInstance instance, String jdbc, QSession admin,
      CaptureIdentity identity, RecordFilesEventType eventType, boolean endSession, boolean failBeforeCommit)
   {
      this.jdbc = jdbc;
      this.eventType = eventType;
      this.failBeforeCommit = failBeforeCommit;
      eventColumn = instance.getTable("record_files_event").getField("eventType").getBackendName();
      terminator = new Thread(() ->
      {
         QContext.init(instance, admin);
         attempted.countDown();
         try
         {
            if(endSession)
            {
               MobileSessionService.end(identity.publicSessionId(), "ADMINISTRATOR", true);
            }
            else
            {
               MobileService.setApproval(identity.deviceId(), false);
            }
         }
         catch(Throwable failure)
         {
            terminationFailure.set(failure);
         }
         finally
         {
            QContext.clear();
         }
      }, "mobile-authorization-terminator");
   }

   /***************************************************************************
    ** Capture remains the production trust boundary.
    ***************************************************************************/
   @Override
   public Optional<CaptureIdentity> capture() throws QException
   {
      return delegate.capture();
   }

   /***************************************************************************
    ** Revalidation remains the production trust boundary.
    ***************************************************************************/
   @Override
   public void revalidate(CaptureIdentity identity) throws QException
   {
      delegate.revalidate(identity);
   }

   /***************************************************************************
    ** Inspect an independent SQL connection before the production guard returns.
    ***************************************************************************/
   @Override
   public <T> T executeAuthorized(Optional<CaptureIdentity> identity, Operation<T> operation) throws QException
   {
      boolean[] release = {false};
      try
      {
         return delegate.executeAuthorized(identity, () ->
         {
            try
            {
               return operation.run();
            }
            finally
            {
               if(pendingCommit.compareAndSet(true, false))
               {
                  release[0] = true;
                  assertNull(RecordFilesStore.currentTransaction(), "Owner commit or rollback must finish before guard release.");
                  assertEquals(failBeforeCommit ? 0 : 1, committedEvents());
                  assertBlockedByCurrentThread();
                  verifiedCommit.set(true);
               }
            }
         });
      }
      finally
      {
         if(release[0])
         {
            awaitTermination();
         }
      }
   }

   /***************************************************************************
    ** Pause after an actual transactional event insert, before its owner commit.
    ***************************************************************************/
   AuditTrail audit() throws QException
   {
      return new AuditTrail()
      {
         /***********************************************************************
          ** Audit insertion shares the real owner transaction being exercised.
          ***********************************************************************/
         @Override
         public void withChange(RecordFilesEvent event) throws QException
         {
            super.withChange(event);
            if(event.getType() == eventType && observed.compareAndSet(false, true))
            {
               pendingCommit.set(true);
               assertEquals(0, committedEvents(), "A second connection cannot observe the pending event.");
               terminator.start();
               try
               {
                  assertTrue(attempted.await(5, TimeUnit.SECONDS));
               }
               catch(InterruptedException interrupted)
               {
                  Thread.currentThread().interrupt();
                  throw new QException("Interrupted authorization probe.", interrupted);
               }
               assertBlockedByCurrentThread();
               if(failBeforeCommit)
               {
                  throw new QException("Injected pre-commit failure.");
               }
            }
         }
      };
   }

   /***************************************************************************
    ** Monitor ownership proves contention without assuming an elapsed delay.
    ***************************************************************************/
   private void assertBlockedByCurrentThread()
   {
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      do
      {
         var info = ManagementFactory.getThreadMXBean().getThreadInfo(terminator.threadId());
         if(info != null && info.getThreadState() == Thread.State.BLOCKED
            && info.getLockOwnerId() == Thread.currentThread().threadId()
            && info.getLockInfo().getClassName().equals(MobileSessionService.class.getName()))
         {
            return;
         }
         Thread.yield();
      }
      while(System.nanoTime() < deadline && terminator.isAlive());
      throw new AssertionError("Administrator termination did not block on the active mobile authorization guard.");
   }

   /***************************************************************************
    ** Read committed state independently of QContext and its transaction.
    ***************************************************************************/
   int committedEvents() throws QException
   {
      try(var connection = DriverManager.getConnection(jdbc, "sa", "");
         var query = connection.prepareStatement("SELECT COUNT(*) FROM record_files_event WHERE " + eventColumn + "=?"))
      {
         query.setString(1, eventType.name());
         try(var rows = query.executeQuery())
         {
            assertTrue(rows.next());
            return rows.getInt(1);
         }
      }
      catch(java.sql.SQLException failure)
      {
         throw new QException("Commit probe failed.", failure);
      }
   }

   /***************************************************************************
    ** Force committed revocation to precede any subsequent finalizer operation.
    ***************************************************************************/
   private void awaitTermination()
   {
      try
      {
         terminator.join(10000);
      }
      catch(InterruptedException interrupted)
      {
         Thread.currentThread().interrupt();
         throw new AssertionError(interrupted);
      }
      assertFalse(terminator.isAlive(), "Guard release must permit administrator termination.");
      assertNull(terminationFailure.get(), "Administrator termination must commit successfully.");
   }

   /***************************************************************************
    ** Every parameterized case must reach the intended commit or rollback seam.
    ***************************************************************************/
   void assertExercised()
   {
      assertTrue(observed.get());
      assertTrue(verifiedCommit.get());
      awaitTermination();
   }
}
