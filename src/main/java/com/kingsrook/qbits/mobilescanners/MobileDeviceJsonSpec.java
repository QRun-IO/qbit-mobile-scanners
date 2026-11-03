/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import com.kingsrook.qqq.openapi.model.In;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Shared device-header contract for authenticated mobile operations.
 *******************************************************************************/
abstract class MobileDeviceJsonSpec extends MobileJsonSpec
{
   /***************************************************************************
    ** Document both credential headers without examples containing secrets.
    ***************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return List.of(new Parameter().withName(MobileScannersRouteProvider.DEVICE_HEADER).withIn(In.HEADER).withRequired(true)
            .withDescription("Enrolled installation UUID").withSchema(new Schema().withType(Type.STRING).withFormat("uuid").withMaxLength(36)),
         new Parameter().withName(MobileScannersRouteProvider.TOKEN_HEADER).withIn(In.HEADER).withRequired(true)
            .withDescription("Private one-time issued device credential; never log").withSchema(new Schema().withType(Type.STRING).withMaxLength(128)));
   }

}
