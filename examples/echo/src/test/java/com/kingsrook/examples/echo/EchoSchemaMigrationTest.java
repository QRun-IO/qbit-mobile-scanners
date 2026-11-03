/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The additive H2 migration keeps existing rows and can be applied repeatedly.
 *******************************************************************************/
class EchoSchemaMigrationTest
{
   /***************************************************************************
    ** A database created before the AIM column keeps its rows unchanged (NULL AIM),
    ** gains the nullable column, and a second application changes nothing.
    ***************************************************************************/
   @Test
   void aimColumnIsAddedToExistingScansWithoutRewritingThem() throws Exception
   {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:echo_migration_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      try(Connection connection = dataSource.getConnection())
      {
         apply(connection);
         execute(connection, "ALTER TABLE echo_scan DROP COLUMN aimIdentifier");
         execute(connection, "INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES ('run','actor','device','session','printer','4x6','OPEN')");
         execute(connection, "INSERT INTO echo_scan(id,runId,eventId,actorId,deviceId,mobileScanId,barcode,symbology,rendererSymbology,scanSource,capturedAt,receivedAt,state)"
            + " VALUES ('legacy','run','event','actor','device','legacy','001234','CODE128','code128','zebra-emdk','2026-11-01T17:00:00Z','2026-11-01T17:00:01Z','QUEUED')");
         apply(connection);
         apply(connection);
         try(Statement query = connection.createStatement(); var rows = query.executeQuery("SELECT barcode,aimIdentifier,state FROM echo_scan"))
         {
            assertTrue(rows.next());
            assertEquals("001234", rows.getString("barcode"));
            assertNull(rows.getString("aimIdentifier"));
            assertEquals("QUEUED", rows.getString("state"));
            assertTrue(!rows.next());
         }
         try(Statement query = connection.createStatement(); var columns = query.executeQuery(
            "SELECT IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='echo_scan' AND COLUMN_NAME='aimIdentifier'"))
         {
            assertTrue(columns.next());
            assertEquals("YES", columns.getString(1));
            assertEquals(3, columns.getInt(2));
         }
         execute(connection, "UPDATE echo_scan SET aimIdentifier=']E0' WHERE id='legacy'");
         apply(connection);
         try(Statement query = connection.createStatement(); var rows = query.executeQuery("SELECT aimIdentifier FROM echo_scan"))
         {
            assertTrue(rows.next());
            assertEquals("]E0", rows.getString(1));
         }
      }
   }



   /***************************************************************************
    ** A run created before copies existed reads back exactly one copy, new runs
    ** default to one, and repeating the script keeps every chosen count.
    ***************************************************************************/
   @Test
   void copiesColumnDefaultsExistingRunsToOne() throws Exception
   {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:echo_copies_" + UUID.randomUUID().toString().replace("-", "") + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      try(Connection connection = dataSource.getConnection())
      {
         apply(connection);
         execute(connection, "ALTER TABLE echo_run DROP COLUMN copies");
         execute(connection, "INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES ('legacy','actor','device','session','printer','4x6','OPEN')");
         apply(connection);
         apply(connection);
         execute(connection, "INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state) VALUES ('fresh','actor','device','session','printer','4x6','SETUP')");
         execute(connection, "INSERT INTO echo_run(id,actorId,deviceId,publicSessionId,printerRecordId,mediaId,state,copies) VALUES ('chosen','actor','device','session','printer','4x6','OPEN',7)");
         apply(connection);
         try(Statement query = connection.createStatement(); var rows = query.executeQuery("SELECT id,copies FROM echo_run ORDER BY id"))
         {
            assertTrue(rows.next());
            assertEquals("chosen", rows.getString(1));
            assertEquals(7, rows.getInt(2));
            assertTrue(rows.next());
            assertEquals("fresh", rows.getString(1));
            assertEquals(1, rows.getInt(2));
            assertTrue(rows.next());
            assertEquals("legacy", rows.getString(1));
            assertEquals(1, rows.getInt(2));
            assertTrue(!rows.next());
         }
         try(Statement query = connection.createStatement(); var columns = query.executeQuery(
            "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='echo_run' AND COLUMN_NAME='copies'"))
         {
            assertTrue(columns.next());
            assertEquals("NO", columns.getString(1));
         }
      }
   }



   /***************************************************************************
    ** Run the shipped script statement by statement, as the host and tests do.
    ***************************************************************************/
   private void apply(Connection connection) throws Exception
   {
      try(var resource = getClass().getResourceAsStream("/db/echo-h2.sql"))
      {
         for(String statement : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";"))
         {
            if(!statement.isBlank())
            {
               execute(connection, statement);
            }
         }
      }
   }



   /***************************************************************************
    ** One statement on its own handle.
    ***************************************************************************/
   private void execute(Connection connection, String sql) throws Exception
   {
      try(Statement statement = connection.createStatement())
      {
         statement.execute(sql);
      }
   }
}
