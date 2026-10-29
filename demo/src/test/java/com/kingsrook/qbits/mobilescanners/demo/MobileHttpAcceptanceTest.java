/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.demo;


import java.net.HttpCookie;
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
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Real HTTP, native QQQ authentication/processes and real H2 persistence.
 *******************************************************************************/
class MobileHttpAcceptanceTest
{
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
   private String origin;
   private String deviceId;
   private String deviceToken;



   /*******************************************************************************
    ** A pending/revoked handheld must not save, even with a valid user session.
    *******************************************************************************/
   @Test
   void loginApproveCaptureRetryAndRevoke() throws Exception
   {
      String database = "jdbc:h2:mem:mobileHttp" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      MobileScannersDemo application = new MobileScannersDemo(database);
      application.initializeDatabase(scannerPassword, adminPassword);
      Integer port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }
      origin = "http://127.0.0.1:" + port;
      QApplicationJavalinServer server = application.start(port);
      try
      {
         assertEquals(401, request("POST", "/mobile/v1/devices/enroll", null, "{}", false).statusCode());
         String scanner = login("scanner", scannerPassword);
         String admin = login("admin", adminPassword);
         JSONObject enrollment = new JSONObject().put("installationId", UUID.randomUUID().toString())
            .put("displayName", "TC22 HTTP acceptance").put("manufacturer", "Zebra").put("model", "TC22")
            .put("osVersion", "acceptance-fixture").put("appVersion", "0.1.0");
         var enrolled = request("POST", "/mobile/v1/devices/enroll", scanner, enrollment.toString(), false);
         assertTrue(enrolled.statusCode() == 200 || enrolled.statusCode() == 201, "Enrollment must succeed");
         JSONObject device = new JSONObject(enrolled.body());
         deviceId = device.getString("deviceId");
         deviceToken = device.getString("deviceToken");
         assertEquals("PENDING", device.getString("status"));
         assertDenied(processInit("mobileScanCapture", scanner, true));
         assertDenied(processInit("mobileDeviceApprove", scanner, false));
         runAdmin("mobileDeviceApprove", admin);
         var current = request("GET", "/mobile/v1/devices/current", scanner, null, true);
         assertEquals(200, current.statusCode());
         assertEquals("APPROVED", new JSONObject(current.body()).getString("status"));
         assertFalse(current.body().contains(deviceToken), "Status must never echo the token");

         JSONObject launch = complete(processInit("mobileScanCapture", scanner, true), "mobileScanCapture", scanner, true);
         var metadataResponse = request("GET", "/qqq/v1/metaData?frontendName=qqq-android&frontendVersion=0.1.0&applicationName=qqq-mobile-scanners&applicationVersion=0.1.0", scanner, null, true);
         assertEquals(200, metadataResponse.statusCode());
         JSONObject metadata = new JSONObject(metadataResponse.body());
         assertEquals("composite", metadata.getJSONObject("widgets").getJSONObject("mobileScanner").getString("type"));
         var processMetadataResponse = request("GET", "/qqq/v1/metaData/process/mobileScanCapture", scanner, null, true);
         assertEquals(200, processMetadataResponse.statusCode());
         Path fixtures = Path.of("target", "android-contract");
         Files.createDirectories(fixtures);
         Files.writeString(fixtures.resolve("metadata.json"), metadata.toString(2));
         Files.writeString(fixtures.resolve("process.json"), processMetadataResponse.body());
         Files.writeString(fixtures.resolve("init.json"), new JSONObject(launch.toString()).put("processUUID", "00000000-0000-4000-8000-000000000001").toString(2));
         String processId = launch.getString("processUUID");
         assertEquals("scan", launch.getString("nextStep"));
         JSONObject widget = launch.getJSONObject("values").getJSONObject("mobileScanner");
         assertEquals("SCANNER", widget.getJSONArray("subBlocks").getJSONObject(0).getString("blockType"));
         String eventId = UUID.randomUUID().toString();
         JSONObject values = new JSONObject().put("barcode", "000123456789").put("symbology", "LABEL-TYPE-CODE128")
            .put("scanSource", "scanner").put("eventId", eventId).put("capturedAt", Instant.now().toString())
            .put("userId", "forged-admin").put("deviceId", "forged-device");
         JSONObject first = complete(processStep("mobileScanCapture", processId, "scan", scanner, values, true), "mobileScanCapture", scanner, true);
         assertEquals(eventId, first.getJSONObject("values").getString("acknowledgedEventId"));
         JSONObject retry = complete(processStep("mobileScanCapture", processId, "scan", scanner, values, true), "mobileScanCapture", scanner, true);
         assertEquals(first.getJSONObject("values").getString("scanId"), retry.getJSONObject("values").getString("scanId"));
         assertEquals(1, scanCount(database));

         try(var connection = DriverManager.getConnection(database, "sa", ""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM mobileScan"))
         {
            assertTrue(rows.next());
            assertEquals("000123456789", rows.getString("barcode"));
            assertEquals("scanner", rows.getString("userId"));
            assertEquals(deviceId, rows.getString("deviceId"));
            assertNotNull(rows.getTimestamp("receivedAt"));
         }

         String validToken = deviceToken;
         deviceToken = "incorrect-token";
         assertDenied(processInit("mobileScanCapture", scanner, true));
         deviceToken = validToken;
         assertDenied(processInit("mobileScanCapture", scanner, false));
         runAdmin("mobileDeviceRevoke", admin);
         values.put("eventId", UUID.randomUUID().toString());
         assertDenied(processStep("mobileScanCapture", processId, "scan", scanner, values, true));
         assertEquals(1, scanCount(database));
         assertEquals(200, request("POST", "/qqq/v1/logout", scanner, "{}", false).statusCode());
         assertEquals(401, request("GET", "/mobile/v1/devices/current", scanner, null, true).statusCode());
      }
      finally
      {
         server.stop();
      }
   }



   /*******************************************************************************
    ** Native login exchanges Basic credentials for a QQQ user session.
    *******************************************************************************/
   private String login(String username, String password) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      var response = client.send(HttpRequest.newBuilder(URI.create(origin + "/qqq/v1/manageSession"))
         .header("Authorization", "Basic " + basic).header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), "Native login failed");
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /*******************************************************************************
    ** Process actions use the same native endpoints as Android.
    *******************************************************************************/
   private void runAdmin(String name, String session) throws Exception
   {
      JSONObject launched = complete(processInit(name, session, false), name, session, false);
      complete(processStep(name, launched.getString("processUUID"), "input", session, new JSONObject().put("deviceId", deviceId), false), name, session, false);
   }



