/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.modules.authentication.QSessionStoreHelper;
import com.kingsrook.qqq.backend.core.realtime.RealtimeBus;
import com.kingsrook.qqq.backend.core.realtime.RealtimeMessage;
import com.kingsrook.qqq.middleware.javalin.executors.LogoutExecutor;
import com.kingsrook.qqq.middleware.javalin.executors.io.LogoutInput;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.LogoutResponseV1;


/*******************************************************************************
 ** Device session history bound only to verified QQQ authentication context.
 *******************************************************************************/
final class MobileSessionService
{
   private static final Map<QInstance, MobileSessionService> INSTANCES = new WeakHashMap<>();
   private final MobileScreenService screens = new MobileScreenService();

   /***************************************************************************
    ** One lock and bounded transient screen cache per server instance.
    ***************************************************************************/
   static synchronized MobileSessionService current()
   {
      return INSTANCES.computeIfAbsent(QContext.getQInstance(), key -> new MobileSessionService());
   }

   /***************************************************************************
    ** A session ended on this server cannot race a local scan persistence write.
    ***************************************************************************/
   static <T> T withActiveSession(MobileService.Operation<T> operation) throws QException
   {
      synchronized(current())
      {
         requireNotEnded();
         return operation.run();
      }
   }

   /***************************************************************************
    ** Apply the durable tombstone even when device headers are omitted.
    ***************************************************************************/
   static void requireNotEnded() throws QException
   {
      String uuid = authenticatedUuid();
      QRecord binding = MobileService.get(MobileScannersQBitProducer.SESSION_BINDING, MobileService.hash(uuid));
      if(binding != null)
      {
         QRecord session = MobileService.get(MobileScannersQBitProducer.SESSION, binding.getValueString("sessionId"));
         if(session == null || !"ACTIVE".equals(session.getValueString("status")))
         {
            throw new QAuthenticationException("This mobile session has ended. Sign in again.");
         }
      }
   }

