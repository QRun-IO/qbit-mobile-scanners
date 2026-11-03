/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.collaboration.CollaborationSecurity;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileCaptureIdentityProvider;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileRecordFilesContract;
import com.kingsrook.qbits.mobilescanners.recordfiles.RecordFilesCaptureWidgetData;
import com.kingsrook.qbits.recordfiles.RecordFilesFixture;
import com.kingsrook.qbits.recordfiles.api.CaptureIdentity;
import com.kingsrook.qbits.recordfiles.api.RecordFilesUploadApi;
import com.kingsrook.qbits.recordfiles.audit.RecordFilesEventType;
import com.kingsrook.qbits.recordfiles.collections.CollectionService;
import com.kingsrook.qbits.recordfiles.files.RetainedFileService;
import com.kingsrook.qbits.recordfiles.ingestion.UploadChunkStorage;
import com.kingsrook.qbits.recordfiles.ingestion.UploadConfiguration;
import com.kingsrook.qbits.recordfiles.ingestion.UploadFinalizer;
import com.kingsrook.qbits.recordfiles.ingestion.UploadManifest;
import com.kingsrook.qbits.recordfiles.ingestion.UploadQuota;
import com.kingsrook.qbits.recordfiles.ingestion.UploadSessionService;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.TableBasedAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeature;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeaturesMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.TableBasedAuthenticationModule;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Real password login, native mobile approval and native retained-file HTTP API.
 ** Metadata and owner object bytes persist on disk in an isolated test directory.
 *******************************************************************************/
public class MobileRecordFilesIntegrationTest extends RecordFilesFixture
{
   private static final ObjectMapper JSON = new ObjectMapper();
   private static final String FILES = "/qqq/v1/qbit/record-files";
   private static final byte[] CONTENT = {1, 2, 3};
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
   private final String password = UUID.randomUUID().toString();
   private QApplicationJavalinServer server;
   private String origin;
   private String jdbc;
   private String deviceId;
   private String deviceToken;



   /***************************************************************************
    ** Keep the file database open between HTTP requests, avoiding reopen overhead.
    ***************************************************************************/
   @Override
   protected RDBMSBackendMetaData createBackend() throws Exception
   {
      var backend = super.createBackend();
      jdbc = backend.getJdbcUrl() + ";DB_CLOSE_DELAY=-1";
      backend.setJdbcUrl(jdbc);
      return backend;
   }