   /*******************************************************************************
    ** Request an asynchronous handoff to exercise device context propagation.
    *******************************************************************************/
   private HttpResponse<String> processInit(String name, String session, boolean device) throws Exception
   {
      return form("/qqq/v1/processes/" + name + "/init", session, new JSONObject(), device);
   }



   /*******************************************************************************
    ** Submit one native frontend step, preserving the event UUID on retry.
    *******************************************************************************/
   private HttpResponse<String> processStep(String name, String processId, String step, String session, JSONObject values, boolean device) throws Exception
   {
      return form("/qqq/v1/processes/" + name + "/" + processId + "/step/" + step, session, values, device);
   }



   /*******************************************************************************
    ** Versioned process endpoints accept form values as JSON, like Retrofit.
    *******************************************************************************/
   private HttpResponse<String> form(String path, String session, JSONObject values, boolean device) throws Exception
   {
      String boundary = "qqq-mobile-" + UUID.randomUUID();
      HttpRequest.Builder builder = headers(path, session, device).header("Content-Type", "multipart/form-data; boundary=" + boundary);
      String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"values\"\r\n\r\n" + values
         + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"stepTimeoutMillis\"\r\n\r\n1\r\n--" + boundary + "--\r\n";
      return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /*******************************************************************************
    ** Poll native jobs to completion with a bounded deadline.
    *******************************************************************************/
   private JSONObject complete(HttpResponse<String> response, String process, String session, boolean device) throws Exception
   {
      assertEquals(200, response.statusCode(), "Process HTTP failure");
      JSONObject body = new JSONObject(response.body());
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      String jobId = body.optString("jobUUID");
      String processId = body.optString("processUUID");
      while("JOB_STARTED".equals(body.optString("type")) || "RUNNING".equals(body.optString("type")))
      {
         assertTrue(System.nanoTime() < deadline, "Native process timed out");
         Thread.sleep(25);
         response = request("GET", "/qqq/v1/processes/" + process + "/" + processId + "/status/" + jobId, session, null, device);
         assertEquals(200, response.statusCode());
         body = new JSONObject(response.body());
      }
      assertFalse(body.has("error"), "Native process error: " + body.optString("error"));
      return body;
   }



   /*******************************************************************************
    ** A rejection can be native process error JSON or an HTTP authorization error.
    *******************************************************************************/
   private void assertDenied(HttpResponse<String> response)
   {
      assertTrue(response.statusCode() == 401 || response.statusCode() == 403 || (response.statusCode() == 200 && new JSONObject(response.body()).has("error")), "Expected denied action");
   }



   /*******************************************************************************
    ** Keep credentials only in request headers and never in assertion messages.
    *******************************************************************************/
   private HttpRequest.Builder headers(String path, String session, boolean device)
   {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20));
      if(session != null)
      {
         builder.header("Cookie", "sessionUUID=" + session);
      }
      if(device)
      {
         builder.header("X-QQQ-Device-Id", deviceId).header("X-QQQ-Device-Token", deviceToken);
      }
      return builder;
   }



   /*******************************************************************************
    ** Enrollment and status are the only extra API routes used in this proof.
    *******************************************************************************/
   private HttpResponse<String> request(String method, String path, String session, String body, boolean device) throws Exception
   {
      return client.send(headers(path, session, device).header("Content-Type", "application/json")
         .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
   }



   /*******************************************************************************
    ** Query durable rows independently of HTTP success reporting.
    *******************************************************************************/
   private Integer scanCount(String database) throws Exception
   {
      try(var connection = DriverManager.getConnection(database, "sa", ""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM mobileScan"))
      {
         rows.next();
         return rows.getInt(1);
      }
   }
}
