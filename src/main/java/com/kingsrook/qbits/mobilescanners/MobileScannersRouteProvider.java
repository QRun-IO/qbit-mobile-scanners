/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.Map;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.apibuilder.ApiBuilder;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;


/*******************************************************************************
 ** Enrollment plus request-scoped device credentials for native QQQ UI APIs.
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
         ApiBuilder.post("/mobile/v1/devices/enroll", context -> handle(context, true));
         ApiBuilder.get("/mobile/v1/devices/current", context -> handle(context, false));
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
      if(context.path().endsWith("/manageSession") || context.path().endsWith("/authenticationMetaData") || context.path().endsWith("/logout"))
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
      catch(QException e)
      {
         throw new ForbiddenResponse("Invalid device credential.");
      }
      finally
      {
         QContext.clear();
      }
   }

   /***************************************************************************
    ** Require user authentication for registration and status, never anonymous.
    ***************************************************************************/
   @SuppressWarnings("unchecked")
   private void handle(Context context, boolean enroll)
   {
      context.header("Cache-Control", "no-store");
      try
      {
         ExecutorSessionUtils.setupSession(context, instance);
         MobilePermissions.require(MobilePermissions.SCAN);
         if(enroll)
         {
            if(context.body().length() > 4096)
            {
               context.status(413).json(Map.of("error", "Enrollment request is too large."));
               return;
            }
            Map<String, Object> input = context.bodyAsClass(Map.class);
            context.status(201).json(MobileService.enroll(input));
         }
         else
         {
            QRecord device = MobileService.authenticateDevice(context.header(DEVICE_HEADER), context.header(TOKEN_HEADER));
            context.json(Map.of("deviceId", device.getValueString("id"), "status", device.getValueString("status"),
               "displayName", device.getValueString("displayName") == null ? "" : device.getValueString("displayName")));
         }
      }
      catch(QAuthenticationException e)
      {
         context.status(401).json(Map.of("error", "Sign in to continue."));
      }
      catch(QPermissionDeniedException e)
      {
         context.status(403).json(Map.of("error", "User or device is not authorized."));
      }
      catch(MobileService.EnrollmentConflictException e)
      {
         context.status(409).json(Map.of("error", e.getMessage()));
      }
      catch(Exception e)
      {
         context.status(400).json(Map.of("error", "The mobile request could not be completed."));
      }
      finally
      {
         QContext.clear();
         MobilePermissions.REQUEST_DEVICE.remove();
      }
   }
}
