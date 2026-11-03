/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.mobilescanners.MobileScannersQBitProducer;
import com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The normal local test server's existing mobile database is adopted in place
 ** by the combined Echo host: a baseline MobileScannersDemo creates accounts, an
 ** approved device and a scan in a file database, then EchoDemo opens that same
 ** file through the explicit existing-local-server mode. Temporary directories and
 ** fresh loopback ports only; the canonical port and data are never touched.
 *******************************************************************************/
class EchoDemoAdoptionTest
{
   private static final String TOKEN = UUID.randomUUID() + "-" + UUID.randomUUID();
   private static final List<String> TABLES = List.of("demoUser", "mobileDevice", "mobileDeviceCredential", "mobileScan");

   @TempDir
   Path root;

   @TempDir
   Path data;

   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
   private String origin;



   /***************************************************************************
    ** Fixture renderer: adoption never renders and never validates a template.
    ***************************************************************************/
   private static final class UnusedRenderer implements GenerationPorts.IsolatedRenderer
   {
      /***********************************************************************
       ** Catalog text with the owner's required heading.
       ***********************************************************************/
      @Override
      public String barcodeReference() throws QException
      {
         return "# Barcode catalog: qqq-barcodes-9 (adoption test renderer)";
      }



      /***********************************************************************
       ** Not used by adoption.
       ***********************************************************************/
      @Override
      public void validate(String actor, GenerationPorts.Revision revision) throws QException
      {
      }



      /***********************************************************************
       ** Adoption never renders.
       ***********************************************************************/
      @Override
      public GenerationPorts.Content render(GenerationPorts.RenderRequest request) throws QException
      {
         throw new QException("This adoption test never renders.");
      }
   }



