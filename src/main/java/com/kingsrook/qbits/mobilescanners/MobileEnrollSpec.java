/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Authenticated enrollment issues one private device credential exactly once.
 *******************************************************************************/
public class MobileEnrollSpec extends MobileJsonSpec
{
   /***************************************************************************
    ** Stable enrollment operation.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.POST).withPath(MobileScannersUiApi.PREFIX + "/devices/enroll")
         .withOperationId("mobileScannersEnrollDevice").withShortSummary("Enroll a handheld")
         .withLongDescription("Requires a QQQ user session with mobile.scan permission. Returns a secret once; store privately and never log. Device remains PENDING until an administrator approves it. Existing installation IDs return 409 without rotating credentials.");
   }



   /***************************************************************************
    ** Versioned enrollment response.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersEnrollmentV1";
   }



   /***************************************************************************
    ** Versioned bounded enrollment input.
    ***************************************************************************/
   @Override
   protected Schema defineRequestBodySchema()
   {
      return new Schema().withRefToSchema("MobileScannersEnrollRequestV1");
   }



   /***************************************************************************
    ** New enrollment creates a device.
    ***************************************************************************/
   @Override
   public BasicResponse defineBasicSuccessResponse()
   {
      return new BasicResponse(HttpStatus.CREATED, "Pending device and one-time credential", getResponseSchemaName());
   }



   /***************************************************************************
    ** Preserve the 1.0 character limit for international device descriptions.
    ***************************************************************************/
   @Override
   protected int maximumBodyBytes()
   {
      return 16384;
   }



   /***************************************************************************
    ** A bounded UTF-8 request must also meet the existing character contract.
    ***************************************************************************/
   @Override
   public JsonInput buildInput(Context context) throws Exception
   {
      JsonInput input = super.buildInput(context);
      if(context.body().length() > 4096)
      {
         throw new QBadRequestException("The enrollment request exceeds 4096 characters.");
      }
      return input;
   }



   /***************************************************************************
    ** Runtime status matches the published contract.
    ***************************************************************************/
   @Override
   public void handleOutput(Context context, JsonOutput output) throws Exception
   {
      context.status(HttpStatus.CREATED);
      super.handleOutput(context, output);
   }



   /***************************************************************************
    ** Use the existing permission-checked QQQ service and tables.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return MobileService.enroll(input.getBody());
   }
}
