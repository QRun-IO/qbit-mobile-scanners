/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import com.kingsrook.qbits.collaboration.attachments.AttachmentStorage;
import com.kingsrook.qbits.objectstorage.api.ObjectHeaders;
import com.kingsrook.qbits.objectstorage.api.ObjectStream;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StoredObject;


/*******************************************************************************
 ** The standard attachment owner's storage SPI over one private local directory,
 ** for this single-host demo only. It adds no policy of its own: Record Files and
 ** the attachment owner still decide quarantine, authorization, lifecycle and
 ** retention. QQQ instantiates storage by class, so the host sets the root once.
 *******************************************************************************/
public class LocalFilesystemAttachmentStorage extends AttachmentStorage
{
   static final long MAXIMUM_BYTES = 20L * 1024 * 1024;
   private static volatile PrivateObjectFiles files;



   /***************************************************************************
    ** Bind the explicit private root before the server accepts requests.
    ***************************************************************************/
   static void configure(Path root)
   {
      files = new PrivateObjectFiles(root);
   }



   /***************************************************************************
    ** The configured store, or a refusal when the host never configured one.
    ***************************************************************************/
   private static PrivateObjectFiles store()
   {
      PrivateObjectFiles configured = files;
      if(configured == null)
      {
         throw new IllegalStateException("Local attachment storage has no configured root.");
      }
      return configured;
   }



   /***************************************************************************
    ** Small fixed limit for this demo's retained originals.
    ***************************************************************************/
   @Override
   public long maxBytes(String name)
   {
      return MAXIMUM_BYTES;
   }



   /***************************************************************************
    ** Conditional create: an existing key is never replaced.
    ***************************************************************************/
   @Override
   public void put(String name, PutRequest request)
   {
      try
      {
         java.io.InputStream content = request.getBytes() == null ? request.getStream() : new java.io.ByteArrayInputStream(request.getBytes());
         try(content)
         {
            store().create(name, request.getKey(), content, MAXIMUM_BYTES, request.getMetadata());
         }
      }
      catch(IOException failure)
      {
         throw new IllegalStateException("Local object write failed.", failure);
      }
   }



   /***************************************************************************
    ** Reopen the persisted bytes with the owner's metadata.
    ***************************************************************************/
   @Override
   public ObjectStream open(String name, String key)
   {
      try
      {
         PrivateObjectFiles.Stored stored = store().find(name, key).orElseThrow(() -> new IllegalStateException("Local object is absent."));
         return new ObjectStream(Files.newInputStream(stored.file()), stored.size(), "application/octet-stream", null, Instant.now(),
            new ObjectHeaders().withUserMetadata(stored.metadata()));
      }
      catch(IOException failure)
      {
         throw new IllegalStateException("Local object read failed.", failure);
      }
   }



   /***************************************************************************
    ** Existence and size for the owner's idempotent recovery.
    ***************************************************************************/
   @Override
   public Optional<StoredObject> head(String name, String key)
   {
      try
      {
         return store().find(name, key).map(stored -> new StoredObject().withSize(stored.size()));
      }
      catch(IOException failure)
      {
         throw new IllegalStateException("Local object lookup failed.", failure);
      }
   }



   /***************************************************************************
    ** Remove one owner-requested object.
    ***************************************************************************/
   @Override
   public void delete(String name, String key)
   {
      try
      {
         store().delete(name, key);
      }
      catch(IOException failure)
      {
         throw new IllegalStateException("Local object delete failed.", failure);
      }
   }
}
