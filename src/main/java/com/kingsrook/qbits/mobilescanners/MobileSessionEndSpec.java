/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** Operator logout history; admin termination uses an ordinary QQQ process.
 *******************************************************************************/
public class MobileSessionEndSpec extends MobileDeviceJsonSpec
{
   /***************************************************************************
    ** End only the caller's currently verified binding.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.POST).withPath(MobileScannersUiApi.PREFIX + "/sessions/current/end")
         .withOperationId("mobileScannersEndCurrentSession").withShortSummary("End this handheld sign-in")
         .withLongDescription("Ends the authenticated device session and invalidates its QQQ login. No target session ID is accepted. An already ended/expired login may return 401; the client must still clear local authentication. Device approval is unchanged.");
   }

   /***************************************************************************
    ** Safe public session result.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersSessionEndedV1";
   }

   /***************************************************************************
    ** Explicit operator reason, not a free-form secret-bearing message.
    ***************************************************************************/
   @Override
   protected Schema defineRequestBodySchema()
   {
      return new Schema().withRefToSchema("MobileScannersEndSessionRequestV1");
   }

   /***************************************************************************
    ** Durable tombstone plus existing QQQ authentication logout.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return MobileSessionService.endCurrent(input.getBody());
   }
}
