/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.Map;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;


/*******************************************************************************
 ** A public transport heartbeat; it does not imply user/device authorization.
 *******************************************************************************/
public class MobileStatusSpec extends MobileJsonSpec
{
   /***************************************************************************
    ** Stable heartbeat operation.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.GET).withPath(MobileScannersUiApi.PREFIX + "/status")
         .withOperationId("mobileScannersStatus").withShortSummary("Check mobile server connectivity")
         .withLongDescription("Public connectivity only. Client must separately show authentication and device approval status.");
   }

   /***************************************************************************
    ** Connectivity remains visible before login and after credentials expire.
    ***************************************************************************/
   @Override
   public boolean isSecured()
   {
      return false;
   }

   /***************************************************************************
    ** Typed heartbeat response.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersStatusV1";
   }

   /***************************************************************************
    ** A successful response confirms this server can answer requests.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input)
   {
      return Map.of("status", "ok");
   }
}
