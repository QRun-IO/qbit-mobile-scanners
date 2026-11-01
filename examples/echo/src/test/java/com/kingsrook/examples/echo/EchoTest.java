/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.mobilescanners.EchoMobileFixture;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qbits.mobilescanners.MobileScannerActions;
import com.kingsrook.qqq.backend.core.actions.processes.CancelProcessAction;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Real QQQ process state, approved mobile attribution and H2 persistence.
 ** Synthetic destination/generation fixtures do not qualify real media or output.
 *******************************************************************************/
class EchoTest
{
   private JdbcDataSource dataSource;
   private EchoMobileFixture mobile;
   private EchoConfig config;
   private final List<String> requests = new ArrayList<>();
   private final Map<String, String> accepted = new java.util.concurrent.ConcurrentHashMap<>();
   private final Map<String, EchoDestinations.Plan> acceptedPlans = new java.util.concurrent.ConcurrentHashMap<>();
   private final Map<String, EchoDestinations.Destination> acceptedDestinations = new java.util.concurrent.ConcurrentHashMap<>();
   private int setupChoices;
   private boolean destinationAvailable;
   private boolean uncertain;
   private boolean tooSmall;
   private String mediaRevision;
   private java.util.concurrent.CountDownLatch entered;
   private java.util.concurrent.CountDownLatch release;