   /***************************************************************************
    ** Each test starts and ends without a cached connection provider.
    ***************************************************************************/
   @BeforeEach
   @AfterEach
   void isolateConnectionProviders()
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
   }



   /***************************************************************************
    ** Accounts, hashes, device credentials, approval and scans survive adoption
    ** and an idempotent restart; the one QInstance exposes every composed QBit.
    ***************************************************************************/
   @Test
   void adoptingAnExistingLocalDatabasePreservesAccountsDevicesAndScansAcrossRestarts() throws Exception
   {
      Path base = data.resolve("mobile");
      Path file = data.resolve("mobile.mv.db");
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      String[] device = baseline(base, scannerPassword, adminPassword);
      assertTrue(Files.isRegularFile(file));
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();

      EchoDemoSettings settings = new EchoDemoSettings(root, freePort(), "echo-adopt", TOKEN, true, null, null, file);
      Map<String, List<List<String>>> before = snapshot(settings, true);
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      assertFalse(before.get("mobileScan").isEmpty());
      assertFalse(before.get("mobileDeviceCredential").isEmpty());

      Map<String, List<List<String>>> afterDeviceUse;
      try(EchoDemo demo = new EchoDemo(settings, new UnusedRenderer()))
      {
         // different passwords on adoption are ignored: nothing is seeded or changed
         demo.initializeDatabase(UUID.randomUUID().toString(), UUID.randomUUID().toString());
         demo.start(settings.port());
         origin = "http://127.0.0.1:" + settings.port();
         QInstance instance = demo.defineQInstance();
         Set<String> composed = new HashSet<>();
         instance.getQBits().values().forEach(bit -> composed.add(bit.getConfig().getClass().getSimpleName()));
         for(String expected : List.of("MobileScannersQBitConfig", "EchoConfig", "RecordFilesQBitConfig", "CollaborationQBitConfig", "DocumentGenerationQBitConfig", "PrintingQBitConfig"))
         {
            assertTrue(composed.contains(expected), expected + " must be composed into the one server instance");
         }
         assertNotNull(instance.getTable(EchoQBitProducer.SCAN_TABLE));
         assertNotNull(instance.getTable(MobileScannersQBitProducer.SCAN));
         assertNotNull(instance.getTable("printing_printer"));
         assertNotNull(instance.getTable("document_generation_template"));
         assertNotNull(instance.getProcess(EchoQBitProducer.PROCESS));

         // the old native accounts still work; the passwords supplied at adoption do not
         String scanner = login("scanner", scannerPassword);
         login("admin", adminPassword);
         assertNotEquals(200, loginStatus("scanner", UUID.randomUUID().toString()));
         assertEquals(before, snapshot(settings, false), "Adopting the database must not change accounts, devices, credentials or scans.");

         // the already approved device credential works against the composed server
         assertEquals(200, request("GET", "/qqq/v1/metaData/process/" + EchoQBitProducer.PROCESS, scanner, device).statusCode());
         assertEquals(200, request("GET", "/qqq/v1/metaData", scanner, device).statusCode());
         assertTrue(List.of(401, 403).contains(request("GET", "/qqq/v1/metaData", scanner, new String[] { device[0], "wrong-proof" }).statusCode()));
      }
      shutdown(settings);
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      afterDeviceUse = snapshot(settings, true);
      for(String table : List.of("demoUser", "mobileDeviceCredential", "mobileScan"))
      {
         assertEquals(before.get(table), afterDeviceUse.get(table), table + " must be unchanged by adoption and device use.");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();

      // idempotent restart of the same file
      EchoDemoSettings again = new EchoDemoSettings(root, freePort(), "echo-adopt", TOKEN, true, null, null, file);
      try(EchoDemo demo = new EchoDemo(again, new UnusedRenderer()))
      {
         demo.initializeDatabase(null, null);
         demo.start(again.port());
         origin = "http://127.0.0.1:" + again.port();
         login("scanner", scannerPassword);
         assertEquals(afterDeviceUse, snapshot(again, false), "A second startup must change nothing.");
      }
      shutdown(again);
   }



   /***************************************************************************
    ** Only an existing absolute H2 .mv.db file is accepted, and the canonical
    ** local server port is allowed only in that explicit mode.
    ***************************************************************************/
   @Test
   void existingLocalServerModeRefusesUnsafeDatabasesAndPorts() throws Exception
   {
      Path file = data.resolve("mobile.mv.db");
      Files.writeString(file, "h2");
      Path wrong = data.resolve("mobile.h2.db");
      Files.writeString(wrong, "h2");
      Path directory = Files.createDirectory(data.resolve("directory.mv.db"));
      int port = freePort();
      for(Path invalid : new Path[] { data.resolve("missing.mv.db"), wrong, directory, Path.of("relative.mv.db"), data.resolve(".mv.db") })
      {
         assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, port, "echo-adopt", TOKEN, true, null, null, invalid));
      }
      // fresh private demos keep every protected port, including the canonical server and peer printing
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 57832, "echo-adopt", TOKEN, true, null, null));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 57832, "echo-adopt", TOKEN, true, null, null, null));
      // adoption permits only the canonical port; peer runtimes stay protected
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 18081, "echo-adopt", TOKEN, true, null, null, file));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 51631, "echo-adopt", TOKEN, true, null, null, file));
      // construction only validates; nothing here binds the canonical port
      EchoDemoSettings normal = new EchoDemoSettings(root, 57832, "echo-adopt", TOKEN, true, null, null, file);
      String url = EchoDemo.jdbcUrl(normal);
      assertTrue(url.contains("IFEXISTS=TRUE") && url.contains("DATABASE_TO_UPPER=FALSE") && url.contains("WRITE_DELAY=0"));
      assertTrue(url.endsWith("WRITE_DELAY=0") && url.startsWith("jdbc:h2:file:") && !url.contains(".mv.db"));
      assertFalse(normal.toString().contains(TOKEN));
      // the environment contract
      Map<String, String> environment = Map.of("ECHO_DEMO_ROOT", root.toString(), "ECHO_DEMO_PORT", String.valueOf(port), "ECHO_PRINTING_TENANT", "echo-adopt",
         "ECHO_PRINTING_ADMIN_TOKEN", TOKEN, "ECHO_EXISTING_DATABASE", file.toString());
      assertEquals(file, EchoDemoSettings.fromLocalEnvironment(environment, null).existingDatabase());
      assertEquals(null, EchoDemoSettings.fromLocalEnvironment(Map.of("ECHO_DEMO_ROOT", root.toString(), "ECHO_DEMO_PORT", String.valueOf(port),
         "ECHO_PRINTING_TENANT", "echo-adopt", "ECHO_PRINTING_ADMIN_TOKEN", TOKEN), null).existingDatabase());
   }



   /***************************************************************************
    ** Baseline: a plain MobileScannersDemo creates accounts, enrolls and
    ** approves a device through the native routes and records one scan. Returns
    ** the device id and token, then closes the database file cleanly.
    ***************************************************************************/
   private String[] baseline(Path base, String scannerPassword, String adminPassword) throws Exception
   {
      String url = "jdbc:h2:file:" + base.toString().replace('\\', '/') + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
      MobileScannersDemo demo = new MobileScannersDemo(url);
      demo.initializeDatabase(scannerPassword, adminPassword);
      int port = freePort();
      QApplicationJavalinServer server = demo.start(port);
      origin = "http://127.0.0.1:" + port;
      try
      {
         String scanner = login("scanner", scannerPassword);
         String admin = login("admin", adminPassword);
         JSONObject enrolled = new JSONObject(post("/qqq/v1/qbit/mobile-scanners/devices/enroll", scanner, new JSONObject().put("installationId", UUID.randomUUID().toString()).toString(), null).body());
         String[] device = { enrolled.getString("deviceId"), enrolled.getString("deviceToken") };
         JSONObject approval = complete(form("/qqq/v1/processes/mobileDeviceApprove/init", admin,
            new JSONObject().put("recordsParam", "recordIds").put("recordIds", device[0]).put("tableName", "mobileDevice"), null), "mobileDeviceApprove", admin, null);
         complete(form("/qqq/v1/processes/mobileDeviceApprove/" + approval.getString("processUUID") + "/step/input", admin, new JSONObject(), null),
            "mobileDeviceApprove", admin, null);
         JSONObject launch = complete(form("/qqq/v1/processes/mobileScanCapture/init", scanner, new JSONObject(), device), "mobileScanCapture", scanner, device);
         JSONObject values = new JSONObject().put("barcode", "000123456789").put("symbology", "LABEL-TYPE-CODE128").put("scanSource", "scanner")
            .put("eventId", UUID.randomUUID().toString()).put("capturedAt", Instant.now().toString());
         complete(form("/qqq/v1/processes/mobileScanCapture/" + launch.getString("processUUID") + "/step/scan", scanner, values, device), "mobileScanCapture", scanner, device);
         return device;
      }
      finally
      {
         server.stop();
         try(Connection connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement())
         {
            statement.execute("SHUTDOWN");
         }
      }
   }



   /***************************************************************************
    ** Every column of the identity, device, credential and scan tables, as text.
    ***************************************************************************/
   private static Map<String, List<List<String>>> snapshot(EchoDemoSettings settings, boolean closeAfter) throws Exception
   {
      Map<String, List<List<String>>> result = new LinkedHashMap<>();
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); var statement = connection.createStatement())
      {
         for(String table : TABLES)
         {
            List<List<String>> rows = new ArrayList<>();
            try(var set = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1"))
            {
               int columns = set.getMetaData().getColumnCount();
               while(set.next())
               {
                  List<String> row = new ArrayList<>();
                  for(int column = 1; column <= columns; column++)
                  {
                     row.add(set.getString(column));
                  }
                  rows.add(row);
               }
            }
            result.put(table, rows);
         }
         if(closeAfter)
         {
            statement.execute("SHUTDOWN");
         }
      }
      return result;
   }



   /***************************************************************************
    ** Close the adopted file cleanly so the next open sees only flushed data.
    ***************************************************************************/
   private static void shutdown(EchoDemoSettings settings) throws Exception
   {
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); var statement = connection.createStatement())
      {
         statement.execute("SHUTDOWN");
      }
   }



   /***************************************************************************
    ** A fresh loopback port that is not any protected local runtime.
    ***************************************************************************/
   private static int freePort() throws Exception
   {
      while(true)
      {
         try(ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress()))
         {
            if(socket.getLocalPort() >= 1024 && !Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593).contains(socket.getLocalPort()))
            {
               return socket.getLocalPort();
            }
         }
      }
   }



   /***************************************************************************
    ** Native Basic login: returns the session cookie value.
    ***************************************************************************/
   private String login(String username, String password) throws Exception
   {
      HttpResponse<String> response = loginResponse(username, password);
      assertEquals(200, response.statusCode(), "Native login failed.");
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> java.net.HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /***************************************************************************
    ** Login status only, for a credential that must be refused.
    ***************************************************************************/
   private int loginStatus(String username, String password) throws Exception
   {
      return loginResponse(username, password).statusCode();
   }



   /***************************************************************************
    ** One Basic login request.
    ***************************************************************************/
   private HttpResponse<String> loginResponse(String username, String password) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      return client.send(builder("/qqq/v1/manageSession", null, null).header("Authorization", "Basic " + basic).header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** A request with the session cookie and optional device proof headers.
    ***************************************************************************/
   private HttpResponse<String> request(String method, String path, String session, String[] device) throws Exception
   {
      return client.send(builder(path, session, device).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** A JSON POST.
    ***************************************************************************/
   private HttpResponse<String> post(String path, String session, String body, String[] device) throws Exception
   {
      return client.send(builder(path, session, device).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
         HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** The multipart JSON-values body the native process client sends.
    ***************************************************************************/
   private HttpResponse<String> form(String path, String session, JSONObject values, String[] device) throws Exception
   {
      String boundary = "adopt-" + UUID.randomUUID();
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
      return client.send(builder(path, session, device).header("Content-Type", "multipart/form-data; boundary=" + boundary)
         .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /***************************************************************************
    ** Poll an asynchronous process step to completion; an error body fails.
    ***************************************************************************/
   private JSONObject complete(HttpResponse<String> response, String process, String session, String[] device) throws Exception
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
         response = request("GET", "/qqq/v1/processes/" + process + "/" + processId + "/status/" + jobId, session, device);
         assertEquals(200, response.statusCode());
         body = new JSONObject(response.body());
      }
      assertFalse(body.has("error"), "Native process returned an error.");
      return body;
   }



   /***************************************************************************
    ** Loopback request builder; credentials stay in headers.
    ***************************************************************************/
   private HttpRequest.Builder builder(String path, String session, String[] device)
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20)).header("X-QQQ-Client", "android/qqq-mobile")
         .header("Origin", origin);
      if(session != null)
      {
         request.header("Cookie", "sessionUUID=" + session);
      }
      if(device != null)
      {
         request.header("X-QQQ-Device-Id", device[0]).header("X-QQQ-Device-Token", device[1]);
      }
      return request;
   }
}
