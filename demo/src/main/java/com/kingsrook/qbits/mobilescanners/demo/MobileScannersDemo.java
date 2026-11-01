/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.demo;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import com.kingsrook.qbits.mobilescanners.MobileScannersQBitConfig;
import com.kingsrook.qbits.mobilescanners.MobileScannersQBitProducer;
import com.kingsrook.qbits.mobilescanners.MobileScannersRouteProvider;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.TableBasedAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.TableBasedAuthenticationModule;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;


/*******************************************************************************
 ** Persistent, local-only acceptance host for the mobile qbit and native client.
 *******************************************************************************/
public class MobileScannersDemo extends AbstractQQQApplication
{
   private final String jdbcUrl;
   private MobileScannersQBitConfig mobileConfig = new MobileScannersQBitConfig().withBackendName("mobileDb");
   private QInstance instance;



   /*******************************************************************************
    ** Tests use an isolated database; the launcher uses a file-backed database.
    *******************************************************************************/
   public MobileScannersDemo(String jdbcUrl)
   {
      this.jdbcUrl = jdbcUrl;
   }



   /*******************************************************************************
    ** Keep H2 open between QQQ connections; normal JVM shutdown flushes the file.
    *******************************************************************************/
   static String fileDatabaseUrl(Path database)
   {
      return "jdbc:h2:file:" + database.toAbsolutePath().normalize().toString().replace('\\', '/') + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
   }



   /*******************************************************************************
    ** Configure the same public appearance for every client of this demo server.
    *******************************************************************************/
   public MobileScannersDemo withBranding(String displayName, String primaryColor, String logoUrl)
   {
      if(instance != null)
      {
         throw new IllegalStateException("Configure branding before defining the demo instance.");
      }
      MobileScannersQBitConfig configured = new MobileScannersQBitConfig().withBackendName("mobileDb");
      if(displayName != null)
      {
         configured.withDisplayName(displayName);
      }
      if(primaryColor != null)
      {
         configured.withPrimaryColor(primaryColor);
      }
      configured.withLogoUrl(logoUrl);
      mobileConfig = configured;
      return this;
   }