   /***************************************************************************
    ** Each test receives real isolated tables and a currently approved scanner.
    ***************************************************************************/
   @BeforeEach
   void setup() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:echo_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      QInstance instance = new QInstance();
      mobile = new EchoMobileFixture(instance, dataSource.getURL());
      instance.addPossibleValueSource(new QPossibleValueSource().withName("echoFixturePrinters").withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(new QPossibleValue<>("printer", "Fixture printer"))));
      instance.addPossibleValueSource(new QPossibleValueSource().withName("echoFixtureMedia").withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(new QPossibleValue<>("4x6", "Fixture 4 x 6 in"))));
      destinationAvailable = true;
      requests.clear();
      accepted.clear();
      acceptedPlans.clear();
      acceptedDestinations.clear();
      setupChoices = 0;
      uncertain = false;
      tooSmall = false;
      mediaRevision = "a".repeat(64);
      entered = null;
      release = null;
      EchoDestinations destinations = new EchoDestinations()
      {
         /*******************************************************************
          ** Fixture authority rejects forged/unavailable IDs, not just labels.
          *******************************************************************/
         @Override
         public Destination authorize(MobileDeviceAuthorization.Identity identity, String printer, String media) throws QException
         {
            setupChoices++;
            if(!destinationAvailable || !List.of("printer", "printer2").contains(printer) || !"4x6".equals(media))
            {
               throw new QException("The printer/media choice is unavailable.");
            }
            return new Destination(printer, media, mediaRevision, 101600, 152400, 300, 300);
         }

         /*******************************************************************
          ** Fixed qualified-fixture plan is not a production fitting algorithm.
          *******************************************************************/
         @Override
         public Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, EchoService.Scan scan)
         {
            return tooSmall ? null : new Plan(1, 1, new GenerationPorts.Stock(101600, 152400, 5000, 5000, 5000, 5000, 300), GenerationPorts.Format.PDF);
         }
      };
      config = new EchoConfig("db", dataSource, "echoFixturePrinters", "echoFixtureMedia", destinations, new EchoGenerator()
      {
         /*******************************************************************
          ** The fixture records an accepted job before losing its HTTP receipt.
          *******************************************************************/
         @Override
         public String accept(EchoDestinations.Plan plan, String source, String key, EchoDestinations.Destination destination) throws QException
         {
            requests.add(key);
            if(entered != null)
            {
               entered.countDown();
               try
               {
                  if(!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                  {
                     throw new QException("Fixture generation gate timed out.");
                  }
               }
               catch(InterruptedException failure)
               {
                  Thread.currentThread().interrupt();
                  throw new QException("Fixture interrupted.");
               }
            }
            String job = "job:" + source;
            acceptedPlans.put(key, plan);
            acceptedDestinations.put(key, destination);
            accepted.put(key, job);
            if(uncertain)
            {
               throw new QException("Fixture lost acceptance response.");
            }
            return job;
         }

         /*******************************************************************
          ** This test owner lookup never invokes render or enqueue again.
          *******************************************************************/
         @Override
         public String lookupAccepted(EchoDestinations.Plan plan, String source, String key, EchoDestinations.Destination destination)
         {
            if(accepted.containsKey(key))
            {
               assertEquals("echo:" + source, key);
               assertEquals(acceptedPlans.get(key), plan);
               assertEquals(acceptedDestinations.get(key), destination);
            }
            return accepted.get(key);
         }
      });
      new EchoQBitProducer().produce(instance, config);
      new QInstanceValidator().validate(instance);
      try(Connection connection = dataSource.getConnection(); var resource = getClass().getResourceAsStream("/db/echo-h2.sql"))
      {
         String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
         for(String statement : sql.split(";"))
         {
            if(!statement.isBlank())
            {
               try(var command = connection.createStatement())
               {
                  command.execute(statement);
               }
            }
         }
      }
   }



   /***************************************************************************
    ** No thread-bound user/device identity survives the test boundary.
    ***************************************************************************/
   @AfterEach
   void clear()
   {
      EchoMobileFixture.clear();
   }



   /***************************************************************************
    ** Setup is a real frontend, then both scans return to the same scan step.
    ***************************************************************************/
   @Test
   void processChoosesOnceAndLoopsWithExactSymbology() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      assertEquals(0, setupChoices);
      RunProcessInput selection = resume(setup, "setup");
      selection.addValue("printerId", "printer");
      selection.addValue("mediaId", "4x6");
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      String run = scanning.getValueString("echoRunId");
      assertNotNull(run);
      RunProcessOutput first = scan(scanning, "CODE128", "001234567890");
      assertEquals(true, first.getValue("scanSaved"));
      assertEquals("QUEUED", first.getValue("printStatus"));
      assertEquals(first.getValue("scanId"), ((Map<?, ?>) first.getValue("scanContext")).get("scanId"));
      RunProcessOutput second = scan(first, "QRCODE", "001234567890");
      assertEquals(run, second.getValueString("echoRunId"));
      assertEquals(2, requests.size());
      assertEquals(3, setupChoices);
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT barcode,symbology,rendererSymbology FROM echo_scan ORDER BY symbology"))
      {
         assertTrue(rows.next());
         assertEquals("001234567890", rows.getString("barcode"));
         assertEquals("CODE128", rows.getString("symbology"));
         assertEquals("code128", rows.getString("rendererSymbology"));
         assertTrue(rows.next());
         assertEquals("001234567890", rows.getString("barcode"));
         assertEquals("QRCODE", rows.getString("symbology"));
         assertEquals("qr", rows.getString("rendererSymbology"));
      }
   }



   /***************************************************************************
    ** Accepted retry returns one queue identity; different type cannot reuse it.
    ***************************************************************************/
   @Test
   void retryUsesSameRequestAndRefusesChangedType() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      Map<String, Serializable> context = captured("CODE128", "001234567890");
      var first = config.service.print(run, context);
      assertEquals(first, config.service.print(run, context));
      assertEquals(1, requests.size());
      Map<String, Serializable> changed = new HashMap<>(context);
      changed.put("symbology", "QRCODE");
      assertThrows(QException.class, () -> config.service.print(run, changed));
      assertEquals(1, requests.size());
   }



   /***************************************************************************
    ** A canonical physical event cannot become a second job by reopening Echo.
    ** Deliberate new trigger events with the same value still print normally.
    ***************************************************************************/
   @Test
   void reopeningCannotReassignTheSameCanonicalScanToAnotherPrint() throws Exception
   {
      String firstRun = config.service.start("printer", "4x6");
      Map<String, Serializable> context = captured("CODE128", "001234567890");
      var first = config.service.print(firstRun, context);
      assertEquals(context.get("scanId"), first.scanId());
      config.service.close(firstRun);
      String secondRun = config.service.start("printer", "4x6");
      assertThrows(QException.class, () -> config.service.print(secondRun, context));
      assertEquals(1, requests.size());
      config.service.print(secondRun, captured("CODE128", "001234567890"));
      assertEquals(2, requests.size());
   }



   /***************************************************************************
    ** Uncertainty blocks a new event and reconciles using the original key.
    ***************************************************************************/
   @Test
   void lostResponseNeverCreatesANewPrintIdentity() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      Map<String, Serializable> original = captured("CODE128", "000001");
      uncertain = true;
      assertThrows(QException.class, () -> config.service.print(run, original));
      assertThrows(QException.class, () -> config.service.print(run, captured("QRCODE", "000002")));
      assertEquals(1, requests.size());
      uncertain = false;
      config.service.print(run, original);
      assertEquals(1, requests.size());
   }



   /***************************************************************************
    ** Unsupported/manual/unknown inputs never request a substitute barcode.
    ***************************************************************************/
   @Test
   void unsupportedAndControlPayloadsFailWithoutPrinting() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      for(String type : List.of("UNKNOWN", "MANUAL", "GS1_128", "unsupported"))
      {
         assertThrows(QException.class, () -> config.service.print(run, captured(type, "001234")));
      }
      assertThrows(QException.class, () -> config.service.print(run, captured("QRCODE", "A\nB")));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** Forged IDs and later destination revocation refuse before queue effects.
    ***************************************************************************/
   @Test
   void destinationAuthorityAndRunOwnershipAreRechecked() throws Exception
   {
      assertThrows(QException.class, () -> config.service.start("forged", "4x6"));
      String run = config.service.start("printer", "4x6");
      Map<String, Serializable> context = captured("QRCODE", "001234");
      destinationAvailable = false;
      assertThrows(QException.class, () -> config.service.print(run, context));
      destinationAvailable = true;
      mobile.otherOperator();
      assertThrows(QException.class, () -> config.service.print(run, captured("QRCODE", "different")));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** Closed runs preserve accepted jobs but cannot admit any more scans.
    ***************************************************************************/
   @Test
   void leaveClosesAdmissionWithoutReprinting() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      config.service.print(run, captured("CODE128", "original"));
      config.service.close(run);
      assertThrows(QException.class, () -> config.service.print(run, captured("CODE128", "after-leave")));
      assertEquals(1, requests.size());
      assertFalse(QContext.getQSession().hasPermission("printing_admin"));
   }



   /***************************************************************************
    ** Core cancellation destroys resumable process state rather than looping.
    ***************************************************************************/
   @Test
   void canceledProcessDoesNotAcceptAnotherScan() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      new CancelProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS).withProcessUUID(setup.getProcessUUID()));
      assertThrows(QException.class, () -> new RunProcessAction().execute(resume(setup, "setup")));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** An active configured run is closed by ordinary native process cancel.
    ***************************************************************************/
   @Test
   void canceledConfiguredProcessCannotResumePrinting() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput selection = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6");
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      new CancelProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS).withProcessUUID(scanning.getProcessUUID()));
      assertThrows(QException.class, () -> scan(scanning, "CODE128", "after-cancel"));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** A same-operator alternate run cannot replace protected process identity.
    ***************************************************************************/
   @Test
   void alternateOwnRunCannotRetargetTheProcess() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput selection = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6");
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      String otherRun = config.service.start("printer", "4x6");
      RunProcessInput forged = resume(scanning, "scan").withValue("echoRunId", otherRun);
      forged.getValues().putAll(event("CODE128", "must-not-print"));
      assertThrows(QException.class, () -> new RunProcessAction().execute(forged));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** A concurrent same event can only look up; a different event cannot enter.
    ***************************************************************************/
   @Test
   void oneEventInFlightDoesNotHoldTheDatabaseAcrossGeneration() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      var first = captured("CODE128", "first");
      var second = captured("QRCODE", "second");
      entered = new java.util.concurrent.CountDownLatch(1);
      release = new java.util.concurrent.CountDownLatch(1);
      var worker = java.util.concurrent.CompletableFuture.supplyAsync(() ->
      {
         mobile.bind();
         try
         {
            return config.service.print(run, first);
         }
         catch(QException failure)
         {
            throw new java.util.concurrent.CompletionException(failure);
         }
         finally
         {
            EchoMobileFixture.clear();
         }
      });
      try
      {
         assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
         assertThrows(QException.class, () -> config.service.print(run, first));
         assertThrows(QException.class, () -> config.service.print(run, second));
         assertEquals(1, requests.size());
      }
      finally
      {
         release.countDown();
      }
      assertNotNull(worker.get(5, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, requests.size());
   }



   /***************************************************************************
    ** Crashed admission recovers only an existing exact owner acceptance.
    ***************************************************************************/
   @Test
   void crashAfterQueueAcceptanceRecoversWithoutAnotherSubmission() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      var context = captured("CODE128", "000001");
      uncertain = true;
      assertThrows(QException.class, () -> config.service.print(run, context));
      try(Connection connection = dataSource.getConnection(); var command = connection.createStatement())
      {
         command.executeUpdate("UPDATE echo_scan SET state='IN_FLIGHT'");
      }
      uncertain = false;
      assertNotNull(config.service.print(run, context).printJobId());
      assertEquals(1, requests.size());
   }



   /***************************************************************************
    ** Driver-profile drift and insufficient geometry are rejected before print.
    ***************************************************************************/
   @Test
   void changedMediaAndTooSmallLayoutCannotPrint() throws Exception
   {
      String driftRun = config.service.start("printer", "4x6");
      mediaRevision = "b".repeat(64);
      assertThrows(QException.class, () -> config.service.print(driftRun, captured("CODE128", "drift")));
      mediaRevision = "a".repeat(64);
      String smallRun = config.service.start("printer", "4x6");
      tooSmall = true;
      assertThrows(QException.class, () -> config.service.print(smallRun, captured("CODE128", "too-small")));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** Even another authorized printer cannot silently replace completed setup.
    ***************************************************************************/
   @Test
   void setupReplayCannotRetargetPrinter() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      assertThrows(QException.class, () -> config.service.select(run, "printer2", "4x6"));
      config.service.print(run, captured("QRCODE", "still-original"));
      assertEquals(1, requests.size());
   }



   /***************************************************************************
    ** Generation's ordinary source GetAction can read this operator's scan;
    ** another operator cannot read it and nobody receives raw state-write access.
    ***************************************************************************/
   @Test
   void nativeSourceReadHonorsActualActorSecurityLock() throws Exception
   {
      String run = config.service.start("printer", "4x6");
      var acceptedScan = config.service.print(run, captured("CODE128", "000001"));
      var own = source(acceptedScan.scanId());
      assertNotNull(own);
      assertEquals("operator", own.getValueString("actorId"));
      assertThrows(QException.class, () -> new com.kingsrook.qqq.backend.core.actions.tables.InsertAction().execute(
         new com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput().withTableName(EchoQBitProducer.SCAN_TABLE)
            .withRecord(new com.kingsrook.qqq.backend.core.model.data.QRecord().withValue("id", "forged"))));
      mobile.otherOperator();
      QContext.getQSession().withSecurityKeyValue("qqq.session.userId", "operator");
      assertEquals(null, source(acceptedScan.scanId()));
   }



   /***************************************************************************
    ** Match the generation service's enforced source-read boundary exactly.
    ***************************************************************************/
   private com.kingsrook.qqq.backend.core.model.data.QRecord source(String id) throws QException
   {
      var input = new com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput();
      input.setTableName(EchoQBitProducer.SCAN_TABLE);
      input.setPrimaryKey(id);
      input.setPermissionEnforcement(com.kingsrook.qqq.backend.core.actions.permissions.PermissionEnforcement.ENFORCE);
      return new com.kingsrook.qqq.backend.core.actions.tables.GetAction().execute(input).getRecord();
   }



   /***************************************************************************
    ** Native submitted values are bound by the real mobile persistence façade.
    ***************************************************************************/
   private Map<String, Serializable> captured(String type, String value) throws QException
   {
      return MobileScannerActions.capture(new RunBackendStepInput().withProcessName(EchoQBitProducer.PROCESS)
         .withValues(event(type, value)), EchoQBitProducer.APP);
   }



   /***************************************************************************
    ** Resume uses the same server process UUID, as Android recreation does.
    ***************************************************************************/
   private RunProcessInput resume(RunProcessOutput previous, String step)
   {
      RunProcessInput input = new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS)
         .withProcessUUID(previous.getProcessUUID()).withStartAfterStep(step);
      input.getValues().putAll(previous.getValues());
      return input;
   }



   /***************************************************************************
    ** Scans preserve setup/run identity and replace the transient event payload.
    ***************************************************************************/
   private RunProcessOutput scan(RunProcessOutput previous, String type, String value) throws QException
   {
      RunProcessInput input = resume(previous, "scan");
      input.getValues().putAll(event(type, value));
      return new RunProcessAction().execute(input);
   }



   /***************************************************************************
    ** Synthetic public barcode values keep leading zeros without number parsing.
    ***************************************************************************/
   private Map<String, Serializable> event(String type, String value)
   {
      return new HashMap<>(Map.of("barcode", value, "symbology", type, "scanSource", "zebra-emdk",
         "eventId", UUID.randomUUID().toString(), "capturedAt", "2026-11-01T17:00:00Z"));
   }
}
