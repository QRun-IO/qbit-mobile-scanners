/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.Serializable;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileCaptureIdentityProvider;
import com.kingsrook.qbits.mobilescanners.recordfiles.RecordFilesCaptureWidgetData;
import com.kingsrook.qbits.recordfiles.RecordFilesQBitConfig;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.TableBasedAuthenticationModule;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The mobile Record Files composition in the live Echo host: advertised
 ** extensions, ordinary semantic metadata, and server-side target binding. The
 ** renderer refuses every call and no photo is uploaded; real camera/gallery
 ** upload is a separate, root-run device qualification. Seeded rows exist only
 ** in this test's private temporary database.
 *******************************************************************************/
class EchoPhotoCaptureTest
{
   private static final String TOKEN = UUID.randomUUID() + "-" + UUID.randomUUID();
   private static final String SCANNER_PASSWORD = "scanner-test-password-1";
   private static final String ADMIN_PASSWORD = "admin-test-password-1";
   private static final String OWN_ID = "own-scan-0001";
   private static final String OTHER_ID = "other-scan-0001";
   private static final Set<Integer> PROTECTED = Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593);

   @TempDir
   Path root;

   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();



   /***************************************************************************
    ** The host's renderer is never reached by photo capture.
    ***************************************************************************/
   private static final class RefusingRenderer implements GenerationPorts.IsolatedRenderer
   {
      /***********************************************************************
       ** Never provides a reference.
       ***********************************************************************/
      @Override
      public String barcodeReference() throws QException
      {
         throw new QException("The photo test renderer refuses every call.");
      }



      /***********************************************************************
       ** Never validates.
       ***********************************************************************/
      @Override
      public void validate(String actor, GenerationPorts.Revision revision) throws QException
      {
         throw new QException("The photo test renderer refuses every call.");
      }



      /***********************************************************************
       ** Never renders.
       ***********************************************************************/
      @Override
      public GenerationPorts.Content render(GenerationPorts.RenderRequest request) throws QException
      {
         throw new QException("The photo test renderer refuses every call.");
      }
   }



   /***************************************************************************
    ** A fresh port that is neither privileged nor a protected local runtime.
    ***************************************************************************/
   private static int freePort() throws Exception
   {
      while(true)
      {
         try(ServerSocket socket = new ServerSocket(0))
         {
            if(socket.getLocalPort() >= 1024 && !PROTECTED.contains(socket.getLocalPort()))
            {
               return socket.getLocalPort();
            }
         }
      }
   }



   /***************************************************************************
    ** Connection providers are cached per backend name for the JVM; drop any
    ** left by another test so this host reads its own database.
    ***************************************************************************/
   @BeforeEach
   @AfterEach
   void isolateConnectionProviders()
   {
      QContext.clear();
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
   }



   /***************************************************************************
    ** Extensions, upload policy, catalog placement and ordinary metadata.
    ***************************************************************************/
   @Test
   void composesAdvertisedExtensionsAndOrdinaryMetadata() throws Exception
   {
      int port = freePort();
      EchoDemoSettings settings = new EchoDemoSettings(root, port, "echo-photo-test", TOKEN, false, null, null);
      try(EchoDemo demo = new EchoDemo(settings, new RefusingRenderer()))
      {
         demo.initializeDatabase(SCANNER_PASSWORD, ADMIN_PASSWORD);
         demo.start(port);
         QInstance instance = demo.defineQInstance();

         var extensions = MiddlewareSpecExtensionsMetaData.ofOrWithNew(instance).getExtensions();
         assertNotNull(extensions.get("record-files"), "the versioned upload API must be advertised");
         assertNotNull(extensions.get("mobile-record-files"), "the native widget vocabulary must be advertised");
         assertNotNull(extensions.get("mobile-scanners"), "the mobile contract is unchanged");
         assertNotNull(extensions.get("printing"), "existing Echo composition stays registered");

         RecordFilesQBitConfig config = (RecordFilesQBitConfig) instance.getQBits().values().stream().map(bit -> bit.getConfig())
            .filter(RecordFilesQBitConfig.class::isInstance).findFirst().orElseThrow();
         assertEquals(List.of(EchoQBitProducer.SCAN_TABLE), config.getParentTables());
         assertEquals(Set.of("image/jpeg", "image/png"), config.getUploadConfiguration().limits().acceptedMediaTypes());
         assertTrue(config.getUploadConfiguration().createIdentityProvider() instanceof MobileCaptureIdentityProvider);
         assertTrue(config.getUploadConfiguration().createStorage() instanceof LocalStagingChunkStorage);

         assertTrue(instance.getApp(EchoQBitProducer.APP).getSections().getFirst().getProcesses().containsAll(List.of(EchoQBitProducer.PROCESS, EchoPhotoCapture.PROCESS)));
         var process = instance.getProcess(EchoPhotoCapture.PROCESS);
         assertEquals(EchoPhotoCapture.Permissions.class.getName(), process.getPermissionRules().getCustomPermissionChecker().getName());
         assertEquals(EchoPhotoCapture.Permissions.class.getName(), instance.getWidget(EchoPhotoCapture.WIDGET).getPermissionRules().getCustomPermissionChecker().getName());
         assertEquals(3, process.getStepList().size());
         QFrontendStepMetaData select = (QFrontendStepMetaData) process.getStepList().get(0);
         assertEquals(List.of(EchoPhotoCapture.SELECTION_FIELD), select.getFormFields().stream().map(field -> field.getName()).toList(),
            "the client chooses a record only; it has no table or other record fields to pass");
         assertEquals(EchoPhotoCapture.CHOICES, select.getFormFields().getFirst().getPossibleValueSourceName());
         assertEquals(EchoPhotoCapture.WIDGET, ((QFrontendStepMetaData) process.getStepList().get(2)).getComponents().getFirst().getValues().get("widgetName"));

         HttpResponse<String> contract = send(port, "/qqq/v1/qbit/mobile-record-files/contract");
         assertEquals(200, contract.statusCode());
         assertTrue(contract.body().contains("RECORD_FILES_CAPTURE") && contract.body().contains("\"1.0.0\""));
      }
      finally
      {
         shutdown(settings);
      }
   }



   /***************************************************************************
    ** The widget's parent comes only from a record this actor may see and attach
    ** to, and is built from that record on the fixed Echo scan table.
    ***************************************************************************/
   @Test
   void bindsTargetServerSideAndRejectsForgedOrInvisibleRecords() throws Exception
   {
      int port = freePort();
      EchoDemoSettings settings = new EchoDemoSettings(root, port, "echo-photo-test", TOKEN, false, null, null);
      try(EchoDemo demo = new EchoDemo(settings, new RefusingRenderer()))
      {
         demo.initializeDatabase(SCANNER_PASSWORD, ADMIN_PASSWORD);
         QInstance instance = demo.defineQInstance();
         seed(settings);
         // a real login through the demo's authentication module: its session is stored and revalidated
         // by every native action, so ParentRecordAccess and the record lock run exactly as for a user
         QSession scanner = login(instance, "scanner", SCANNER_PASSWORD);
         QSession admin = login(instance, "admin", ADMIN_PASSWORD);
         QContext.init(instance, scanner);

         RecordFilesCaptureWidgetData widget = EchoPhotoCapture.widgetFor(OWN_ID);
         @SuppressWarnings("unchecked") Map<String, Serializable> parent = (Map<String, Serializable>) ((Map<String, Serializable>) widget.getSubBlocks().getFirst().get("values")).get("parent");
         assertEquals(Map.of("tableName", EchoQBitProducer.SCAN_TABLE, "recordId", OWN_ID), parent);

         assertThrows(QNotFoundException.class, () -> EchoPhotoCapture.widgetFor(OTHER_ID), "another actor's scan is invisible");
         assertThrows(QNotFoundException.class, () -> EchoPhotoCapture.widgetFor("no-such-scan"), "an unknown record id is not found");
         assertThrows(QBadRequestException.class, () -> EchoPhotoCapture.widgetFor(null));
         assertThrows(QBadRequestException.class, () -> EchoPhotoCapture.widgetFor("  "));
         assertThrows(QBadRequestException.class, () -> EchoPhotoCapture.widgetFor("a".repeat(65)));
         assertThrows(QBadRequestException.class, () -> EchoPhotoCapture.widgetFor("bad\nid"));

         // The choice list is the actor's own retained scans only, labelled without the record id.
         var choice = new EchoPhotoCapture.Choices().getPossibleValue(OWN_ID);
         assertNotNull(choice);
         assertEquals("001234567890 (LABEL-TYPE-CODE128)", choice.getLabel());
         assertNull(new EchoPhotoCapture.Choices().getPossibleValue(OTHER_ID));
         assertEquals("12?3 (QRCODE)", EchoPhotoCapture.label(new QRecord().withValue("barcode", "12\u001d3").withValue("symbology", "QRCODE")));

         // Rendering the widget directly cannot name a parent.
         assertThrows(QBadRequestException.class, () -> new EchoPhotoCapture.Widget().render(null));

         // A different, validly signed-in actor cannot reach the scanner's scan, and the scanner's own choice is not theirs.
         QContext.init(instance, admin);
         assertThrows(QNotFoundException.class, () -> EchoPhotoCapture.widgetFor(OWN_ID), "another signed-in actor must not see this scan");
         assertNull(new EchoPhotoCapture.Choices().getPossibleValue(OWN_ID));

         // Without a valid session the call is refused, never served.
         QContext.init(instance, new QSession().withUser(new QUser().withIdReference("scanner")).withSecurityKeyValue("qqq.session.userId", "scanner"));
         assertThrows(QException.class, () -> EchoPhotoCapture.widgetFor(OWN_ID), "a hand-built session is not a login");
      }
      finally
      {
         shutdown(settings);
      }
   }



   /***************************************************************************
    ** The process and its widget require Echo access, the attach grant and an
    ** approved device; a plain authenticated session has none of the device.
    ***************************************************************************/
   @Test
   void refusesSessionsWithoutEchoAttachOrApprovedDevice() throws Exception
   {
      int port = freePort();
      EchoDemoSettings settings = new EchoDemoSettings(root, port, "echo-photo-test", TOKEN, false, null, null);
      try(EchoDemo demo = new EchoDemo(settings, new RefusingRenderer()))
      {
         demo.initializeDatabase(SCANNER_PASSWORD, ADMIN_PASSWORD);
         QInstance instance = demo.defineQInstance();
         EchoPhotoCapture.Permissions permissions = new EchoPhotoCapture.Permissions();

         QContext.init(instance, actor(instance, "scanner"));
         assertThrows(QPermissionDeniedException.class, () -> permissions.checkPermissionsThrowing(null, null), "no verified device in this context");

         QContext.init(instance, actor(instance, "other"));
         assertThrows(QPermissionDeniedException.class, () -> permissions.checkPermissionsThrowing(null, null), "an account without Echo access");
         assertThrows(QException.class, () -> EchoPhotoCapture.widgetFor(OWN_ID), "and no record is selectable for it");
      }
      finally
      {
         shutdown(settings);
      }
   }



   /***************************************************************************
    ** Sign in through the host's real authentication module with the account's
    ** password. The module stores the session and later revalidates it. The
    ** approved-device layer, absent from a direct call, is what pins the actor
    ** key Echo's record lock reads, so only that key is added here.
    ***************************************************************************/
   private static QSession login(QInstance instance, String user, String password) throws Exception
   {
      QContext.setQInstance(instance);
      String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
      QSession session = new EchoDemoAuthentication().createSession(instance, Map.of(TableBasedAuthenticationModule.BASIC_AUTH_KEY, basic));
      assertEquals(user, session.getUser().getIdReference());
      return session.withSecurityKeyValue("qqq.session.userId", user);
   }



   /***************************************************************************
    ** Grants exactly as login does, plus the actor key Echo's record lock reads.
    ** Used only where no stored session is needed: permission checks on context.
    ***************************************************************************/
   private static QSession actor(QInstance instance, String user)
   {
      QSession session = EchoDemoAuthentication.grant(instance, new QSession().withUser(new QUser().withIdReference(user)));
      return session.withSecurityKeyValue("qqq.session.userId", user);
   }



   /***************************************************************************
    ** Two synthetic retained scans in this test's private database: one for the
    ** scanner and one for another actor. They are never printed or published.
    ***************************************************************************/
   private static void seed(EchoDemoSettings settings) throws Exception
   {
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""))
      {
         for(String actor : List.of("scanner", "other-actor"))
         {
            try(PreparedStatement run = connection.prepareStatement("INSERT INTO echo_run (id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES (?,?,?,?,?,?,?)"))
            {
               List<String> values = List.of("run-" + actor, actor, "device-1", "session-1", "printer-1", "media-1", "OPEN");
               for(int index = 0; index < values.size(); index++)
               {
                  run.setString(index + 1, values.get(index));
               }
               run.executeUpdate();
            }
         }
         insertScan(connection, OWN_ID, "run-scanner", "scanner", "001234567890", "LABEL-TYPE-CODE128");
         insertScan(connection, OTHER_ID, "run-other-actor", "other-actor", "OTHER-VALUE", "QRCODE");
      }
   }



   /***************************************************************************
    ** One retained scan row with every required column.
    ***************************************************************************/
   private static void insertScan(Connection connection, String id, String run, String actor, String barcode, String symbology) throws Exception
   {
      try(PreparedStatement scan = connection.prepareStatement("INSERT INTO echo_scan (id,runId,eventId,actorId,deviceId,mobileScanId,barcode,symbology,rendererSymbology,scanSource,capturedAt,receivedAt,state) "
         + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"))
      {
         List<String> values = List.of(id, run, "event-" + id, actor, "device-1", "mobile-" + id, barcode, symbology, "code128", "test", "2026-11-02T00:00:00Z", "2026-11-02T00:00:01Z", "QUEUED");
         for(int index = 0; index < values.size(); index++)
         {
            scan.setString(index + 1, values.get(index));
         }
         scan.executeUpdate();
      }
   }



   /***************************************************************************
    ** Release the private database file.
    ***************************************************************************/
   private static void shutdown(EchoDemoSettings settings) throws Exception
   {
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         statement.execute("SHUTDOWN");
      }
   }



   /***************************************************************************
    ** One unauthenticated loopback GET with the native client channel.
    ***************************************************************************/
   private HttpResponse<String> send(int port, String path) throws Exception
   {
      HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20))
         .header("X-QQQ-Client", "android/qqq-mobile").header("Origin", "http://127.0.0.1:" + port).GET().build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
   }
}
