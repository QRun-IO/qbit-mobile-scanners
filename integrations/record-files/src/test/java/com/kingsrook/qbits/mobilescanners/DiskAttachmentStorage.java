/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.collaboration.attachments.AttachmentStorage;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStream;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.recordfiles.ingestion.UploadChunkStorage;
import com.kingsrook.qbits.recordfiles.ingestion.UploadManifest;


/*******************************************************************************
 ** File-backed owner storage fixture. Not a qualified production storage driver.
 *******************************************************************************/
public class DiskAttachmentStorage extends AttachmentStorage
{
   static Path directory;
   private static final ObjectMapper JSON = new ObjectMapper();



   /***************************************************************************
    ** Hash trusted owner keys into isolated files; never resolve a supplied path.
    ***************************************************************************/
   private static Path path(String name, String key)
   {
      return directory.resolve("staging".equals(name) ? "staging" : "canonical")
         .resolve(UploadManifest.hash((name + "/" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   }



   /***************************************************************************
    ** Match the fixture's small immutable limits.
    ***************************************************************************/
   @Override
   public long maxBytes(String name)
   {
      return 20 * 1024 * 1024;
   }



   /***************************************************************************
    ** Standard attachment owner calls conditional creation for retained originals.
    ***************************************************************************/
   @Override
   public synchronized void put(String name, PutRequest request)
   {
      try
      {
         Path file = path(name, request.getKey());
         Files.createDirectories(file.getParent());
         byte[] bytes = request.getBytes() == null ? request.getStream().readAllBytes() : request.getBytes();
         Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
         JSON.writeValue(Path.of(file + ".json").toFile(), request.getMetadata());
      }
      catch(Exception failure)
      {
         throw new IllegalStateException("Fixture object write failed.", failure);
      }
   }



   /***************************************************************************
    ** Reopen actual persisted bytes across fresh storage adapter instances.
    ***************************************************************************/
   @Override
   public ObjectStream open(String name, String key)
   {
      try
      {
         Path file = path(name, key);
         java.util.Map<String, String> metadata = JSON.readValue(Path.of(file + ".json").toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
         long size = Files.size(file);
         ObjectHeaders headers = new ObjectHeaders().withUserMetadata(metadata);
         Instant openedAt = Instant.now();
         return new ObjectStream(Files.newInputStream(file), size, "application/octet-stream", null, openedAt, headers);
      }
      catch(Exception failure)
      {
         throw new IllegalStateException("Fixture object read failed.", failure);
      }
   }



   /***************************************************************************
    ** Owner idempotent recovery observes persisted object existence.
    ***************************************************************************/
   @Override
   public Optional<StoredObject> head(String name, String key)
   {
      try
      {
         Path file = path(name, key);
         return Files.exists(file) ? Optional.of(new StoredObject().withSize(Files.size(file))) : Optional.empty();
      }
      catch(Exception failure)
      {
         throw new IllegalStateException("Fixture object head failed.", failure);
      }
   }



   /***************************************************************************
    ** Remove only a known fixture object and its metadata sidecar.
    ***************************************************************************/
   @Override
   public void delete(String name, String key)
   {
      try
      {
         Path file = path(name, key);
         Files.deleteIfExists(file);
         Files.deleteIfExists(Path.of(file + ".json"));
      }
      catch(Exception failure)
      {
         throw new IllegalStateException("Fixture object cleanup failed.", failure);
      }
   }



   /***************************************************************************
    ** Staging uses a separate owner namespace in the same isolated file fixture.
    ***************************************************************************/
   static class Staging extends UploadChunkStorage
   {
      private final DiskAttachmentStorage storage = new DiskAttachmentStorage();



      /***********************************************************************
       ** Dedicated staging alias cannot address canonical originals.
       ***********************************************************************/
      Staging()
      {
         super("mobile-fixture-staging");
      }



      /***********************************************************************
       ** Persist immutable staged chunks.
       ***********************************************************************/
      @Override
      protected void put(String uploadId, PutRequest request)
      {
         storage.put("staging", request);
      }



      /***********************************************************************
       ** Recovery sees previously persisted staged bytes.
       ***********************************************************************/
      @Override
      protected Optional<StoredObject> head(String uploadId, String key)
      {
         return storage.head("staging", key);
      }



      /***********************************************************************
       ** Reopen chunks for digest verification.
       ***********************************************************************/
      @Override
      protected ObjectStream open(String uploadId, String key)
      {
         return storage.open("staging", key);
      }



      /***********************************************************************
       ** Terminal cleanup never addresses canonical objects.
       ***********************************************************************/
      @Override
      protected void delete(String uploadId, String key)
      {
         storage.delete("staging", key);
      }
   }
}
