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
   private java.util.concurrent.CyclicBarrier planGate;
   private Runnable planHook;
   private Runnable afterAccept;
   private Runnable afterLookup;
   private boolean documentWithdrawn;
   private int documentRevisionShift;
   private final List<Integer> plannedDocuments = new java.util.concurrent.CopyOnWriteArrayList<>();



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
      planGate = null;
      planHook = null;
      afterAccept = null;
      afterLookup = null;
      documentWithdrawn = false;
      documentRevisionShift = 0;
      plannedDocuments.clear();
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
         public Document authorizeDocument(MobileDeviceAuthorization.Identity identity, Destination destination, Integer templateId) throws QException
         {
            if(documentWithdrawn || !List.of(7, 8).contains(templateId))
            {
               throw new QException("The chosen document is not available.");
            }
            return new Document(templateId, templateId * 10 + documentRevisionShift);
         }

         /*******************************************************************
          ** Two compatible published documents for the one fixture printer/size.
          *******************************************************************/
         @Override
         public List<DocumentChoice> documents(String printer, String media) throws QException
         {
            if(!"printer".equals(printer) || !"4x6".equals(media))
            {
               throw new QException("The printer/media choice is unavailable.");
            }
            return documentWithdrawn ? List.of() : List.of(new DocumentChoice(7, "Label A"), new DocumentChoice(8, "Label B"));
         }

         /*******************************************************************
          ** The pinned document must still be the current published revision.
          *******************************************************************/
         @Override
         public Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, Document document, EchoService.Scan scan) throws QException
         {
            plannedDocuments.add(document.templateId());
            if(!authorizeDocument(identity, destination, document.templateId()).equals(document))
            {
               throw new QException("The chosen document changed or was withdrawn.");
            }
            if(planHook != null)
            {
               planHook.run();
            }
            if(planGate != null)
            {
               try
               {
                  planGate.await(5, java.util.concurrent.TimeUnit.SECONDS);
               }
               catch(Exception failure)
               {
                  throw new IllegalStateException("Fixture plan gate failed.", failure);
               }
            }
            return tooSmall ? null : new Plan(document.templateId(), document.templateRevisionId(), new GenerationPorts.Stock(101600, 152400, 5000, 5000, 5000, 5000, 300), GenerationPorts.Format.PDF);
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
            if(afterAccept != null)
            {
               afterAccept.run();
            }
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
            if(afterLookup != null)
            {
               afterLookup.run();
            }
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
      selection.addValue("templateId", 7);
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      String run = scanning.getValueString("echoRunId");
      assertNotNull(run);
      Map<?, ?> scannerValues = (Map<?, ?>) new EchoWidget.Data().getSubBlocks().getFirst().get("values");
      assertEquals("Print queued", scannerValues.get("acknowledgmentLabel"));
      assertEquals(false, scannerValues.get("manualEntryEnabled"));
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
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "001234567890");
      var first = config.service.print(run, context);
      assertEquals(first, config.service.print(run, context));
      Map<String, Serializable> persistedPrecision = new HashMap<>(context);
      persistedPrecision.put("receivedAt", "2026-11-01T18:00:00.123456Z");
      assertEquals(first, config.service.print(run, persistedPrecision));
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
      String firstRun = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "001234567890");
      var first = config.service.print(firstRun, context);
      assertEquals(context.get("scanId"), first.scanId());
      config.service.close(firstRun);
      String secondRun = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
      for(String type : List.of("UNKNOWN", "MANUAL", "GS1_128", "unsupported", "EAN13", "EAN8", "UPCA", "UPCE0",
         "LABEL-TYPE-EAN13", "LABEL-TYPE-EAN8", "LABEL-TYPE-UPCA", "LABEL-TYPE-UPCE0"))
      {
         assertThrows(QException.class, () -> config.service.print(run, captured(type, "001234")));
      }
      assertThrows(QException.class, () -> config.service.print(run, captured("QRCODE", "A\nB")));
      assertTrue(requests.isEmpty());
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM echo_scan"))
      {
         assertTrue(rows.next());
         assertEquals(0, rows.getInt(1));
      }
   }



   /***************************************************************************
    ** Only the qualified printable ASCII profile is admitted; anything else is
    ** refused untouched before an intent is stored or generation is called.
    ***************************************************************************/
   @Test
   void nonAsciiPayloadsAreRejectedBeforeIntentOrGeneration() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      for(String type : List.of("CODE128", "QRCODE", "CODE39", "DATAMATRIX", "PDF417", "AZTEC"))
      {
         for(String value : List.of("café", "中文", "A\u007fB", "😀", "A B", "\u0080"))
         {
            var failure = assertThrows(QException.class, () -> config.service.print(run, captured(type, value)));
            assertTrue(failure.getMessage().contains("printable ASCII"));
         }
      }
      assertTrue(requests.isEmpty());
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM echo_scan"))
      {
         assertTrue(rows.next());
         assertEquals(0, rows.getInt(1));
      }
      config.service.print(run, captured("CODE128", "A ~"));
      assertEquals(1, requests.size());
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT barcode FROM echo_scan"))
      {
         assertTrue(rows.next());
         assertEquals("A ~", rows.getString("barcode"));
         assertFalse(rows.next());
      }
   }



   /***************************************************************************
    ** A failure before the pin made no external call, so the same event retries
    ** once and then returns its single queue receipt.
    ***************************************************************************/
   @Test
   void transientPrePinFailureRetriesTheSameEventOnce() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> layout = captured("CODE128", "retry-layout");
      tooSmall = true;
      assertThrows(QException.class, () -> config.service.print(run, layout));
      assertEquals("ATTENTION", scanRow(layout)[0]);
      tooSmall = false;
      var first = config.service.print(run, layout);
      assertEquals(1, requests.size());
      assertEquals(first, config.service.print(run, layout));
      assertEquals(1, requests.size());
      assertEquals("QUEUED", scanRow(layout)[0]);

      Map<String, Serializable> authorization = captured("QRCODE", "retry-authorization");
      destinationAvailable = false;
      assertThrows(QException.class, () -> config.service.print(run, authorization));
      assertEquals(1, requests.size());
      destinationAvailable = true;
      assertNotNull(config.service.print(run, authorization).printJobId());
      assertEquals(2, requests.size());
   }



   /***************************************************************************
    ** If the pin cannot be won (row missing, or another attempt pinned first)
    ** the generator is never reached, and a pinned event never generates again.
    ***************************************************************************/
   @Test
   void missingOrConflictingPinNeverReachesTheGenerator() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> missing = captured("CODE128", "pin-missing");
      tooSmall = true;
      assertThrows(QException.class, () -> config.service.print(run, missing));
      tooSmall = false;
      planHook = () -> sql("DELETE FROM echo_scan WHERE id='" + missing.get("scanId") + "'");
      assertThrows(QException.class, () -> config.service.print(run, missing));
      assertTrue(requests.isEmpty());
      config.service.close(run);

      String second = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> conflicting = captured("CODE128", "pin-conflict");
      tooSmall = true;
      assertThrows(QException.class, () -> config.service.print(second, conflicting));
      tooSmall = false;
      planHook = () -> sql("UPDATE echo_scan SET state='IN_FLIGHT',templateId=99,templateRevisionId=99,format='PDF',topMicrons=0,rightMicrons=0,bottomMicrons=0,leftMicrons=0 WHERE id='"
         + conflicting.get("scanId") + "'");
      assertThrows(QException.class, () -> config.service.print(second, conflicting));
      assertTrue(requests.isEmpty());
      planHook = null;
      assertThrows(QException.class, () -> config.service.print(second, conflicting));
      assertTrue(requests.isEmpty());
   }



   /***************************************************************************
    ** Two simultaneous retries of one unpinned event: the pin compare-and-set
    ** lets at most one reach the external generator.
    ***************************************************************************/
   @Test
   void simultaneousSameEventRetriesMakeAtMostOneExternalRequest() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "simultaneous");
      tooSmall = true;
      assertThrows(QException.class, () -> config.service.print(run, context));
      tooSmall = false;
      planGate = new java.util.concurrent.CyclicBarrier(2);
      List<java.util.concurrent.CompletableFuture<Object>> attempts = new ArrayList<>();
      for(int index = 0; index < 2; index++)
      {
         attempts.add(java.util.concurrent.CompletableFuture.supplyAsync(() ->
         {
            mobile.bind();
            try
            {
               return config.service.print(run, context);
            }
            catch(QException failure)
            {
               return failure;
            }
            finally
            {
               EchoMobileFixture.clear();
            }
         }));
      }
      int successes = 0;
      for(var attempt : attempts)
      {
         if(attempt.get(10, java.util.concurrent.TimeUnit.SECONDS) instanceof EchoService.Acceptance)
         {
            successes++;
         }
      }
      assertEquals(1, successes);
      assertEquals(1, requests.size());
      assertEquals("QUEUED", scanRow(context)[0]);
   }



   /***************************************************************************
    ** Authority lost after the owner accepted the job still records the trusted
    ** queue receipt (not ATTENTION) but never acknowledges or does more work.
    ***************************************************************************/
   @Test
   void authorityLostAfterAcceptanceKeepsTheDurableReceiptWithoutAcknowledgment() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "revoked-after-accept");
      afterAccept = mobile::otherOperator;
      assertThrows(QException.class, () -> config.service.print(run, context));
      assertEquals(1, requests.size());
      String[] row = scanRow(context);
      assertEquals("QUEUED", row[0]);
      assertEquals("job:" + context.get("scanId"), row[1]);
   }



   /***************************************************************************
    ** The read-only recovery path records its trusted receipt before the final
    ** authority recheck, so a late revocation cannot lose it either.
    ***************************************************************************/
   @Test
   void authorityLostDuringLookupRecoveryKeepsTheDurableReceipt() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "revoked-during-lookup");
      uncertain = true;
      assertThrows(QException.class, () -> config.service.print(run, context));
      assertEquals("ATTENTION", scanRow(context)[0]);
      uncertain = false;
      afterLookup = mobile::otherOperator;
      assertThrows(QException.class, () -> config.service.print(run, context));
      assertEquals(1, requests.size());
      String[] row = scanRow(context);
      assertEquals("QUEUED", row[0]);
      assertEquals("job:" + context.get("scanId"), row[1]);
   }



   /***************************************************************************
    ** Direct fixture inspection of the example's own state table.
    ***************************************************************************/
   private String[] scanRow(Map<String, Serializable> context) throws Exception
   {
      try(Connection connection = dataSource.getConnection(); var query = connection.prepareStatement("SELECT state,printJobId FROM echo_scan WHERE id=?"))
      {
         query.setString(1, String.valueOf(context.get("scanId")));
         try(var rows = query.executeQuery())
         {
            assertTrue(rows.next());
            return new String[] {rows.getString("state"), rows.getString("printJobId")};
         }
      }
   }



   /***************************************************************************
    ** Fixture-only state manipulation standing in for a competing attempt.
    ***************************************************************************/
   private void sql(String statement)
   {
      try(Connection connection = dataSource.getConnection(); var command = connection.createStatement())
      {
         command.executeUpdate(statement);
      }
      catch(java.sql.SQLException failure)
      {
         throw new IllegalStateException(failure);
      }
   }



   /***************************************************************************
    ** Setup requires a document that is numeric, authorized and compatible; a
    ** missing, unknown, withdrawn or malformed choice never starts printing.
    ***************************************************************************/
   @Test
   void setupRequiresAnAuthorizedCompatibleDocument() throws Exception
   {
      for(Integer invalid : new Integer[] { 999, 0, -7, null })
      {
         assertThrows(QException.class, () -> config.service.start("printer", "4x6", invalid));
      }
      // the typed INTEGER field accepts only integral values; text forms must be plain digits
      assertEquals(7, EchoSteps.documentId(7));
      assertEquals(7, EchoSteps.documentId(7L));
      assertEquals(7, EchoSteps.documentId("7"));
      for(java.io.Serializable invalid : new java.io.Serializable[] { null, " ", "abc", "7.5", "-7", "0", "1e1", "1".repeat(11), "99999999999", 7.5, 7.0f, 0, -1, Long.MAX_VALUE, true })
      {
         assertThrows(QException.class, () -> EchoSteps.documentId(invalid));
      }
      RunProcessOutput typed = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput garbage = resume(typed, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", "abc");
      assertThrows(QException.class, () -> new RunProcessAction().execute(garbage));
      documentWithdrawn = true;
      assertThrows(QException.class, () -> config.service.start("printer", "4x6", 7));
      documentWithdrawn = false;
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput withoutDocument = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6");
      assertThrows(QException.class, () -> new RunProcessAction().execute(withoutDocument));
      assertTrue(requests.isEmpty());
      var step = (com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData) QContext.getQInstance().getProcess(EchoQBitProducer.PROCESS).getStep("setup");
      var field = step.getFormFields().stream().filter(candidate -> "templateId".equals(candidate.getName())).findFirst().orElseThrow();
      assertEquals("Document", field.getLabel());
      assertTrue(field.getIsRequired());
      assertEquals(EchoDocumentChoices.NAME, field.getPossibleValueSourceName());
   }



   /***************************************************************************
    ** The native possible-value search returns exactly the printer-and-size
    ** scoped, authorized documents; unknown printers and sizes are refused.
    ***************************************************************************/
   @Test
   void documentChoicesComeFromTheNativePossibleValueSearch() throws Exception
   {
      var all = documentSearch("printer", "4x6", null);
      assertEquals(List.of("7", "8"), all.stream().map(value -> String.valueOf(value.getId())).toList());
      assertEquals(List.of("Label A", "Label B"), all.stream().map(value -> value.getLabel()).toList());
      assertEquals(List.of("8"), documentSearch("printer", "4x6", "Label B").stream().map(value -> String.valueOf(value.getId())).toList());
      assertTrue(all.getFirst().getId() instanceof Integer, "Document ids are true integers.");
      assertEquals(List.of(8), idSearch(List.of("8")));
      assertEquals(List.of(8), idSearch(List.of(8)));
      assertEquals(List.of(), idSearch(List.of("99")));
      assertThrows(QException.class, () -> idSearch(List.of("abc")));
      assertThrows(QException.class, () -> idSearch(List.of("7.5")));
      assertTrue(documentSearch(null, "4x6", null).isEmpty());
      assertTrue(documentSearch("printer", null, null).isEmpty());
      assertThrows(QException.class, () -> documentSearch("forged", "4x6", null));
      assertThrows(QException.class, () -> documentSearch("printer", "9x9", null));
      documentWithdrawn = true;
      assertTrue(documentSearch("printer", "4x6", null).isEmpty());
   }



   /***************************************************************************
    ** Exact-id lookups arrive as strings over HTTP and must coerce strictly.
    ***************************************************************************/
   private List<Object> idSearch(List<Serializable> ids) throws QException
   {
      var input = new com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput();
      input.setPossibleValueSourceName(EchoDocumentChoices.NAME);
      input.setOtherValues(new HashMap<>(Map.of("printerId", "printer", "mediaId", "4x6")));
      input.setIdList(ids);
      return new com.kingsrook.qqq.backend.core.actions.values.SearchPossibleValueSourceAction().execute(input).getResults().stream().map(value -> (Object) value.getId()).toList();
   }



   /***************************************************************************
    ** Run the ordinary possible-value search action with dependency values.
    ***************************************************************************/
   private List<com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue<?>> documentSearch(String printer, String media, String term) throws QException
   {
      var input = new com.kingsrook.qqq.backend.core.model.actions.values.SearchPossibleValueSourceInput();
      input.setPossibleValueSourceName(EchoDocumentChoices.NAME);
      Map<String, Serializable> others = new HashMap<>();
      if(printer != null)
      {
         others.put("printerId", printer);
      }
      if(media != null)
      {
         others.put("mediaId", media);
      }
      input.setOtherValues(others);
      input.setSearchTerm(term);
      return new com.kingsrook.qqq.backend.core.actions.values.SearchPossibleValueSourceAction().execute(input).getResults();
   }



   /***************************************************************************
    ** The document chosen at setup serves every scan in the run; neither a
    ** repeated setup call nor a forged process value can retarget it.
    ***************************************************************************/
   @Test
   void chosenDocumentIsUsedForEveryScanAndCannotBeRetargeted() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput selection = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", 8);
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      String run = scanning.getValueString("echoRunId");
      RunProcessOutput first = scan(scanning, "CODE128", "first-value");
      assertThrows(QException.class, () -> config.service.select(run, "printer", "4x6", 7));
      RunProcessInput forged = resume(first, "scan");
      forged.getValues().putAll(event("QRCODE", "second-value"));
      forged.addValue("templateId", 7);
      new RunProcessAction().execute(forged);
      assertEquals(2, requests.size());
      assertEquals(List.of(8, 8), plannedDocuments);
      assertEquals(java.util.Set.of(8), acceptedPlans.values().stream().map(plan -> plan.templateId()).collect(java.util.stream.Collectors.toSet()));
      assertEquals(java.util.Set.of(80), acceptedPlans.values().stream().map(plan -> plan.templateRevisionId()).collect(java.util.stream.Collectors.toSet()));
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT templateId,templateRevisionId FROM echo_run"))
      {
         assertTrue(rows.next());
         assertEquals(8, rows.getInt(1));
         assertEquals(80, rows.getInt(2));
         assertFalse(rows.next());
      }
   }



   /***************************************************************************
    ** A republished or withdrawn document fails visibly on the next scan, never
    ** substituting another, while leaving remains available and a retry resumes.
    ***************************************************************************/
   @Test
   void changedOrWithdrawnDocumentFailsVisiblyWithoutSubstitution() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      config.service.print(run, captured("CODE128", "before-change"));
      Map<String, Serializable> next = captured("CODE128", "after-change");
      documentRevisionShift = 1;
      assertThrows(QException.class, () -> config.service.print(run, next));
      documentRevisionShift = 0;
      documentWithdrawn = true;
      assertThrows(QException.class, () -> config.service.print(run, next));
      assertEquals(1, requests.size());
      assertEquals(List.of(7, 7, 7), plannedDocuments);
      documentWithdrawn = false;
      config.service.print(run, next);
      assertEquals(2, requests.size());
      config.service.close(run);
   }



   /***************************************************************************
    ** QQQ empties a process's stored state (and so its ownership record) when
    ** step code throws. Business refusals are therefore recorded on the step
    ** output: a bad setup choice can be corrected in the same process, a failed
    ** scan leaves it resumable, and Leave still closes the run.
    ***************************************************************************/
   @Test
   void businessRefusalsNeverTrapTheProcessAndLeaveStillWorks() throws Exception
   {
      RunProcessOutput setup = new RunProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER).withProcessName(EchoQBitProducer.PROCESS));
      RunProcessInput bad = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", 999);
      assertThrows(QException.class, () -> new RunProcessAction().execute(bad));
      RunProcessInput good = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", 8);
      RunProcessOutput scanning = new RunProcessAction().execute(good);
      RunProcessOutput first = scan(scanning, "CODE128", "before-failure");
      documentWithdrawn = true;
      assertThrows(QException.class, () -> scan(first, "QRCODE", "refused"));
      documentWithdrawn = false;
      assertEquals(1, requests.size());
      new CancelProcessAction().execute(new RunProcessInput().withInputSource(com.kingsrook.qqq.backend.core.model.actions.tables.QInputSource.USER)
         .withProcessName(EchoQBitProducer.PROCESS).withProcessUUID(scanning.getProcessUUID()));
      try(Connection connection = dataSource.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT state FROM echo_run"))
      {
         assertTrue(rows.next());
         assertEquals("CLOSED", rows.getString(1));
         assertFalse(rows.next());
      }
   }



   /***************************************************************************
    ** Re-running the schema script keeps existing runs, which keep a NULL
    ** document and so cannot print until the operator chooses one again.
    ***************************************************************************/
   @Test
   void documentMigrationPreservesExistingRuns() throws Exception
   {
      try(Connection connection = dataSource.getConnection(); var command = connection.createStatement(); var resource = getClass().getResourceAsStream("/db/echo-h2.sql"))
      {
         command.executeUpdate("INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES ('old-run','operator','d','s','printer','4x6','OPEN')");
         for(String statement : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";"))
         {
            if(!statement.isBlank())
            {
               command.execute(statement);
            }
         }
         try(var rows = command.executeQuery("SELECT templateId,templateRevisionId FROM echo_run WHERE id='old-run'"))
         {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
            assertTrue(rows.wasNull());
         }
      }
   }



   /***************************************************************************
    ** Forged IDs and later destination revocation refuse before queue effects.
    ***************************************************************************/
   @Test
   void destinationAuthorityAndRunOwnershipAreRechecked() throws Exception
   {
      assertThrows(QException.class, () -> config.service.start("forged", "4x6", 7));
      String run = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
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
      RunProcessInput selection = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", 7);
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
      RunProcessInput selection = resume(setup, "setup").withValue("printerId", "printer").withValue("mediaId", "4x6").withValue("templateId", 7);
      RunProcessOutput scanning = new RunProcessAction().execute(selection);
      String otherRun = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
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
      String driftRun = config.service.start("printer", "4x6", 7);
      mediaRevision = "b".repeat(64);
      assertThrows(QException.class, () -> config.service.print(driftRun, captured("CODE128", "drift")));
      mediaRevision = "a".repeat(64);
      String smallRun = config.service.start("printer", "4x6", 7);
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
      String run = config.service.start("printer", "4x6", 7);
      assertThrows(QException.class, () -> config.service.select(run, "printer2", "4x6", 7));
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
      String run = config.service.start("printer", "4x6", 7);
      var acceptedScan = config.service.print(run, captured("CODE128", "000001"));
      var own = source(acceptedScan.scanId());
      assertNotNull(own);
      assertEquals("operator", own.getValueString("actorId"));

      // core's capability/discovery READ probe is the exact base input; writes and unknown subclasses stay refused
      var probe = new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput();
      assertTrue(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasTablePermission(probe, EchoQBitProducer.SCAN_TABLE,
         com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType.READ));
      var scanTable = QContext.getQInstance().getTable(EchoQBitProducer.SCAN_TABLE);
      var checker = new EchoSourceReadPermissions();
      checker.checkPermissionsThrowing(probe, scanTable);
      checker.checkPermissionsThrowing(null, scanTable);
      assertThrows(QException.class, () -> checker.checkPermissionsThrowing(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput()
      {
      }, scanTable));
      assertThrows(QException.class, () -> checker.checkPermissionsThrowing(new com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput(), scanTable));
      assertThrows(QException.class, () -> checker.checkPermissionsThrowing(new com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput(), scanTable));
      assertThrows(QException.class, () -> checker.checkPermissionsThrowing(new com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput(), scanTable));
      assertThrows(QException.class, () -> new com.kingsrook.qqq.backend.core.actions.tables.UpdateAction().execute(
         new com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput().withTableName(EchoQBitProducer.SCAN_TABLE)
            .withRecord(new com.kingsrook.qqq.backend.core.model.data.QRecord().withValue("id", acceptedScan.scanId()).withValue("barcode", "forged"))));
      assertEquals("000001", source(acceptedScan.scanId()).getValueString("barcode"));
      assertThrows(QException.class, () -> new com.kingsrook.qqq.backend.core.actions.tables.InsertAction().execute(
         new com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput().withTableName(EchoQBitProducer.SCAN_TABLE)
            .withRecord(new com.kingsrook.qqq.backend.core.model.data.QRecord().withValue("id", "forged"))));
      mobile.otherOperator();
      QContext.getQSession().withSecurityKeyValue("qqq.session.userId", "operator");
      assertEquals(null, source(acceptedScan.scanId()));
      assertTrue(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasTablePermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(),
         EchoQBitProducer.SCAN_TABLE, com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType.READ));
   }



   /***************************************************************************
    ** Leaving capture does not remove the original actor's ordinary source-read
    ** right, and a desktop read cannot acquire process or capture authority.
    ***************************************************************************/
   @Test
   void desktopOriginalActorCanReadRetainedSourceWithoutScannerAuthority() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      Map<String, Serializable> context = captured("CODE128", "000001");
      var acceptedScan = config.service.print(run, context);
      config.service.close(run);
      desktop("operator", true);
      assertEquals("000001", source(acceptedScan.scanId()).getValueString("barcode"));
      assertThrows(QException.class, () -> config.service.print(run, context));
      assertThrows(QException.class, () -> new EchoPermissions().checkPermissionsThrowing(
         new RunProcessInput().withProcessName(EchoQBitProducer.PROCESS), QContext.getQInstance().getProcess(EchoQBitProducer.PROCESS)));
      assertThrows(QException.class, () -> new com.kingsrook.qqq.backend.core.actions.tables.UpdateAction().execute(
         new com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput().withTableName(EchoQBitProducer.SCAN_TABLE)
            .withRecord(new com.kingsrook.qqq.backend.core.model.data.QRecord().withValue("id", acceptedScan.scanId()).withValue("barcode", "forged"))));
      assertThrows(QException.class, () -> new com.kingsrook.qqq.backend.core.actions.tables.DeleteAction().execute(
         new com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput().withTableName(EchoQBitProducer.SCAN_TABLE)
            .withPrimaryKey(acceptedScan.scanId())));
      assertEquals("000001", source(acceptedScan.scanId()).getValueString("barcode"));
   }



   /***************************************************************************
    ** Current grants and actual actor identity govern each retained source read;
    ** an old operator identity or spoofed security key cannot restore access.
    ***************************************************************************/
   @Test
   void desktopRevokedOrDifferentActorCannotReadRetainedSource() throws Exception
   {
      String run = config.service.start("printer", "4x6", 7);
      var acceptedScan = config.service.print(run, captured("CODE128", "000001"));
      config.service.close(run);
      desktop("operator", false);
      assertThrows(QException.class, () -> source(acceptedScan.scanId()));
      assertFalse(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasTablePermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(),
         EchoQBitProducer.SCAN_TABLE, com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType.READ));
      desktop("other-operator", true);
      QContext.getQSession().withSecurityKeyValue("qqq.session.userId", "operator");
      assertEquals(null, source(acceptedScan.scanId()));
      desktop("operator", true);
      QContext.getQSession().setUser(null);
      assertThrows(QException.class, () -> source(acceptedScan.scanId()));
   }



   /***************************************************************************
    ** A fresh desktop session deliberately has no approved-device request scope
    ** or mobile.scan grant; it contains only the current native actor's rights.
    ***************************************************************************/
   private void desktop(String actor, boolean allowed)
   {
      QInstance instance = QContext.getQInstance();
      EchoMobileFixture.clear();
      var session = new com.kingsrook.qqq.backend.core.model.session.QSession()
         .withUser(new com.kingsrook.qqq.backend.core.model.session.QUser().withIdReference(actor));
      session.setUuid(UUID.randomUUID().toString());
      if(allowed)
      {
         session.withPermission(EchoQBitProducer.PERMISSION);
      }
      QContext.init(instance, session);
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