   /*******************************************************************************
    ** The same instance backs native UI routes and the enrollment route provider.
    *******************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      if(instance != null)
      {
         return instance;
      }
      instance = new QInstance();
      instance.setEnforcePermissionsInCoreActions(true);
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData().withName("mobileDb").withVendor("h2")
         .withHostName("localhost").withDatabaseName("mobile").withUsername("sa");
      backend.setJdbcUrl(jdbcUrl);
      instance.addBackend(backend);

      TableBasedAuthenticationMetaData authentication = new TableBasedAuthenticationMetaData()
         .withUserTableName("demoUser").withSessionTableName("demoSession");
      authentication.withName("localDemo").withModuleCodeReference(new QCodeReference(DemoAuthenticationModule.class));
      instance.withInstanceDefaultAuthentication(authentication);
      for(QTableMetaData table : List.of(authentication.defineStandardUserTable("mobileDb"), authentication.defineStandardSessionTable("mobileDb")))
      {
         table.withBackendDetails(new RDBMSTableBackendDetails().withTableName(table.getName()));
         table.getFields().values().forEach(field -> field.setBackendName(field.getName()));
         table.withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
            .withPermissionBaseName("demoCredentials"));
         instance.addTable(table);
      }
      instance.getTable("demoUser").getField("passwordHash").setIsHidden(true);
      new MobileScannersQBitProducer().withConfig(mobileConfig).produce(instance, "mobile");
      for(String name : List.of(MobileScannersQBitProducer.DEVICE, MobileScannersQBitProducer.CREDENTIAL, MobileScannersQBitProducer.SCAN,
         MobileScannersQBitProducer.SESSION, MobileScannersQBitProducer.SESSION_BINDING))
      {
         QTableMetaData table = instance.getTable(name);
         table.withBackendDetails(new RDBMSTableBackendDetails().withTableName(name));
         table.getFields().values().forEach(field -> field.setBackendName(field.getName()));
      }
      return instance;
   }



   /*******************************************************************************
    ** DDL and user seeding happen before the HTTP server starts accepting requests.
    *******************************************************************************/
   public void initializeDatabase(String scannerPassword, String adminPassword) throws Exception
   {
      try(Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""))
      {
         try(var statement = connection.createStatement())
         {
            statement.execute("CREATE TABLE IF NOT EXISTS demoUser (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, fullName VARCHAR(200), passwordHash VARCHAR(1024) NOT NULL, createDate TIMESTAMP WITH TIME ZONE, modifyDate TIMESTAMP WITH TIME ZONE)");
            statement.execute("CREATE TABLE IF NOT EXISTS demoSession (id VARCHAR(100) PRIMARY KEY, userId INTEGER NOT NULL REFERENCES demoUser(id), accessTimestamp TIMESTAMP WITH TIME ZONE, createDate TIMESTAMP WITH TIME ZONE, modifyDate TIMESTAMP WITH TIME ZONE)");
            try(InputStream stream = MobileScannersDemo.class.getResourceAsStream("/db/mobile-scanners-h2.sql"))
            {
               if(stream == null)
               {
                  throw new IllegalStateException("Mobile scanner schema resource is missing");
               }
               String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
               for(String sql : schema.split(";"))
               {
                  if(!sql.isBlank())
                  {
                     statement.execute(sql);
                  }
               }
            }
         }
         seedUser(connection, "scanner", "Scanner Operator", scannerPassword);
         seedUser(connection, "admin", "Device Administrator", adminPassword);
      }
   }



   /*******************************************************************************
    ** Existing accounts persist. Startup never silently changes their passwords.
    *******************************************************************************/
   private void seedUser(Connection connection, String username, String fullName, String password) throws Exception
   {
      try(PreparedStatement query = connection.prepareStatement("SELECT id FROM demoUser WHERE username = ?"))
      {
         query.setString(1, username);
         try(var rows = query.executeQuery())
         {
            if(rows.next())
            {
               return;
            }
         }
      }
      if(password == null || password.length() < 12)
      {
         throw new IllegalArgumentException("Set MOBILE_DEMO_SCANNER_PASSWORD and MOBILE_DEMO_ADMIN_PASSWORD (at least 12 characters) before first startup");
      }
      try(PreparedStatement insert = connection.prepareStatement("INSERT INTO demoUser (username, fullName, passwordHash) VALUES (?, ?, ?)"))
      {
         insert.setString(1, username);
         insert.setString(2, fullName);
         insert.setString(3, TableBasedAuthenticationModule.PasswordHasher.createHashedPassword(password));
         insert.executeUpdate();
      }
   }



   /*******************************************************************************
    ** HTTP is deliberately bound to loopback; TC22 testing uses adb reverse.
    *******************************************************************************/
   public QApplicationJavalinServer start(Integer port) throws Exception
   {
      QInstance configuredInstance = defineQInstance();
      QApplicationJavalinServer server = new QApplicationJavalinServer(this).withPort(port)
         .withServeFrontendNext(true).withServeLegacyUnversionedMiddlewareAPI(false)
         .withAdditionalRouteProviders(List.of(new MobileScannersRouteProvider(configuredInstance)))
         .withJavalinConfigCustomizer(config -> config.jetty.host = "127.0.0.1");
      server.start();
      return server;
   }



   /*******************************************************************************
    ** Credentials enter through environment variables and are never printed.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      System.setProperty("qqq.logger.logSessionId.disabled", "true");
      Integer port = args.length == 0 ? 8080 : Integer.valueOf(args[0]);
      Path database = Path.of("data", "mobile").toAbsolutePath().normalize();
      Files.createDirectories(database.getParent());
      MobileScannersDemo application = new MobileScannersDemo(fileDatabaseUrl(database));
      application.withBranding(System.getenv("MOBILE_BRAND_DISPLAY_NAME"), System.getenv("MOBILE_BRAND_PRIMARY_COLOR"), System.getenv("MOBILE_BRAND_LOGO_URL"));
      application.initializeDatabase(System.getenv("MOBILE_DEMO_SCANNER_PASSWORD"), System.getenv("MOBILE_DEMO_ADMIN_PASSWORD"));
      application.start(port);
   }
}
