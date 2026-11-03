/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;


/*******************************************************************************
 ** Current device state is visible with authenticated user and device headers.
 *******************************************************************************/
public class MobileCurrentDeviceSpec extends MobileDeviceJsonSpec
{
   /***************************************************************************
    ** Stable device-status operation.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.GET).withPath(MobileScannersUiApi.PREFIX + "/devices/current")
         .withOperationId("mobileScannersCurrentDevice").withShortSummary("Read the authenticated device state")
         .withLongDescription("Requires QQQ user session with mobile.scan and both device headers. PENDING and REVOKED devices may read their state but cannot run scanner processes. Headers also bind device identity on core metadata/widget/process requests; never send them to another origin or in process values.");
   }



   /***************************************************************************
    ** Versioned safe device response.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersDeviceV1";
   }



   /***************************************************************************
    ** Request-scoped identity is established by the credential binding filter.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      String id = MobilePermissions.REQUEST_DEVICE.get();
      QRecord device = id == null ? null : MobileService.get(MobileScannersQBitProducer.DEVICE, id);
      if(device == null)
      {
         throw new QPermissionDeniedException("Invalid device credential.");
      }
      return Map.of("deviceId", id, "status", device.getValueString("status"), "shortName", device.getValueString("shortName"),
         "description", java.util.Objects.toString(device.getValueString("description"), ""),
         "displayName", device.getValueString("displayName") == null ? "" : device.getValueString("displayName"));
   }
}
