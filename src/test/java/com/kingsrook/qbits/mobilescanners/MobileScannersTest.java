/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Real H2 persistence, permission boundaries and asynchronous device isolation.
 *******************************************************************************/
class MobileScannersTest
{
   private QInstance instance;
   private QSession scanner;
   private QSession admin;
   private String jdbcUrl;



   /***************************************************************************
    ** Each test receives an isolated database using the shipped schema.
    ***************************************************************************/
   @BeforeEach
   void setup() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      jdbcUrl = "jdbc:h2:mem:mobile_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
      instance = new QInstance();
      instance.setEnforcePermissionsInCoreActions(true);
      instance.registerAuthenticationProvider(AuthScope.instanceDefault(), new QAuthenticationMetaData().withName("test").withType(QAuthenticationType.MOCK));
      instance.addBackend(new RDBMSBackendMetaData().withName("db").withVendor("h2").withJdbcUrl(jdbcUrl).withUsername("sa"));
      new MobileScannersQBitProducer().withConfig(new MobileScannersQBitConfig().withBackendName("db")).produce(instance, "mobile");
      for(var table : instance.getTables().values())
      {
         table.setBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
      }
      new QInstanceValidator().validate(instance);
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         String schema = new String(getClass().getResourceAsStream("/db/mobile-scanners-h2.sql").readAllBytes(), StandardCharsets.UTF_8);
         for(String statement : schema.split(";"))
         {
            if(!statement.isBlank())
            {
               connection.createStatement().execute(statement);
            }
         }
      }
      scanner = new QSession().withUser(new QUser().withIdReference("scanner")).withPermission(MobilePermissions.SCAN);
      scanner.setUuid(UUID.randomUUID().toString());
      admin = new QSession().withUser(new QUser().withIdReference("admin")).withPermissions(MobilePermissions.ADMIN, MobilePermissions.SCAN);
      admin.setUuid(UUID.randomUUID().toString());
      QContext.init(instance, scanner);
   }



   /***************************************************************************
    ** No request device or QQQ thread state survives test boundaries.
    ***************************************************************************/
   @AfterEach
   void clear()
   {
      MobilePermissions.REQUEST_DEVICE.remove();
      QContext.clear();
   }



   /***************************************************************************
    ** Pending, revoked and invalid credentials cannot grant scanning access.
    ***************************************************************************/
   @Test
   void enrollmentApprovalAndRevocation() throws Exception
   {
      Map<String, Object> enrollment = enroll();
      String id = (String) enrollment.get("deviceId");
      String token = (String) enrollment.get("deviceToken");
      assertEquals("PENDING", MobileService.authenticateDevice(id, token).getValueString("status"));
      assertThrows(QException.class, () -> MobileService.authenticateDevice(id, "invalid"));
      assertThrows(QException.class, () -> bind(id));
      assertThrows(QException.class, () -> MobileService.setApproval(id, true));
      QContext.init(instance, admin);
      MobileService.setApproval(id, true);
      QContext.init(instance, scanner);
      bind(id);
      assertInstanceOf(VerifiedDeviceSession.class, QContext.getQSession());
      assertFalse(scanner instanceof VerifiedDeviceSession);
      QSession bound = QContext.getQSession();
      QContext.init(instance, admin);
      MobileService.setApproval(id, false);
      QContext.init(instance, bound);
      assertThrows(QException.class, () -> MobileService.capture(scan()));
   }



   /***************************************************************************
    ** Identical transport retry inserts once; physical rescans get distinct IDs.
    ***************************************************************************/
   @Test
   void captureIsIdempotentAndServerStampsIdentity() throws Exception
   {
      String id = approvedDevice();
      bind(id);
      Map<String, Serializable> input = scan();
      input.put("userId", "forged");
      input.put("deviceId", "forged");
      QRecord first = MobileService.capture(input);
      QRecord retry = MobileService.capture(input);
      assertEquals(first.getValueString("id"), retry.getValueString("id"));
      assertEquals("scanner", first.getValueString("userId"));
      assertEquals(id, first.getValueString("deviceId"));
      assertEquals("00001234", first.getValueString("barcode"));
      assertNotNull(first.getValue("receivedAt"));
      input.put("barcode", "another");
      assertThrows(QException.class, () -> MobileService.capture(input));
      input.put("eventId", UUID.randomUUID().toString());
      assertNotEquals(first.getValueString("id"), MobileService.capture(input).getValueString("id"));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM mobileScan");
         assertTrue(result.next());
         assertEquals(2, result.getInt(1));
      }
   }



   /***************************************************************************
    ** Same text in different symbologies is distinct; retries bind the exact type.
    ***************************************************************************/
   @Test
   void scanTypesArePreservedAndChangedRetryTypesAreRejected() throws Exception
   {
      bind(approvedDevice());
      Map<String, Serializable> input = scan();
      QRecord linear = MobileService.capture(input);
      assertThrows(QException.class, () -> MobileService.capture(input, "anotherProcess"));
      input.put("symbology", "QR_CODE");
      assertThrows(QException.class, () -> MobileService.capture(input));
      input.put("eventId", UUID.randomUUID().toString());
      QRecord matrix = MobileService.capture(input);
      assertEquals(linear.getValueString("barcode"), matrix.getValueString("barcode"));
      assertEquals("CODE128", linear.getValueString("symbology"));
      assertEquals("QR_CODE", matrix.getValueString("symbology"));
      assertNotEquals(linear.getValueString("id"), matrix.getValueString("id"));
      input.put("eventId", UUID.randomUUID().toString());
      assertEquals("anotherProcess", MobileService.capture(input, "anotherProcess").getValueString("processName"));
   }



   /***************************************************************************
    ** Legacy NULLs normalize without assigning a new claimed decoder on retry.
    ***************************************************************************/
   @Test
   void missingTypesAndHistoricalNullsRemainExplicitAndIdempotent() throws Exception
   {
      bind(approvedDevice());
      Map<String, Serializable> input = scan();
      input.remove("symbology");
      input.remove("scanSource");
      QRecord saved = MobileService.capture(input);
      assertEquals("UNKNOWN", saved.getValueString("symbology"));
      assertEquals("UNKNOWN", saved.getValueString("scanSource"));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         var statement = connection.prepareStatement("UPDATE mobileScan SET symbology = NULL, scanSource = NULL WHERE id = ?"))
      {
         statement.setString(1, saved.getValueString("id"));
         assertEquals(1, statement.executeUpdate());
      }
      QRecord retry = MobileService.capture(input);
      assertEquals(saved.getValueString("id"), retry.getValueString("id"));
      assertEquals("UNKNOWN", retry.getValueString("symbology"));
      input.put("symbology", "CODE128");
      assertThrows(QException.class, () -> MobileService.capture(input));
      input.put("eventId", UUID.randomUUID().toString());
      input.remove("symbology");
      input.put("scanSource", "manual");
      assertEquals("MANUAL", MobileService.capture(input).getValueString("symbology"));
      input.put("symbology", "MANUAL");
      assertEquals("MANUAL", MobileService.capture(input).getValueString("symbology"));
   }



   /***************************************************************************
    ** Ordinary process state cannot reuse the preceding event's decoder type.
    ***************************************************************************/
   @Test
   void processCaptureConsumesRawFieldsAndReplacesForgedContext() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      RunBackendStepInput input = new RunBackendStepInput().withProcessName(MobileScannersQBitProducer.CAPTURE).withValues(scan());
      Map<String, Serializable> first = MobileScannerActions.capture(input);
      assertEquals("CODE128", first.get("symbology"));
      assertFalse(input.getValues().containsKey("symbology"));
      assertFalse(input.getValues().containsKey("barcode"));
      input.addValue("scanContext", new HashMap<>(Map.of("symbology", "QR_CODE", "userId", "forged-user", "deviceId", "forged-device")));
      input.addValue("scanSaved", true);
      input.addValue("acknowledgedEventId", first.get("eventId"));
      input.addValue("barcode", "00001234");
      input.addValue("eventId", UUID.randomUUID().toString());
      input.addValue("capturedAt", "2026-11-01T12:00:00Z");
      Map<String, Serializable> legacy = MobileScannerActions.capture(input);
      assertEquals("UNKNOWN", legacy.get("symbology"));
      assertEquals("UNKNOWN", legacy.get("scanSource"));
      assertEquals("scanner", legacy.get("userId"));
      assertEquals(device, legacy.get("deviceId"));
      assertFalse(input.getValues().containsKey("scanContext"));
      input.addValue("barcode", legacy.get("barcode"));
      input.addValue("eventId", legacy.get("eventId"));
      input.addValue("capturedAt", legacy.get("capturedAt"));
      input.addValue("symbology", "QR_CODE");
      assertThrows(QException.class, () -> MobileScannerActions.capture(input));
      assertFalse(input.getValues().containsKey("symbology"), "A rejected event must not contaminate the following submission.");
   }



   /***************************************************************************
    ** Pre-context saved state must be reopened rather than reuse stale input.
    ***************************************************************************/
   @Test
   void preContextProcessStateRequiresRestartWithoutSaving() throws Exception
   {
      bind(approvedDevice());
      RunBackendStepInput input = new RunBackendStepInput().withProcessName(MobileScannersQBitProducer.CAPTURE).withValues(scan());
      input.addValue("scanSaved", true);
      input.addValue("acknowledgedEventId", UUID.randomUUID().toString());
      input.addValue("symbology", "QR_CODE");
      assertTrue(assertThrows(QException.class, () -> MobileScannerActions.capture(input)).getMessage().contains("Restart the scanner process"));
      assertThrows(QException.class, () -> MobileScannerActions.capture(input));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
         var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM mobileScan"))
      {
         assertTrue(rows.next());
         assertEquals(0, rows.getInt(1));
      }
   }



   /***************************************************************************
    ** Application attribution is registered backend configuration, not user input.
    ***************************************************************************/
   @Test
   void hostApplicationContextRejectsUnknownAppsAndCrossAppRetries() throws Exception
   {
      bind(approvedDevice());
      instance.addApp(new QAppMetaData().withName("hostScanner"));
      Map<String, Serializable> submitted = scan();
      submitted.put("appId", "forged-client-app");
      RunBackendStepInput input = new RunBackendStepInput().withProcessName(MobileScannersQBitProducer.CAPTURE).withValues(new HashMap<>(submitted));
      assertThrows(QException.class, () -> MobileScannerActions.capture(input, "unregistered-app"));
      Map<String, Serializable> accepted = MobileScannerActions.capture(input, "hostScanner");
      assertEquals("hostScanner", accepted.get("appId"));
      input.setValues(new HashMap<>(submitted));
      assertEquals(accepted.get("scanId"), MobileScannerActions.capture(input, "hostScanner").get("scanId"));
      input.setValues(new HashMap<>(submitted));
      assertThrows(QException.class, () -> MobileScannerActions.capture(input, MobileScannersQBitProducer.APP));
   }



   /***************************************************************************
    ** Client values cannot fabricate a trusted device session.
    ***************************************************************************/
   @Test
   void rawWritesAndCredentialReadsAreDenied() throws Exception
   {
      Map<String, Object> enrollment = enroll();
      String id = (String) enrollment.get("deviceId");
      QContext.init(instance, admin);
      InsertInput insert = new InsertInput().withTableName(MobileScannersQBitProducer.SCAN).withRecord(new QRecord().withValue("id", "forged"));
      assertThrows(QException.class, () -> new InsertAction().execute(insert));
      GetInput get = new GetInput();
      get.setTableName(MobileScannersQBitProducer.CREDENTIAL);
      get.setPrimaryKey(id);
      get.setShouldOmitHiddenFields(false);
      assertThrows(QException.class, () -> new GetAction().execute(get));
      QContext.init(instance, scanner.withValue("mobile.scanners.verifiedDevice", id));
      assertThrows(QException.class, () -> MobileService.capture(scan()));
      assertThrows(MobileService.EnrollmentConflictException.class, () -> MobileService.enroll(Map.of("installationId", id)));
   }



   /***************************************************************************
    ** QQQ's capture/handoff keeps trusted identity without leaking to sessions.
    ***************************************************************************/
   @Test
   void deviceBindingSurvivesWorkerHandoff() throws Exception
   {
      String id = approvedDevice();
      bind(id);
      var context = QContext.capture();
      MobilePermissions.REQUEST_DEVICE.remove();
      String savedDevice = CompletableFuture.supplyAsync(() ->
      {
         QContext.init(context);
         try
         {
            PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput().withProcessName(MobileScannersQBitProducer.CAPTURE), MobileScannersQBitProducer.CAPTURE);
            return MobileService.capture(scan()).getValueString("deviceId");
         }
         catch(Exception e)
         {
            throw new IllegalStateException(e);
         }
         finally
         {
            QContext.clear();
         }
      }).get();
      assertEquals(id, savedDevice);
      QContext.init(instance, scanner);
      assertThrows(QException.class, () -> MobileService.capture(scan()));
   }



   /***************************************************************************
    ** Separate worker requests contend on the durable primary key, not a JVM lock.
    ***************************************************************************/
   @Test
   void concurrentRetriesPersistExactlyOnce() throws Exception
   {
      String id = approvedDevice();
      bind(id);
      var context = QContext.capture();
      var payload = scan();
      java.util.List<CompletableFuture<String>> futures = new java.util.ArrayList<>();
      for(int i = 0; i < 4; i++)
      {
         futures.add(CompletableFuture.supplyAsync(() ->
         {
            QContext.init(context);
            try
            {
               return MobileService.capture(payload).getValueString("id");
            }
            catch(QException e)
            {
               throw new IllegalStateException(e);
            }
            finally
            {
               QContext.clear();
            }
         }));
      }
      String savedId = futures.getFirst().get();
      for(var future : futures)
      {
         assertEquals(savedId, future.get());
      }
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM mobileScan");
         assertTrue(result.next());
         assertEquals(1, result.getInt(1));
      }
   }



   /***************************************************************************
    ** Retry acknowledgment is scoped to the original authenticated user/device.
    ***************************************************************************/
   @Test
   void sameEventCannotBeReplayedAsAnotherUser() throws Exception
   {
      String id = approvedDevice();
      bind(id);
      Map<String, Serializable> payload = scan();
      MobileService.capture(payload);
      QSession secondUser = new QSession().withUser(new QUser().withIdReference("other-scanner")).withPermission(MobilePermissions.SCAN);
      QContext.init(instance, secondUser);
      bind(id);
      assertThrows(QException.class, () -> MobileService.capture(payload));
      payload.put("eventId", UUID.randomUUID().toString());
      assertEquals("other-scanner", MobileService.capture(payload).getValueString("userId"));
      assertFalse(secondUser instanceof VerifiedDeviceSession);
      assertFalse(scanner instanceof VerifiedDeviceSession);
   }



   /***************************************************************************
    ** A credential-only interrupted enrollment has an explicit recovery response.
    ***************************************************************************/
   @Test
   void interruptedEnrollmentRequiresNewInstallationIdentity() throws Exception
   {
      String id = UUID.randomUUID().toString();
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         var statement = connection.prepareStatement("INSERT INTO mobileDeviceCredential(id, tokenHash) VALUES (?, ?)");
         statement.setString(1, id);
         statement.setString(2, "0".repeat(64));
         statement.executeUpdate();
      }
      assertThrows(MobileService.EnrollmentConflictException.class, () -> MobileService.enroll(Map.of("installationId", id)));
   }



   /***************************************************************************
    ** A realistic installation, with no hardware identifier requirement.
    ***************************************************************************/
   private Map<String, Object> enroll() throws QException
   {
      return MobileService.enroll(Map.of("installationId", UUID.randomUUID().toString(), "displayName", "Test TC22", "manufacturer", "Zebra", "model", "TC22"));
   }



   /***************************************************************************
    ** Record-screen launches prefill the selected device without applying early.
    ***************************************************************************/
   @Test
   void recordLaunchPrefillsDeviceBeforeConfirmation() throws Exception
   {
      String id = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      var launched = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "recordIds").withValue("recordIds", id).withValue("tableName", MobileScannersQBitProducer.DEVICE));
      assertEquals(id, launched.getValue("deviceId"));
      assertEquals("PENDING", deviceStatus(id));
      RunProcessInput confirmed = new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withProcessUUID(launched.getProcessUUID()).withStartAfterStep("input");
      confirmed.getValues().putAll(launched.getValues());
      new RunProcessAction().execute(confirmed);
      assertEquals("APPROVED", deviceStatus(id));
   }



   /***************************************************************************
    ** Bulk selection updates all reviewed devices and reports a visible result.
    ***************************************************************************/
   @Test
   void bulkApprovalAndRevocationApplyOnlyAfterConfirmation() throws Exception
   {
      String first = (String) enroll().get("deviceId");
      String second = (String) enroll().get("deviceId");
      String selection = first + "," + second;
      QContext.init(instance, admin);
      for(String process : java.util.List.of(MobileScannersQBitProducer.APPROVE, MobileScannersQBitProducer.REVOKE))
      {
         var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(process)
            .withValue("recordsParam", "recordIds").withValue("recordIds", selection).withValue("tableName", MobileScannersQBitProducer.DEVICE));
         assertEquals(selection, launch.getValue("deviceId"));
         assertTrue(launch.getValueString("selectionSummary").startsWith("2 device(s) selected:"));
         String before = process.equals(MobileScannersQBitProducer.APPROVE) ? "PENDING" : "APPROVED";
         assertEquals(before, deviceStatus(first));
         assertEquals(before, deviceStatus(second));
         var applied = confirmAdmin(process, launch);
         String after = process.equals(MobileScannersQBitProducer.APPROVE) ? "APPROVED" : "REVOKED";
         assertEquals(after, deviceStatus(first));
         assertEquals(after, deviceStatus(second));
         assertEquals(2, applied.getValue("processedDeviceCount"));
         assertEquals(process.equals(MobileScannersQBitProducer.APPROVE) ? "2 devices approved." : "2 devices revoked.", applied.getValue("result"));
      }
   }



   /***************************************************************************
    ** Scanner users cannot launch or resume an administrative selection.
    ***************************************************************************/
   @Test
   void selectedDeviceProcessesRequireAdminAtLaunchAndApply() throws Exception
   {
      String id = (String) enroll().get("deviceId");
      for(String process : java.util.List.of(MobileScannersQBitProducer.APPROVE, MobileScannersQBitProducer.REVOKE))
      {
         assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(process)
            .withValue("recordsParam", "recordIds").withValue("recordIds", id)));
         QContext.init(instance, admin);
         var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(process)
            .withValue("recordsParam", "recordIds").withValue("recordIds", id));
         QContext.init(instance, scanner);
         assertThrows(QException.class, () -> confirmAdmin(process, launch));
         assertEquals("PENDING", deviceStatus(id));
      }
   }



   /***************************************************************************
    ** Invalid bulk targets fail before changing any otherwise valid device.
    ***************************************************************************/
   @Test
   void invalidSelectionAndManualTargetsDoNotPartiallyApply() throws Exception
   {
      String id = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      String missing = UUID.randomUUID().toString();
      assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "recordIds").withValue("recordIds", id + "," + missing)));
      assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "unknownSelection").withValue("filterJSON", "{}")));
      assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "recordIds").withValue("recordIds", id).withValue("tableName", MobileScannersQBitProducer.SCAN)));
      var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE));
      launch.addValue("deviceId", id + "," + missing);
      assertThrows(QException.class, () -> confirmAdmin(MobileScannersQBitProducer.APPROVE, launch));
      assertEquals("PENDING", deviceStatus(id));
   }



   /***************************************************************************
    ** Standalone administration retains manual device ID entry.
    ***************************************************************************/
   @Test
   void standaloneAdminProcessAcceptsManualDeviceId() throws Exception
   {
      String id = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE));
      launch.addValue("deviceId", id);
      var applied = confirmAdmin(MobileScannersQBitProducer.APPROVE, launch);
      assertEquals("APPROVED", deviceStatus(id));
      assertEquals("1 device approved.", applied.getValue("result"));
   }



   /***************************************************************************
    ** Resume the real process state at the normal confirmation boundary.
    ***************************************************************************/
   private RunProcessOutput confirmAdmin(String process, RunProcessOutput launch) throws QException
   {
      RunProcessInput input = new RunProcessInput().withProcessName(process).withProcessUUID(launch.getProcessUUID()).withStartAfterStep("input");
      input.getValues().putAll(launch.getValues());
      return new RunProcessAction().execute(input);
   }



   /***************************************************************************
    ** Select-all snapshots are stable when records or submitted values change.
    ***************************************************************************/
   @Test
   void filterSelectionUsesReviewedSnapshotAndIgnoresSubmittedReplacement() throws Exception
   {
      String first = (String) enroll().get("deviceId");
      String second = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "filterJSON").withValue("filterJSON", "{}"));
      String later = (String) enroll().get("deviceId");
      launch.addValue("deviceId", later);
      launch.addValue("recordIds", later);
      var applied = confirmAdmin(MobileScannersQBitProducer.APPROVE, launch);
      assertEquals(2, applied.getValue("processedDeviceCount"));
      assertEquals("APPROVED", deviceStatus(first));
      assertEquals("APPROVED", deviceStatus(second));
      assertEquals("PENDING", deviceStatus(later));
   }



   /***************************************************************************
    ** First-N limits are preserved and an empty selection never becomes manual.
    ***************************************************************************/
   @Test
   void filteredFirstSelectionHonorsLimitAndRejectsEmptyOrMalformedFilters() throws Exception
   {
      enroll();
      enroll();
      QContext.init(instance, admin);
      var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "filterJSON").withValue("filterJSON", "{\"limit\":1}"));
      assertEquals(1, launch.getRecords().size());
      assertEquals(1, confirmAdmin(MobileScannersQBitProducer.APPROVE, launch).getValue("processedDeviceCount"));
      for(String filter : java.util.List.of("{", "null", "{\"limit\":0}", "{\"criteria\":[{\"fieldName\":\"status\",\"operator\":\"EQUALS\",\"values\":[\"MISSING\"]}]}"))
      {
         assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
            .withValue("recordsParam", "filterJSON").withValue("filterJSON", filter)));
      }
   }



   /***************************************************************************
    ** Oversized select-all requests fail explicitly rather than truncate work.
    ***************************************************************************/
   @Test
   void filterSelectionOverLimitDoesNotApplyAnyDevice() throws Exception
   {
      String first = (String) enroll().get("deviceId");
      for(int i = 0; i < 100; i++)
      {
         enroll();
      }
      QContext.init(instance, admin);
      assertThrows(QException.class, () -> new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.APPROVE)
         .withValue("recordsParam", "filterJSON").withValue("filterJSON", "{}")));
      assertEquals("PENDING", deviceStatus(first));
   }



   /***************************************************************************
    ** Read persisted status independently of process output.
    ***************************************************************************/
   private String deviceStatus(String id) throws Exception
   {
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         var statement = connection.prepareStatement("SELECT status FROM mobileDevice WHERE id = ?");
         statement.setString(1, id);
         var rows = statement.executeQuery();
         assertTrue(rows.next());
         return rows.getString("status");
      }
   }



   /***************************************************************************
    ** Approve using an administrative user, then restore the scanner user.
    ***************************************************************************/
   private String approvedDevice() throws QException
   {
      String id = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      MobileService.setApproval(id, true);
      QContext.init(instance, scanner);
      return id;
   }



   /***************************************************************************
    ** Simulate the already-verified HTTP header identity at the permission gate.
    ***************************************************************************/
   private void bind(String id) throws QException
   {
      MobilePermissions.REQUEST_DEVICE.set(id);
      PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput().withProcessName(MobileScannersQBitProducer.CAPTURE), MobileScannersQBitProducer.CAPTURE);
   }



   /***************************************************************************
    ** Server binding ignores forged identity and ending survives async handoff.
    ***************************************************************************/
   @Test
   void endedSessionBlocksCapturedWorkerEvenWithNoOpAuthenticationLogout() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      var heartbeat = MobileSessionService.heartbeat(new HashMap<>(Map.of("foreground", true, "screenAvailable", true, "userId", "forged")));
      String sessionId = (String) heartbeat.get("sessionId");
      assertNotEquals(scanner.getUuid(), sessionId);
      assertEquals("scanner", MobileService.get(MobileScannersQBitProducer.SESSION, sessionId).getValueString("userId"));
      var worker = QContext.capture();
      QContext.init(instance, admin);
      MobileSessionService.end(sessionId, "Acceptance", true);
      assertEquals("ENDED", MobileService.get(MobileScannersQBitProducer.SESSION, sessionId).getValueString("status"));
      assertEquals("APPROVED", deviceStatus(device));
      QContext.init(worker);
      MobilePermissions.REQUEST_DEVICE.remove();
      assertThrows(QException.class, MobileSessionService::requireNotEnded);
      assertThrows(QException.class, () -> MobileService.capture(scan()));
      MobilePermissions.REQUEST_DEVICE.set(device);
      assertThrows(QException.class, () -> MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", true)));
   }



   /***************************************************************************
    ** A cookie cannot move to another device and private bindings stay unreadable.
    ***************************************************************************/
   @Test
   void sessionIdentityAndPrivateBindingAreProtected() throws Exception
   {
      String firstDevice = approvedDevice();
      bind(firstDevice);
      String sessionId = (String) MobileSessionService.heartbeat(Map.of("foreground", false, "screenAvailable", false)).get("sessionId");
      String otherDevice = (String) enroll().get("deviceId");
      assertThrows(QException.class, () -> MobileSessionService.observe(otherDevice));
      assertThrows(QException.class, () -> MobileSessionService.view(sessionId));
      QContext.init(instance, admin);
      GetInput read = new GetInput();
      read.setTableName(MobileScannersQBitProducer.SESSION_BINDING);
      read.setPrimaryKey(MobileService.hash(scanner.getUuid()));
      read.setShouldOmitHiddenFields(false);
      assertThrows(QException.class, () -> new GetAction().execute(read));
      assertEquals("Screen unavailable: Capture is unavailable", MobileSessionService.view(sessionId).status());
   }



   /***************************************************************************
    ** Background eligibility is opt-in and cannot retain a former mode's lease.
    ***************************************************************************/
   @Test
   void deviceScreenModeRequiresExplicitHeartbeatAndAdminDemand() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      String session = (String) MobileSessionService.heartbeat(Map.of("foreground", false, "screenAvailable", true)).get("sessionId");
      assertEquals(false, MobileService.get(MobileScannersQBitProducer.SESSION, session).getValueBoolean("screenAvailable"));
      var deviceState = Map.<String, Object>of("foreground", false, "screenAvailable", true, "captureMode", "DEVICE_SCREEN");
      assertEquals(false, MobileSessionService.heartbeat(deviceState).containsKey("screenMirror"));
      assertThrows(QException.class, () -> MobileSessionService.view(session));
      QContext.init(instance, admin);
      assertEquals("Waiting for a consented device screen", MobileSessionService.view(session).status());
      QContext.init(instance, scanner);
      bind(device);
      var heartbeat = MobileSessionService.heartbeat(deviceState);
      assertEquals("DEVICE_SCREEN", ((Map<?, ?>) heartbeat.get("screenMirror")).get("captureMode"));
      assertThrows(QException.class, () -> MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", true, "captureMode", "INVALID")));
      assertEquals(false, MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", true)).containsKey("screenMirror"));
      assertEquals("APP_CONTENT", MobileService.get(MobileScannersQBitProducer.SESSION, session).getValueString("captureMode"));
      QContext.init(instance, admin);
      MobileSessionService.end(session, "Test", true);
      QContext.init(instance, scanner);
      assertThrows(QException.class, () ->
      {
         bind(device);
         MobileSessionService.heartbeat(deviceState);
      });
   }



   /***************************************************************************
    ** Ending a reviewed record does not affect a later or replacement selection.
    ***************************************************************************/
   @Test
   void endSessionProcessUsesStableSelection() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      String first = MobileSessionService.observe(device).getValueString("id");
      QContext.init(instance, admin);
      var launch = new RunProcessAction().execute(new RunProcessInput().withProcessName(MobileScannersQBitProducer.SESSION_END)
         .withValue("recordsParam", "recordIds").withValue("recordIds", first).withValue("tableName", MobileScannersQBitProducer.SESSION));
      assertEquals("ACTIVE", MobileService.get(MobileScannersQBitProducer.SESSION, first).getValueString("status"));
      QSession secondLogin = scanner.clone();
      secondLogin.setUuid(UUID.randomUUID().toString());
      QContext.init(instance, secondLogin);
      String second = MobileSessionService.observe(device).getValueString("id");
      QContext.init(instance, admin);
      launch.addValue("sessionId", second);
      launch.addValue("reason", "Selected by administrator");
      var result = confirmAdmin(MobileScannersQBitProducer.SESSION_END, launch);
      assertEquals("1 session ended.", result.getValue("result"));
      assertEquals("ENDED", MobileService.get(MobileScannersQBitProducer.SESSION, first).getValueString("status"));
      assertEquals("ACTIVE", MobileService.get(MobileScannersQBitProducer.SESSION, second).getValueString("status"));
   }



   /***************************************************************************
    ** Failed second writes roll back credentials and earlier bulk approvals.
    ***************************************************************************/
   @Test
   void enrollmentAndBulkApprovalAreAtomic() throws Exception
   {
      String first = (String) enroll().get("deviceId");
      String duplicate = MobileService.get(MobileScannersQBitProducer.DEVICE, first).getValueString("shortName");
      String attempted = UUID.randomUUID().toString();
      assertThrows(QException.class, () -> MobileService.enroll(Map.of("installationId", attempted, "shortName", duplicate)));
      assertEquals(null, MobileService.get(MobileScannersQBitProducer.CREDENTIAL, attempted));
      assertEquals(null, MobileService.get(MobileScannersQBitProducer.DEVICE, attempted));
      QContext.init(instance, admin);
      assertThrows(QException.class, () -> MobileService.setApproval(java.util.List.of(first, UUID.randomUUID().toString()), true));
      assertEquals("PENDING", deviceStatus(first));
   }



   /***************************************************************************
    ** Native edits cannot smuggle approval, identity or telemetry changes.
    ***************************************************************************/
   @Test
   void profileEditHasStrictAllowlistAndCanonicalUniqueNames() throws Exception
   {
      String first = (String) enroll().get("deviceId");
      String second = (String) enroll().get("deviceId");
      QContext.init(instance, admin);
      var update = new UpdateAction();
      var valid = new QRecord().withValue("id", first).withValue("shortName", " Dock-01 ").withValue("description", "Receiving")
         .withValue("status", "PENDING");
      var saved = update.execute(new UpdateInput()
         .withTableName(MobileScannersQBitProducer.DEVICE).withRecord(valid));
      assertTrue(saved.getRecords().getFirst().getErrors() == null || saved.getRecords().getFirst().getErrors().isEmpty());
      assertEquals("dock-01", MobileService.get(MobileScannersQBitProducer.DEVICE, first).getValueString("shortName"));
      assertFalse(valid.getValues().containsKey("status"));
      assertThrows(QException.class, () -> update.execute(new UpdateInput()
         .withTableName(MobileScannersQBitProducer.DEVICE).withRecord(new QRecord().withValue("id", first).withValue("status", "APPROVED"))));
      var duplicate = update.execute(new UpdateInput()
         .withTableName(MobileScannersQBitProducer.DEVICE).withRecord(new QRecord().withValue("id", second).withValue("shortName", "DOCK-01")));
      assertFalse(duplicate.getRecords().getFirst().getErrors().isEmpty());
      assertEquals("PENDING", deviceStatus(first));
   }



   /***************************************************************************
    ** Location is optional and online filtering expires before backend queries.
    ***************************************************************************/
   @Test
   void locationValidationAndQueryableOnlineFreshness() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      var location = Map.of("latitude", 38.6, "longitude", -90.2, "accuracyMeters", 12.5, "capturedAt", "2026-10-30T12:00:00Z");
      MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", false, "locationStatus", "AVAILABLE", "location", location));
      assertEquals("AVAILABLE", MobileService.get(MobileScannersQBitProducer.DEVICE, device).getValueString("locationStatus"));
      assertNotNull(MobileService.get(MobileScannersQBitProducer.DEVICE, device).getValueInstant("locationReceivedAt"));
      assertThrows(QException.class, () -> MobileSessionService.heartbeat(Map.of("foreground", false, "screenAvailable", false, "locationStatus", "AVAILABLE", "location", location)));
      MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", false, "locationStatus", "PERMISSION_DENIED"));
      assertNotNull(MobileService.get(MobileScannersQBitProducer.DEVICE, device).getValue("latitude"));
      QContext.init(instance, admin);
      var selected = new QueryInput().withTableName(MobileScannersQBitProducer.DEVICE)
         .withFieldNamesToInclude(java.util.Set.of("id", "online"));
      assertEquals(true, new QueryAction().execute(selected).getRecords().getFirst().getValueBoolean("online"));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         connection.createStatement().executeUpdate("UPDATE mobileDevice SET lastSeenAt = TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00+00:00'");
      }
      QContext.init(instance, admin);
      var query = new QueryInput().withTableName(MobileScannersQBitProducer.DEVICE)
         .withFilter(new QQueryFilter()
            .withCriteria("online", QCriteriaOperator.EQUALS, true));
      assertEquals(0, new QueryAction().execute(query).getRecords().size());
   }



   /***************************************************************************
    ** Device possession does not grant scanning/session permissions to a user.
    ***************************************************************************/
   @Test
   void sessionTelemetryRequiresScanPermission() throws Exception
   {
      String device = approvedDevice();
      bind(device);
      QSession unauthorized = new QSession().withUser(new QUser().withIdReference("scanner"));
      unauthorized.setUuid(scanner.getUuid());
      QContext.init(instance, unauthorized);
      assertThrows(QException.class, () -> MobileSessionService.observe(device));
      assertThrows(QException.class, () -> MobileSessionService.upload(Map.of()));
      assertThrows(QException.class, () -> MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", false)));
   }



   /***************************************************************************
    ** Captured administrator identity cannot mutate approvals after termination.
    ***************************************************************************/
   @Test
   void endedAdministratorCannotApproveFromCapturedWorker() throws Exception
   {
      String device = approvedDevice();
      QContext.init(instance, admin);
      String adminSession = MobileSessionService.observe(device).getValueString("id");
      var worker = QContext.capture();
      MobileSessionService.end(adminSession, "ADMINISTRATOR", true);
      QContext.init(worker);
      assertThrows(QException.class, () -> MobileService.setApproval(device, false));
      assertThrows(QException.class, () -> MobileSessionService.endAll(java.util.List.of(adminSession), "Again"));
      assertEquals("APPROVED", deviceStatus(device));
   }



   /***************************************************************************
    ** Reapplying the migration repairs old failed enrollments without token reset.
    ***************************************************************************/
   @Test
   void migrationRemovesOnlyOrphanedCredentials() throws Exception
   {
      Map<String, Object> valid = enroll();
      String orphan = UUID.randomUUID().toString();
      MobileService.insert(MobileScannersQBitProducer.CREDENTIAL, new QRecord().withValue("id", orphan).withValue("tokenHash", MobileService.hash("never-returned")));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         String schema = new String(getClass().getResourceAsStream("/db/mobile-scanners-h2.sql").readAllBytes(), StandardCharsets.UTF_8);
         for(String statement : schema.split(";"))
         {
            if(!statement.isBlank())
            {
               connection.createStatement().execute(statement);
            }
         }
      }
      assertEquals(null, MobileService.get(MobileScannersQBitProducer.CREDENTIAL, orphan));
      assertNotNull(MobileService.authenticateDevice((String) valid.get("deviceId"), (String) valid.get("deviceToken")));
      assertNotNull(MobileService.enroll(Map.of("installationId", orphan)).get("deviceToken"));
   }



   /***************************************************************************
    ** Shared UUID prefixes and partially applied migrations preserve identities.
    ***************************************************************************/
   @Test
   void generatedNamesAndMigrationSurvivePrefixCollisions() throws Exception
   {
      String firstId = "12345678-1234-4234-8234-123400000001";
      String secondId = "12345678-1234-4234-8234-123400000002";
      Map<String, Object> first = MobileService.enroll(Map.of("installationId", firstId));
      Map<String, Object> second = MobileService.enroll(Map.of("installationId", secondId));
      assertNotEquals(first.get("shortName"), second.get("shortName"));
      Map<String, Object> custom = MobileService.enroll(Map.of("installationId", UUID.randomUUID().toString(), "shortName", "scanner-1"));
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""); var sql = connection.createStatement())
      {
         sql.execute("DROP INDEX mobileDevice_shortName");
         sql.execute("UPDATE mobileDevice SET shortName = 'scanner-123456781234423482341234' WHERE id IN ('" + firstId + "', '" + secondId + "')");
         String schema = new String(getClass().getResourceAsStream("/db/mobile-scanners-h2.sql").readAllBytes(), StandardCharsets.UTF_8);
         for(int pass = 0; pass < 2; pass++)
         {
            for(String statement : schema.split(";"))
            {
               if(!statement.isBlank())
               {
                  sql.execute(statement);
               }
            }
         }
      }
      assertEquals("scanner-1", MobileService.get(MobileScannersQBitProducer.DEVICE, (String) custom.get("deviceId")).getValueString("shortName"));
      assertNotEquals(MobileService.get(MobileScannersQBitProducer.DEVICE, firstId).getValueString("shortName"),
         MobileService.get(MobileScannersQBitProducer.DEVICE, secondId).getValueString("shortName"));
      assertNotNull(MobileService.authenticateDevice(firstId, (String) first.get("deviceToken")));
      assertNotNull(MobileService.authenticateDevice(secondId, (String) second.get("deviceToken")));
   }



   /***************************************************************************
    ** QQQ binds UTC wall-clock timestamps, independent of the host timezone.
    ***************************************************************************/
   @Test
   void timestampRoundTripsOutsideUtcAndPresenceExpires() throws Exception
   {
      java.util.TimeZone previous = java.util.TimeZone.getDefault();
      try
      {
         java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Chicago"));
         setup();
         String device = approvedDevice();
         bind(device);
         java.time.Instant captured = java.time.Instant.parse("2026-10-30T12:34:56.123Z");
         MobileSessionService.heartbeat(Map.of("foreground", true, "screenAvailable", false, "locationStatus", "AVAILABLE",
            "location", Map.of("latitude", 10.125, "longitude", 20.25, "accuracyMeters", 7, "capturedAt", captured.toString())));
         QRecord record = MobileService.get(MobileScannersQBitProducer.DEVICE, device);
         assertEquals(captured, record.getValueInstant("locationCapturedAt"));
         assertTrue(java.time.Duration.between(record.getValueInstant("lastSeenAt"), java.time.Instant.now()).abs().getSeconds() < 2);
         try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
         {
            connection.createStatement().executeUpdate("UPDATE mobileDevice SET lastSeenAt = TIMESTAMP '2000-01-01 00:00:00'");
         }
         assertEquals(false, MobileService.get(MobileScannersQBitProducer.DEVICE, device).getValueBoolean("online"));
      }
      finally
      {
         java.util.TimeZone.setDefault(previous);
      }
   }



   /***************************************************************************
    ** Distinct event ID for each physical scan.
    ***************************************************************************/
   private Map<String, Serializable> scan()
   {
      return new HashMap<>(Map.of("eventId", UUID.randomUUID().toString(), "barcode", "00001234", "symbology", "CODE128", "scanSource", "datawedge", "capturedAt", "2026-10-29T12:00:00Z"));
   }
}
