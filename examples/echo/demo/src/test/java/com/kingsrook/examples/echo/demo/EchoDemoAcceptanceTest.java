/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import com.kingsrook.examples.echo.EchoDestinations;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.examples.echo.EchoTemplatePublisher;
import com.kingsrook.qbits.documentgeneration.DocumentDefinitionService;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationQBitConfig;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.TableFeaturePermissionsMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Complete composed-host acceptance over a fresh private file database: an
 ** authenticated, approved scanner runs the real Echo process, which drives the
 ** real Generation, Record Files retention and Printing job acceptance. Only two
 ** inputs are controlled: the renderer returns fixed synthetic ZPL, and the
 ** "printer" is a Printing device enrolled and inventoried through the owner's
 ** public service API. No Docker, physical printer, agent or device is touched,
 ** so this qualifies composition and contracts, not physical output.
 *******************************************************************************/
class EchoDemoAcceptanceTest
{
   private static final String TOKEN = "0123456789abcdef0123456789abcdef-secret";
   private static final String TENANT = "echo-accept";
   private static final String MEDIA = "echo-4x6-300";
   private static final String REVISION = "c".repeat(64);
   private static final String QUEUE = "EchoAcceptanceQueue";
   private static final String CODE_VALUE = "001234567890";
   private static final String QR_VALUE = "QR-VALUE-0099";
   private static final Set<Integer> PROTECTED = Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593);

   @TempDir
   Path root;

   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
   private String origin;
   private String deviceId;
   private String deviceToken;



   /***************************************************************************
    ** Fixed synthetic output, derived only from the exact context the real
    ** Generation service hands the renderer. It records every call.
    ***************************************************************************/
   private static final class ControlledRenderer implements GenerationPorts.IsolatedRenderer
   {
      private final List<GenerationPorts.RenderRequest> renders = new CopyOnWriteArrayList<>();
      private final AtomicInteger validations = new AtomicInteger();



      /***********************************************************************
       ** Catalog text with the owner's required heading.
       ***********************************************************************/
      @Override
      public String barcodeReference() throws QException
      {
         return "# Barcode catalog: qqq-barcodes-9 (controlled synthetic test renderer)";
      }



      /***********************************************************************
       ** Accept the published revision; the call is counted.
       ***********************************************************************/
      @Override
      public void validate(String actor, GenerationPorts.Revision revision) throws QException
      {
         validations.incrementAndGet();
      }



      /***********************************************************************
       ** Return fixed ZPL naming the exact symbology and value received.
       ***********************************************************************/
      @Override
      public GenerationPorts.Content render(GenerationPorts.RenderRequest request) throws QException
      {
         renders.add(request);
         return new GenerationPorts.Content(zpl((Map<?, ?>) request.context().get("record")).getBytes(StandardCharsets.US_ASCII));
      }
   }



   /***************************************************************************
    ** The synthetic label for one scanned record context.
    ***************************************************************************/
   private static String zpl(Map<?, ?> record)
   {
      return "^XA^FO48,48^FD" + record.get("rendererSymbology") + "|" + record.get("barcode") + "^FS^XZ";
   }



   /***************************************************************************
    ** The RDBMS connection providers are cached per backend name for the whole
    ** JVM, and every composed host here names its backend "mobileDb". Drop any
    ** provider left by another test so this run never depends on test order.
    ***************************************************************************/
   @BeforeEach
   @AfterEach
   void isolateConnectionProviders()
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
   }



   /***************************************************************************
    ** Setup through owner APIs, then scanner scans over real native HTTP.
    ***************************************************************************/
   @Test
   void approvedScannerScansBecomeRetainedOriginalsAndPrintingJobs() throws Exception
   {
      ControlledRenderer renderer = new ControlledRenderer();
      int setupPort = freePort();
      int port = freePort();
      EchoDemoSettings setup = new EchoDemoSettings(root, setupPort, TENANT, TOKEN, true, null, null);
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      String printerRowId;
      Integer templateId;
      try(EchoDemo demo = new EchoDemo(setup, renderer))
      {
         demo.initializeDatabase(scannerPassword, adminPassword);
         printerRowId = enrollPrinter(demo, setup);
         demo.start(setupPort);
         origin = "http://127.0.0.1:" + setupPort;
         templateId = publishTemplate(demo, printerRowId, login("admin", adminPassword));
      }
      assertTrue(renderer.validations.get() > 0, "Publishing must validate the exact template in the renderer.");

      EchoDemoSettings settings = new EchoDemoSettings(root, port, TENANT, TOKEN, true, new EchoDemoSettings.TemplatePin(MEDIA, REVISION, templateId), null);
      origin = "http://127.0.0.1:" + port;
      try(EchoDemo demo = new EchoDemo(settings, renderer))
      {
         assertNotNull(TableFeaturePermissionsMetaData.of(demo.defineQInstance()), "Host enrichment must register record-feature permissions.");
         demo.start(port);
         String scanner = login("scanner", scannerPassword);
         String admin = login("admin", adminPassword);
         enrollAndApproveDevice(scanner, admin);

         JSONObject launched = echo(form("/qqq/v1/processes/echoPrint/init", scanner, new JSONObject(), true), scanner);
         assertEquals("setup", launched.getString("nextStep"));
         String process = launched.getString("processUUID");
         JSONObject selected = echo(form("/qqq/v1/processes/echoPrint/" + process + "/step/setup", scanner,
            new JSONObject().put("printerId", printerRowId).put("mediaId", MEDIA), true), scanner);
         assertEquals("scan", selected.getString("nextStep"));

         JSONObject firstEvent = event("LABEL-TYPE-CODE128", CODE_VALUE);
         JSONObject first = echo(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, firstEvent, true), scanner).getJSONObject("values");
         assertEquals("QUEUED", first.getString("printStatus"));
         String firstJob = first.getString("printJobId");
         assertFalse(firstJob.isBlank());
         JSONObject retry = echo(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, firstEvent, true), scanner).getJSONObject("values");
         assertEquals(firstJob, retry.getString("printJobId"));
         JSONObject secondResult = echo(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, event("QRCODE", QR_VALUE), true), scanner);
         assertEquals("scan", secondResult.getString("nextStep"));
         String secondJob = secondResult.getJSONObject("values").getString("printJobId");
         assertFalse(secondJob.isBlank());
         assertFalse(firstJob.equals(secondJob), "Each scan is its own accepted job.");

         String firstZpl = "^XA^FO48,48^FDcode128|" + CODE_VALUE + "^FS^XZ";
         String secondZpl = "^XA^FO48,48^FDqr|" + QR_VALUE + "^FS^XZ";
         assertEquals(2, renderer.renders.size(), "A retry must not render again.");
         for(GenerationPorts.RenderRequest render : renderer.renders)
         {
            assertEquals(GenerationPorts.Format.ZPL, render.format());
            assertEquals(EchoQBitProducer.SCAN_TABLE, render.source().tableName());
            assertEquals(templateId, render.revision().templateId());
         }
         Map<?, ?> firstRecord = (Map<?, ?>) renderer.renders.get(0).context().get("record");
         Map<?, ?> secondRecord = (Map<?, ?>) renderer.renders.get(1).context().get("record");
         assertEquals("code128", firstRecord.get("rendererSymbology"));
         assertEquals(CODE_VALUE, firstRecord.get("barcode"));
         assertEquals("qr", secondRecord.get("rendererSymbology"));
         assertEquals(QR_VALUE, secondRecord.get("barcode"));

         JSONArray scans = query(EchoQBitProducer.SCAN_TABLE, scanner);
         assertEquals(2, scans.length());
         Set<String> accepted = new HashSet<>();
         for(Object item : scans)
         {
            JSONObject values = ((JSONObject) item).getJSONObject("values");
            assertEquals("QUEUED", values.getString("state"));
            accepted.add(values.getString("rendererSymbology") + "|" + values.getString("barcode") + "|" + values.getString("printJobId"));
         }
         assertEquals(Set.of("code128|" + CODE_VALUE + "|" + firstJob, "qr|" + QR_VALUE + "|" + secondJob), accepted);

         List<List<String>> jobs = sql(setup, "SELECT id, printer_row_id, printer_id, format, content_base64 FROM printing_job", 5);
         assertEquals(2, jobs.size(), "Exactly one accepted Printing job per scan.");
         Set<String> jobIds = new HashSet<>();
         Set<String> content = new HashSet<>();
         for(List<String> job : jobs)
         {
            jobIds.add(job.get(0));
            assertEquals(printerRowId, job.get(1), "Both scans use the one selected destination.");
            assertEquals(QUEUE, job.get(2));
            assertEquals("ZPL", job.get(3));
            content.add(new String(Base64.getDecoder().decode(job.get(4)), StandardCharsets.US_ASCII));
         }
         assertEquals(Set.of(firstJob, secondJob), jobIds);
         assertEquals(Set.of(firstZpl, secondZpl), content);

         Set<String> retained = new HashSet<>();
         try(Stream<Path> files = Files.walk(root.resolve("objects")))
         {
            for(Path file : files.filter(Files::isRegularFile).filter(path -> !path.getFileName().toString().endsWith(".json")).toList())
            {
               retained.add(Files.readString(file, StandardCharsets.US_ASCII));
            }
         }
         assertEquals(Set.of(firstZpl, secondZpl), retained, "Record Files retained exactly the two generated originals.");

         assertEquals(200, form("/qqq/v1/processes/echoPrint/" + process + "/cancel", scanner, new JSONObject(), true).statusCode());
         List<List<String>> runs = sql(setup, "SELECT state, printerRecordId, mediaId FROM echo_run", 3);
         assertEquals(List.of(List.of("CLOSED", printerRowId, MEDIA)), runs);
      }
   }



   /***************************************************************************
    ** The shared X-QQQ-Device-* headers belong to two independent registries.
    ** A real native Printing agent enrollment and session must work on its own
    ** channel, while a printer proof never satisfies scanner authentication and
    ** the channel marker alone never replaces it.
    ***************************************************************************/
   @Test
   void printerDeviceProofIsAuthorizedByPrintingAndNeverByScannerAuthentication() throws Exception
   {
      int port = freePort();
      EchoDemoSettings settings = new EchoDemoSettings(root, port, TENANT, TOKEN, true, null, null);
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      origin = "http://127.0.0.1:" + port;
      try(EchoDemo demo = new EchoDemo(settings, new ControlledRenderer()))
      {
         demo.initializeDatabase(scannerPassword, adminPassword);
         String code = demo.printing().createEnrollment("default", "Printer agent").getString("enrollmentCode");
         demo.start(port);
         String scanner = login("scanner", scannerPassword);
         String admin = login("admin", adminPassword);
         enrollAndApproveDevice(scanner, admin);
         String scannerId = deviceId;
         String scannerToken = deviceToken;
         String agent = "api/qqq-print-agent";

         HttpResponse<String> enrolled = as(agent, "POST", "/qqq/v1/qbit/printing/devices/enroll", admin,
            new JSONObject().put("enrollmentCode", code).put("deviceName", "Printer agent").put("agentVersion", "acceptance-1").toString(), null, null);
         assertEquals(200, enrolled.statusCode(), "Printing enrollment must not be intercepted by scanner device binding.");
         String printerId = new JSONObject(enrolled.body()).getString("deviceId");
         String printerToken = new JSONObject(enrolled.body()).getString("deviceToken");
         assertFalse(printerId.equals(scannerId), "Printing and scanner devices are separate registries.");

         assertEquals(200, as(agent, "POST", "/qqq/v1/qbit/printing/device/bind", admin, "{}", printerId, printerToken).statusCode());
         HttpResponse<String> session = as(agent, "GET", "/qqq/v1/qbit/printing/session", admin, null, printerId, printerToken);
         assertEquals(200, session.statusCode(), "The Printing session with its own device proof must succeed.");
         assertEquals(printerId, new JSONObject(session.body()).getString("deviceId"));

         assertEquals(403, as(agent, "GET", "/qqq/v1/qbit/printing/session", admin, null, printerId, "wrong-proof").statusCode());
         assertEquals(403, as(agent, "GET", "/qqq/v1/qbit/printing/session", scanner, null, printerId, printerToken).statusCode(),
            "A different actor cannot use the printer proof.");

         // scanner operations always verify the scanner registry, whatever channel the request claims
         assertEquals(403, as("android/qqq-mobile", "POST", "/qqq/v1/processes/echoPrint/init", scanner, "{}", printerId, printerToken).statusCode());
         assertEquals(403, as(agent, "POST", "/qqq/v1/processes/echoPrint/init", scanner, "{}", printerId, printerToken).statusCode());
         assertEquals(403, as(agent, "POST", "/qqq/v1/processes/echoPrint/init", scanner, "{}", scannerId, "wrong-proof").statusCode());
         assertEquals(403, as(agent, "GET", "/qqq/v1/qbit/mobile-scanners/devices/current", scanner, null, printerId, printerToken).statusCode());
         // Android keeps scanner verification even on another extension's routes
         assertEquals(403, as("android/qqq-mobile", "GET", "/qqq/v1/qbit/printing/session", admin, null, printerId, printerToken).statusCode());
         // and the scanner's own proof still works on its own channel and routes
         assertEquals(200, as("android/qqq-mobile", "GET", "/qqq/v1/metaData", scanner, null, scannerId, scannerToken).statusCode());
      }
   }



   /***************************************************************************
    ** One request with an explicit client channel and optional device proof.
    ***************************************************************************/
   private HttpResponse<String> as(String channel, String method, String path, String session, String body, String id, String token) throws Exception
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20)).header("X-QQQ-Client", channel)
         .header("Origin", origin).header("Content-Type", "application/json");
      if(session != null)
      {
         request.header("Cookie", "sessionUUID=" + session);
      }
      if(id != null)
      {
         request.header("X-QQQ-Device-Id", id).header("X-QQQ-Device-Token", token);
      }
      return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
         HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** A real Printing device and one ready ZPL queue with one reported media
    ** profile, enabled, through the owner's public service. Returns the row id.
    ***************************************************************************/
   private static String enrollPrinter(EchoDemo demo, EchoDemoSettings setup) throws Exception
   {
      var printing = demo.printing();
      String code = printing.createEnrollment("echo-facility", "Echo acceptance host").getString("enrollmentCode");
      String device = printing.enroll(new JSONObject().put("enrollmentCode", code).put("deviceName", "Echo acceptance host").put("agentVersion", "acceptance-1"))
         .getString("deviceId");
      JSONObject media = new JSONObject().put("id", MEDIA).put("revision", REVISION).put("name", "4 x 6 in").put("widthMicrons", 101600)
         .put("heightMicrons", 152400).put("dpiX", 300).put("dpiY", 300).put("source", "WINDOWS_QUEUE_DEFAULT").put("applyMode", "DRIVER_DEFAULT");
      printing.inventory(device, new JSONArray().put(new JSONObject().put("printerId", QUEUE).put("name", QUEUE)
         .put("formats", new JSONArray().put("ZPL")).put("status", "READY").put("mediaProfiles", new JSONArray().put(media))));
      printing.enablePrinter(device, QUEUE, true);
      return sql(setup, "SELECT id FROM printing_printer WHERE device_id='" + device + "'", 1).getFirst().getFirst();
   }



   /***************************************************************************
    ** Publish the ZPL template for the reported media as the authenticated admin.
    ** The session is the demo authentication module's own session for the stored
    ** native login (not a fabricated user), so core session validation and the
    ** admin's real grants apply. The renderer validates the template.
    ***************************************************************************/
   private Integer publishTemplate(EchoDemo demo, String printerRowId, String adminSessionId) throws Exception
   {
      QInstance instance = demo.defineQInstance();
      DocumentGenerationQBitConfig config = instance.getQBits().values().stream().map(bit -> bit.getConfig())
         .filter(DocumentGenerationQBitConfig.class::isInstance).map(DocumentGenerationQBitConfig.class::cast).findFirst().orElseThrow();
      // The module's session resume runs a real query, so the instance must be in the context first
      // (the module sets the resumed session itself). Resume and publish share one cleared scope.
      QContext.init(instance, null);
      try
      {
         QSession admin = new EchoDemoAuthentication().createSession(instance, Map.of("sessionUUID", adminSessionId, "sessionId", adminSessionId));
         QContext.init(instance, admin);
         return EchoTemplatePublisher.publish(new DocumentDefinitionService(config),
            new EchoDestinations.Destination(printerRowId, MEDIA, REVISION, 101600, 152400, 300, 300));
      }
      finally
      {
         QContext.clear();
      }
   }



   /***************************************************************************
    ** Read rows over JDBC from the private file database, as strings.
    ***************************************************************************/
   private static List<List<String>> sql(EchoDemoSettings settings, String statement, int columns) throws Exception
   {
      List<List<String>> rows = new ArrayList<>();
      try(var connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); var query = connection.createStatement();
         var result = query.executeQuery(statement))
      {
         while(result.next())
         {
            List<String> row = new ArrayList<>();
            for(int column = 1; column <= columns; column++)
            {
               row.add(result.getString(column));
            }
            rows.add(row);
         }
      }
      return rows;
   }



   /***************************************************************************
    ** The scanner enrolls its device through the native route and the admin
    ** approves it with the native approval process.
    ***************************************************************************/
   private void enrollAndApproveDevice(String scanner, String admin) throws Exception
   {
      HttpResponse<String> enrolled = request("POST", "/qqq/v1/qbit/mobile-scanners/devices/enroll", scanner,
         new JSONObject().put("installationId", UUID.randomUUID().toString()).toString(), false);
      assertTrue(enrolled.statusCode() == 200 || enrolled.statusCode() == 201, "Device enrollment returned " + enrolled.statusCode() + ": " + enrolled.body());
      JSONObject device = new JSONObject(enrolled.body());
      deviceId = device.getString("deviceId");
      deviceToken = device.getString("deviceToken");
      JSONObject approval = complete(form("/qqq/v1/processes/mobileDeviceApprove/init", admin,
         new JSONObject().put("recordsParam", "recordIds").put("recordIds", deviceId).put("tableName", "mobileDevice"), false), "mobileDeviceApprove", admin, false);
      complete(form("/qqq/v1/processes/mobileDeviceApprove/" + approval.getString("processUUID") + "/step/input", admin, new JSONObject(), false),
         "mobileDeviceApprove", admin, false);
      assertEquals(200, request("GET", "/qqq/v1/metaData", scanner, null, true).statusCode());
   }



   /***************************************************************************
    ** A fresh port that is neither privileged nor a protected local runtime.
    ***************************************************************************/
   private static int freePort() throws Exception
   {
      while(true)
      {
         try(ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress()))
         {
            if(socket.getLocalPort() >= 1024 && !PROTECTED.contains(socket.getLocalPort()))
            {
               return socket.getLocalPort();
            }
         }
      }
   }



   /***************************************************************************
    ** QQQ validates Basic credentials; secrets never appear in assertion output.
    ***************************************************************************/
   private String login(String username, String password) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      HttpResponse<String> response = client.send(headers("/qqq/v1/manageSession", null, false).header("Authorization", "Basic " + basic)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), "Native login failed.");
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /***************************************************************************
    ** Observations use ordinary authorized QQQ table queries as the scanner.
    ***************************************************************************/
   private JSONArray query(String table, String session) throws Exception
   {
      HttpResponse<String> response = request("POST", "/qqq/v1/table/" + table + "/query", session,
         new JSONObject().put("filter", new JSONObject().put("limit", 20)).toString(), true);
      assertEquals(200, response.statusCode(), "Native inspection failed.");
      return new JSONObject(response.body()).getJSONArray("records");
   }



   /***************************************************************************
    ** Echo process step as the scanner with the approved device headers.
    ***************************************************************************/
   private JSONObject echo(HttpResponse<String> response, String session) throws Exception
   {
      return complete(response, EchoQBitProducer.PROCESS, session, true);
   }



   /***************************************************************************
    ** Poll an async process step to completion; an error body fails the test.
    ***************************************************************************/
   private JSONObject complete(HttpResponse<String> response, String process, String session, boolean device) throws Exception
   {
      assertEquals(200, response.statusCode(), "Native process request failed.");
      JSONObject body = new JSONObject(response.body());
      String processId = body.optString("processUUID");
      String jobId = body.optString("jobUUID");
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while("JOB_STARTED".equals(body.optString("type")) || "RUNNING".equals(body.optString("type")))
      {
         assertTrue(System.nanoTime() < deadline, "Native process timed out.");
         Thread.sleep(25);
         response = request("GET", "/qqq/v1/processes/" + process + "/" + processId + "/status/" + jobId, session, null, device);
         assertEquals(200, response.statusCode());
         body = new JSONObject(response.body());
      }
      assertFalse(body.has("error"), "Native process returned an error: " + body.optString("error"));
      return body;
   }



   /***************************************************************************
    ** The same multipart JSON-values body as the native QQQ process client.
    ***************************************************************************/
   private HttpResponse<String> form(String path, String session, JSONObject values, boolean device) throws Exception
   {
      String boundary = "echo-accept-" + UUID.randomUUID();
      String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"values\"\r\n\r\n" + values
         + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"stepTimeoutMillis\"\r\n\r\n1\r\n";
      for(String key : new String[] { "recordsParam", "recordIds", "filterJSON" })
      {
         if(values.has(key))
         {
            body += "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + key + "\"\r\n\r\n" + values.getString(key) + "\r\n";
         }
      }
      body += "--" + boundary + "--\r\n";
      return client.send(headers(path, session, device).header("Content-Type", "multipart/form-data; boundary=" + boundary)
         .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** All requests target this test's loopback origin.
    ***************************************************************************/
   private HttpResponse<String> request(String method, String path, String session, String body, boolean device) throws Exception
   {
      return client.send(headers(path, session, device).header("Content-Type", "application/json")
         .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** Credentials and device tokens remain request headers, never process values.
    ***************************************************************************/
   private HttpRequest.Builder headers(String path, String session, boolean device)
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20)).header("X-QQQ-Client", "android/qqq-mobile")
         .header("Origin", origin);
      if(session != null)
      {
         request.header("Cookie", "sessionUUID=" + session);
      }
      if(device)
      {
         request.header("X-QQQ-Device-Id", deviceId).header("X-QQQ-Device-Token", deviceToken);
      }
      return request;
   }



   /***************************************************************************
    ** One scan event with a unique id and the exact scanned text.
    ***************************************************************************/
   private static JSONObject event(String type, String value)
   {
      return new JSONObject().put("barcode", value).put("symbology", type).put("scanSource", "zebra-emdk")
         .put("eventId", UUID.randomUUID().toString()).put("capturedAt", "2026-11-01T18:00:00Z");
   }
}
