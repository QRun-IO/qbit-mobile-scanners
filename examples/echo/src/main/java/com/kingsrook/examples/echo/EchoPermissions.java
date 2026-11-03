/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;


/*******************************************************************************
 ** Bind approved mobile authority before QQQ captures async process context.
 ** Ordinary operators can use Echo without printer-administration permission.
 *******************************************************************************/
public final class EchoPermissions implements CustomPermissionChecker
{
   /***************************************************************************
    ** Read locks are native metadata; all writes go through the example service.
    ***************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput action, MetaDataWithPermissionRules metadata) throws QPermissionDeniedException
   {
      try
      {
         if(QContext.getQSession() == null || !QContext.getQSession().hasPermission(EchoQBitProducer.PERMISSION)
            || action instanceof InsertInput || action instanceof UpdateInput || action instanceof DeleteInput)
         {
            throw new QPermissionDeniedException("Echo access is required; direct state writes are not allowed.");
         }
         EchoConfig.current().identities.current();
      }
      catch(Exception failure)
      {
         throw new QPermissionDeniedException("Echo requires an approved scanner and current Echo access.");
      }
   }
}
