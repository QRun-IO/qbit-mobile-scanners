/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;


/*******************************************************************************
 ** Discoverable mobile transport and SCANNER vocabulary within the QQQ UI API.
 *******************************************************************************/
public final class MobileScannersUiApi
{
   public static final String PREFIX = "/qbit/mobile-scanners";

   /***************************************************************************
    ** Utility class.
    ***************************************************************************/
   private MobileScannersUiApi()
   {
   }

   /***************************************************************************
    ** Register the independently versioned contract with the host UI API.
    ***************************************************************************/
   public static void register(QInstance instance)
   {
      ClientChannelsMetaData.ofOrWithNew(instance).withChannel("android", "Native Android client");
      MiddlewareSpecExtensionsMetaData.ofOrWithNew(instance).withExtension(extension());
   }

   /***************************************************************************
    ** The same specs produce runtime routes, discovery and merged OpenAPI.
    ***************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return new MiddlewareSpecExtension().withName("mobile-scanners").withSemanticVersion("1.1.0")
         .withDescription("Native scanner devices, customer branding and SCANNER process widget contract.")
         .withSpec(MobileBrandingSpec.class).withSpec(MobileStatusSpec.class)
         .withSpec(MobileEnrollSpec.class).withSpec(MobileCurrentDeviceSpec.class)
         .withSpec(MobileSessionHeartbeatSpec.class).withSpec(MobileSessionEndSpec.class).withSpec(MobileSessionScreenSpec.class);
   }
}
