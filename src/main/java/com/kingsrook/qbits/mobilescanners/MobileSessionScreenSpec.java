/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Bounded app-only screen frame upload for an active administrator view lease.
 *******************************************************************************/
public class MobileSessionScreenSpec extends MobileDeviceJsonSpec
{
   /***************************************************************************
    ** Upload never accepts a target session or external URL.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.POST).withPath(MobileScannersUiApi.PREFIX + "/sessions/current/screen")
         .withOperationId("mobileScannersUploadSessionScreen").withShortSummary("Upload an app screen for a live viewer")
         .withLongDescription("Requires the current approved device session, screenAvailable=true and a mode-matched active viewer lease. Default APP_CONTENT excludes login/password/OS/other-app pixels. Optional DEVICE_SCREEN requires operator system consent for this session. JPEG decoded size at most 262144 bytes, dimensions at most 640x1280, JSON at most 368640 bytes. Sequence is scoped to leaseId. Frames are ephemeral and cleared on unavailable capture, mode change, expiry or session end. A 409 asks the client to refresh heartbeat sequence/rate state.");
   }



   /***************************************************************************
    ** No payload is returned on success.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return null;
   }



   /***************************************************************************
    ** Typed bounded JPEG document.
    ***************************************************************************/
   @Override
   protected Schema defineRequestBodySchema()
   {
      return new Schema().withRefToSchema("MobileScannersScreenRequestV1");
   }



   /***************************************************************************
    ** The sole larger JSON operation has a strict total byte limit.
    ***************************************************************************/
   @Override
   protected int maximumBodyBytes()
   {
      return 368640;
   }



   /***************************************************************************
    ** Match runtime's empty success response in the generated contract.
    ***************************************************************************/
   @Override
   public BasicResponse defineBasicSuccessResponse()
   {
      return new BasicResponse(HttpStatus.NO_CONTENT, "Current frame accepted", null);
   }



   /***************************************************************************
    ** Avoid serializing image data or a fabricated JSON success body.
    ***************************************************************************/
   @Override
   public void handleOutput(Context context, JsonOutput output)
   {
      context.status(HttpStatus.NO_CONTENT).result("");
   }



   /***************************************************************************
    ** Permission, bounds and sequence are validated before retaining a frame.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      MobileSessionService.upload(input.getBody());
      return null;
   }
}
