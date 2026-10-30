/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.Set;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;


/*******************************************************************************
 ** Request-scoped device credentials for registered native QQQ UI APIs.
 *******************************************************************************/
public class MobileScannersRouteProvider implements QJavalinRouteProviderInterface
{
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
    ** Verify credentials before native handlers; authentication remains QQQ's.
    ***************************************************************************/
   private void bindDevice(Context context) throws Exception
   {
      MobilePermissions.REQUEST_DEVICE.remove();
      String id = context.header(DEVICE_HEADER);
      String token = context.header(TOKEN_HEADER);
      if(Set.of("/qqq/v1/version", "/qqq/v1/openapi.json", "/qqq/v1/openapi.yaml", "/qqq/v1/metaData/authentication",
         "/qqq/v1/manageSession", "/qqq/v1/logout", "/qqq/v1" + MobileScannersUiApi.PREFIX + "/branding",
         "/qqq/v1" + MobileScannersUiApi.PREFIX + "/status", "/qqq/v1" + MobileScannersUiApi.PREFIX + "/devices/enroll").contains(context.path()))
      {
         return;
      }
      if(id == null && token == null)
      {
         return;
      }
      try
      {
         ExecutorSessionUtils.setupSession(context, instance);
         QRecord device = MobileService.authenticateDevice(id, token);
         MobilePermissions.REQUEST_DEVICE.set(device.getValueString("id"));
      }
      catch(QAuthenticationException e)
      {
         context.contentType("application/json");
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.UNAUTHORIZED, "Sign in to continue.");
         context.skipRemainingHandlers();
      }
      catch(Exception e)
      {
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
