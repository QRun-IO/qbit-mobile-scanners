/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.demo;


import java.io.File;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Exercise the same standalone file-database entry point as native acceptance.
 *******************************************************************************/
class MobileStandalonePresenceTest
{
   @TempDir
   private Path directory;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
   private String origin;
   private String deviceId;
   private String deviceToken;



   /***************************************************************************
    ** A non-UTC child JVM must keep receiving viewing leases after fifteen seconds.
    ***************************************************************************/
   @Test
   @Timeout(90)
   void standaloneFileServerRetainsFreshPresenceAndViewingDemand() throws Exception
   {
      int port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }
      origin = "http://127.0.0.1:" + port;
      String scannerPassword = UUID.randomUUID().toString();
      String adminPassword = UUID.randomUUID().toString();
      String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
         .map(value -> Path.of(value).toAbsolutePath().normalize().toString()).collect(Collectors.joining(File.pathSeparator));
      String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
      ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
         "-XX:ActiveProcessorCount=2", "-Xmx512m", "-Duser.timezone=America/Chicago", "-cp", classpath,
         MobileScannersDemo.class.getName(), String.valueOf(port));
      builder.directory(directory.toFile());
      builder.environment().put("MOBILE_DEMO_SCANNER_PASSWORD", scannerPassword);
      builder.environment().put("MOBILE_DEMO_ADMIN_PASSWORD", adminPassword);
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
      Process server = builder.start();
      try
      {
         awaitServer(server);
         String scanner = login("scanner", scannerPassword);
         String admin = login("admin", adminPassword);
         JSONObject enrolled = json("POST", "/qqq/v1/qbit/mobile-scanners/devices/enroll", scanner,
            new JSONObject().put("installationId", UUID.randomUUID().toString()).put("displayName", "Standalone presence regression"), true);
         deviceId = enrolled.getString("deviceId");
         deviceToken = enrolled.getString("deviceToken");
         String process = "/qqq/v1/processes/mobileDeviceApprove";
         JSONObject launched = form(process + "/init", admin, new JSONObject());
         String processId = launched.getString("processUUID");
         JSONObject applied = form(process + "/" + processId + "/step/input", admin, new JSONObject().put("deviceId", deviceId));
         assertEquals("result", applied.getString("nextStep"));
         form(process + "/" + processId + "/step/result", admin, new JSONObject());
         JSONObject activity = new JSONObject().put("foreground", true).put("screenAvailable", true);
         Instant began = Instant.now();
         for(int iteration = 0; iteration < 4; iteration++)
         {
            if(iteration > 0)
            {
               Thread.sleep(6000);
            }
            JSONObject heartbeat = json("POST", "/qqq/v1/qbit/mobile-scanners/sessions/heartbeat", scanner, activity, true);
            String sessionId = heartbeat.getString("sessionId");
            JSONObject row = json("POST", "/qqq/v1/table/mobileSession/query", admin, new JSONObject(), false)
               .getJSONArray("records").getJSONObject(0).getJSONObject("values");
            assertEquals(sessionId, row.getString("id"));
            Instant seen = Instant.parse(row.getString("lastSeenAt"));
            Instant serverTime = Instant.parse(heartbeat.getString("serverTime"));
            long ageMillis = Duration.between(seen, serverTime).toMillis();
            assertTrue(Math.abs(ageMillis) < 3000,
               "Heartbeat age must be current: ageMillis=" + ageMillis + ", seen=" + seen + ", serverTime=" + serverTime);
            JSONObject widget = json("POST", "/qqq/v1/widget/mobileSessionScreen", admin, new JSONObject().put("id", sessionId), false);
            String status = widget.getJSONArray("blocks").getJSONObject(0).getJSONObject("values").getString("text");
            assertEquals("Waiting for a current app screen", status);
            JSONObject next = json("POST", "/qqq/v1/qbit/mobile-scanners/sessions/heartbeat", scanner, activity, true);
            assertTrue(next.has("screenMirror"), "Fresh native heartbeat must receive the browser viewer's lease");
         }
         assertTrue(Duration.between(began, Instant.now()).getSeconds() > 15);
      }
      finally
      {
         server.destroy();
         if(!server.waitFor(5, TimeUnit.SECONDS))
         {
            server.destroyForcibly();
            assertTrue(server.waitFor(5, TimeUnit.SECONDS), "Isolated standalone server must stop");
         }
      }
   }



   /***************************************************************************
    ** Bound startup without printing child environment variables or responses.
    ***************************************************************************/
   private void awaitServer(Process server) throws Exception
   {
      long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
      while(System.nanoTime() < deadline)
      {
         assertTrue(server.isAlive(), "Standalone demo exited during startup");
         try
         {
            if(client.send(HttpRequest.newBuilder(URI.create(origin + "/qqq/v1/version")).timeout(Duration.ofSeconds(2)).GET().build(),
               HttpResponse.BodyHandlers.discarding()).statusCode() == 200)
            {
               return;
            }
         }
         catch(java.io.IOException ignored)
         {
            // The child has not bound its loopback socket yet.
         }
         Thread.sleep(100);
      }
      throw new AssertionError("Standalone demo startup timed out");
   }



   /***************************************************************************
    ** Passwords remain exclusively in the request header and child environment.
    ***************************************************************************/
   private String login(String username, String password) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      var response = client.send(headers("/qqq/v1/manageSession", null, true).header("Authorization", "Basic " + basic)
         .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), "Standalone login failed");
      return response.headers().allValues("Set-Cookie").stream().flatMap(value -> HttpCookie.parse(value).stream())
         .filter(cookie -> "sessionUUID".equals(cookie.getName())).findFirst().orElseThrow().getValue();
   }



   /***************************************************************************
    ** Browser requests omit the native channel and device headers deliberately.
    ***************************************************************************/
   private HttpRequest.Builder headers(String path, String session, boolean nativeClient)
   {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(10));
      if(session != null)
      {
         builder.header("Cookie", "sessionUUID=" + session);
      }
      if(nativeClient)
      {
         builder.header("X-QQQ-Client", "android/qqq-mobile");
         if(deviceId != null)
         {
            builder.header("X-QQQ-Device-Id", deviceId).header("X-QQQ-Device-Token", deviceToken);
         }
      }
      return builder;
   }



   /***************************************************************************
    ** Use the ordinary versioned API; never print credential-bearing bodies.
    ***************************************************************************/
   private JSONObject json(String method, String path, String session, JSONObject body, boolean nativeClient) throws Exception
   {
      var response = client.send(headers(path, session, nativeClient).header("Content-Type", "application/json")
         .method(method, HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
      assertTrue(response.statusCode() == 200 || response.statusCode() == 201, "Standalone JSON operation failed: " + path + " status=" + response.statusCode());
      return new JSONObject(response.body());
   }



   /***************************************************************************
    ** Drive the real approval process, including a bounded async job handoff.
    ***************************************************************************/
   private JSONObject form(String path, String session, JSONObject values) throws Exception
   {
      String boundary = "qqq-presence-" + UUID.randomUUID();
      String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"values\"\r\n\r\n" + values
         + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"stepTimeoutMillis\"\r\n\r\n5000\r\n--" + boundary + "--\r\n";
      var response = client.send(headers(path, session, false).header("Content-Type", "multipart/form-data; boundary=" + boundary)
         .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), "Standalone approval process failed");
      JSONObject result = new JSONObject(response.body());
      long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      String processId = result.optString("processUUID");
      String jobId = result.optString("jobUUID");
      while("JOB_STARTED".equals(result.optString("type")) || "RUNNING".equals(result.optString("type")))
      {
         assertTrue(System.nanoTime() < deadline, "Standalone approval job timed out");
         Thread.sleep(25);
         var poll = client.send(headers("/qqq/v1/processes/mobileDeviceApprove/" + processId + "/status/" + jobId, session, false).GET().build(),
            HttpResponse.BodyHandlers.ofString());
         assertEquals(200, poll.statusCode());
         result = new JSONObject(poll.body());
      }
      assertFalse(result.has("error"), "Standalone approval returned a process error");
      return result;
   }
}
