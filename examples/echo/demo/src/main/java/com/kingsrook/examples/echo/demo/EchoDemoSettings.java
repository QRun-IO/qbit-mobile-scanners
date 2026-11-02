/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.documentgeneration.renderer.RendererConfiguration;


/*******************************************************************************
 ** Deployment-owned Echo demo inputs. Every value comes from the environment (or
 ** one port argument) and nothing here has a default that could reach a real
 ** printer, renderer, tenant or account. Messages name a variable, never a value.
 *******************************************************************************/
public record EchoDemoSettings(Path root, int port, String tenant, String adminToken, boolean releaseGeneratedFiles, TemplatePin template,
   RendererConfiguration renderer)
{
   /** Local runtimes that must never be bound or shadowed by a demo. */
   private static final Set<Integer> PROTECTED_PORTS = Set.of(57832, 51631, 18081, 18086, 18443, 19443, 18447, 18448, 18547, 18549, 18593);
   private static final String LABEL = "[A-Za-z0-9._:-]{1,128}";



   /***************************************************************************
    ** The one published ZPL template for exactly one reported media identity and
    ** revision. Absent, every scan is refused with Echo's no-layout message.
    ***************************************************************************/
   public record TemplatePin(String mediaId, String mediaRevision, int templateId)
   {
      /***********************************************************************
       ** Bounded labels and a real template id.
       ***********************************************************************/
      public TemplatePin
      {
         if(mediaId == null || !mediaId.matches(LABEL) || mediaRevision == null || !mediaRevision.matches(LABEL) || templateId < 1)
         {
            throw new IllegalArgumentException("ECHO_TEMPLATE_ID, ECHO_TEMPLATE_MEDIA_ID and ECHO_TEMPLATE_MEDIA_REVISION are invalid");
         }
      }
   }



   /***************************************************************************
    ** Refuse an unsafe port, root, tenant, token or permission list up front.
    ***************************************************************************/
   public EchoDemoSettings
   {
      if(root == null || !root.isAbsolute() || !Files.isDirectory(root) || Files.isSymbolicLink(root))
      {
         throw new IllegalArgumentException("ECHO_DEMO_ROOT must be an existing absolute directory that is not a symbolic link");
      }
      if(port < 1024 || port > 65535 || PROTECTED_PORTS.contains(port))
      {
         throw new IllegalArgumentException("The Echo demo port must be a fresh unprotected port from 1024 to 65535");
      }
      if(tenant == null || !tenant.matches(LABEL))
      {
         throw new IllegalArgumentException("ECHO_PRINTING_TENANT is required and must be a bounded label");
      }
      if(adminToken == null || adminToken.length() < 32 || adminToken.length() > 1024)
      {
         throw new IllegalArgumentException("ECHO_PRINTING_ADMIN_TOKEN is required and must be 32 to 1024 characters");
      }
   }



   /***************************************************************************
    ** Parse the documented environment. The port argument, when present, wins.
    ***************************************************************************/
   public static EchoDemoSettings fromEnvironment(Map<String, String> environment, String portArgument)
   {
      return parse(environment, portArgument, renderer(environment));
   }



   /***************************************************************************
    ** Shared settings for the explicit trusted local development launcher. These
    ** settings carry no cold-bridge configuration, so the production EchoDemo
    ** constructor refuses them; the local renderer is a separate opt-in record.
    ***************************************************************************/
   public static EchoDemoSettings fromLocalEnvironment(Map<String, String> environment, String portArgument)
   {
      return parse(environment, portArgument, null);
   }



   /***************************************************************************
    ** One parser for the shared root, port, tenant, token, pin and release policy.
    ***************************************************************************/
   private static EchoDemoSettings parse(Map<String, String> environment, String portArgument, RendererConfiguration renderer)
   {
      String port = portArgument != null ? portArgument : required(environment, "ECHO_DEMO_PORT");
      TemplatePin pin = null;
      if(environment.get("ECHO_TEMPLATE_ID") != null || environment.get("ECHO_TEMPLATE_MEDIA_ID") != null || environment.get("ECHO_TEMPLATE_MEDIA_REVISION") != null)
      {
         pin = new TemplatePin(required(environment, "ECHO_TEMPLATE_MEDIA_ID"), required(environment, "ECHO_TEMPLATE_MEDIA_REVISION"),
            integer(required(environment, "ECHO_TEMPLATE_ID"), "ECHO_TEMPLATE_ID"));
      }
      return new EchoDemoSettings(Path.of(required(environment, "ECHO_DEMO_ROOT")), integer(port, "ECHO_DEMO_PORT"),
         required(environment, "ECHO_PRINTING_TENANT"), required(environment, "ECHO_PRINTING_ADMIN_TOKEN"),
         "true".equals(environment.get("ECHO_RELEASE_GENERATED_FILES")), pin, renderer);
   }



   /***************************************************************************
    ** Explicit trusted local development renderer: an absolute Docker executable
    ** and the exact local image id. It has no repository digest and no
    ** qualification id, and it is never a fallback for the production bridge.
    ***************************************************************************/
   public record LocalRenderer(Path docker, String imageId, Duration deadline, int maximumBytes, int maximumPages)
   {
      /** Fixed value of ECHO_LOCAL_RENDERER; anything else is not an opt-in. */
      static final String OPT_IN = "TRUSTED_LOCAL_DEVELOPMENT";



      /***********************************************************************
       ** Bounded values; the exchange and the owner's limits validate again.
       ***********************************************************************/
      public LocalRenderer
      {
         if(docker == null || !docker.isAbsolute() || !Files.isRegularFile(docker) || imageId == null || !imageId.matches("sha256:[a-f0-9]{64}")
            || deadline == null || deadline.compareTo(Duration.ofSeconds(1)) < 0 || deadline.compareTo(Duration.ofSeconds(30)) > 0
            || maximumBytes < 1 || maximumBytes > 100 * 1024 * 1024 || maximumPages < 1 || maximumPages > 500)
         {
            throw new IllegalArgumentException("The ECHO_LOCAL_RENDERER_* configuration is absent or invalid");
         }
      }



      /***********************************************************************
       ** Requires ECHO_LOCAL_RENDERER=TRUSTED_LOCAL_DEVELOPMENT, the absolute
       ** ECHO_LOCAL_RENDERER_DOCKER and the sha256 ECHO_LOCAL_RENDERER_IMAGE_ID.
       ***********************************************************************/
      public static LocalRenderer fromEnvironment(Map<String, String> environment)
      {
         if(!OPT_IN.equals(environment.get("ECHO_LOCAL_RENDERER")))
         {
            throw new IllegalArgumentException("Set ECHO_LOCAL_RENDERER=" + OPT_IN + " to use the trusted local development renderer");
         }
         return new LocalRenderer(Path.of(required(environment, "ECHO_LOCAL_RENDERER_DOCKER")), required(environment, "ECHO_LOCAL_RENDERER_IMAGE_ID"),
            Duration.ofSeconds(integer(environment.getOrDefault("ECHO_LOCAL_RENDERER_DEADLINE_SECONDS", "10"), "ECHO_LOCAL_RENDERER_DEADLINE_SECONDS")),
            integer(environment.getOrDefault("ECHO_RENDERER_MAXIMUM_BYTES", "1048576"), "ECHO_RENDERER_MAXIMUM_BYTES"),
            integer(environment.getOrDefault("ECHO_RENDERER_MAXIMUM_PAGES", "1"), "ECHO_RENDERER_MAXIMUM_PAGES"));
      }
   }



   /***************************************************************************
    ** The owner's cold bridge configuration, validated by the owner's record:
    ** absolute Docker executable, digest-pinned image, image id, matching
    ** qualification evidence, owner id and a private state directory.
    ***************************************************************************/
   private static RendererConfiguration renderer(Map<String, String> environment)
   {
      try
      {
         return new RendererConfiguration(Path.of(required(environment, "ECHO_RENDERER_DOCKER")), required(environment, "ECHO_RENDERER_IMAGE"),
            required(environment, "ECHO_RENDERER_IMAGE_ID"), required(environment, "ECHO_RENDERER_QUALIFICATION_ID"),
            required(environment, "ECHO_RENDERER_REQUIRED_QUALIFICATION_ID"), required(environment, "ECHO_RENDERER_OWNER_ID"),
            Path.of(required(environment, "ECHO_RENDERER_STATE_DIR")),
            Duration.ofSeconds(integer(environment.getOrDefault("ECHO_RENDERER_DEADLINE_SECONDS", "10"), "ECHO_RENDERER_DEADLINE_SECONDS")),
            integer(environment.getOrDefault("ECHO_RENDERER_MAXIMUM_BYTES", "1048576"), "ECHO_RENDERER_MAXIMUM_BYTES"),
            integer(environment.getOrDefault("ECHO_RENDERER_MAXIMUM_PAGES", "1"), "ECHO_RENDERER_MAXIMUM_PAGES"));
      }
      catch(IllegalArgumentException failure)
      {
         throw new IllegalArgumentException("The ECHO_RENDERER_* deployment configuration is absent or outside the renderer owner's policy");
      }
   }



   /***************************************************************************
    ** Required value, reported by variable name only.
    ***************************************************************************/
   private static String required(Map<String, String> environment, String name)
   {
      String value = environment.get(name);
      if(value == null || value.isBlank())
      {
         throw new IllegalArgumentException("Set " + name);
      }
      return value;
   }



   /***************************************************************************
    ** Whole-number value, reported by variable name only.
    ***************************************************************************/
   private static int integer(String value, String name)
   {
      try
      {
         return Integer.parseInt(value.trim());
      }
      catch(NumberFormatException failure)
      {
         throw new IllegalArgumentException(name + " must be a whole number");
      }
   }



   /***************************************************************************
    ** The administrator token is never rendered.
    ***************************************************************************/
   @Override
   public String toString()
   {
      return "EchoDemoSettings[port=" + port + ", tenant=" + tenant + ", secrets=redacted]";
   }
}
