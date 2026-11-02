/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo.demo;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.documentgeneration.recordfiles.RetainedAssetReader;
import com.kingsrook.qbits.documentgeneration.renderer.IsolatedRendererBridge;
import com.kingsrook.qbits.documentgeneration.renderer.TrustedLocalDockerExchange;
import org.apache.logging.log4j.LogManager;


/*******************************************************************************
 ** Explicit TRUSTED LOCAL DEVELOPMENT Echo host. Same composition, accounts,
 ** permissions and loopback server as EchoDemo, but the renderer is the owner's
 ** production bridge over the owner's supported trusted-local Docker exchange.
 ** That exchange is not a production sandbox qualification. It is a separate
 ** main: the production EchoDemo never selects it and nothing here falls back to
 ** or from the production renderer.
 *******************************************************************************/
public final class TrustedLocalEchoDemo
{
   static final String OWNER_ID = "echo-local";
   static final String STATE_DIRECTORY = "renderer-state";



   /***************************************************************************
    ** Static launcher only.
    ***************************************************************************/
   private TrustedLocalEchoDemo()
   {
   }



   /***************************************************************************
    ** The owner's bridge over the owner's trusted-local exchange for an exact
    ** local image id. Its durable journal lives in a private directory under the
    ** demo root, which must be fresh for each runtime. Expired leases left by a
    ** killed process are reclaimed once, explicitly, before the first render.
    ***************************************************************************/
   static GenerationPorts.IsolatedRenderer renderer(EchoDemoSettings.LocalRenderer local, Path demoRoot) throws Exception
   {
      TrustedLocalDockerExchange exchange = new TrustedLocalDockerExchange(TrustedLocalDockerExchange.configuration(local.docker(), local.imageId(),
         stateDirectory(demoRoot), OWNER_ID, local.deadline(), local.maximumBytes(), local.maximumPages(), 1));
      List<String> unreclaimed = exchange.reapExpired();
      if(!unreclaimed.isEmpty())
      {
         LogManager.getLogger(TrustedLocalEchoDemo.class).warn("Trusted local renderer left {} expired lease(s) unreclaimed", unreclaimed.size());
      }
      return new IsolatedRendererBridge(exchange.limits(), new RetainedAssetReader(), exchange);
   }



   /***************************************************************************
    ** An existing absolute, non-symbolic-link directory private to this runtime.
    ***************************************************************************/
   static Path stateDirectory(Path demoRoot) throws IOException
   {
      Path directory = demoRoot.resolve(STATE_DIRECTORY);
      if(!Files.isDirectory(directory))
      {
         Files.createDirectory(directory);
      }
      if(!directory.isAbsolute() || Files.isSymbolicLink(directory))
      {
         throw new IllegalArgumentException("The renderer state directory must be an existing absolute directory that is not a symbolic link");
      }
      return directory;
   }



   /***************************************************************************
    ** Secrets enter through the environment only. Requires the explicit opt-in
    ** ECHO_LOCAL_RENDERER=TRUSTED_LOCAL_DEVELOPMENT plus the shared EchoDemo
    ** settings; a missing opt-in or a bad image or executable stops startup.
    ***************************************************************************/
   public static void main(String[] args) throws Exception
   {
      System.setProperty("qqq.logger.logSessionId.disabled", "true");
      Map<String, String> environment = System.getenv();
      EchoDemoSettings.LocalRenderer local = EchoDemoSettings.LocalRenderer.fromEnvironment(environment);
      EchoDemoSettings settings = EchoDemoSettings.fromLocalEnvironment(environment, args.length == 0 ? null : args[0]);
      EchoDemo.launch(new EchoDemo(settings, renderer(local, settings.root())), settings, "trusted-local-development");
   }
}
