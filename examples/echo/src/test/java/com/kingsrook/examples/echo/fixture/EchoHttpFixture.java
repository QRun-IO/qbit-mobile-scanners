/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.fixture;


import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.examples.echo.EchoConfig;
import com.kingsrook.examples.echo.EchoDestinations;
import com.kingsrook.examples.echo.EchoGenerator;
import com.kingsrook.examples.echo.EchoPermissions;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.examples.echo.EchoService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qbits.mobilescanners.demo.DemoAuthenticationModule;
import com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import org.apache.logging.log4j.LogManager;
import org.h2.jdbcx.JdbcDataSource;


/*******************************************************************************
 ** Test-only semantic HTTP host. Authentication/device approval are the existing
 ** demo implementation; output acceptance is visibly simulated and never prints.
 *******************************************************************************/
public final class EchoHttpFixture implements AutoCloseable
{
   public static final String TYPE = "echo-ui-e2e";
   public static final String RUN_TABLE = "echoFixtureRun";
   public static final String PRINTER = "fixture-printer";
   public static final String MEDIA = "fixture-4x6";
   public static final int DOCUMENT_A = 7;
   public static final int DOCUMENT_B = 8;
   private final MobileScannersDemo application;
   private QApplicationJavalinServer server;



   /***************************************************************************
    ** Random memory databases and caller-owned secrets cannot reference normal
    ** operator data. All metadata is composed before serving any request.
    ***************************************************************************/
   public EchoHttpFixture(String scannerPassword, String adminPassword) throws Exception
   {
      requirePassword(scannerPassword);
      requirePassword(adminPassword);
      String jdbc = "jdbc:h2:mem:echo_ui_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL(jdbc);
      dataSource.setUser("sa");
      application = new MobileScannersDemo(jdbc).withBranding("Echo UI E2E — simulated printing", "#375A7F", null);
      application.initializeDatabase(scannerPassword, adminPassword);
      QInstance instance = application.defineQInstance();
      instance.getAuthentication().withModuleCodeReference(new QCodeReference(Authentication.class));
      instance.addPossibleValueSource(new QPossibleValueSource().withName("echoFixturePrinters").withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(new QPossibleValue<>(PRINTER, "Simulated printer"))));
      instance.addPossibleValueSource(new QPossibleValueSource().withName("echoFixtureMedia").withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(List.of(new QPossibleValue<>(MEDIA, "Simulated 4 x 6 in — 300 dpi"))));
      EchoConfig config = new EchoConfig("mobileDb", dataSource, "echoFixturePrinters", "echoFixtureMedia", new Destinations(), new Generator());
      new EchoQBitProducer().produce(instance, config);
      QTableMetaData run = new QTableMetaData().withName(RUN_TABLE).withLabel("Simulated Echo run inspection").withBackendName("mobileDb")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("echo_run")).withPrimaryKeyField("id")
         .withRecordSecurityLock(new RecordSecurityLock().withFieldName("actorId").withSecurityKeyType("qqq.session.userId"))
         .withPermissionRules(readRules());
      for(String name : List.of("id", "actorId", "deviceId", "state", "pendingEventId", "printerRecordId", "mediaId"))
      {
         run.withField(new QFieldMetaData(name, QFieldType.STRING).withMaxLength(200).withIsEditable(false));
      }
      run.withField(new QFieldMetaData("templateId", QFieldType.INTEGER).withIsEditable(false));
      run.withField(new QFieldMetaData("templateRevisionId", QFieldType.INTEGER).withIsEditable(false));
      instance.addTable(run);
      try(var connection = dataSource.getConnection(); var resource = EchoHttpFixture.class.getResourceAsStream("/db/echo-h2.sql"))
      {
         if(resource == null)
         {
            throw new IllegalStateException("Echo test schema is missing.");
         }
         String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
         for(String statement : sql.split(";"))
         {
            if(!statement.isBlank())
            {
               try(var command = connection.createStatement())
               {
                  command.execute(statement);
               }
            }
         }
      }
   }



   /***************************************************************************
    ** Only a fresh loopback listener is allowed; protected runtimes are refused.
    ***************************************************************************/
   public void start(int port) throws Exception
   {
      if(port < 1024 || port > 65535 || Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593).contains(port))
      {
         throw new IllegalArgumentException("Echo UI tests require a fresh unprotected loopback port.");
      }
      if(server != null)
      {
         throw new IllegalStateException("Echo UI fixture already started.");
      }
      server = application.start(port);
      LogManager.getLogger(EchoHttpFixture.class).info("ECHO_UI_E2E_READY port={} simulatedPrinting=true", port);
   }



   /***************************************************************************
    ** Stop only the server constructed by this fixture.
    ***************************************************************************/
   @Override
   public void close()
   {
      if(server != null)
      {
         server.stop();
      }
   }



   /***************************************************************************
    ** Explicit test opt-in and environment-only secrets; no fallback passwords.
    ***************************************************************************/
   public static void main(String[] args) throws Exception
   {
      if(!TYPE.equals(System.getenv("ECHO_FIXTURE_TYPE")) || args.length != 1)
      {
         throw new IllegalArgumentException("Set ECHO_FIXTURE_TYPE=echo-ui-e2e and provide one fresh loopback port.");
      }
      System.setProperty("qqq.logger.logSessionId.disabled", "true");
      EchoHttpFixture fixture = new EchoHttpFixture(System.getenv("ECHO_FIXTURE_SCANNER_PASSWORD"), System.getenv("ECHO_FIXTURE_ADMIN_PASSWORD"));
      Runtime.getRuntime().addShutdownHook(new Thread(fixture::close));
      fixture.start(Integer.parseInt(args[0]));
   }



   /***************************************************************************
    ** Missing secrets fail before any server listener is created.
    ***************************************************************************/
   private static void requirePassword(String value)
   {
      if(value == null || value.length() < 12)
      {
         throw new IllegalArgumentException("Provide private ECHO_FIXTURE_SCANNER_PASSWORD and ECHO_FIXTURE_ADMIN_PASSWORD values of at least 12 characters.");
      }
   }



   /***************************************************************************
    ** Native queries are real QQQ operations; no fixture business API is added.
    ***************************************************************************/
   private static QPermissionRules readRules()
   {
      return new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION).withCustomPermissionChecker(new QCodeReference(EchoPermissions.class));
   }



   /***************************************************************************
    ** Existing TABLE password/session validation, with narrow fixture role grants.
    ***************************************************************************/
   public static final class Authentication extends DemoAuthenticationModule
   {
      /***********************************************************************
       ** The validated account selects its role, never a request parameter.
       ***********************************************************************/
      @Override
      public QSession createSession(QInstance instance, Map<String, String> context) throws QAuthenticationException
      {
         return permissions(super.createSession(instance, context));
      }

      /***********************************************************************
       ** Keep direct native action session revalidation consistent with login.
       ***********************************************************************/
      @Override
      public boolean isSessionValid(QInstance instance, QSession session)
      {
         boolean valid = super.isSessionValid(instance, session);
         if(valid)
         {
            permissions(session);
         }
         return valid;
      }

      /***********************************************************************
       ** No printing administration permission exists in this fixture.
       ***********************************************************************/
      private static QSession permissions(QSession session)
      {
         if(session.getUser() != null && List.of("scanner", "admin").contains(session.getUser().getIdReference()))
         {
            session.withPermission(EchoQBitProducer.PERMISSION);
         }
         return session;
      }
   }



   /***************************************************************************
    ** Explicit synthetic profile is never discovered from a physical printer.
    ***************************************************************************/
   private static final class Destinations implements EchoDestinations
   {
      /***********************************************************************
       ** Submitted selection IDs are checked independently from the form labels.
       ***********************************************************************/
      @Override
      public Destination authorize(MobileDeviceAuthorization.Identity identity, String printer, String media) throws QException
      {
         if(!PRINTER.equals(printer) || !MEDIA.equals(media))
         {
            throw new QException("Choose the simulated printer and simulated media in this test fixture.");
         }
         return new Destination(PRINTER, MEDIA, "f".repeat(64), 101600, 152400, 300, 300);
      }

      /***********************************************************************
       ** This fixture exercises orchestration, not actual fitting or rendering.
       ***********************************************************************/
      @Override
      public Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, Document document, EchoService.Scan scan) throws QException
      {
         if(!authorizeDocument(identity, destination, document.templateId()).equals(document))
         {
            throw new QException("The chosen simulated document changed.");
         }
         return new Plan(document.templateId(), document.templateRevisionId(), new GenerationPorts.Stock(101600, 152400, 5000, 5000, 5000, 5000, 300), GenerationPorts.Format.PDF);
      }

      /***********************************************************************
       ** Two simulated published documents, selected by operator choice.
       ***********************************************************************/
      @Override
      public Document authorizeDocument(MobileDeviceAuthorization.Identity identity, Destination destination, Integer templateId) throws QException
      {
         if(!List.of(DOCUMENT_A, DOCUMENT_B).contains(templateId))
         {
            throw new QException("Choose a simulated document in this test fixture.");
         }
         return new Document(templateId, 1);
      }

      /***********************************************************************
       ** Simulated choices for the dependent Document field.
       ***********************************************************************/
      @Override
      public List<DocumentChoice> documents(String printer, String media) throws QException
      {
         return PRINTER.equals(printer) && MEDIA.equals(media) ? List.of(new DocumentChoice(DOCUMENT_A, "Simulated label A"), new DocumentChoice(DOCUMENT_B, "Simulated label B")) : List.of();
      }
   }



   /***************************************************************************
    ** Test-only acceptance recorder, not a production queue or printing adapter.
    ***************************************************************************/
   private static final class Generator implements EchoGenerator
   {
      private final Map<String, Accepted> receipts = new ConcurrentHashMap<>();

      /***********************************************************************
       ** Namespaced receipt IDs make simulated output unmistakable in admin rows.
       ***********************************************************************/
      @Override
      public String accept(EchoDestinations.Plan plan, String source, String key, EchoDestinations.Destination destination) throws QException
      {
         String actor = MobileDeviceAuthorization.captureCurrent().orElseThrow(() -> new QException("A verified scanner is required.")).actorId();
         Accepted value = new Accepted(plan, source, destination, actor, "fixture-job-" + source);
         Accepted prior = receipts.putIfAbsent(key, value);
         if(prior != null && !prior.equals(value))
         {
            throw new QException("Simulated receipt identity was reused for a different intent.");
         }
         return value.job();
      }

      /***********************************************************************
       ** Lookup is strictly read-only and binds current actor plus exact intent.
       ***********************************************************************/
      @Override
      public String lookupAccepted(EchoDestinations.Plan plan, String source, String key, EchoDestinations.Destination destination) throws QException
      {
         Accepted receipt = receipts.get(key);
         String actor = MobileDeviceAuthorization.captureCurrent().orElseThrow(() -> new QException("A verified scanner is required.")).actorId();
         if(receipt == null || !receipt.plan().equals(plan) || !receipt.source().equals(source) || !receipt.destination().equals(destination) || !receipt.actor().equals(actor))
         {
            throw new QException("No exact simulated acceptance receipt is available.");
         }
         return receipt.job();
      }
   }



   /***************************************************************************
    ** Complete immutable fixture observation; no printer or document bytes exist.
    ***************************************************************************/
   private record Accepted(EchoDestinations.Plan plan, String source, EchoDestinations.Destination destination, String actor, String job)
   {
   }
}
