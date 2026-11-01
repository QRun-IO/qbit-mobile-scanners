/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Deterministic published contract, compatibility baseline and registration.
 *******************************************************************************/
class MobileScannersApiContractTest
{
   /***************************************************************************
    ** Regeneration is explicit; ordinary builds detect drift and breaking edits.
    ***************************************************************************/
   @Test
   void committedContractMatchesRegisteredSpecs() throws Exception
   {
      var extension = MobileScannersUiApi.extension();
      var version = new MiddlewareVersionV1();
      String generated = UiApiContract.generateExtensionYaml(version, extension);
      Path contract = Path.of("src/main/resources/openapi/qqq-v1/mobile-scanners.yaml");
      Path baseline = Path.of("src/test/resources/openapi/mobile-scanners-1.0.0.yaml");
      if(Boolean.getBoolean("mobile.openapi.update"))
      {
         Files.createDirectories(contract.getParent());
         Files.writeString(contract, generated);
      }
      if(Boolean.getBoolean("mobile.openapi.initializeBaseline") && !Files.exists(baseline))
      {
         Files.createDirectories(baseline.getParent());
         Files.writeString(baseline, generated);
      }
      assertEquals(java.util.List.of(), UiApiContract.check(generated, Files.readString(contract), Files.readString(baseline), 1,
         "mvnw.cmd -Dtest=MobileScannersApiContractTest -Dmobile.openapi.update=true test"));
      Path sessionBaseline = Path.of("src/test/resources/openapi/mobile-scanners-1.1.0.yaml");
      if(Boolean.getBoolean("mobile.openapi.initializeSessionBaseline") && !Files.exists(sessionBaseline))
      {
         Files.writeString(sessionBaseline, generated);
      }
      assertEquals(java.util.List.of(), UiApiContract.check(generated, Files.readString(contract), Files.readString(sessionBaseline), 1, "Regenerate the current extension contract."));
      var model = version.generateExtensionOpenAPIModel("qqq", extension);
      assertEquals(Set.of("/qqq/v1/qbit/mobile-scanners/branding", "/qqq/v1/qbit/mobile-scanners/status",
         "/qqq/v1/qbit/mobile-scanners/devices/enroll", "/qqq/v1/qbit/mobile-scanners/devices/current",
         "/qqq/v1/qbit/mobile-scanners/sessions/heartbeat", "/qqq/v1/qbit/mobile-scanners/sessions/current/end",
         "/qqq/v1/qbit/mobile-scanners/sessions/current/screen"), model.getPaths().keySet());
      assertTrue(model.getComponents().getSchemas().keySet().stream().allMatch(name -> name.startsWith("MobileScanners") || name.equals("BasicErrorResponseV1")));
      for(String schema : MobileScannersSchemas.components().keySet())
      {
         assertTrue(model.getComponents().getSchemas().containsKey(schema), schema);
      }
      assertEquals(7, extension.getSpecs().size());
      assertEquals("1.2.0", extension.getSemanticVersion());
      assertTrue(generated.contains("mobileScannersEnrollDevice"));
      assertTrue(generated.contains("X-QQQ-Device-Token"));
      assertTrue(generated.contains("acknowledgedEventId"));
   }

   /***************************************************************************
    ** Re-registering replaces the same extension, never adds duplicate routes.
    ***************************************************************************/
   @Test
   void registrationIsStable()
   {
      QInstance instance = new QInstance();
      MobileScannersUiApi.register(instance);
      MobileScannersUiApi.register(instance);
      assertEquals(Set.of("mobile-scanners"), MiddlewareSpecExtensionsMetaData.of(instance).getExtensions().keySet());
      assertEquals("/qbit/mobile-scanners", MobileScannersUiApi.extension().getEffectivePathPrefix());
   }
}
