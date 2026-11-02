/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


/*******************************************************************************
 ** Immutable object files under one explicit private root, using standard Files
 ** primitives only. Owner keys are opaque: they are validated and hashed into the
 ** file name, so no key can address another path, alias another object or climb
 ** out of the root. Objects are written to a temporary file in their own folder
 ** and linked into place, so a reader never sees a partial object and an existing
 ** object is never replaced.
 *******************************************************************************/
final class PrivateObjectFiles
{
   private static final ObjectMapper JSON = new ObjectMapper();
   private static final int MAXIMUM_KEY = 2048;
   private final Path root;



   /***************************************************************************
    ** A stored object: its bytes' file, size and the owner's metadata.
    ***************************************************************************/
   record Stored(Path file, long size, Map<String, String> metadata)
   {
   }



   /***************************************************************************
    ** The root must be an existing absolute directory that is not a link.
    ***************************************************************************/
   PrivateObjectFiles(Path root)
   {
      if(root == null || !root.isAbsolute() || !Files.isDirectory(root) || Files.isSymbolicLink(root))
      {
         throw new IllegalArgumentException("Object storage needs an existing absolute private directory that is not a symbolic link");
      }
      this.root = root;
   }



   /***************************************************************************
    ** Hash the validated bucket and key into one fixed-shape file name.
    ***************************************************************************/
   Path file(String bucket, String key)
   {
      if(bucket == null || !bucket.matches("[A-Za-z0-9._-]{1,64}") || key == null || key.isEmpty() || key.length() > MAXIMUM_KEY)
      {
         throw new IllegalArgumentException("Object bucket or key is outside policy");
      }
      try
      {
         String name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((bucket + "\n" + key).getBytes(StandardCharsets.UTF_8)));
         return root.resolve(bucket).resolve(name.substring(0, 2)).resolve(name);
      }
      catch(java.security.NoSuchAlgorithmException failure)
      {
         throw new IllegalStateException(failure);
      }
   }



   /***************************************************************************
    ** Create one object, refusing an existing one and anything over the limit.
    ** Metadata is made durable first, so an object that exists has its metadata.
    ***************************************************************************/
   void create(String bucket, String key, InputStream content, long maximumBytes, Object metadata) throws IOException
   {
      Path target = file(bucket, key);
      Path folder = target.getParent();
      Files.createDirectories(folder);
      if(Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS))
      {
         throw new FileAlreadyExistsException("Object already exists");
      }
      Path temporary = Files.createTempFile(folder, ".incoming-", ".part");
      Path sidecar = Files.createTempFile(folder, ".incoming-", ".meta");
      try
      {
         long total = 0;
         byte[] buffer = new byte[8192];
         try(OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.WRITE))
         {
            for(int read = content.read(buffer); read >= 0; read = content.read(buffer))
            {
               total += read;
               if(total > maximumBytes)
               {
                  throw new IOException("Object exceeds the storage limit");
               }
               out.write(buffer, 0, read);
            }
         }
         JSON.writeValue(sidecar.toFile(), metadata == null ? Map.of() : metadata);
         Files.move(sidecar, metadataFile(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         Files.createLink(target, temporary);
      }
      finally
      {
         Files.deleteIfExists(temporary);
         Files.deleteIfExists(sidecar);
      }
   }



   /***************************************************************************
    ** Read an object's identity; absent means empty. Links are refused.
    ***************************************************************************/
   Optional<Stored> find(String bucket, String key) throws IOException
   {
      Path target = file(bucket, key);
      if(!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS))
      {
         return Optional.empty();
      }
      if(!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(metadataFile(target)))
      {
         throw new IOException("Object path is not a plain file");
      }
      try
      {
         Map<String, String> metadata = JSON.readValue(metadataFile(target).toFile(), new TypeReference<Map<String, String>>() {});
         return Optional.of(new Stored(target, Files.size(target), metadata));
      }
      catch(NoSuchFileException failure)
      {
         throw new IOException("Object metadata is missing", failure);
      }
   }



   /***************************************************************************
    ** Remove one object and its metadata; an absent object is not an error.
    ***************************************************************************/
   void delete(String bucket, String key) throws IOException
   {
      Path target = file(bucket, key);
      Files.deleteIfExists(target);
      Files.deleteIfExists(metadataFile(target));
   }



   /***************************************************************************
    ** The metadata sidecar beside an object file.
    ***************************************************************************/
   private static Path metadataFile(Path target)
   {
      return target.resolveSibling(target.getFileName() + ".json");
   }
}
