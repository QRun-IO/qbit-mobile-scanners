/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.demo;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Preserve historical instants when replacing H2 zoned timestamp columns.
 *******************************************************************************/
class MobileTimestampMigrationTest
{
   @TempDir
   private Path directory;



   /***************************************************************************
    ** A separate non-UTC JVM avoids changing the timezone of concurrent tests.
    ***************************************************************************/
   @Test
   @Timeout(40)
   void zonedFileDatabaseMigratesWithoutChangingInstantsOrExistingRecords() throws Exception
   {
      String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
         .map(value -> Path.of(value).toAbsolutePath().normalize().toString()).collect(Collectors.joining(File.pathSeparator));
      String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
      Path output = directory.resolve("migration.log");
      Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
         "-XX:ActiveProcessorCount=2", "-Xmx256m", "-Duser.timezone=America/Chicago", "-cp", classpath,
         MigrationProbe.class.getName(), directory.resolve("legacy").toString())
         .redirectErrorStream(true).redirectOutput(output.toFile()).start();
      try
      {
         assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Isolated migration probe must finish");
         assertEquals(0, child.exitValue(), () -> "Migration probe failed: " + readOutput(output));
      }
      finally
      {
         if(child.isAlive())
         {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
         }
      }
   }



   /***************************************************************************
    ** Only synthetic fixture data can appear in child assertions.
    ***************************************************************************/
   private static String readOutput(Path output)
   {
      try
      {
         return Files.readString(output);
      }
      catch(Exception exception)
      {
         return exception.getClass().getSimpleName();
      }
   }



   /***************************************************************************
    ** Run the actual initializer twice against a closed/reopened file database.
    ***************************************************************************/
   public static class MigrationProbe
   {
      private static final Map<String, List<String>> TIMESTAMPS = Map.of(
         "mobileDevice", List.of("enrolledAt", "approvedAt", "lastSeenAt", "locationCapturedAt", "locationReceivedAt"),
         "mobileScan", List.of("capturedAt", "receivedAt"),
         "mobileSession", List.of("startedAt", "lastSeenAt", "endedAt", "authCleanupAt"));
      private static final List<OffsetDateTime> INSTANTS = List.of(
         OffsetDateTime.parse("2024-03-10T01:59:59.123456-06:00"),
         OffsetDateTime.parse("2024-11-03T12:30:00.654321+05:45"));



      /************************************************************************
       ** Use only an isolated fixture; no HTTP listener or operator credentials.
       ************************************************************************/
      public static void main(String[] args) throws Exception
      {
         assertEquals(ZoneId.of("America/Chicago"), ZoneId.systemDefault());
         String url = "jdbc:h2:file:" + args[0].replace('\\', '/') + ";DATABASE_TO_UPPER=FALSE";
         MobileScannersDemo demo = new MobileScannersDemo(url);
         demo.initializeDatabase("synthetic-scanner-password", "synthetic-admin-password");
         Map<String, String> passwords;
         try(Connection connection = DriverManager.getConnection(url, "sa", ""))
         {
            passwords = passwordHashes(connection);
            seedLegacyZonedRows(connection);
         }
         for(int run = 0; run < 2; run++)
         {
            demo.initializeDatabase(null, null);
            try(Connection connection = DriverManager.getConnection(url, "sa", ""))
            {
               assertEquals(passwords, passwordHashes(connection));
               for(var entry : TIMESTAMPS.entrySet())
               {
                  try(var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM " + entry.getKey() + " ORDER BY id"))
                  {
                     int index = 0;
                     while(rows.next())
                     {
                        for(String field : entry.getValue())
                        {
                           assertEquals(INSTANTS.get(index).toInstant(), rows.getObject(field, LocalDateTime.class).toInstant(ZoneOffset.UTC), entry.getKey() + "." + field);
                        }
                        if(entry.getKey().equals("mobileDevice"))
                        {
                           assertEquals("APPROVED", rows.getString("status"));
                           assertEquals("Original name " + index, rows.getString("displayName"));
                        }
                        if(entry.getKey().equals("mobileScan"))
                        {
                           assertEquals("000123-raw-" + index, rows.getString("barcode"));
                           assertEquals("scanner", rows.getString("userId"));
                        }
                        index++;
                     }
                     assertEquals(INSTANTS.size(), index);
                  }
               }
            }
         }
      }



      /************************************************************************
       ** Existing passwords must remain byte-for-byte identical across startup.
       ************************************************************************/
      private static Map<String, String> passwordHashes(Connection connection) throws Exception
      {
         Map<String, String> hashes = new LinkedHashMap<>();
         try(var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT username, passwordHash FROM demoUser ORDER BY username"))
         {
            while(rows.next())
            {
               hashes.put(rows.getString(1), rows.getString(2));
            }
         }
         assertEquals(2, hashes.size());
         return hashes;
      }



      /************************************************************************
       ** Recreate legacy column types before writing explicit offset timestamps.
       ************************************************************************/
      private static void seedLegacyZonedRows(Connection connection) throws Exception
      {
         try(var statement = connection.createStatement())
         {
            for(var entry : TIMESTAMPS.entrySet())
            {
               for(String field : entry.getValue())
               {
                  statement.execute("ALTER TABLE " + entry.getKey() + " ALTER COLUMN " + field + " TIMESTAMP WITH TIME ZONE");
               }
            }
            for(int index = 0; index < INSTANTS.size(); index++)
            {
               String id = "fixture-" + index;
               statement.execute("INSERT INTO mobileDevice (id, installationId, shortName, displayName, status, enrolledBy, enrolledAt) VALUES ('" + id + "', '" + id + "', 'scanner-" + index + "', 'Original name " + index + "', 'APPROVED', 'scanner', CURRENT_TIMESTAMP)");
               statement.execute("INSERT INTO mobileScan (id, deviceId, eventId, userId, appId, processName, barcode, capturedAt, receivedAt) VALUES ('" + id + "', '" + id + "', 'event-" + index + "', 'scanner', 'scan', 'capture', '000123-raw-" + index + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
               statement.execute("INSERT INTO mobileSession (id, deviceId, userId, status, startedAt, lastSeenAt) VALUES ('" + id + "', '" + id + "', 'scanner', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
               for(var entry : TIMESTAMPS.entrySet())
               {
                  for(String field : entry.getValue())
                  {
                     try(var update = connection.prepareStatement("UPDATE " + entry.getKey() + " SET " + field + " = ? WHERE id = ?"))
                     {
                        update.setObject(1, INSTANTS.get(index));
                        update.setString(2, id);
                        assertEquals(1, update.executeUpdate());
                     }
                  }
               }
            }
         }
      }
   }
}
