/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** Public appearance data available before authentication.
 *******************************************************************************/
public class MobileBrandingSpec extends MobileJsonSpec
{
   /***************************************************************************
    ** Public, stable semantic operation.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.GET).withPath(MobileScannersUiApi.PREFIX + "/branding")
         .withOperationId("mobileScannersBranding").withShortSummary("Read customer branding")
         .withLongDescription("Public appearance data; ignores stale user and device credentials. Never includes private configuration.");
   }



   /***************************************************************************
    ** Login screens also need appearance data.
    ***************************************************************************/
   @Override
   public boolean isSecured()
   {
      return false;
   }



   /***************************************************************************
    ** Typed public response.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileScannersBrandingV1";
   }



   /***************************************************************************
    ** Publish the complete extension vocabulary once.
    ***************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      return MobileScannersSchemas.components();
   }



   /***************************************************************************
    ** Only explicitly allowlisted branding properties leave the server.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input)
   {
      MobileScannersQBitConfig config = QContext.getQInstance().getQBits().values().stream().map(bit -> bit.getConfig())
         .filter(MobileScannersQBitConfig.class::isInstance).map(MobileScannersQBitConfig.class::cast)
         .findFirst().orElseGet(MobileScannersQBitConfig::new);
      Map<String, String> response = new LinkedHashMap<>();
      response.put("displayName", config.getDisplayName());
      response.put("primaryColor", config.getPrimaryColor());
      if(config.getLogoUrl() != null)
      {
         response.put("logoUrl", config.getLogoUrl());
      }
      return response;
   }
}
