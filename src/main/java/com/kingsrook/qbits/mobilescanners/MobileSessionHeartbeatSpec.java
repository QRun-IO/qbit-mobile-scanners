/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** Current sign-in activity and server-controlled screen capture demand.
 *******************************************************************************/
public class MobileSessionHeartbeatSpec extends MobileDeviceJsonSpec
{
   /***************************************************************************
    ** Bind identity from QQQ authentication and verified device headers only.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.POST).withPath(MobileScannersUiApi.PREFIX + "/sessions/heartbeat")
         .withOperationId("mobileScannersSessionHeartbeat").withShortSummary("Report current handheld session activity")
         .withLongDescription("Binds the current QQQ sign-in to the verified device. Nullable display metadata never grants permissions. Ended sign-ins return 401 and cannot start another mobile session. screenAvailable=false immediately removes the retained frame. Lease duration is expiresAt minus serverTime, not the device clock.");
   }



   /***************************************************************************
    ** Additive 1.1 response.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersHeartbeatV1";
   }



   /***************************************************************************
    ** Small, bounded activity document.
    ***************************************************************************/
   @Override
   protected Schema defineRequestBodySchema()
   {
      return new Schema().withRefToSchema("MobileScannersHeartbeatRequestV1");
   }



   /***************************************************************************
    ** Server owns session and user/device identity.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return MobileSessionService.heartbeat(input.getBody());
   }
}
