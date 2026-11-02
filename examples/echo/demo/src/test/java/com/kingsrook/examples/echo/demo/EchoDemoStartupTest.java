/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


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
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Real HTTP startup of the public launcher class over an isolated file database.
 ** The renderer refuses every call, no printer is enrolled and no template exists,
 ** so this test cannot render or print. It proves composition, schema, accounts
 ** and route registration only.
 *******************************************************************************/
class EchoDemoStartupTest
{
   private static final String TOKEN = "0123456789abcdef0123456789abcdef-secret";
   private static final String SCANNER_PASSWORD = "scanner-test-password-1";
   private static final String ADMIN_PASSWORD = "admin-test-password-1";
   private static final Set<Integer> PROTECTED = Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593);

   @TempDir
   Path root;

   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();



   /***************************************************************************
    ** Counts calls so the test can show nothing was ever rendered.
    ***************************************************************************/
   private static final class RefusingRenderer implements GenerationPorts.IsolatedRenderer
   {
      private final AtomicInteger calls = new AtomicInteger();



      /***********************************************************************
       ** Never provides a reference.
       ***********************************************************************/
      @Override
      public String barcodeReference() throws QException
      {
         calls.incrementAndGet();
         throw new QException("The startup test renderer refuses every call.");
      }



      /***********************************************************************
       ** Never validates.
       ***********************************************************************/
      @Override
      public void validate(String actor, GenerationPorts.Revision revision) throws QException
      {
         calls.incrementAndGet();
         throw new QException("The startup test renderer refuses every call.");
      }



      /***********************************************************************
       ** Never renders.
       ***********************************************************************/
      @Override
      public GenerationPorts.Content render(GenerationPorts.RenderRequest request) throws QException
      {
         calls.incrementAndGet();
         throw new QException("The startup test renderer refuses every call.");
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
    ** The RDBMS connection providers are cached per backend name for the whole
    ** JVM, and every composed host here names its backend "mobileDb". Drop any
    ** provider left by an earlier test so this host reads its own database.
    ***************************************************************************/
   @BeforeEach
   @AfterEach
   void isolateConnectionProviders()
   {
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
   }



   /***************************************************************************
    ** Compose, initialize and serve; then observe over real HTTP and JDBC.
    ***************************************************************************/
   @Test
   void startsComposedHostOverIsolatedDatabaseWithoutOutput() throws Exception
   {
      int port = freePort();
      EchoDemoSettings settings = new EchoDemoSettings(root, port, "echo-test", TOKEN, false, null, null);
      RefusingRenderer renderer = new RefusingRenderer();
      try(EchoDemo demo = new EchoDemo(settings, renderer))
      {
         demo.initializeDatabase(SCANNER_PASSWORD, ADMIN_PASSWORD);
         demo.start(port);

         QInstance instance = demo.defineQInstance();
         assertNotNull(instance.getApp("echo"));
         assertNotNull(instance.getProcess("echoPrint"));
         assertNotNull(instance.getTable("echoScan"));
         assertNotNull(instance.getTable("printing_printer"));
         assertNotNull(MiddlewareSpecExtensionsMetaData.ofOrWithNew(instance).getExtensions().get("printing"));
         assertTrue(Files.isDirectory(root.resolve("objects")));

         QSession scanner = EchoDemoAuthentication.grant(instance, new QSession().withUser(new QUser().withIdReference("scanner")));
         QSession admin = EchoDemoAuthentication.grant(instance, new QSession().withUser(new QUser().withIdReference("admin")));
         QSession stranger = EchoDemoAuthentication.grant(instance, new QSession().withUser(new QUser().withIdReference("other")));
         assertTrue(scanner.hasPermission("printing_operator") && scanner.hasPermission("echo.use"));
         assertFalse(scanner.hasPermission("printing_admin") || scanner.hasPermission("document-generation.publish"));
         assertTrue(admin.hasPermission("printing_admin") && admin.hasPermission("document-generation.publish"));
         assertFalse(stranger.hasPermission("echo.use") || stranger.hasPermission("printing_operator"));

         HttpResponse<String> version = send(port, "/printing/v1/version", null, null);
         assertEquals(200, version.statusCode());
         assertTrue(version.body().contains("protocolVersion"));
         assertNotEquals(200, send(port, "/qqq/v1/metaData", null, null).statusCode());
         assertEquals(200, login(port, "scanner", SCANNER_PASSWORD).statusCode());
         assertNotEquals(200, login(port, "scanner", "not-the-password-1").statusCode());

         assertEquals(0, renderer.calls.get());
         try(var connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT COUNT(*) FROM printing_job"))
         {
            rows.next();
            assertEquals(0, rows.getInt(1));
         }
      }
      finally
      {
         try(var connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); var statement = connection.createStatement())
         {
            statement.execute("SHUTDOWN");
         }
      }
   }



   /***************************************************************************
    ** A Basic-authenticated native login, exactly as the existing fixtures do.
    ***************************************************************************/
   private HttpResponse<String> login(int port, String user, String password) throws Exception
   {
      String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
      return send(port, "/qqq/v1/manageSession", "Basic " + basic, "{}");
   }



   /***************************************************************************
    ** One loopback request; a body makes it a JSON POST.
    ***************************************************************************/
   private HttpResponse<String> send(int port, String path, String authorization, String body) throws Exception
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20))
         .header("X-QQQ-Client", "android/qqq-mobile").header("Origin", "http://127.0.0.1:" + port);
      if(authorization != null)
      {
         request.header("Authorization", authorization);
      }
      if(body == null)
      {
         request.GET();
      }
      else
      {
         request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
      }
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
   }
}
