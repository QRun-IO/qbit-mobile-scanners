/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.kingsrook.examples.echo.EchoHost;
import com.kingsrook.examples.echo.EchoQBitProducer;
import com.kingsrook.qbits.collaboration.CollaborationQBitConfig;
import com.kingsrook.qbits.collaboration.CollaborationQBitProducer;
import com.kingsrook.qbits.collaboration.attachments.uploadjournal.UploadJournalTable;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationQBitConfig;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationSchema;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.documentgeneration.recordfiles.RetainedAssetReader;
import com.kingsrook.qbits.documentgeneration.renderer.IsolatedRendererBridge;
import com.kingsrook.qbits.mobilescanners.MobileScannersRouteProvider;
import com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo;
import com.kingsrook.qbits.printing.PrintingRealtimeRoutes;
import com.kingsrook.qbits.printing.PrintingRoutes;
import com.kingsrook.qbits.printing.PrintingService;
import com.kingsrook.qbits.recordfiles.RecordFilesQBitConfig;
import com.kingsrook.qbits.recordfiles.RecordFilesQBitProducer;
import com.kingsrook.qbits.recordfiles.RecordFilesSchema;
import com.kingsrook.qbits.recordfiles.lifecycle.RecordFilesAttachmentLifecycle;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeature;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import org.apache.logging.log4j.LogManager;
import org.h2.jdbcx.JdbcDataSource;


/*******************************************************************************
 ** Runnable local Echo host. It reuses the mobile demo's database, accounts and
 ** native server, then composes Collaboration, Record Files, Printing, Document
 ** Generation and Echo from their public production classes. The renderer is the
 ** owner's cold Docker bridge from explicit deployment configuration; nothing is
 ** simulated, and the server is bound to loopback only.
 *******************************************************************************/
public class EchoDemo extends MobileScannersDemo implements AutoCloseable
{
   static final String BACKEND = "mobileDb";
   private static final Set<Integer> TOLERATED_REAPPLY = Set.of(42101, 42111, 42121, 90045);
   private static final List<String> GENERATION_MIGRATIONS = List.of("20261031-output-filenames-h2.sql", "20261031-warm-batch-completion-h2.sql",
      "20261101-print-media-profile-h2.sql");

   private final EchoDemoSettings settings;
   private final GenerationPorts.IsolatedRenderer renderer;
   private final DataSource dataSource;
   private final PrintingService printing;
   private PrintingRealtimeRoutes realtime;
   private QApplicationJavalinServer server;
   private boolean composed;



   /***************************************************************************
    ** Production: the renderer is always the owner's cold bridge built from the
    ** explicit deployment configuration, reading assets through Record Files.
    ***************************************************************************/
   public EchoDemo(EchoDemoSettings settings)
   {
      this(settings, bridge(settings));
   }



   /***************************************************************************
    ** Boundary tests may supply a refusing renderer; no other part is replaceable.
    ***************************************************************************/
   EchoDemo(EchoDemoSettings settings, GenerationPorts.IsolatedRenderer renderer)
   {
      super(jdbcUrl(settings));
      this.settings = settings;
      this.renderer = java.util.Objects.requireNonNull(renderer);
      JdbcDataSource source = new JdbcDataSource();
      source.setURL(jdbcUrl(settings));
      source.setUser("sa");
      dataSource = source;
      printing = new PrintingService(dataSource, settings.tenant());
   }



   /***************************************************************************
    ** The host's own Printing service, so setup and tests use its public API.
    ***************************************************************************/
   PrintingService printing()
   {
      return printing;
   }



   /***************************************************************************
    ** Owner factory only; there is no fallback renderer class.
    ***************************************************************************/
   private static GenerationPorts.IsolatedRenderer bridge(EchoDemoSettings settings)
   {
      if(settings.renderer() == null)
      {
         throw new IllegalArgumentException("The ECHO_RENDERER_* deployment configuration is required");
      }
      return new IsolatedRendererBridge(settings.renderer(), new RetainedAssetReader());
   }