   /***************************************************************************
    ** Install both independent qbits and the optional provider on actual routes.
    ***************************************************************************/
   @BeforeEach
   void startMobileHost() throws Exception
   {
      Authentication.deniedPermissions = Set.of();
      new MobileScannersQBitProducer().withConfig(new MobileScannersQBitConfig().withBackendName(backendName)).produce(instance, "mobile");
      var captureRules = new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withCustomPermissionChecker(new QCodeReference(CapturePermissions.class));
      instance.addWidget(new QWidgetMetaData().withName("mobileRecordPhotos").withLabel("Record photos").withType("composite")
         .withCodeReference(new QCodeReference(CaptureWidget.class)).withPermissionRules(captureRules));
      instance.addProcess(new QProcessMetaData().withName("mobileRecordPhotos").withLabel("Record photos").withPermissionRules(captureRules)
         .withStep(new QBackendStepMetaData().withName("prepare").withCode(new QCodeReference(CapturePrepare.class)))
         .withStep(new QFrontendStepMetaData().withName("capture").withLabel("Record photos")
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.WIDGET).withValue("widgetName", "mobileRecordPhotos"))));
      instance.getApp(MobileScannersQBitProducer.APP).getSections().getFirst().withProcesses(List.of(MobileScannersQBitProducer.CAPTURE, "mobileRecordPhotos"));
      for(var table : instance.getTables().values())
      {
         if(table.getName().startsWith("mobile"))
         {
            table.withBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
         }
      }
      var authentication = new TableBasedAuthenticationMetaData().withUserTableName("fixtureUser").withSessionTableName("fixtureSession");
      authentication.withName("fixturePasswords").withModuleCodeReference(new QCodeReference(Authentication.class));
      instance.withInstanceDefaultAuthentication(authentication);
      for(var table : List.of(authentication.defineStandardUserTable(backendName), authentication.defineStandardSessionTable(backendName)))
      {
         table.withBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
         table.getFields().values().forEach(field -> field.setBackendName(field.getName()));
         table.withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withPermissionBaseName("fixtureCredentials"));
         instance.addTable(table);
      }
      try(var connection = DriverManager.getConnection(jdbc, "sa", ""); var statement = connection.createStatement())
      {
         statement.execute("CREATE TABLE fixtureUser (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, username VARCHAR(100) UNIQUE, fullName VARCHAR(200), passwordHash VARCHAR(1024), createDate TIMESTAMP, modifyDate TIMESTAMP)");
         statement.execute("CREATE TABLE fixtureSession (id VARCHAR(100) PRIMARY KEY, userId INTEGER REFERENCES fixtureUser(id), accessTimestamp TIMESTAMP, createDate TIMESTAMP, modifyDate TIMESTAMP)");
         for(String sql : new String(getClass().getResourceAsStream("/db/mobile-scanners-h2.sql").readAllBytes(), StandardCharsets.UTF_8).split(";"))
         {
            if(!sql.isBlank())
            {
               statement.execute(sql);
            }
         }
         for(String actor : List.of("scanner", "admin", "other"))
         {
            try(var insert = connection.prepareStatement("INSERT INTO fixtureUser (username,fullName,passwordHash) VALUES (?,?,?)"))
            {
               insert.setString(1, actor);
               insert.setString(2, actor);
               insert.setString(3, TableBasedAuthenticationModule.PasswordHasher.createHashedPassword(password));
               insert.executeUpdate();
            }
         }
      }
      DiskAttachmentStorage.directory = Files.createTempDirectory(java.nio.file.Path.of("target"), "owner-objects-");
      collaboration.withAttachmentStorage(new QCodeReference(DiskAttachmentStorage.class));
      config.withUploadConfiguration(new UploadConfiguration("mobile-fixture-staging",
         new UploadManifest.Limits(20971520L, 41943040L, 2, 65536, 600, Set.of("application/pdf", "image/jpeg", "image/png")),
         new UploadQuota.Policy(10, 83886080L, 100), new QCodeReference(MobileCaptureIdentityProvider.class))
      {
         /***********************************************************************
          ** Disk-backed staging persists independently of HTTP request instances.
          ***********************************************************************/
         @Override
         public UploadChunkStorage createStorage()
         {
            return new DiskAttachmentStorage.Staging();
         }
      });
      RecordFilesUploadApi.register(instance);
      MobileRecordFilesContract.register(instance);
      new QInstanceEnricher(instance).enrich();
      int port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }
      origin = "http://127.0.0.1:" + port;
      server = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         /***********************************************************************
          ** The real middleware owns authentication and native request contexts.
          ***********************************************************************/
         @Override
         public QInstance defineQInstance()
         {
            return instance;
         }
      }).withPort(port).withServeFrontendNext(false).withServeFrontendMaterialDashboard(false)
         .withServeLegacyUnversionedMiddlewareAPI(false).withStrictUiApiRoutes(true)
         .withAdditionalRouteProviders(List.of(new MobileScannersRouteProvider(instance)))
         .withJavalinConfigCustomizer(configuration -> configuration.jetty.host = "127.0.0.1");
      server.start();
   }



   /***************************************************************************
    ** Fixture metadata never delegates record-target inference to the client.
    ***************************************************************************/
   public static class CaptureWidget extends AbstractWidgetRenderer
   {
      /***********************************************************************
       ** A fixed synthetic parent makes end-to-end image association inspectable.
       ***********************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         return new RenderWidgetOutput(new RecordFilesCaptureWidgetData("document", "1", "Document photos", "Capture or choose a photo for the first fixture document."));
      }
   }



   /***************************************************************************
    ** Native process values contain the actual widget before the frontend step.
    ***************************************************************************/
   public static class CapturePrepare implements BackendStep
   {
      /***********************************************************************
       ** Explicit metadata supplies a record target; no client field inference.
       ***********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if(MobileDeviceAuthorization.captureCurrent().isEmpty())
         {
            throw new QPermissionDeniedException("An approved device is required.");
         }
         output.addValue("mobileRecordPhotos", new RecordFilesCaptureWidgetData("document", "1", "Document photos", "Capture or choose a photo for the first fixture document."));
      }
   }



   /***************************************************************************
    ** Fixture capture processes require the same verified device authority.
    ***************************************************************************/
   public static class CapturePermissions implements CustomPermissionChecker
   {
      /***********************************************************************
       ** Missing/ended/revoked device context cannot launch photo capture.
       ***********************************************************************/
      @Override
      public void checkPermissionsThrowing(AbstractActionInput input, MetaDataWithPermissionRules metadata) throws QPermissionDeniedException
      {
         try
         {
            if(MobileDeviceAuthorization.captureCurrent().isEmpty())
            {
               throw new QPermissionDeniedException("An approved device is required.");
            }
         }
         catch(QException failure)
         {
            throw new QPermissionDeniedException("An approved device is required.");
         }
      }
   }



   /***************************************************************************
    ** Dedicated local consumer fixture; caller must provide a private output path.
    ** Synthetic credentials are written once and never printed to logs/stdout.
    ***************************************************************************/
   public static void main(String[] arguments) throws Exception
   {
      if(arguments.length != 2)
      {
         throw new IllegalArgumentException("Expected private credentials path and lifetime seconds.");
      }
      int lifetime = Integer.parseInt(arguments[1]);
      if(lifetime < 1 || lifetime > 3600)
      {
         throw new IllegalArgumentException("Fixture lifetime must be 1 through 3600 seconds.");
      }
      var fixture = new MobileRecordFilesIntegrationTest();
      try
      {
         fixture.setUp();
         fixture.startMobileHost();
         Files.writeString(java.nio.file.Path.of(arguments[0]), JSON.writeValueAsString(Map.of("baseUrl", fixture.origin,
            "username", "scanner", "password", fixture.password, "adminUsername", "admin", "adminPassword", fixture.password,
            "processName", "mobileRecordPhotos", "tableName", "document", "recordId", "1", "databaseUrl", fixture.jdbc)),
            java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
         Thread.sleep(Duration.ofSeconds(lifetime));
      }
      finally
      {
         fixture.stopMobileHost();
         fixture.tearDown();
      }
   }



   /***************************************************************************
    ** Stop only this isolated fixture; never operate the shared demo or handheld.
    ***************************************************************************/
   @AfterEach
   void stopMobileHost()
   {
      if(server != null)
      {
         server.stop();
      }
      client.close();
      MobilePermissions.REQUEST_DEVICE.remove();
   }



   /***************************************************************************
    ** Receipt, ordered association and captured identity survive fresh DB reads.
    ***************************************************************************/
   @Test
   void actualLoginApprovalUploadAndPersistentAssociation() throws Exception
   {
      String scanner = signIn("scanner");
      String admin = signIn("admin");
      enrollHttp(scanner);
      assertEquals(403, send("POST", FILES + "/uploads", scanner, manifest(UUID.randomUUID().toString()), true, Map.of()).statusCode());
      approveHttp(admin, "mobileDeviceApprove");
      JsonNode photoStep = processForm(scanner, "mobileRecordPhotos", "/init", Map.of(), true);
      JsonNode photoWidget = photoStep.at("/values/mobileRecordPhotos");
      assertEquals("composite", photoWidget.path("type").asText());
      assertEquals("COMPOSITE", photoWidget.path("blockType").asText());
      assertEquals("RECORD_FILES_CAPTURE", photoWidget.at("/subBlocks/0/blockType").asText());
      assertEquals("document", photoWidget.at("/subBlocks/0/values/parent/tableName").asText());
      assertEquals("1", photoWidget.at("/subBlocks/0/values/parent/recordId").asText());
      var contractResponse = client.send(HttpRequest.newBuilder(URI.create(origin + "/qqq/v1/qbit/mobile-record-files/contract")).GET().build(), HttpResponse.BodyHandlers.ofString());
      JsonNode contract = ok(contractResponse);
      assertEquals(JSON.valueToTree(Map.of("version", "1.0.0", "blockType", "RECORD_FILES_CAPTURE", "widgetType", "composite")), contract);
      JsonNode version = ok(send("GET", "/qqq/v1/version", scanner, null, false, Map.of()));
      assertTrue(java.util.stream.StreamSupport.stream(version.path("extensions").spliterator(), false)
         .anyMatch(extension -> "mobile-record-files".equals(extension.path("name").asText()) && "1.0.0".equals(extension.path("version").asText())));
      JsonNode openApi = ok(send("GET", "/qqq/v1/openapi.json", scanner, null, false, Map.of()));
      JsonNode schemas = openApi.at("/components/schemas");
      assertEquals("string", schemas.path("MobileRecordFilesCaptureParentV1").at("/properties/recordId/type").asText());
      assertTrue(schemas.path("MobileRecordFilesCaptureValuesV1").path("required").toString().contains("parent"));
      RecordFilesCaptureContractTest.assertConforms(photoWidget, schemas.path("MobileRecordFilesCaptureWidgetV1"), schemas);
      Files.writeString(java.nio.file.Path.of("target", "record-files-capture-widget.json"), photoWidget.toPrettyString());
      assertTrue(ok(send("GET", FILES + "/capabilities?tableName=document&recordId=1", scanner, null, true, Map.of())).path("canUpload").asBoolean());
      String request = UUID.randomUUID().toString();
      JsonNode created = ok(send("POST", FILES + "/uploads", scanner, manifest(request), true, Map.of()));
      String id = created.path("uploadId").asText();
      ok(send("PUT", FILES + "/uploads/" + id + "/items/20000000-0000-0000-0000-000000000001/chunks/0", scanner, CONTENT, true,
         Map.of("Content-Type", "application/octet-stream", "X-Chunk-SHA256", UploadManifest.hash(CONTENT))));
      JsonNode receipt = ok(send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request))).path("receipt");
      assertEquals("QUARANTINED", receipt.at("/items/0/lifecycleAtCommit").asText());
      assertEquals(UploadManifest.hash(CONTENT), receipt.at("/items/0/sha256").asText());
      assertEquals(receipt, ok(send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request))).path("receipt"));
      QContext.init(instance, authenticated(scanner));
      assertEquals(1, files.listForRecord("document", "1").size());
      try(var connection = DriverManager.getConnection(jdbc, "sa", ""); var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM mobileSession WHERE deviceId='" + deviceId + "' AND userId='scanner'"))
      {
         assertTrue(rows.next());
         assertEquals(1, rows.getInt(1));
      }
      try(var paths = Files.list(DiskAttachmentStorage.directory.resolve("canonical")))
      {
         var originals = paths.filter(path -> !path.toString().endsWith(".json")).toList();
         assertEquals(1, originals.size(), "Idempotent finalization retains one original.");
         assertEquals(UploadManifest.hash(CONTENT), UploadManifest.hash(Files.readAllBytes(originals.getFirst())));
      }
      var uploadTable = instance.getTable("record_files_upload");
      String identityColumn = uploadTable.getField("captureIdentityJson").getBackendName();
      String uploadColumn = uploadTable.getField("uploadId").getBackendName();
      try(var connection = DriverManager.getConnection(jdbc, "sa", ""); var query = connection.prepareStatement("SELECT " + identityColumn + " FROM record_files_upload WHERE " + uploadColumn + "=?"))
      {
         query.setString(1, id);
         try(var rows = query.executeQuery())
         {
            assertTrue(rows.next());
            JsonNode identity = JSON.readTree(rows.getString(1));
            assertEquals("scanner", identity.path("actorId").asText());
            assertEquals(deviceId, identity.path("deviceId").asText());
            assertFalse(identity.path("publicSessionId").asText().isBlank());
            assertFalse(identity.toString().contains(scanner));
            assertFalse(identity.toString().contains(deviceToken));
         }
      }
      assertFalse(receipt.toString().contains(scanner));
      assertFalse(receipt.toString().contains(deviceToken));
      Files.writeString(java.nio.file.Path.of("target", "mobile-upload-receipt.json"), receipt.toPrettyString());
   }



   /***************************************************************************
    ** Different actors, missing device proof and revocation cannot resume intake.
    ***************************************************************************/
   @Test
   void authorizationChangesBlockResumptionAndFinalization() throws Exception
   {
      String scanner = signIn("scanner");
      String admin = signIn("admin");
      enrollHttp(scanner);
      approveHttp(admin, "mobileDeviceApprove");
      String request = UUID.randomUUID().toString();
      String id = ok(send("POST", FILES + "/uploads", scanner, manifest(request), true, Map.of())).path("uploadId").asText();
      ok(send("PUT", FILES + "/uploads/" + id + "/items/20000000-0000-0000-0000-000000000001/chunks/0", scanner, CONTENT, true,
         Map.of("Content-Type", "application/octet-stream", "X-Chunk-SHA256", UploadManifest.hash(CONTENT))));
      assertEquals(403, send("GET", FILES + "/uploads/" + id, scanner, null, false, Map.of()).statusCode());
      assertEquals(404, send("GET", FILES + "/uploads/" + id, signIn("other"), null, true, Map.of()).statusCode());
      approveHttp(admin, "mobileDeviceRevoke");
      assertEquals(403, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      approveHttp(admin, "mobileDeviceApprove");
      ok(send("POST", "/qqq/v1/qbit/mobile-scanners/sessions/current/end", scanner,
         JSON.writeValueAsBytes(Map.of("reason", "SIGN_OUT")), true, Map.of()));
      assertEquals(401, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      scanner = signIn("scanner");
      assertEquals(404, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      QContext.init(instance, authenticated(scanner));
      assertTrue(files.listForRecord("document", "1").isEmpty());
   }



   /***************************************************************************
    ** Approved devices still obey tenant, configured parent and current grants.
    ***************************************************************************/
   @Test
   void parentAuthorizationAndAttachmentPolicyCannotBeBypassed() throws Exception
   {
      String scanner = signIn("scanner");
      String admin = signIn("admin");
      enrollHttp(scanner);
      approveHttp(admin, "mobileDeviceApprove");
      for(var parent : List.of(Map.of("tableName", "document", "recordId", "3"), Map.of("tableName", "memo", "recordId", "1")))
      {
         String path = FILES + "/capabilities?tableName=" + parent.get("tableName") + "&recordId=" + parent.get("recordId");
         assertEquals(404, send("GET", path, scanner, null, true, Map.of()).statusCode());
         var body = JSON.readTree(manifest(UUID.randomUUID().toString()));
         ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("parent", JSON.valueToTree(parent));
         assertEquals(404, send("POST", FILES + "/uploads", scanner, JSON.writeValueAsBytes(body), true, Map.of()).statusCode());
      }
      String request = UUID.randomUUID().toString();
      String id = ok(send("POST", FILES + "/uploads", scanner, manifest(request), true, Map.of())).path("uploadId").asText();
      ok(send("PUT", FILES + "/uploads/" + id + "/items/20000000-0000-0000-0000-000000000001/chunks/0", scanner, CONTENT, true,
         Map.of("Content-Type", "application/octet-stream", "X-Chunk-SHA256", UploadManifest.hash(CONTENT))));
      Authentication.deniedPermissions = Set.of("document.write", "document.attach");
      assertFalse(ok(send("GET", FILES + "/capabilities?tableName=document&recordId=1", scanner, null, true, Map.of())).path("canUpload").asBoolean());
      assertEquals(403, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      Authentication.deniedPermissions = Set.of();
      RecordFeaturesMetaData.ofOrWithNew(instance.getTable("document")).withEnabled(RecordFeature.ATTACHMENTS, false);
      com.kingsrook.qqq.backend.core.actions.collaboration.RuntimeMetaDataOverlay.refresh(instance);
      assertFalse(ok(send("GET", FILES + "/capabilities?tableName=document&recordId=1", scanner, null, true, Map.of())).path("canUpload").asBoolean());
      assertEquals(403, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      QContext.init(instance, authenticated(scanner));
      assertTrue(files.listForRecord("document", "1").isEmpty());
      assertFalse(Files.exists(DiskAttachmentStorage.directory.resolve("canonical")), "Denied finalization must not write retained bytes.");
   }



   /***************************************************************************
    ** Public identity values cannot substitute for the verified private binding.
    ***************************************************************************/
   @Test
   void adapterRejectsIdentitySubstitutionAndEndedSessions() throws Exception
   {
      QSession scanner = authenticated(signIn("scanner"));
      QContext.init(instance, scanner);
      MobileCaptureIdentityProvider provider = new MobileCaptureIdentityProvider();
      assertTrue(provider.capture().isEmpty());
      assertEquals("desktop", provider.executeAuthorized(Optional.empty(), () -> "desktop"));
      assertThrows(QException.class, () -> provider.executeAuthorized(null, () -> "forbidden"));
      assertThrows(QException.class, () -> MobileDeviceAuthorization.executeAuthorized((MobileDeviceAuthorization.Identity) null, () -> "forbidden"));
      scanner.setClientChannel("android/fixture");
      assertThrows(QException.class, provider::capture);
      var enrolled = MobileService.enroll(Map.of("installationId", UUID.randomUUID().toString(), "displayName", "fixture"));
      String id = (String) enrolled.get("deviceId");
      QSession admin = authenticated(signIn("admin"));
      QContext.init(instance, admin);
      MobileService.setApproval(id, true);
      QContext.init(instance, scanner);
      MobilePermissions.REQUEST_DEVICE.set(id);
      CaptureIdentity captured = provider.capture().orElseThrow();
      MobilePermissions.REQUEST_DEVICE.remove();
      provider.revalidate(captured);
      for(Optional<CaptureIdentity> expected : List.of(Optional.<CaptureIdentity>empty(),
         Optional.of(new CaptureIdentity("other-provider", captured.actorId(), id, captured.publicSessionId())),
         Optional.of(new CaptureIdentity(captured.providerKey(), "other", id, captured.publicSessionId())),
         Optional.of(new CaptureIdentity(captured.providerKey(), captured.actorId(), "other-device", captured.publicSessionId())),
         Optional.of(new CaptureIdentity(captured.providerKey(), captured.actorId(), id, "other-session"))))
      {
         assertThrows(QException.class, () -> provider.executeAuthorized(expected, () ->
         {
            throw new AssertionError("Invalid or missing identity must not execute guarded work.");
         }));
      }
      assertThrows(QException.class, () -> provider.revalidate(null));
      assertThrows(QException.class, () -> provider.revalidate(new CaptureIdentity("other-provider", captured.actorId(), id, captured.publicSessionId())));
      assertThrows(QException.class, () -> provider.revalidate(new CaptureIdentity(captured.providerKey(), "other", id, captured.publicSessionId())));
      assertThrows(QException.class, () -> provider.revalidate(new CaptureIdentity(captured.providerKey(), captured.actorId(), "other-device", captured.publicSessionId())));
      assertThrows(QException.class, () -> provider.revalidate(new CaptureIdentity(captured.providerKey(), captured.actorId(), id, "other-session")));
      QSession bound = QContext.getQSession();
      MobilePermissions.REQUEST_DEVICE.set("another-verified-request-device");
      assertThrows(QException.class, provider::capture);
      MobilePermissions.REQUEST_DEVICE.remove();
      QContext.init(instance, null);
      assertThrows(QException.class, provider::capture);
      QContext.init(instance, bound);
      assertThrows(QException.class, () -> MobileDeviceAuthorization.executeAuthorized(
         new MobileDeviceAuthorization.Identity("other", id, captured.publicSessionId()), () ->
         {
            throw new AssertionError("A rejected identity must not execute the callback.");
         }));
      QContext.init(instance, scanner);
      scanner.setClientChannel(null);
      assertThrows(QException.class, provider::capture);
      QContext.init(instance, admin);
      MobileService.setApproval(id, false);
      QContext.init(instance, bound);
      assertThrows(QException.class, () -> provider.revalidate(captured));
      QContext.init(instance, admin);
      MobileService.setApproval(id, true);
      try(var connection = DriverManager.getConnection(jdbc, "sa", ""); var update = connection.prepareStatement("UPDATE mobileSession SET status='ENDED' WHERE id=?"))
      {
         update.setString(1, captured.publicSessionId());
         update.executeUpdate();
      }
      QContext.init(instance, bound);
      assertThrows(QException.class, () -> provider.revalidate(captured));
   }



   /***************************************************************************
    ** Real revoke/end cannot cross owner intake, association or receipt commits.
    ** Each failure case also proves rollback releases the same authorization lock.
    ***************************************************************************/
   @ParameterizedTest
   @CsvSource({
      "INTAKE_COMPLETED,false,false", "INTAKE_COMPLETED,true,false",
      "COLLECTION_ITEM_ADDED,false,false", "COLLECTION_ITEM_ADDED,true,false",
      "UPLOAD_COMPLETED,false,false", "UPLOAD_COMPLETED,true,false",
      "INTAKE_COMPLETED,false,true", "INTAKE_COMPLETED,true,true",
      "COLLECTION_ITEM_ADDED,false,true", "COLLECTION_ITEM_ADDED,true,true",
      "UPLOAD_COMPLETED,false,true", "UPLOAD_COMPLETED,true,true"
   })
   void scannerAuthorityRemainsHeldThroughCommit(RecordFilesEventType event, boolean endSession, boolean failBeforeCommit) throws Exception
   {
      String scanner = signIn("scanner");
      String adminCookie = signIn("admin");
      enrollHttp(scanner);
      approveHttp(adminCookie, "mobileDeviceApprove");
      String request = UUID.randomUUID().toString();
      String id = ok(send("POST", FILES + "/uploads", scanner, manifest(request), true, Map.of())).path("uploadId").asText();
      ok(send("PUT", FILES + "/uploads/" + id + "/items/20000000-0000-0000-0000-000000000001/chunks/0", scanner, CONTENT, true,
         Map.of("Content-Type", "application/octet-stream", "X-Chunk-SHA256", UploadManifest.hash(CONTENT))));
      QSession admin = authenticated(adminCookie);
      QContext.init(instance, authenticated(scanner));
      MobilePermissions.REQUEST_DEVICE.set(deviceId);
      CaptureIdentity identity;
      try
      {
         identity = new MobileCaptureIdentityProvider().capture().orElseThrow();
      }
      finally
      {
         MobilePermissions.REQUEST_DEVICE.remove();
      }
      var probe = new MobileAuthorizationCommitProbe(instance, jdbc, admin, identity, event, endSession, failBeforeCommit);
      var audit = probe.audit();
      ObservedAttachmentLifecycle.audit.set(audit);
      collaboration.withAttachmentLifecycle(new QCodeReference(ObservedAttachmentLifecycle.class));
      var configuration = config.getUploadConfiguration();
      var clock = java.time.Clock.systemUTC();
      var sessions = new UploadSessionService(config, configuration.limits(), configuration.createStorage(), probe, audit, clock);
      var retained = new RetainedFileService(config, audit,
         new com.kingsrook.qbits.collaboration.attachments.AttachmentsService(collaboration, new DiskAttachmentStorage()));
      var finalizer = new UploadFinalizer(sessions, retained, new CollectionService(config, audit), clock);
      try
      {
         assertThrows(QException.class, () -> finalizer.finalizeUpload(id, request));
      }
      finally
      {
         ObservedAttachmentLifecycle.audit.remove();
      }
      probe.assertExercised();
      assertEquals(failBeforeCommit ? 0 : 1, probe.committedEvents());
      int expectedStatus = endSession ? 401 : 403;
      assertEquals(expectedStatus, send("POST", FILES + "/uploads/" + id + "/finalize", scanner, null, true, Map.of("Idempotency-Key", request)).statusCode());
      String stateColumn = instance.getTable("record_files_upload").getField("state").getBackendName();
      String uploadColumn = instance.getTable("record_files_upload").getField("uploadId").getBackendName();
      try(var connection = DriverManager.getConnection(jdbc, "sa", "");
         var query = connection.prepareStatement("SELECT " + stateColumn + " FROM record_files_upload WHERE " + uploadColumn + "=?"))
      {
         query.setString(1, id);
         try(var rows = query.executeQuery())
         {
            assertTrue(rows.next());
            assertEquals(event == RecordFilesEventType.UPLOAD_COMPLETED && !failBeforeCommit ? "COMPLETE" : "FINALIZING", rows.getString(1));
         }
      }
      QContext.init(instance, admin);
      int retainedCount = files.listForRecord("document", "1").size();
      int expectedRetained = event == RecordFilesEventType.INTAKE_COMPLETED && failBeforeCommit ? 0 : 1;
      assertEquals(expectedRetained, retainedCount, "Rolled-back registration stays absent; committed originals remain retained.");
   }



   /***************************************************************************
    ** Actual password hashing and QQQ table session validation with fixture roles.
    ***************************************************************************/
   public static class ObservedAttachmentLifecycle extends com.kingsrook.qbits.recordfiles.lifecycle.RecordFilesAttachmentLifecycle
   {
      private static final ThreadLocal<com.kingsrook.qbits.recordfiles.audit.AuditTrail> audit = new ThreadLocal<>();



      /***********************************************************************
       ** Observe the actual owner registration transaction, preserving policy.
       ***********************************************************************/
      @Override
      public void afterStored(com.kingsrook.qqq.backend.core.model.data.QRecord attachment) throws QException
      {
         var current = audit.get();
         if(current == null)
         {
            super.afterStored(attachment);
         }
         else if(com.kingsrook.qbits.recordfiles.RecordFilesContext.config().isManaged(attachment.getValueString("tableName")))
         {
            new RetainedFileService(configForLifecycle(), current, null).registerStoredAttachment(attachment);
         }
      }



      /***********************************************************************
       ** Resolve the same installed configuration as the production lifecycle.
       ***********************************************************************/
      private com.kingsrook.qbits.recordfiles.RecordFilesQBitConfig configForLifecycle() throws QException
      {
         return com.kingsrook.qbits.recordfiles.RecordFilesContext.config();
      }
   }



   /***************************************************************************
    ** Real fixture authentication; no test identity bypass.
    ***************************************************************************/
   public static class Authentication extends TableBasedAuthenticationModule
   {
      private static volatile Set<String> deniedPermissions = Set.of();



      /***********************************************************************
       ** Server roles are assigned only after password/session validation.
       ***********************************************************************/
      @Override
      public QSession createSession(QInstance instance, Map<String, String> context) throws QAuthenticationException
      {
         return grant(instance, super.createSession(instance, context));
      }



      /***********************************************************************
       ** Native asynchronous actions retain the same server-owned permissions.
       ***********************************************************************/
      @Override
      public boolean isSessionValid(QInstance instance, QSession session)
      {
         boolean valid = super.isSessionValid(instance, session);
         if(valid)
         {
            grant(instance, session);
         }
         return valid;
      }



      /***********************************************************************
       ** Request fields cannot supply role or tenant grants.
       ***********************************************************************/
      private QSession grant(QInstance instance, QSession session)
      {
         if(session.getUser() == null)
         {
            return session;
         }
         session.withPermissions(ALL).withPermissions("document.read", "document.write", MobilePermissions.SCAN).withSecurityKeyValue("tenant", "a");
         if("admin".equals(session.getUser().getIdReference()))
         {
            session.withPermission(MobilePermissions.ADMIN);
         }
         else
         {
            session.getPermissions().removeAll(deniedPermissions);
         }
         CollaborationSecurity.applyToSession(instance, session);
         return session;
      }
   }



   /***************************************************************************
    ** Direct adapter checks use a real validated table-backed login session.
    ***************************************************************************/
   private QSession authenticated(String cookie) throws QAuthenticationException
   {
      return new Authentication().createSession(instance, Map.of(TableBasedAuthenticationModule.SESSION_UUID_KEY, cookie));
   }



   /***************************************************************************
    ** Login uses the same versioned password endpoint as the handheld.
    ***************************************************************************/
   private String signIn(String actor) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((actor + ":" + password).getBytes(StandardCharsets.UTF_8));
      var response = client.send(HttpRequest.newBuilder(URI.create(origin + "/qqq/v1/manageSession"))
         .header("Authorization", "Basic " + basic).header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /***************************************************************************
    ** Device identity is created by enrollment and returned only at that boundary.
    ***************************************************************************/
   private void enrollHttp(String session) throws Exception
   {
      var response = send("POST", "/qqq/v1/qbit/mobile-scanners/devices/enroll", session,
         JSON.writeValueAsBytes(Map.of("installationId", UUID.randomUUID().toString(), "displayName", "HTTP capture fixture")), false, Map.of());
      assertEquals(201, response.statusCode());
      JsonNode enrolled = JSON.readTree(response.body());
      deviceId = enrolled.path("deviceId").asText();
      deviceToken = enrolled.path("deviceToken").asText();
   }



   /***************************************************************************
    ** Native process selection starts from a record and requires confirmation.
    ***************************************************************************/
   private void approveHttp(String session, String process) throws Exception
   {
      JsonNode started = processForm(session, process, "/init", Map.of("recordsParam", "recordIds", "recordIds", deviceId, "tableName", "mobileDevice"));
      JsonNode result = processForm(session, process, "/" + started.path("processUUID").asText() + "/step/input", Map.of());
      assertFalse(result.has("error"), "Approval process must succeed.");
   }



   /***************************************************************************
    ** Multipart native process envelope and bounded async completion polling.
    ***************************************************************************/
   private JsonNode processForm(String session, String process, String suffix, Map<String, String> values) throws Exception
   {
      return processForm(session, process, suffix, values, false);
   }



   /***************************************************************************
    ** Device-bound frontend widgets use the same process API as admin actions.
    ***************************************************************************/
   private JsonNode processForm(String session, String process, String suffix, Map<String, String> values, boolean device) throws Exception
   {
      String boundary = "fixture" + UUID.randomUUID();
      StringBuilder body = new StringBuilder();
      Map<String, String> fields = new java.util.HashMap<>(values);
      fields.put("values", JSON.writeValueAsString(values));
      fields.put("stepTimeoutMillis", "1000");
      for(var field : fields.entrySet())
      {
         body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n").append(field.getValue()).append("\r\n");
      }
      body.append("--").append(boundary).append("--\r\n");
      JsonNode result = ok(send("POST", "/qqq/v1/processes/" + process + suffix, session, body.toString().getBytes(StandardCharsets.UTF_8), device,
         Map.of("Content-Type", "multipart/form-data; boundary=" + boundary)));
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while(Set.of("RUNNING", "JOB_STARTED").contains(result.path("type").asText()))
      {
         assertTrue(System.nanoTime() < deadline, "Native process timed out.");
         Thread.sleep(25);
         result = ok(send("GET", "/qqq/v1/processes/" + process + "/" + result.path("processUUID").asText() + "/status/" + result.path("jobUUID").asText(), session, null, device, Map.of()));
      }
      assertFalse(result.has("error"), "Native process returned an error.");
      return result;
   }



   /***************************************************************************
    ** Immutable manifest has no device, user or session identity fields.
    ***************************************************************************/
   private byte[] manifest(String request) throws Exception
   {
      return JSON.writeValueAsBytes(Map.of("requestId", request, "parent", Map.of("tableName", "document", "recordId", "1"),
         "items", List.of(Map.of("itemId", "20000000-0000-0000-0000-000000000001", "position", 0, "fileName", "fixture.pdf", "declaredMediaType", "application/pdf",
            "sizeBytes", CONTENT.length, "sha256", UploadManifest.hash(CONTENT), "origin", "UPLOAD"))));
   }



   /***************************************************************************
    ** Headers alone carry authentication; no credentials enter test diagnostics.
    ***************************************************************************/
   private HttpResponse<String> send(String method, String path, String session, byte[] bytes, boolean device, Map<String, String> extra) throws Exception
   {
      var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20))
         .header("Cookie", "sessionUUID=" + session).header("X-Record-Files-Request", "1")
         .header("Content-Type", extra.getOrDefault("Content-Type", "application/json"));
      if(device)
      {
         request.header("X-QQQ-Device-Id", deviceId).header("X-QQQ-Device-Token", deviceToken).header("X-QQQ-Client", "android/fixture");
      }
      extra.forEach((key, value) ->
      {
         if(!"Content-Type".equals(key))
         {
            request.header(key, value);
         }
      });
      return client.send(request.method(method, bytes == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** Report only status on failure, never credentials or arbitrary response data.
    ***************************************************************************/
   private JsonNode ok(HttpResponse<String> response) throws Exception
   {
      assertEquals(200, response.statusCode());
      return JSON.readTree(response.body());
   }
}
