/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.fixture;


import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import com.kingsrook.examples.echo.EchoQBitProducer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Actual loopback HTTP uses only registered versioned native QQQ operations.
 ** The test generator simulates acceptance; no render or printer is contacted.
 *******************************************************************************/
class EchoHttpFixtureTest
{
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
   private String origin;
   private String deviceId;
   private String deviceToken;



   /***************************************************************************
    ** Login, enroll/approve, setup once, two scans, retry, query and cancel all
    ** cross the same semantic HTTP surface used by the Android client.
    ***************************************************************************/
   @Test
   void nativeSemanticHttpRunsEchoWithSimulatedAcceptance() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      int port;
      try(ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getLoopbackAddress()))
      {
         port = reservation.getLocalPort();
      }
      origin = "http://127.0.0.1:" + port;
      try(EchoHttpFixture fixture = new EchoHttpFixture(scannerPassword, adminPassword))
      {
         fixture.start(port);
         JSONObject version = new JSONObject(request("GET", "/qqq/v1/version", null, null, false).body());
         assertEquals("1.10.0", version.getString("apiVersion"));
         boolean mobile15 = false;
         for(Object extension : version.getJSONArray("extensions"))
         {
            JSONObject item = (JSONObject) extension;
            if("mobile-scanners".equals(item.getString("name")))
            {
               mobile15 = "1.5.0".equals(item.getString("version"));
            }
         }
         assertTrue(mobile15);
         String scanner = enrollApprovedScanner(scannerPassword, adminPassword);
         HttpResponse<String> metadata = request("GET", "/qqq/v1/metaData", scanner, null, true);
         assertEquals(200, metadata.statusCode());
         assertTrue(metadata.body().contains("\"echo\""));
         JSONObject launched = complete(form("/qqq/v1/processes/echoPrint/init", scanner, new JSONObject(), true), EchoQBitProducer.PROCESS, scanner, true);
         assertEquals("setup", launched.getString("nextStep"));
         String process = launched.getString("processUUID");
         // the published searchProcessPossibleValues contract: POST JSON {values, processValues, useCase}
         HttpResponse<String> documents = request("POST", "/qqq/v1/processes/echoPrint/possibleValues/templateId", scanner, new JSONObject()
            .put("values", new JSONObject().put("printerId", EchoHttpFixture.PRINTER).put("mediaId", EchoHttpFixture.MEDIA))
            .put("processValues", new JSONObject()).put("useCase", "form").toString(), true);
         assertEquals(200, documents.statusCode());
         JSONArray options = new JSONObject(documents.body()).getJSONArray("options");
         assertEquals(2, options.length());
         assertEquals(EchoHttpFixture.DOCUMENT_A, options.getJSONObject(0).getInt("id"));
         assertEquals(EchoHttpFixture.DOCUMENT_B, options.getJSONObject(1).getInt("id"));
         assertTrue(options.getJSONObject(0).get("id") instanceof Number, "Document ids are JSON integers.");
         HttpResponse<String> withoutMedia = request("POST", "/qqq/v1/processes/echoPrint/possibleValues/templateId", scanner, new JSONObject()
            .put("values", new JSONObject().put("printerId", EchoHttpFixture.PRINTER)).put("processValues", new JSONObject()).put("useCase", "form").toString(), true);
         assertEquals(200, withoutMedia.statusCode());
         // the semantic API may omit optional empty options
         JSONArray noOptions = new JSONObject(withoutMedia.body()).optJSONArray("options");
         assertTrue(noOptions == null || noOptions.length() == 0, "No document is offered without a chosen size.");
         JSONObject selected = complete(form("/qqq/v1/processes/echoPrint/" + process + "/step/setup", scanner,
            new JSONObject().put("printerId", EchoHttpFixture.PRINTER).put("mediaId", EchoHttpFixture.MEDIA)
               .put("templateId", EchoHttpFixture.DOCUMENT_B), true), EchoQBitProducer.PROCESS, scanner, true);
         assertEquals("scan", selected.getString("nextStep"));
         String run = selected.getJSONObject("values").getString("echoRunId");
         JSONObject block = selected.getJSONObject("values").getJSONObject(EchoQBitProducer.WIDGET).getJSONArray("subBlocks").getJSONObject(0).getJSONObject("values");
         assertEquals("Print queued", block.getString("acknowledgmentLabel"));
         assertFalse(block.getBoolean("manualEntryEnabled"));
         JSONObject firstEvent = event("LABEL-TYPE-CODE128");
         JSONObject first = complete(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, firstEvent, true), EchoQBitProducer.PROCESS, scanner, true);
         JSONObject firstValues = first.getJSONObject("values");
         assertTrue(firstValues.getBoolean("scanSaved"));
         assertEquals("QUEUED", firstValues.getString("printStatus"));
         assertTrue(firstValues.getString("printJobId").startsWith("fixture-job-"));
         JSONObject retry = complete(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, firstEvent, true), EchoQBitProducer.PROCESS, scanner, true);
         assertEquals(firstValues.getString("printJobId"), retry.getJSONObject("values").getString("printJobId"));
         JSONObject second = complete(form("/qqq/v1/processes/echoPrint/" + process + "/step/scan", scanner, event("QRCODE"), true), EchoQBitProducer.PROCESS, scanner, true);
         assertEquals(run, second.getJSONObject("values").getString("echoRunId"));
         assertEquals("scan", second.getString("nextStep"));
         JSONArray scans = query(EchoQBitProducer.SCAN_TABLE, scanner);
         assertEquals(2, scans.length());
         for(Object item : scans)
         {
            JSONObject values = ((JSONObject) item).getJSONObject("values");
            assertEquals("001234567890", values.getString("barcode"));
            assertEquals("QUEUED", values.getString("state"));
            assertTrue(values.getString("printJobId").startsWith("fixture-job-"));
         }
         HttpResponse<String> canceled = form("/qqq/v1/processes/echoPrint/" + process + "/cancel", scanner, new JSONObject(), true);
         assertEquals(200, canceled.statusCode());
         JSONArray runs = query(EchoHttpFixture.RUN_TABLE, scanner);
         assertEquals(1, runs.length());
         JSONObject runValues = runs.getJSONObject(0).getJSONObject("values");
         assertEquals(run, runValues.getString("id"));
         assertEquals("CLOSED", runValues.getString("state"));
         assertEquals(EchoHttpFixture.PRINTER, runValues.getString("printerRecordId"));
         assertEquals(EchoHttpFixture.MEDIA, runValues.getString("mediaId"));
         assertEquals(EchoHttpFixture.DOCUMENT_B, runValues.getInt("templateId"), "The chosen document is persisted as the same integer id.");
      }
   }



   /***************************************************************************
    ** Real login, device enrollment and administrator approval; returns the
    ** scanner's session and sets this test's approved device headers.
    ***************************************************************************/
   private String enrollApprovedScanner(String scannerPassword, String adminPassword) throws Exception
   {
      String scanner = login("scanner", scannerPassword);
      String admin = login("admin", adminPassword);
      HttpResponse<String> enrolled = request("POST", "/qqq/v1/qbit/mobile-scanners/devices/enroll", scanner,
         new JSONObject().put("installationId", UUID.randomUUID().toString()).toString(), false);
      assertTrue(enrolled.statusCode() == 200 || enrolled.statusCode() == 201);
      JSONObject device = new JSONObject(enrolled.body());
      deviceId = device.getString("deviceId");
      deviceToken = device.getString("deviceToken");
      JSONObject approval = complete(form("/qqq/v1/processes/mobileDeviceApprove/init", admin,
         new JSONObject().put("recordsParam", "recordIds").put("recordIds", deviceId).put("tableName", "mobileDevice"), false), "mobileDeviceApprove", admin, false);
      complete(form("/qqq/v1/processes/mobileDeviceApprove/" + approval.getString("processUUID") + "/step/input", admin, new JSONObject(), false),
         "mobileDeviceApprove", admin, false);
      return scanner;
   }



   /***************************************************************************
    ** A reserved loopback port, released immediately for the fixture to bind.
    ***************************************************************************/
   private int freePort() throws Exception
   {
      try(ServerSocket reservation = new ServerSocket(0, 0, InetAddress.getLoopbackAddress()))
      {
         origin = "http://127.0.0.1:" + reservation.getLocalPort();
         return reservation.getLocalPort();
      }
   }



   /***************************************************************************
    ** Start the Echo process and return its protected process id.
    ***************************************************************************/
   private String open(String scanner) throws Exception
   {
      JSONObject launched = complete(form("/qqq/v1/processes/echoPrint/init", scanner, new JSONObject(), true), EchoQBitProducer.PROCESS, scanner, true);
      assertEquals("setup", launched.getString("nextStep"));
      return launched.getString("processUUID");
   }



   /***************************************************************************
    ** The setup form's values: the simulated printer, media and second document,
    ** plus whatever copies entry a test supplies (or none).
    ***************************************************************************/
   private static JSONObject setupValues(Object... copies)
   {
      JSONObject values = new JSONObject().put("printerId", EchoHttpFixture.PRINTER).put("mediaId", EchoHttpFixture.MEDIA).put("templateId", EchoHttpFixture.DOCUMENT_B);
      if(copies.length == 1)
      {
         values.put("copies", copies[0]);
      }
      return values;
   }



   /***************************************************************************
    ** One native step request; the finished response, whether it succeeded or not.
    ***************************************************************************/
   private JSONObject attempt(String process, String step, String scanner, JSONObject values) throws Exception
   {
      return finish(form("/qqq/v1/processes/echoPrint/" + process + "/step/" + step, scanner, values, true), EchoQBitProducer.PROCESS, scanner, true);
   }



   /***************************************************************************
    ** True when the server reported the step as failed rather than completed.
    ***************************************************************************/
   private static boolean failed(JSONObject response)
   {
      return response.has("error") || response.has("errorMessage") || "ERROR".equals(response.optString("type"));
   }



   /***************************************************************************
    ** The pinned copies, instructions and accepted printing of one finished step.
    ***************************************************************************/
   private static String instructions(JSONObject finished)
   {
      return finished.getJSONObject("values").getJSONObject(EchoQBitProducer.WIDGET).getJSONArray("subBlocks").getJSONObject(0).getJSONObject("values").getString("instructions");
   }



   /***************************************************************************
    ** The persisted run rows, by id, as ordinary authorized native queries.
    ***************************************************************************/
   private JSONObject runRow(String scanner, String run) throws Exception
   {
      for(Object item : query(EchoHttpFixture.RUN_TABLE, scanner))
      {
         JSONObject values = ((JSONObject) item).getJSONObject("values");
         if(run.equals(values.getString("id")))
         {
            return values;
         }
      }
      throw new AssertionError("The run row is not visible to its scanner.");
   }



   /***************************************************************************
    ** The setup form advertises one optional integer with explicit 1..100 limits
    ** and the default of one, from the same registered process metadata the
    ** native client reads.
    ***************************************************************************/
   @Test
   void setupMetadataAdvertisesOptionalCopiesWithExplicitLimits() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      int port = freePort();
      try(EchoHttpFixture fixture = new EchoHttpFixture(scannerPassword, adminPassword))
      {
         fixture.start(port);
         String scanner = enrollApprovedScanner(scannerPassword, adminPassword);
         HttpResponse<String> response = request("GET", "/qqq/v1/metaData/process/echoPrint", scanner, null, true);
         assertEquals(200, response.statusCode());
         JSONObject field = findField(new JSONObject(response.body()), "copies");
         assertEquals("Copies per scan", field.getString("label"));
         assertEquals("INTEGER", field.getString("type").toUpperCase(java.util.Locale.ROOT));
         assertEquals("1", String.valueOf(field.get("defaultValue")));
         assertFalse(field.optBoolean("isRequired", false));
         JSONObject validation = field.getJSONObject("validation");
         assertEquals("1", String.valueOf(validation.get("minValue")));
         assertEquals("100", String.valueOf(validation.get("maxValue")));
         assertTrue(validation.optBoolean("minInclusive", true));
         assertTrue(validation.optBoolean("maxInclusive", true));
      }
   }



   /***************************************************************************
    ** Depth-first search for the named form field in the process metadata.
    ***************************************************************************/
   private static JSONObject findField(Object node, String name)
   {
      if(node instanceof JSONObject object)
      {
         if(name.equals(object.optString("name")) && object.has("label"))
         {
            return object;
         }
         for(String key : object.keySet())
         {
            JSONObject found = findField(object.get(key), name);
            if(found != null)
            {
               return found;
            }
         }
      }
      else if(node instanceof JSONArray array)
      {
         for(Object item : array)
         {
            JSONObject found = findField(item, name);
            if(found != null)
            {
               return found;
            }
         }
      }
      return null;
   }



   /***************************************************************************
    ** Omitted, null and blank setup entries all mean one copy, shown and pinned.
    ***************************************************************************/
   @Test
   void copiesDefaultToOneWhenOmittedClearedOrBlank() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      int port = freePort();
      try(EchoHttpFixture fixture = new EchoHttpFixture(scannerPassword, adminPassword))
      {
         fixture.start(port);
         String scanner = enrollApprovedScanner(scannerPassword, adminPassword);
         int expectedJobs = 0;
         for(Object entry : new Object[] { "omitted", JSONObject.NULL, "", "   " })
         {
            String process = open(scanner);
            JSONObject selected = attempt(process, "setup", scanner, "omitted".equals(entry) ? setupValues() : setupValues(entry));
            assertFalse(failed(selected));
            assertEquals(1, selected.getJSONObject("values").getInt("copies"));
            assertFalse(instructions(selected).contains("copies"));
            String run = selected.getJSONObject("values").getString("echoRunId");
            JSONObject scanned = attempt(process, "scan", scanner, event("LABEL-TYPE-CODE128"));
            assertFalse(failed(scanned));
            assertEquals(1, scanned.getJSONObject("values").getInt("copies"));
            assertEquals(++expectedJobs, fixture.acceptedJobCount());
            assertEquals(1, runRow(scanner, run).getInt("copies"));
            assertEquals(200, form("/qqq/v1/processes/echoPrint/" + process + "/cancel", scanner, new JSONObject(), true).statusCode());
         }
         assertEquals(java.util.List.of(1, 1, 1, 1), fixture.acceptedCopies());
      }
   }



   /***************************************************************************
    ** Three copies are chosen once, shown, pinned in the run, and used by every
    ** scan as ONE job each; retries add no job and every mutation is refused.
    ***************************************************************************/
   @Test
   void chosenCopiesStayPinnedAcrossScansRetriesAndMutations() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      int port = freePort();
      try(EchoHttpFixture fixture = new EchoHttpFixture(scannerPassword, adminPassword))
      {
         fixture.start(port);
         String scanner = enrollApprovedScanner(scannerPassword, adminPassword);
         String process = open(scanner);
         JSONObject selected = attempt(process, "setup", scanner, setupValues(3));
         assertFalse(failed(selected));
         assertEquals("scan", selected.getString("nextStep"));
         assertEquals(3, selected.getJSONObject("values").getInt("copies"));
         assertTrue(instructions(selected).contains("Each scan prints 3 copies."));
         String run = selected.getJSONObject("values").getString("echoRunId");
         assertEquals(3, runRow(scanner, run).getInt("copies"));

         JSONObject firstEvent = event("LABEL-TYPE-CODE128");
         JSONObject first = attempt(process, "scan", scanner, firstEvent);
         assertFalse(failed(first));
         assertEquals(3, first.getJSONObject("values").getInt("copies"));
         String job = first.getJSONObject("values").getString("printJobId");
         assertEquals(1, fixture.acceptedJobCount());
         JSONObject retry = attempt(process, "scan", scanner, firstEvent);
         assertFalse(failed(retry));
         assertEquals(job, retry.getJSONObject("values").getString("printJobId"));
         assertEquals(1, fixture.acceptedJobCount());
         JSONObject second = attempt(process, "scan", scanner, event("QRCODE"));
         assertFalse(failed(second));
         assertEquals(3, second.getJSONObject("values").getInt("copies"));
         assertEquals(2, fixture.acceptedJobCount());
         assertEquals(java.util.List.of(3, 3), fixture.acceptedCopies());

         // a scan-stage value that differs from the pinned count is refused: no event, no job
         for(Object forged : new Object[] { 1, 2, 4, 9, 100, 0, 101, "abc", 2.5 })
         {
            JSONObject mutation = event("LABEL-TYPE-CODE128").put("copies", forged);
            assertTrue(failed(attempt(process, "scan", scanner, mutation)), "forged copies " + forged);
         }
         // a repeated setup cannot change the pinned count either
         for(Object other : new Object[] { 1, 5 })
         {
            assertTrue(failed(attempt(process, "setup", scanner, setupValues(other))), "setup replay " + other);
         }
         assertEquals(2, fixture.acceptedJobCount());
         assertEquals(2, query(EchoQBitProducer.SCAN_TABLE, scanner).length());
         assertEquals(3, runRow(scanner, run).getInt("copies"));

         // the refused values do not poison the run: the next ordinary scan still prints three
         JSONObject third = attempt(process, "scan", scanner, event("QRCODE"));
         assertFalse(failed(third), third.toString());
         assertEquals(3, third.getJSONObject("values").getInt("copies"));
         assertEquals(3, fixture.acceptedJobCount());
         assertEquals(java.util.List.of(3, 3, 3), fixture.acceptedCopies());
         assertEquals(3, query(EchoQBitProducer.SCAN_TABLE, scanner).length());
         assertEquals(200, form("/qqq/v1/processes/echoPrint/" + process + "/cancel", scanner, new JSONObject(), true).statusCode());
         assertEquals(3, runRow(scanner, run).getInt("copies"));
         assertEquals("CLOSED", runRow(scanner, run).getString("state"));
      }
   }



   /***************************************************************************
    ** Fractions, zero, out-of-range and text are rejected at setup; the run stays
    ** in setup, so a valid count then completes it.
    ***************************************************************************/
   @Test
   void invalidCopiesAreRejectedAtSetupBeforeAnyRunIsPinned() throws Exception
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      int port = freePort();
      try(EchoHttpFixture fixture = new EchoHttpFixture(scannerPassword, adminPassword))
      {
         fixture.start(port);
         String scanner = enrollApprovedScanner(scannerPassword, adminPassword);
         String process = open(scanner);
         for(Object invalid : new Object[] { 0, -1, 101, 1000, 2.5, "2.5", "abc", "1e1", true })
         {
            assertTrue(failed(attempt(process, "setup", scanner, setupValues(invalid))), "setup copies " + invalid);
         }
         JSONObject selected = attempt(process, "setup", scanner, setupValues(100));
         assertFalse(failed(selected), selected.toString());
         assertEquals(100, selected.getJSONObject("values").getInt("copies"));
         assertEquals(100, runRow(scanner, selected.getJSONObject("values").getString("echoRunId")).getInt("copies"));
         assertEquals(0, fixture.acceptedJobCount());
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
      assertEquals(200, response.statusCode(), "Native fixture login failed.");
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /***************************************************************************
    ** Test observations use ordinary authorized QQQ table queries, never SQL.
    ***************************************************************************/
   private JSONArray query(String table, String session) throws Exception
   {
      HttpResponse<String> response = request("POST", "/qqq/v1/table/" + table + "/query", session,
         new JSONObject().put("filter", new JSONObject().put("limit", 20)).toString(), true);
      assertEquals(200, response.statusCode(), "Native fixture inspection failed.");
      return new JSONObject(response.body()).getJSONArray("records");
   }



   /***************************************************************************
    ** Async process state retains approved device headers across status polling.
    ***************************************************************************/
   private JSONObject complete(HttpResponse<String> response, String process, String session, boolean device) throws Exception
   {
      assertEquals(200, response.statusCode(), "Native fixture process request failed.");
      JSONObject body = finish(response, process, session, device);
      assertFalse(body.has("error"), "Native fixture process returned an error.");
      return body;
   }



   /***************************************************************************
    ** Poll an async step to its end and return what the server said, including
    ** a refusal, which is reported as an object with an error key.
    ***************************************************************************/
   private JSONObject finish(HttpResponse<String> response, String process, String session, boolean device) throws Exception
   {
      if(response.statusCode() != 200)
      {
         return new JSONObject().put("error", "HTTP " + response.statusCode());
      }
      JSONObject body = new JSONObject(response.body());
      String processId = body.optString("processUUID");
      String jobId = body.optString("jobUUID");
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while("JOB_STARTED".equals(body.optString("type")) || "RUNNING".equals(body.optString("type")))
      {
         assertTrue(System.nanoTime() < deadline, "Native fixture process timed out.");
         Thread.sleep(25);
         response = request("GET", "/qqq/v1/processes/" + process + "/" + processId + "/status/" + jobId, session, null, device);
         if(response.statusCode() != 200)
         {
            return new JSONObject().put("error", "HTTP " + response.statusCode());
         }
         body = new JSONObject(response.body());
      }
      return body;
   }



   /***************************************************************************
    ** The same multipart JSON-values body as the native QQQ process client.
    ***************************************************************************/
   private HttpResponse<String> form(String path, String session, JSONObject values, boolean device) throws Exception
   {
      String boundary = "echo-fixture-" + UUID.randomUUID();
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
    ** All observed requests target this test's selected loopback origin.
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
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20)).header("X-QQQ-Client", "android/qqq-mobile");
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
    ** Public synthetic value checks preservation of leading zeroes and type.
    ***************************************************************************/
   private static JSONObject event(String type)
   {
      return new JSONObject().put("barcode", "001234567890").put("symbology", type).put("scanSource", "zebra-emdk")
         .put("eventId", UUID.randomUUID().toString()).put("capturedAt", "2026-11-01T18:00:00Z");
   }
}