   /***************************************************************************
    ** One private file database. Generation requires file H2 with WRITE_DELAY=0.
    ***************************************************************************/
   static String jdbcUrl(EchoDemoSettings settings)
   {
      return "jdbc:h2:file:" + settings.root().resolve("db").resolve("echo").toAbsolutePath().normalize().toString().replace('\\', '/')
         + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000;WRITE_DELAY=0";
   }



   /***************************************************************************
    ** The mobile demo's instance, composed once with every owner QBit and Echo.
    ***************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance instance = super.defineQInstance();
      if(!composed)
      {
         // Owner facades (RecordFilesPrintSource -> AttachmentsService) read the installed QBits from QContext
         // while EchoHost.install constructs them. Scope the system context to this one composition step and
         // restore whatever was there; no request ever sees it.
         QInstance previousInstance = QContext.getQInstance();
         QSession previousSession = QContext.getQSession();
         QContext.init(instance, new QSystemUserSession());
         try
         {
            compose(instance);
         }
         finally
         {
            if(previousInstance != null)
            {
               QContext.init(previousInstance, previousSession);
            }
            else
            {
               QContext.clear();
            }
         }
         composed = true;
      }
      return instance;
   }



   /***************************************************************************
    ** Order matters: Collaboration owns attachments, Echo's source table must
    ** exist before Record Files names it as a retained parent.
    ***************************************************************************/
   private void compose(QInstance instance) throws QException
   {
      EchoDemoAuthentication.configure(settings.tenant());
      instance.getAuthentication().withModuleCodeReference(new QCodeReference(EchoDemoAuthentication.class));
      try
      {
         LocalFilesystemAttachmentStorage.configure(Files.createDirectories(settings.root().resolve("objects")));
      }
      catch(java.io.IOException failure)
      {
         throw new QException("The private object directory is unavailable.", failure);
      }
      CollaborationQBitConfig collaboration = new CollaborationQBitConfig().withBackendName(BACKEND).withDefaultFeatures(RecordFeature.values())
         .withAttachmentLifecycle(new QCodeReference(RecordFilesAttachmentLifecycle.class))
         .withAttachmentStorage(new QCodeReference(LocalFilesystemAttachmentStorage.class));
      new CollaborationQBitProducer().withQBitConfig(collaboration).produce(instance);
      // Documents are the operator's published, Echo-compatible templates, discovered per run; the legacy
      // ECHO_TEMPLATE_* host pin no longer selects one.
      EchoHost.install(instance, new EchoHost.Deployment(BACKEND, BACKEND, BACKEND, dataSource, printing, renderer));
      RecordFilesQBitConfig recordFiles = new RecordFilesQBitConfig().withBackendName(BACKEND).withParentTables(List.of(EchoQBitProducer.SCAN_TABLE))
         .withQuarantineGeneratedFiles(settings.releaseGeneratedFiles() ? Boolean.FALSE : null);
      new RecordFilesQBitProducer().withQBitConfig(recordFiles).produce(instance);
      // The server only validates the instance, it never enriches it. Enrichment (after every QBit) is what
      // registers Collaboration's record-feature permissions (QInstanceEnricher -> RecordFeaturePermissionsRegistrar).
      new QInstanceEnricher(instance).enrich();
   }



