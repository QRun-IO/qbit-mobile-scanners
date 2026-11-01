/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;


/*******************************************************************************
 ** Retained source reads use the current actor's ordinary Echo permission and
 ** the table's native actor lock; reading never grants approved-device capture.
 *******************************************************************************/
public final class EchoSourceReadPermissions implements CustomPermissionChecker
{
   /***************************************************************************
    ** Metadata discovery and explicit read actions only; no saved scanner session
    ** or device identifier is reconstructed for desktop or retained-source reads.
    ***************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput action, MetaDataWithPermissionRules metadata) throws QPermissionDeniedException
   {
      var session = QContext.getQSession();
      if(session == null || session.getUser() == null || session.getUser().getIdReference() == null
         || session.getUser().getIdReference().isBlank() || !session.hasPermission(EchoQBitProducer.PERMISSION)
         || (action != null && !(action instanceof GetInput || action instanceof QueryInput || action instanceof CountInput || action instanceof AggregateInput)))
      {
         throw new QPermissionDeniedException("Current Echo access is required; retained scan sources are read-only.");
      }
   }
}
