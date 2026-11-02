/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.Set;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.ClientChannelsMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/*******************************************************************************
 ** Request-scoped device credentials for registered native QQQ UI APIs.
 *******************************************************************************/
public class MobileScannersRouteProvider implements QJavalinRouteProviderInterface
{
   //////////////////////////////////////////////////////////////////////////////
   // QLogger adds user identity automatically. Keep boundary diagnostics      //
   // entirely static without changing authentication context or log settings. //
   //////////////////////////////////////////////////////////////////////////////
   private static final Logger LOG = LogManager.getLogger(MobileScannersRouteProvider.class);

   public static final String DEVICE_HEADER = "X-QQQ-Device-Id";
   public static final String TOKEN_HEADER = "X-QQQ-Device-Token";
   private QInstance instance;



   /***************************************************************************
    ** Install on the same QJavalinServer that serves native QQQ routes.
    ***************************************************************************/
   public MobileScannersRouteProvider(QInstance instance)
   {
      this.instance = instance;
   }



   /***************************************************************************
    ** Support host metadata refresh.
    ***************************************************************************/
   @Override
   public void setQInstance(QInstance instance)
   {
      this.instance = instance;
   }



   /***************************************************************************
    ** Native metadata, widget and process calls carry the same device headers.
    ***************************************************************************/
   @Override
   public EndpointGroup getJavalinEndpointGroup()
   {
      return () ->
      {
         ApiBuilder.before("/qqq/*", this::bindDevice);
         ApiBuilder.after("/qqq/*", context -> MobilePermissions.REQUEST_DEVICE.remove());
      };
   }



   /***************************************************************************
    ** The shared device headers also belong to other extensions' own device
    ** registries. Only a request for another extension's route (not scanner
    ** routes, and not generic metadata/process/widget calls) from a distinct
    ** registered client channel is left to that extension's authenticator. The
    ** channel is attribution that QQQ already validated against its registry;
    ** it grants nothing here: no scanner device is bound, so scanner capture
    ** still requires its own verified credential, and Android stays verified.
    ***************************************************************************/
   private boolean isForeignExtensionClient(Context context)
   {
      String path = context.path();
      if(!path.startsWith("/qqq/v1/qbit/") || path.startsWith("/qqq/v1" + MobileScannersUiApi.PREFIX + "/"))
      {
         return false;
      }
      var session = QContext.getQSession();
      String channel = session == null ? null : session.getClientChannelName();
      return channel != null && session.getCredentialScope() == null && !"android".equals(channel) && !"web".equals(channel)
         && ClientChannelsMetaData.of(instance).isAllowedChannel(channel);
   }



   /***************************************************************************
    ** Verify credentials before native handlers; authentication remains QQQ's.
    ***************************************************************************/
   private void bindDevice(Context context) throws Exception
   {
      MobilePermissions.REQUEST_DEVICE.remove();
      if(context.path().endsWith("/widget/mobileSessionScreen") || context.path().endsWith("/widget/mobileDeviceStatus"))
      {
         context.header("Cache-Control", "no-store");
      }
      String id = context.header(DEVICE_HEADER);
      String token = context.header(TOKEN_HEADER);
      if("/qqq/v1/logout".equals(context.path()))
      {
         try
         {
            ExecutorSessionUtils.setupSession(context, instance);
            MobileSessionService.recordLogout();
         }
         catch(Exception ignored)
         {
            // Core logout must still clear an expired session if history cannot be updated.
         }
         finally
         {
            QContext.clear();
         }
         return;
      }
      if(Set.of("/qqq/v1/version", "/qqq/v1/openapi.json", "/qqq/v1/openapi.yaml", "/qqq/v1/metaData/authentication",
         "/qqq/v1/manageSession", "/qqq/v1/logout", "/qqq/v1" + MobileScannersUiApi.PREFIX + "/branding",
         "/qqq/v1" + MobileScannersUiApi.PREFIX + "/status", "/qqq/v1" + MobileScannersUiApi.PREFIX + "/devices/enroll").contains(context.path()))
      {
         return;
      }
      if(id == null && token == null && context.cookie("sessionUUID") == null && context.cookie("sessionId") == null && context.header("Authorization") == null)
      {
         return;
      }
      try
      {
         ExecutorSessionUtils.setupSession(context, instance);
         MobileSessionService.requireNotEnded();
         if(id == null && token == null)
         {
            return;
         }
         if(isForeignExtensionClient(context))
         {
            return;
         }
         QRecord device = MobileService.authenticateDevice(id, token);
         MobilePermissions.REQUEST_DEVICE.set(device.getValueString("id"));
         if(!("/qqq/v1" + MobileScannersUiApi.PREFIX + "/sessions/heartbeat").equals(context.path().replaceFirst("/+$", "")))
         {
            // Heartbeats authorize approval before creating or advancing activity.
            MobileSessionService.observe(device.getValueString("id"));
         }
      }
      catch(QAuthenticationException e)
      {
         context.contentType("application/json");
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.UNAUTHORIZED, "Sign in to continue.");
         context.skipRemainingHandlers();
      }
      catch(Exception e)
      {
         if(!(e instanceof QPermissionDeniedException) && !(e instanceof QBadRequestException))
         {
            LOG.warn("{\"message\":\"Device authorization failed\",\"category\":\"unexpected-failure\",\"operation\":\"bind-device\"}");
         }
         context.contentType("application/json");
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.FORBIDDEN, "Invalid device credential.");
         context.skipRemainingHandlers();
      }
      finally
      {
         QContext.clear();
      }
   }

}