   /***************************************************************************
    ** Mobile schema and the two seeded accounts come from the existing demo; the
    ** owners' schemas are then applied. Existing databases are not migrated: an
    ** already-present object is skipped and anything else fails startup.
    ***************************************************************************/
   @Override
   public void initializeDatabase(String scannerPassword, String adminPassword) throws Exception
   {
      Files.createDirectories(settings.root().resolve("db"));
      super.initializeDatabase(scannerPassword, adminPassword);
      QInstance instance = defineQInstance();
      printing.initialize();
      try(Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
      {
         script(statement, resource("/db/echo-h2.sql"));
         script(statement, DocumentGenerationSchema.postgresScript(config(instance, DocumentGenerationQBitConfig.class)).replace("TIMESTAMPTZ", "TIMESTAMP"));
         for(String migration : GENERATION_MIGRATIONS)
         {
            script(statement, resource("/db/migrations/" + migration));
         }
         collaborationTables(statement, instance);
         script(statement, UploadJournalTable.h2Script());
         script(statement, RecordFilesSchema.h2Script(config(instance, RecordFilesQBitConfig.class)));
      }
   }



   /***************************************************************************
    ** Resolve one installed QBit configuration, never a copy that could differ.
    ***************************************************************************/
   private static <T> T config(QInstance instance, Class<T> type) throws QException
   {
      return instance.getQBits().values().stream().map(bit -> bit.getConfig()).filter(type::isInstance).map(type::cast).findFirst()
         .orElseThrow(() -> new QException("A required QBit is not installed."));
   }



   /***************************************************************************
    ** Collaboration tables use the same metadata-derived H2 DDL as the Record
    ** Files owner's fixture; an owner-shipped script should replace it.
    ***************************************************************************/
   private static void collaborationTables(Statement statement, QInstance instance) throws SQLException
   {
      for(QTableMetaData table : instance.getTables().values())
      {
         if(!table.getName().startsWith("collaboration_") || UploadJournalTable.NAME.equals(table.getName()))
         {
            continue;
         }
         List<String> columns = new java.util.ArrayList<>();
         for(QFieldMetaData field : table.getFields().values())
         {
            String type = field.getName().equals(table.getPrimaryKeyField()) ? "INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY" : switch(field.getType())
            {
               case INTEGER -> "INTEGER";
               case LONG -> "BIGINT";
               case BOOLEAN -> "BOOLEAN";
               case DATE_TIME -> "TIMESTAMP";
               case DATE -> "DATE";
               case DECIMAL -> "DECIMAL(30,10)";
               default -> "VARCHAR(100000)";
            };
            columns.add("\"" + physical(field) + "\" " + type);
         }
         apply(statement, "CREATE TABLE IF NOT EXISTS \"" + table.getName() + "\" (" + String.join(",", columns) + ")");
         int index = 0;
         if(table.getUniqueKeys() != null)
         {
            for(var key : table.getUniqueKeys())
            {
               List<String> names = key.getFieldNames().stream().map(name -> "\"" + physical(table.getField(name)) + "\"").toList();
               apply(statement, "CREATE UNIQUE INDEX IF NOT EXISTS uq_" + table.getName() + "_" + (++index) + " ON \"" + table.getName() + "\" (" + String.join(",", names) + ")");
            }
         }
      }
   }



   /***************************************************************************
    ** The column name QQQ will use for a field.
    ***************************************************************************/
   private static String physical(QFieldMetaData field)
   {
      return field.getBackendName() == null ? field.getName() : field.getBackendName();
   }



   /***************************************************************************
    ** Read one owner resource from the classpath.
    ***************************************************************************/
   private static String resource(String name) throws java.io.IOException
   {
      try(InputStream stream = EchoDemo.class.getResourceAsStream(name))
      {
         if(stream == null)
         {
            throw new IllegalStateException("Owner schema resource is missing: " + name);
         }
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }



   /***************************************************************************
    ** Run each statement of an owner script.
    ***************************************************************************/
   private static void script(Statement statement, String script) throws SQLException
   {
      for(String sql : script.replaceAll("(?m)^--.*$", "").split(";"))
      {
         if(!sql.isBlank())
         {
            apply(statement, sql);
         }
      }
   }



   /***************************************************************************
    ** Skip only an object that already exists (table, index, column, constraint).
    ***************************************************************************/
   private static void apply(Statement statement, String sql) throws SQLException
   {
      try
      {
         statement.execute(sql);
      }
      catch(SQLException failure)
      {
         if(!TOLERATED_REAPPLY.contains(failure.getErrorCode()))
         {
            throw failure;
         }
      }
   }



   /***************************************************************************
    ** Loopback only. Printing's agent and administration routes share this
    ** server with the mobile routes; the operator token guards administration.
    ***************************************************************************/
   @Override
   public QApplicationJavalinServer start(Integer port) throws Exception
   {
      var policy = printing.realtimePolicy();
      long uploadBodyLimit = ((Math.max(policy.get("maxPdfBytes"), policy.get("maxZplBytes")) + 2) / 3) * 4 + 65536;
      PrintingRoutes routes = new PrintingRoutes(printing, context -> MessageDigest.isEqual(settings.adminToken().getBytes(StandardCharsets.UTF_8),
         PrintingRoutes.bearer(context).getBytes(StandardCharsets.UTF_8)), PrintingRoutes.LegacyEnrollment.DISABLED);
      realtime = new PrintingRealtimeRoutes(printing);
      server = new QApplicationJavalinServer(this).withPort(port).withServeFrontendNext(true).withServeLegacyUnversionedMiddlewareAPI(false)
         .withAdditionalRouteProviders(List.of(new MobileScannersRouteProvider(defineQInstance()), routes, realtime))
         .withJavalinConfigCustomizer(config ->
         {
            config.jetty.host = "127.0.0.1";
            config.http.maxRequestSize = uploadBodyLimit;
            config.routes.before(EchoDemo::guardOrigin);
         });
      server.start();
      return server;
   }



   /***************************************************************************
    ** Reject DNS rebinding and cross-site cookie mutations, as Printing's own
    ** local host does. Explicit API credentials without cookies stay usable.
    ***************************************************************************/
   static void guardOrigin(Context context)
   {
      String host = context.header("Host");
      int port = context.req().getLocalPort();
      if(host == null || !Set.of("localhost:" + port, "127.0.0.1:" + port).contains(host.toLowerCase(Locale.ROOT)))
      {
         throw new ForbiddenResponse("The Echo demo accepts only loopback host names");
      }
      String origin = context.header("Origin");
      if(origin != null && !origin.equals("http://" + host))
      {
         throw new ForbiddenResponse("Cross-origin access to the Echo demo is not allowed");
      }
      if("cross-site".equalsIgnoreCase(context.header("Sec-Fetch-Site")))
      {
         throw new ForbiddenResponse("Cross-site access to the Echo demo is not allowed");
      }
      boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(context.method().name());
      boolean cookie = context.cookie("sessionUUID") != null || context.cookie("sessionId") != null;
      if(unsafe && cookie && origin == null)
      {
         throw new ForbiddenResponse("Cookie-authenticated changes require a same-origin request");
      }
   }



   /***************************************************************************
    ** Stop only what this host started.
    ***************************************************************************/
   @Override
   public void close()
   {
      if(server != null)
      {
         server.stop();
      }
      if(realtime != null)
      {
         realtime.close();
      }
   }



   /***************************************************************************
    ** Secrets enter through the environment only and are never printed. First
    ** startup needs both mobile demo passwords; later startups keep the accounts.
    ***************************************************************************/
   public static void main(String[] args) throws Exception
   {
      System.setProperty("qqq.logger.logSessionId.disabled", "true");
      EchoDemoSettings settings = EchoDemoSettings.fromEnvironment(System.getenv(), args.length == 0 ? null : args[0]);
      launch(new EchoDemo(settings), settings, "production");
   }



   /***************************************************************************
    ** Shared startup for the production and the explicit local development mains.
    ***************************************************************************/
   static void launch(EchoDemo demo, EchoDemoSettings settings, String renderer) throws Exception
   {
      demo.withBranding(System.getenv("MOBILE_BRAND_DISPLAY_NAME"), System.getenv("MOBILE_BRAND_PRIMARY_COLOR"), System.getenv("MOBILE_BRAND_LOGO_URL"));
      demo.initializeDatabase(System.getenv("MOBILE_DEMO_SCANNER_PASSWORD"), System.getenv("MOBILE_DEMO_ADMIN_PASSWORD"));
      Runtime.getRuntime().addShutdownHook(new Thread(demo::close));
      demo.start(settings.port());
      LogManager.getLogger(EchoDemo.class).info("ECHO_DEMO_READY port={} loopback=true renderer={}", settings.port(), renderer);
   }
}
