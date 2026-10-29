/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Deny raw writes and keep device authentication outside process data.
 *******************************************************************************/
public class MobilePermissions implements CustomPermissionChecker
{
   public static final String SCAN = "mobile.scan";
   public static final String ADMIN = "mobile.admin";
   static final ThreadLocal<String> REQUEST_DEVICE = new ThreadLocal<>();
   static final ThreadLocal<Boolean> INTERNAL = new ThreadLocal<>();

   /***************************************************************************
    ** Clone before attaching request identity: stored sessions may be shared.
    ***************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput action, MetaDataWithPermissionRules metadata) throws QPermissionDeniedException
   {
      if(Boolean.TRUE.equals(INTERNAL.get()))
      {
         return;
      }
      boolean scanner = MobileScannersQBitProducer.CAPTURE.equals(metadata.getName())
         || MobileScannersQBitProducer.APP.equals(metadata.getName()) || MobileScannersQBitProducer.WIDGET.equals(metadata.getName());
      if(scanner)
      {
         require(SCAN);
         try
         {
            String deviceId = REQUEST_DEVICE.get();
            if(deviceId == null && QContext.getQSession() instanceof VerifiedDeviceSession verified)
            {
               deviceId = verified.deviceId();
            }
            if(deviceId == null)
            {
               throw new QPermissionDeniedException("An approved device credential is required.");
            }
            MobileService.requireApproved(deviceId);
            if(action instanceof RunProcessInput)
            {
               QContext.setQSession(new VerifiedDeviceSession(QContext.getQSession(), deviceId));
            }
         }
         catch(Exception e)
         {
            throw new QPermissionDeniedException("An approved device credential is required.");
         }
         return;
      }
      require(ADMIN);
      if(metadata instanceof QTableMetaData table)
      {
         if(MobileScannersQBitProducer.CREDENTIAL.equals(table.getName()) || action instanceof InsertInput || action instanceof UpdateInput || action instanceof DeleteInput)
         {
            throw new QPermissionDeniedException("Use the mobile device processes; direct writes and credential reads are forbidden.");
         }
      }
   }

   /***************************************************************************
    ** Explicit authenticated user and permission, even for direct backend calls.
    ***************************************************************************/
   static void require(String permission) throws QPermissionDeniedException
   {
      QSession session = QContext.getQSession();
      if(session == null || session.getUser() == null || session.getUser().getIdReference() == null || !session.hasPermission(permission))
      {
         throw new QPermissionDeniedException("This action requires " + permission + ".");
      }
   }
}
