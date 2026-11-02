/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Deployment inputs are refused before any database, server or renderer exists.
 *******************************************************************************/
class EchoDemoSettingsTest
{
   private static final String TOKEN = "0123456789abcdef0123456789abcdef-secret";

   @TempDir
   Path root;



   /***************************************************************************
    ** Everything but the renderer.
    ***************************************************************************/
   private Map<String, String> environment()
   {
      Map<String, String> environment = new HashMap<>();
      environment.put("ECHO_DEMO_ROOT", root.toString());
      environment.put("ECHO_DEMO_PORT", "48123");
      environment.put("ECHO_PRINTING_TENANT", "echo-demo");
      environment.put("ECHO_PRINTING_ADMIN_TOKEN", TOKEN);
      return environment;
   }



   /***************************************************************************
    ** Without the owner's cold bridge configuration there is no renderer, and
    ** the message names the variables rather than any configured value.
    ***************************************************************************/
   @Test
   void missingRendererConfigurationIsRefusedWithoutEchoingValues()
   {
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.fromEnvironment(environment(), null));
      assertTrue(failure.getMessage().contains("ECHO_RENDERER_"));
      assertFalse(failure.getMessage().contains(TOKEN));
   }



   /***************************************************************************
    ** A mutable image tag is not a digest pin and is refused by the owner record.
    ***************************************************************************/
   @Test
   void mutableImageIsRefused()
   {
      Map<String, String> environment = environment();
      environment.put("ECHO_RENDERER_DOCKER", root.resolve("docker").toString());
      environment.put("ECHO_RENDERER_IMAGE", "renderer:latest");
      assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.fromEnvironment(environment, null));
   }



   /***************************************************************************
    ** Relative roots, protected ports and weak tokens never reach a running host.
    ***************************************************************************/
   @Test
   void unsafeInputsAreRefused()
   {
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(Path.of("relative"), 48123, "t", TOKEN, false, null, null));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 18081, "t", TOKEN, false, null, null));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 80, "t", TOKEN, false, null, null));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 48123, "t", "short", false, null, null));
      assertThrows(IllegalArgumentException.class, () -> new EchoDemoSettings(root, 48123, "bad tenant", TOKEN, false, null, null));
   }



   /***************************************************************************
    ** Settings never render the administrator token.
    ***************************************************************************/
   @Test
   void tokenIsNotRendered()
   {
      EchoDemoSettings settings = new EchoDemoSettings(root, 48123, "t", TOKEN, false, null, null);
      assertFalse(settings.toString().contains(TOKEN));
   }



   /***************************************************************************
    ** Scanner grants are exactly the owner-required operator set; configure and
    ** publish belong only to the admin additions, and no administration name
    ** appears anywhere.
    ***************************************************************************/
   @Test
   void permissionSetsAreLeastPrivilege()
   {
      assertEquals(List.of("echo.use", "printing_operator", "document-generation.view", "document-generation.generate", "document-generation.print",
         "record-files.view", "record-files.manage", "echoScan.attach"), EchoDemoAuthentication.SCANNER);
      assertEquals(List.of("printing_admin", "document-generation.configure", "document-generation.publish"), EchoDemoAuthentication.ADMIN_ADDITIONS);
      assertFalse(EchoDemoAuthentication.SCANNER.stream().anyMatch(name -> name.endsWith(".configure") || name.endsWith(".publish") || name.endsWith("admin")));
   }
}
