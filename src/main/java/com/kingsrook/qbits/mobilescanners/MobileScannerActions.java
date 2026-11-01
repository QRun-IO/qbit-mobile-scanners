/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Persist one scan from a registered backend process using verified authority.
 *******************************************************************************/
public final class MobileScannerActions
{
   /***************************************************************************
    ** Static process action; no independent identity or event framework.
    ***************************************************************************/
   private MobileScannerActions()
   {
   }



   /***************************************************************************
    ** Return immutable accepted context; consume transient inputs before reuse.
    ** Host permission checkers bind with captureCurrent before async dispatch.
    ***************************************************************************/
   public static Map<String, Serializable> capture(RunBackendStepInput input) throws QException
   {
      return capture(input, MobileScannersQBitProducer.APP);
   }



   /***************************************************************************
    ** Attribute host-owned actions to a configured app, never caller values.
    ***************************************************************************/
   public static Map<String, Serializable> capture(RunBackendStepInput input, String appName) throws QException
   {
      if(input.getProcess() == null || appName == null || appName.isBlank() || QContext.getQInstance().getApp(appName) == null)
      {
         throw new QException("A registered scanner process and application are required.");
      }
      if((Boolean.TRUE.equals(input.getValue("scanSaved")) || input.getValue("acknowledgedEventId") != null)
         && !(input.getValue("scanContext") instanceof Map<?, ?>))
      {
         throw new QException("Restart the scanner process to use typed scan context. Leave or cancel this process, then reopen it.");
      }
      Map<String, Serializable> submitted = new HashMap<>(input.getValues());
      input.getValues().keySet().removeAll(List.of("barcode", "symbology", "scanSource", "eventId", "capturedAt", "receivedAt",
         "scanContext", "scanSaved", "scanId", "acknowledgedEventId", "userId", "deviceId", "appId", "processName"));
      if(MobileDeviceAuthorization.captureCurrent().isEmpty())
      {
         throw new QPermissionDeniedException("An approved device credential is required.");
      }
      QRecord saved = MobileService.capture(submitted, input.getProcess().getName(), appName);
      Map<String, Serializable> context = new HashMap<>();
      for(String field : List.of("barcode", "symbology", "scanSource", "eventId", "deviceId", "userId", "appId", "processName"))
      {
         context.put(field, saved.getValueString(field));
      }
      context.put("capturedAt", saved.getValueInstant("capturedAt").toString());
      context.put("receivedAt", saved.getValueInstant("receivedAt").toString());
      context.put("scanId", saved.getValueString("id"));
      return Map.copyOf(context);
   }
}
