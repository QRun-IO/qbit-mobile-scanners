/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.documentgeneration.GenerationTables;
import com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The print `copies` column reaches an existing local development database
 ** through the demo's own migration list, before any request is read, and the
 ** composed Generation classes accept a count of three. Temporary file databases
 ** and fresh ports only; the canonical server and its data are never touched.
 *******************************************************************************/
class EchoDemoCopiesMigrationTest
{
   private static final String TOKEN = UUID.randomUUID() + "-" + UUID.randomUUID();
   private static final String REQUEST_TABLE = "document_generation_request";

   @TempDir
   Path root;

   @TempDir
   Path data;



   /***************************************************************************
    ** Fixture renderer: migration never renders or validates a template.
    ***************************************************************************/
   private static final class UnusedRenderer implements GenerationPorts.IsolatedRenderer
   {
      /***********************************************************************
       ** Catalog text with the owner's required heading.
       ***********************************************************************/
      @Override
      public String barcodeReference() throws QException
      {
         return "# Barcode catalog: qqq-barcodes-9 (copies migration test renderer)";
      }



      /***********************************************************************
       ** Not used by migration.
       ***********************************************************************/
      @Override
      public void validate(String actor, GenerationPorts.Revision revision) throws QException
      {
      }



      /***********************************************************************
       ** Migration never renders.
       ***********************************************************************/
      @Override
      public GenerationPorts.Content render(GenerationPorts.RenderRequest request) throws QException
      {
         throw new QException("This migration test never renders.");
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
    ** The shipped list ends with the copies migration, which the owner publishes.
    ***************************************************************************/
   @Test
   void copiesMigrationIsPartOfTheDemoMigrationList() throws Exception
   {
      assertEquals("20261102-print-copies-h2.sql", EchoDemo.GENERATION_MIGRATIONS.getLast());
      assertEquals(EchoDemo.GENERATION_MIGRATIONS.size(), Set.copyOf(EchoDemo.GENERATION_MIGRATIONS).size());
      try(var resource = EchoDemo.class.getResourceAsStream("/db/migrations/20261102-print-copies-h2.sql"))
      {
         assertTrue(resource != null, "The Generation owner's copies migration must be on the demo classpath.");
         String sql = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
         assertTrue(sql.contains("ALTER TABLE " + REQUEST_TABLE + " ADD COLUMN copies"), sql);
      }
   }



   /***************************************************************************
    ** An existing database from before copies gains the nullable column on
    ** adoption, keeps its legacy rows untouched (null means one copy), keeps a
    ** chosen count across repeated migration, and the composed metadata and
    ** request classes carry the count.
    ***************************************************************************/
   @Test
   void adoptingAnOlderDatabaseAddsCopiesAndPreservesRowsAcrossRepeatedMigration() throws Exception
   {
      Path base = data.resolve("mobile");
      Path file = data.resolve("mobile.mv.db");
      String url = "jdbc:h2:file:" + base.toString().replace('\\', '/') + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
      MobileScannersDemo baseline = new MobileScannersDemo(url);
      baseline.initializeDatabase(UUID.randomUUID().toString(), UUID.randomUUID().toString());
      try(Connection connection = DriverManager.getConnection(url, "sa", ""); Statement statement = connection.createStatement())
      {
         statement.execute("SHUTDOWN");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
      EchoDemoSettings settings = new EchoDemoSettings(root, freePort(), "echo-copies", TOKEN, true, null, null, file);

      // a full adoption builds the current schema; then make it an older database, with a fixture legacy request row
      adopt(settings);
      List<String> legacy;
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         assertTrue(columns(statement).contains("copies"), "A fresh adoption already has the owner's current column.");
         statement.execute("ALTER TABLE " + REQUEST_TABLE + " DROP COLUMN copies");
         assertTrue(!columns(statement).contains("copies"));
         insertFixtureRow(connection, "legacy", null);
         legacy = row(statement, "legacy");
         statement.execute("SHUTDOWN");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();

      // adopting the older database migrates it before any request is read
      adopt(settings);
      List<String> migrated;
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         assertTrue(columns(statement).contains("copies"));
         try(var set = statement.executeQuery("SELECT IS_NULLABLE, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME)='" + REQUEST_TABLE
            + "' AND LOWER(COLUMN_NAME)='copies'"))
         {
            assertTrue(set.next());
            assertEquals("YES", set.getString(1));
            assertEquals("INTEGER", set.getString(2));
         }
         migrated = row(statement, "legacy");
         assertEquals(legacy.size() + 1, migrated.size());
         assertEquals(legacy, migrated.subList(0, legacy.size()), "The legacy row must be unchanged.");
         assertNull(migrated.getLast(), "A row from before copies reads as no count, meaning one copy.");
         insertFixtureRow(connection, "chosen", 3);
         statement.execute("SHUTDOWN");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();

      // repeating the migration changes nothing, including a chosen count
      adopt(settings);
      adopt(settings);
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         assertEquals(migrated, row(statement, "legacy"));
         assertEquals("3", row(statement, "chosen").getLast());
         try(var set = statement.executeQuery("SELECT COUNT(*) FROM " + REQUEST_TABLE))
         {
            assertTrue(set.next());
            assertEquals(2, set.getInt(1));
         }
         statement.execute("SHUTDOWN");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();

      // the composed metadata and ordinary request classes carry the count
      try(EchoDemo demo = new EchoDemo(settings, new UnusedRenderer()))
      {
         var field = demo.defineQInstance().getTable(GenerationTables.REQUEST).getField("copies");
         assertEquals(QFieldType.INTEGER, field.getType());
      }
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         statement.execute("SHUTDOWN");
      }
   }



   /***************************************************************************
    ** The composed Generation classes accept three copies on the request and on
    ** the print intent, default to one for every earlier constructor, and refuse
    ** counts outside Printing's 1..100 before they can reach admission.
    ***************************************************************************/
   @Test
   void composedGenerationRequestAndPrintIntentCarryThreeCopies()
   {
      GenerationPorts.Source source = new GenerationPorts.Source("echoScan", "scan-1");
      GenerationPorts.Stock stock = new GenerationPorts.Stock(101600, 25400, 3000, 3000, 3000, 3000, 300);
      GenerationPorts.MediaProfile media = new GenerationPorts.MediaProfile("4x1", "a".repeat(64));
      DocumentGenerationService.Expected expected = new DocumentGenerationService.Expected(7, 11, stock);
      DocumentGenerationService.Request three = new DocumentGenerationService.Request(7, source, GenerationPorts.Format.ZPL, "echo:scan-1", "printer-row", expected, media, 3);
      assertEquals(3, three.copies());
      assertEquals(1, new DocumentGenerationService.Request(7, source, GenerationPorts.Format.ZPL, "echo:scan-1", "printer-row", expected, media).copies());
      assertEquals(1, new DocumentGenerationService.Request(7, source, GenerationPorts.Format.ZPL, "echo:scan-1", "printer-row", expected, media, null).copies());
      assertEquals(3, new GenerationPorts.PrintRequest("op", "actor", source, null, "printer-row", media, 3).copies());
      assertEquals(1, new GenerationPorts.PrintRequest("op", "actor", source, null, "printer-row", media).copies());
      assertEquals(100, new GenerationPorts.PrintRequest("op", "actor", source, null, "printer-row", media, 100).copies());
      for(int invalid : new int[] { 0, -1, 101, Integer.MAX_VALUE })
      {
         assertThrows(IllegalArgumentException.class, () -> new DocumentGenerationService.Request(7, source, GenerationPorts.Format.ZPL, "echo:scan-1", "printer-row", expected, media, invalid));
         assertThrows(IllegalArgumentException.class, () -> new GenerationPorts.PrintRequest("op", "actor", source, null, "printer-row", media, invalid));
      }
   }



   /***************************************************************************
    ** One adoption of the existing file database, closed cleanly afterwards.
    ***************************************************************************/
   private void adopt(EchoDemoSettings settings) throws Exception
   {
      try(EchoDemo demo = new EchoDemo(settings, new UnusedRenderer()))
      {
         demo.initializeDatabase(null, null);
      }
      try(Connection connection = DriverManager.getConnection(EchoDemo.jdbcUrl(settings), "sa", ""); Statement statement = connection.createStatement())
      {
         statement.execute("SHUTDOWN");
      }
      com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.resetConnectionProviders();
   }



   /***************************************************************************
    ** Lower-case column names of the request table.
    ***************************************************************************/
   private static List<String> columns(Statement statement) throws Exception
   {
      List<String> names = new ArrayList<>();
      try(var set = statement.executeQuery("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME)='" + REQUEST_TABLE + "' ORDER BY ORDINAL_POSITION"))
      {
         while(set.next())
         {
            names.add(set.getString(1));
         }
      }
      return names;
   }



   /***************************************************************************
    ** Test-fixture request row only, with the owner's real physical columns (the
    ** table's columns other than the identity id are all nullable). The stable
    ** operation_id identifies it; the state says it is a fixture, so no printed
    ** output is implied. The count column is set only when one is given.
    ***************************************************************************/
   private static void insertFixtureRow(Connection connection, String tag, Integer copies) throws Exception
   {
      String sql = "INSERT INTO " + REQUEST_TABLE + " (operation_id, request_key, actor_id, table_name, record_id, kind, format, state"
         + (copies == null ? "" : ", copies") + ") VALUES (?,?,?,?,?,?,?,?" + (copies == null ? "" : ",?") + ")";
      try(PreparedStatement insert = connection.prepareStatement(sql))
      {
         insert.setString(1, operationId(tag));
         insert.setString(2, "fixture:" + tag);
         insert.setString(3, "fixture-actor");
         insert.setString(4, "echoScan");
         insert.setString(5, "fixture-" + tag);
         insert.setString(6, "FIXTURE");
         insert.setString(7, "ZPL");
         insert.setString(8, "FIXTURE");
         if(copies != null)
         {
            insert.setInt(9, copies);
         }
         insert.executeUpdate();
      }
   }



   /***************************************************************************
    ** The stable operation id of one fixture row.
    ***************************************************************************/
   private static String operationId(String tag)
   {
      return "fixture-op-" + tag;
   }



   /***************************************************************************
    ** The fixture row with this tag's operation id, every column as text.
    ***************************************************************************/
   private static List<String> row(Statement statement, String tag) throws Exception
   {
      try(var set = statement.executeQuery("SELECT * FROM " + REQUEST_TABLE + " WHERE operation_id='" + operationId(tag) + "'"))
      {
         assertTrue(set.next(), "The tagged fixture row is missing.");
         List<String> result = new ArrayList<>();
         for(int column = 1; column <= set.getMetaData().getColumnCount(); column++)
         {
            result.add(set.getString(column));
         }
         assertTrue(!set.next(), "The tagged fixture row is not unique.");
         return result;
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
}
