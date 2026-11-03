/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.util.Optional;
import com.kingsrook.qbits.objectstorage.api.ObjectStream;
import com.kingsrook.qbits.objectstorage.api.PutRequest;
import com.kingsrook.qbits.objectstorage.api.StoredObject;
import com.kingsrook.qbits.recordfiles.ingestion.UploadChunkStorage;


/*******************************************************************************
 ** Record Files' temporary upload chunks in this demo's private object directory,
 ** under a dedicated staging namespace that retained originals never use. It adds
 ** no policy: the owner's coordinator authorizes, verifies and cleans every chunk,
 ** and keys are derived by the owner, never taken from a request.
 *******************************************************************************/
final class LocalStagingChunkStorage extends UploadChunkStorage
{
   static final String ALIAS = "echo-photo-staging";

   private final LocalFilesystemAttachmentStorage files = new LocalFilesystemAttachmentStorage();



   /***************************************************************************
    ** The dedicated staging alias; the private root is the one the host configured.
    ***************************************************************************/
   LocalStagingChunkStorage()
   {
      super(ALIAS);
   }



   /***************************************************************************
    ** Conditional create; an existing chunk is never replaced.
    ***************************************************************************/
   @Override
   protected void put(String uploadId, PutRequest request)
   {
      files.put(ALIAS, request);
   }



   /***************************************************************************
    ** Existence for the owner's recovery of an uncertain write.
    ***************************************************************************/
   @Override
   protected Optional<StoredObject> head(String uploadId, String key)
   {
      return files.head(ALIAS, key);
   }



   /***************************************************************************
    ** Reopen a staged chunk for digest verification.
    ***************************************************************************/
   @Override
   protected ObjectStream open(String uploadId, String key)
   {
      return files.open(ALIAS, key);
   }



   /***************************************************************************
    ** Remove one staged chunk after the owner's committed terminal state.
    ***************************************************************************/
   @Override
   protected void delete(String uploadId, String key)
   {
      files.delete(ALIAS, key);
   }
}
