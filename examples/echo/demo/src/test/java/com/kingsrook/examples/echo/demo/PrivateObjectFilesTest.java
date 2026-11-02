/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The private object files keep every object under the root, whole and immutable.
 *******************************************************************************/
class PrivateObjectFilesTest
{
   @TempDir
   Path root;



   /***************************************************************************
    ** Small fixed content as a stream.
    ***************************************************************************/
   private static ByteArrayInputStream bytes(String value)
   {
      return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }



   /***************************************************************************
    ** Bytes and owner metadata come back exactly; no temporary files remain.
    ***************************************************************************/
   @Test
   void roundTripKeepsBytesAndMetadataAndLeavesNoTemporaryFiles() throws IOException
   {
      PrivateObjectFiles files = new PrivateObjectFiles(root);
      files.create("canonical", "owner/key=1", bytes("^XA^XZ"), 1024, Map.of("sha256", "abc"));
      var stored = files.find("canonical", "owner/key=1").orElseThrow();
      assertEquals(6, stored.size());
      assertArrayEquals("^XA^XZ".getBytes(java.nio.charset.StandardCharsets.UTF_8), Files.readAllBytes(stored.file()));
      assertEquals(Map.of("sha256", "abc"), stored.metadata());
      try(Stream<Path> all = Files.walk(root))
      {
         assertFalse(all.anyMatch(path -> path.getFileName().toString().startsWith(".incoming-")));
      }
   }



   /***************************************************************************
    ** An existing object is never replaced, even by identical bytes.
    ***************************************************************************/
   @Test
   void existingObjectIsNeverReplaced() throws IOException
   {
      PrivateObjectFiles files = new PrivateObjectFiles(root);
      files.create("canonical", "k", bytes("one"), 1024, Map.of());
      assertThrows(FileAlreadyExistsException.class, () -> files.create("canonical", "k", bytes("two"), 1024, Map.of()));
      assertEquals(3, files.find("canonical", "k").orElseThrow().size());
   }



   /***************************************************************************
    ** Oversize content is refused and leaves no object behind.
    ***************************************************************************/
   @Test
   void oversizeContentIsRefusedWithoutAnObject() throws IOException
   {
      PrivateObjectFiles files = new PrivateObjectFiles(root);
      assertThrows(IOException.class, () -> files.create("canonical", "big", bytes("0123456789"), 5, Map.of()));
      assertTrue(files.find("canonical", "big").isEmpty());
   }



   /***************************************************************************
    ** Keys are hashed, so traversal text cannot leave the root or alias another
    ** object, and different buckets never share a key.
    ***************************************************************************/
   @Test
   void keysCannotTraverseOrAlias() throws IOException
   {
      PrivateObjectFiles files = new PrivateObjectFiles(root);
      Path hostile = files.file("canonical", "../../outside\\..\\x");
      assertTrue(hostile.normalize().startsWith(root));
      assertNotEquals(files.file("canonical", "k"), files.file("staging", "k"));
      assertNotEquals(files.file("canonical", "a/b"), files.file("canonical", "a//b"));
      assertThrows(IllegalArgumentException.class, () -> files.file("../canonical", "k"));
      assertThrows(IllegalArgumentException.class, () -> files.file("canonical", ""));
      assertThrows(IllegalArgumentException.class, () -> files.file("canonical", "x".repeat(2049)));
   }



   /***************************************************************************
    ** Delete removes the object and its metadata; absent is not an error.
    ***************************************************************************/
   @Test
   void deleteRemovesObjectAndMetadata() throws IOException
   {
      PrivateObjectFiles files = new PrivateObjectFiles(root);
      files.create("canonical", "k", bytes("one"), 1024, Map.of());
      files.delete("canonical", "k");
      files.delete("canonical", "k");
      assertTrue(files.find("canonical", "k").isEmpty());
   }



   /***************************************************************************
    ** The root itself must be an existing absolute directory.
    ***************************************************************************/
   @Test
   void rootMustBeAnExistingAbsoluteDirectory()
   {
      assertThrows(IllegalArgumentException.class, () -> new PrivateObjectFiles(Path.of("relative")));
      assertThrows(IllegalArgumentException.class, () -> new PrivateObjectFiles(root.resolve("missing")));
   }
}
