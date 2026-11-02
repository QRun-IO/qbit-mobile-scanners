/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** The explicit trusted local renderer is opt-in and validated; the production
 ** configuration stays mandatory. No test starts Docker or a server.
 *******************************************************************************/
class TrustedLocalRendererSettingsTest
{
   private static final String TOKEN = "0123456789abcdef0123456789abcdef-secret";
   private static final String IMAGE = "sha256:" + "a".repeat(64);

   @TempDir
   Path root;

   private Path docker;



   /***************************************************************************
    ** A regular file stands in for the Docker executable; it is never run.
    ***************************************************************************/
   @BeforeEach
   void setup() throws IOException
   {
      docker = Files.createFile(root.resolve("docker-stand-in"));
   }



   /***************************************************************************
    ** Shared settings only: no production renderer variables.
    ***************************************************************************/
   private Map<String, String> shared()
   {
      Map<String, String> environment = new HashMap<>();
      environment.put("ECHO_DEMO_ROOT", root.toString());
      environment.put("ECHO_DEMO_PORT", "48123");
      environment.put("ECHO_PRINTING_TENANT", "echo-demo");
      environment.put("ECHO_PRINTING_ADMIN_TOKEN", TOKEN);
      return environment;
   }



   /***************************************************************************
    ** The complete explicit local opt-in.
    ***************************************************************************/
   private Map<String, String> local()
   {
      Map<String, String> environment = shared();
      environment.put("ECHO_LOCAL_RENDERER", "TRUSTED_LOCAL_DEVELOPMENT");
      environment.put("ECHO_LOCAL_RENDERER_DOCKER", docker.toString());
      environment.put("ECHO_LOCAL_RENDERER_IMAGE_ID", IMAGE);
      return environment;
   }



   /***************************************************************************
    ** Local variables never satisfy the production settings, which still require
    ** the owner's cold bridge configuration; there is no automatic fallback.
    ***************************************************************************/
   @Test
   void productionConfigurationStillRequiresTheColdBridge()
   {
      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.fromEnvironment(local(), null));
      assertTrue(failure.getMessage().contains("ECHO_RENDERER_"));
      assertFalse(failure.getMessage().contains(TOKEN));
   }



   /***************************************************************************
    ** Local settings carry no cold bridge, so the production EchoDemo refuses
    ** them before any database or renderer exists.
    ***************************************************************************/
   @Test
   void localSettingsAreRefusedByTheProductionHost()
   {
      EchoDemoSettings settings = EchoDemoSettings.fromLocalEnvironment(local(), null);
      assertNull(settings.renderer());
      assertEquals(48123, settings.port());
      assertThrows(IllegalArgumentException.class, () -> new EchoDemo(settings));
   }



   /***************************************************************************
    ** The shared parser still applies the shared safety checks.
    ***************************************************************************/
   @Test
   void sharedSettingsKeepTheirValidation()
   {
      Map<String, String> weak = local();
      weak.put("ECHO_PRINTING_ADMIN_TOKEN", "short");
      assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.fromLocalEnvironment(weak, null));
      assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.fromLocalEnvironment(local(), "18081"));
   }



   /***************************************************************************
    ** A complete opt-in parses to exactly the local image and executable.
    ***************************************************************************/
   @Test
   void explicitOptInParses()
   {
      EchoDemoSettings.LocalRenderer parsed = EchoDemoSettings.LocalRenderer.fromEnvironment(local());
      assertEquals(docker, parsed.docker());
      assertEquals(IMAGE, parsed.imageId());
      assertEquals(Duration.ofSeconds(10), parsed.deadline());
   }



   /***************************************************************************
    ** Absent, wrong or case-changed opt-in never selects the local renderer.
    ***************************************************************************/
   @Test
   void missingOrWrongOptInIsRefused()
   {
      Map<String, String> missing = local();
      missing.remove("ECHO_LOCAL_RENDERER");
      assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(missing));
      for(String value : new String[] { "true", "1", "trusted_local_development", "" })
      {
         Map<String, String> wrong = local();
         wrong.put("ECHO_LOCAL_RENDERER", value);
         assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(wrong));
      }
   }



   /***************************************************************************
    ** Only an exact local sha256 image id is accepted: no tag, repository digest
    ** reference, short or uppercase id.
    ***************************************************************************/
   @Test
   void badImageIdIsRefused()
   {
      for(String image : new String[] { "renderer:latest", "repo@sha256:" + "a".repeat(64), "sha256:abc", "sha256:" + "A".repeat(64), "sha256:" + "a".repeat(65), " " })
      {
         Map<String, String> environment = local();
         environment.put("ECHO_LOCAL_RENDERER_IMAGE_ID", image);
         assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(environment));
      }
   }



   /***************************************************************************
    ** The executable must be an existing absolute regular file.
    ***************************************************************************/
   @Test
   void badExecutableIsRefused()
   {
      for(String path : new String[] { "docker", root.resolve("missing").toString(), root.toString() })
      {
         Map<String, String> environment = local();
         environment.put("ECHO_LOCAL_RENDERER_DOCKER", path);
         assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(environment));
      }
   }



   /***************************************************************************
    ** Deadline and output limits stay inside the bounded ranges.
    ***************************************************************************/
   @Test
   void deadlineAndLimitsAreBounded()
   {
      for(String deadline : new String[] { "0", "31", "-1", "ten" })
      {
         Map<String, String> environment = local();
         environment.put("ECHO_LOCAL_RENDERER_DEADLINE_SECONDS", deadline);
         assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(environment));
      }
      Map<String, String> pages = local();
      pages.put("ECHO_RENDERER_MAXIMUM_PAGES", "501");
      assertThrows(IllegalArgumentException.class, () -> EchoDemoSettings.LocalRenderer.fromEnvironment(pages));
   }



   /***************************************************************************
    ** The journal directory is created under the demo root, absolute and
    ** private; a regular file in its place is refused rather than replaced.
    ***************************************************************************/
   @Test
   void rendererStateDirectoryIsPrivateUnderTheDemoRoot() throws IOException
   {
      Path state = TrustedLocalEchoDemo.stateDirectory(root);
      assertEquals(root.resolve("renderer-state"), state);
      assertTrue(state.isAbsolute() && Files.isDirectory(state) && !Files.isSymbolicLink(state));
      assertEquals(state, TrustedLocalEchoDemo.stateDirectory(root));
      Path other = Files.createDirectory(root.resolve("other"));
      Files.createFile(other.resolve("renderer-state"));
      assertThrows(IOException.class, () -> TrustedLocalEchoDemo.stateDirectory(other));
   }
}