   /***************************************************************************
    ** Observe old clients too; binding never accepts a client-supplied user/UUID.
    ***************************************************************************/
   static QRecord observe(String deviceId) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      synchronized(current())
      {
         requireNotEnded();
         String uuid = authenticatedUuid();
         String key = MobileService.hash(uuid);
         QRecord binding = MobileService.get(MobileScannersQBitProducer.SESSION_BINDING, key);
         QRecord session;
         if(binding == null)
         {
            session = MobileService.transaction(() ->
            {
               QRecord created = MobileService.insert(MobileScannersQBitProducer.SESSION, new QRecord()
                  .withValue("id", UUID.randomUUID().toString()).withValue("deviceId", deviceId).withValue("userId", MobileService.userId())
                  .withValue("status", "ACTIVE").withValue("startedAt", Instant.now()).withValue("lastSeenAt", Instant.now())
                  .withValue("screenAvailable", false).withValue("captureBlockedReason", "CLIENT_NOT_REPORTING"));
               MobileService.insert(MobileScannersQBitProducer.SESSION_BINDING, new QRecord().withValue("id", key)
                  .withValue("sessionId", created.getValueString("id")).withValue("coreSessionUuid", uuid));
               MobileDeviceActivity.seen(deviceId);
               return created;
            });
         }
         else
         {
            session = MobileService.get(MobileScannersQBitProducer.SESSION, binding.getValueString("sessionId"));
            if(!deviceId.equals(session.getValueString("deviceId")) || !MobileService.userId().equals(session.getValueString("userId")))
            {
               throw new QPermissionDeniedException("This sign-in belongs to another device.");
            }
         }
         // Coalesce request activity; explicit heartbeat updates are always persisted.
         Instant seen = session.getValueInstant("lastSeenAt");
         if(seen == null || seen.plusSeconds(5).isBefore(Instant.now()))
         {
            update(new QRecord().withValue("id", session.getValueString("id")).withValue("lastSeenAt", Instant.now()));
            MobileDeviceActivity.seen(deviceId);
         }
         return session;
      }
   }

   /***************************************************************************
    ** Safe activity is display metadata, never authorization or process input.
    ***************************************************************************/
   static Map<String, Object> heartbeat(Map<String, Object> input) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      String deviceId = currentDevice();
      synchronized(current())
      {
         QRecord session = observe(deviceId);
         boolean foreground = bool(input, "foreground");
         boolean available = bool(input, "screenAvailable") && foreground;
         QRecord changes = new QRecord().withValue("id", session.getValueString("id")).withValue("lastSeenAt", Instant.now())
            .withValue("foreground", foreground).withValue("screenAvailable", available);
         for(String name : List.of("appName", "processName", "processInstanceId", "stepName", "screenLabel"))
         {
            changes.setValue(name, MobileService.optional(input, name, 200));
         }
         for(String name : List.of("lastAction", "errorCode", "captureBlockedReason"))
         {
            changes.setValue(name, MobileService.optional(input, name, 80));
         }
         changes.setValue("traceId", MobileService.optional(input, "traceId", 100));
         MobileDeviceActivity.heartbeat(deviceId, input, foreground);
         update(changes);
         String id = session.getValueString("id");
         if(!available)
         {
            current().screens.remove(id);
         }
         Map<String, Object> response = new LinkedHashMap<>();
         response.put("sessionId", id);
         response.put("status", "ACTIVE");
         response.put("serverTime", Instant.now().toString());
         response.put("heartbeatIntervalMillis", 5000);
         if(available && "APPROVED".equals(MobileService.get(MobileScannersQBitProducer.DEVICE, deviceId).getValueString("status")))
         {
            Map<String, Object> lease = current().screens.activeLease(id);
            if(lease != null)
            {
               response.put("screenMirror", lease);
            }
         }
         return response;
      }
   }

   /***************************************************************************
    ** An operator can end only the session bound to their current credentials.
    ***************************************************************************/
   static Map<String, Object> endCurrent(Map<String, Object> input) throws QException
   {
      String reason = MobileService.required(input, "reason", 30);
      if(!List.of("SIGN_OUT", "SERVER_CHANGE").contains(reason))
      {
         throw new QBadRequestException("Unknown session end reason.");
      }
      QRecord session = observe(currentDevice());
      Instant endedAt = end(session.getValueString("id"), reason, false);
      return Map.of("sessionId", session.getValueString("id"), "status", "ENDED", "endedAt", endedAt.toString());
   }

   /***************************************************************************
    ** Standard logout remains usable by clients that predate session telemetry.
    ***************************************************************************/
   static void recordLogout() throws QException
   {
      QRecord binding = MobileService.get(MobileScannersQBitProducer.SESSION_BINDING, MobileService.hash(authenticatedUuid()));
      if(binding != null)
      {
         end(binding.getValueString("sessionId"), "SIGN_OUT", false);
      }
   }

   /***************************************************************************
    ** Persist the tombstone before calling provider cleanup; never trust a no-op.
    ***************************************************************************/
   static Instant end(String id, String reason, boolean administrator) throws QException
   {
      synchronized(current())
      {
         if(administrator)
         {
            MobilePermissions.require(MobilePermissions.ADMIN);
         }
         QRecord session = MobileService.get(MobileScannersQBitProducer.SESSION, id);
         if(session == null)
         {
            throw new QBadRequestException("Session not found.");
         }
         QRecord binding = bindingFor(id);
         if(!administrator && (binding == null || !authenticatedUuid().equals(binding.getValueString("coreSessionUuid"))))
         {
            throw new QPermissionDeniedException("This session does not belong to this sign-in.");
         }
         Instant endedAt = session.getValueInstant("endedAt");
         if(!"ENDED".equals(session.getValueString("status")))
         {
            endedAt = Instant.now();
            update(new QRecord().withValue("id", id).withValue("status", "ENDED").withValue("endedAt", endedAt)
               .withValue("endedBy", MobileService.userId()).withValue("endReason", reason == null ? "ADMINISTRATOR" : reason)
               .withValue("screenAvailable", false).withValue("foreground", false).withValue("authCleanupStatus", "PENDING"));
         }
         current().screens.remove(id);
         if(binding != null)
         {
            String coreUuid = binding.getValueString("coreSessionUuid");
            if(MobileService.TRANSACTION.get() == null)
            {
               cleanupAuthentication(id, coreUuid);
            }
         }
         return endedAt;
      }
   }

   /***************************************************************************
    ** Provider cleanup is retried by an idempotent end; the tombstone is final.
    ***************************************************************************/
   private static void cleanupAuthentication(String id, String coreUuid) throws QException
   {
      update(new QRecord().withValue("id", id).withValue("authCleanupStatus", "REQUESTED").withValue("authCleanupAt", Instant.now()));
      try
      {
         LogoutInput logout = new LogoutInput();
         logout.setSessionUUID(coreUuid);
         new LogoutExecutor().execute(logout, new LogoutResponseV1());
         QSessionStoreHelper.removeSession(coreUuid);
      }
      catch(QException failure)
      {
         update(new QRecord().withValue("id", id).withValue("authCleanupStatus", "RETRY_NEEDED"));
      }
   }

   /***************************************************************************
    ** Commit every selected tombstone before any external authentication cleanup.
    ***************************************************************************/
   static void endAll(List<String> ids, String reason) throws QException
   {
      synchronized(current())
      {
         requireNotEnded();
         String caller = authenticatedUuid();
         List<CleanupTarget> targets = new ArrayList<>();
         MobileService.transaction(() ->
         {
            for(String id : ids)
            {
               end(id, reason, true);
               QRecord binding = bindingFor(id);
               if(binding != null)
               {
                  targets.add(new CleanupTarget(id, binding.getValueString("coreSessionUuid")));
               }
            }
            return null;
         });
         targets.sort(java.util.Comparator.comparing(target -> caller.equals(target.coreUuid())));
         for(CleanupTarget target : targets)
         {
            cleanupAuthentication(target.id(), target.coreUuid());
         }
      }
   }

   /***************************************************************************
    ** Server-only cleanup snapshot, never serialized into process/API values.
    ***************************************************************************/
   private record CleanupTarget(String id, String coreUuid)
   {
   }

   /***************************************************************************
    ** Frame validation and storage occur only for the authenticated bound owner.
    ***************************************************************************/
   static void upload(Map<String, Object> input) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      synchronized(current())
      {
         String device = currentDevice();
         MobileService.requireApproved(device);
         QRecord session = observe(device);
         if(!Boolean.TRUE.equals(session.getValueBoolean("screenAvailable")) || !Boolean.TRUE.equals(session.getValueBoolean("foreground")))
         {
            throw new QPermissionDeniedException("Screen capture is unavailable.");
         }
         current().screens.upload(session.getValueString("id"), input);
      }
   }

   /***************************************************************************
    ** Admin view refresh grants short-lived demand, not persistent recording.
    ***************************************************************************/
   static MobileScreenService.View view(String id) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      synchronized(current())
      {
         QRecord session = MobileService.get(MobileScannersQBitProducer.SESSION, id);
         if(session == null)
         {
            throw new QBadRequestException("Session not found.");
         }
         String state = "Live";
         if(!"ACTIVE".equals(session.getValueString("status")))
         {
            state = "Session ended";
         }
         else if(session.getValueInstant("lastSeenAt").plusSeconds(15).isBefore(Instant.now()))
         {
            state = "Offline: no recent heartbeat";
         }
         else if(!"APPROVED".equals(MobileService.get(MobileScannersQBitProducer.DEVICE, session.getValueString("deviceId")).getValueString("status")))
         {
            state = "Device is not approved";
         }
         else if(!Boolean.TRUE.equals(session.getValueBoolean("screenAvailable")))
         {
            state = "Screen unavailable: " + java.util.Objects.toString(session.getValueString("captureBlockedReason"), "Capture is unavailable");
         }
         if(!"Live".equals(state))
         {
            current().screens.remove(id);
            return new MobileScreenService.View(state, null, null);
         }
         return current().screens.view(id);
      }
   }

   /***************************************************************************
    ** Private core session IDs are never queried through the public table API.
    ***************************************************************************/
   private static QRecord bindingFor(String id) throws QException
   {
      return MobileService.internally(() -> new QueryAction().execute(new QueryInput().withTableName(MobileScannersQBitProducer.SESSION_BINDING)
         .withTransaction(MobileService.TRANSACTION.get()).withShouldOmitHiddenFields(false).withFilter(new QQueryFilter(new QFilterCriteria("sessionId", QCriteriaOperator.EQUALS, id)).withLimit(1)))
         .getRecords().stream().findFirst().orElse(null));
   }

   /***************************************************************************
    ** Changes use QQQ actions and publish safe invalidation hints only.
    ***************************************************************************/
   private static void update(QRecord record) throws QException
   {
      MobileService.internally(() ->
      {
         var saved = new UpdateAction().execute(new UpdateInput().withTableName(MobileScannersQBitProducer.SESSION).withTransaction(MobileService.TRANSACTION.get()).withRecord(record)).getRecords();
         if(saved == null || saved.size() != 1 || (saved.getFirst().getErrors() != null && !saved.getFirst().getErrors().isEmpty()))
         {
            throw new QException("The mobile session could not be updated.");
         }
         return null;
      });
      RealtimeBus.get(QContext.getQInstance()).publish(new RealtimeMessage().withChannel("record:mobileSession:" + record.getValueString("id"))
         .withEvent("record.changed").withData(Map.of("table", MobileScannersQBitProducer.SESSION, "pk", record.getValueString("id"))));
      RealtimeBus.get(QContext.getQInstance()).publish(new RealtimeMessage().withChannel("table:mobileSession")
         .withEvent("table.changed").withData(Map.of("table", MobileScannersQBitProducer.SESSION)));
   }

   /***************************************************************************
    ** No request body can manufacture a trusted device identity.
    ***************************************************************************/
   private static String currentDevice() throws QException
   {
      String id = MobilePermissions.REQUEST_DEVICE.get();
      if(id == null)
      {
         throw new QPermissionDeniedException("A verified device is required.");
      }
      return id;
   }

   /***************************************************************************
    ** Only QQQ's verified session supplies the private authentication UUID.
    ***************************************************************************/
   private static String authenticatedUuid() throws QAuthenticationException
   {
      var session = QContext.getQSession();
      if(session == null || session.getUser() == null || session.getUuid() == null || session.getUuid().isBlank())
      {
         throw new QAuthenticationException("Sign in to continue.");
      }
      return session.getUuid();
   }

   /***************************************************************************
    ** Do not coerce text into safety-critical capture state.
    ***************************************************************************/
   private static boolean bool(Map<String, Object> input, String name) throws QBadRequestException
   {
      if(!(input.get(name) instanceof Boolean value))
      {
         throw new QBadRequestException(name + " must be a boolean.");
      }
      return value;
   }
}
