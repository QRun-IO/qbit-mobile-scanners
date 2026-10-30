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
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
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
    ** Distinct event ID for each physical scan.
    ***************************************************************************/
   private Map<String, Serializable> scan()
   {
      return new HashMap<>(Map.of("eventId", UUID.randomUUID().toString(), "barcode", "00001234", "symbology", "CODE128", "scanSource", "datawedge", "capturedAt", "2026-10-29T12:00:00Z"));
   }
}
