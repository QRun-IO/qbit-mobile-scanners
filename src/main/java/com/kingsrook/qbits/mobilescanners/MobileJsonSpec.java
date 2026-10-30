/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import java.util.Locale;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.BasicErrorResponseV1;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Standard semantic JSON transport and safe structured errors.
 *******************************************************************************/
abstract class MobileJsonSpec extends AbstractJsonEndpointSpec
{
   /***************************************************************************
    ** Bound enrollment input before parsing and reject non-JSON payloads.
    ***************************************************************************/
   @Override
   public JsonInput buildInput(Context context) throws Exception
   {
      context.header("Cache-Control", "no-store");
      if(defineRequestBodySchema() != null)
      {
         if(context.body().length() > 4096)
         {
            throw new QBadRequestException("Enrollment request must be at most 4096 characters.");
         }
         if(context.contentType() == null || !context.contentType().toLowerCase(Locale.ROOT).startsWith("application/json"))
         {
            throw new QBadRequestException("The request body must be JSON.");
         }
      }
      return super.buildInput(context);
   }

   /***************************************************************************
    ** Authentication errors must never expose credential or storage details.
    ***************************************************************************/
   @Override
   protected void handleException(Context context, Exception exception)
   {
      context.header("Cache-Control", "no-store").contentType("application/json");
      if(exception instanceof QAuthenticationException)
      {
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.UNAUTHORIZED, "Sign in to continue.");
      }
      else if(exception instanceof QPermissionDeniedException)
      {
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.FORBIDDEN, "User or device is not authorized.");
      }
      else if(exception instanceof MobileService.EnrollmentConflictException)
      {
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.CONFLICT, exception.getMessage());
      }
      else if(exception instanceof QBadRequestException)
      {
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST, exception.getMessage());
      }
      else
      {
         QJavalinUtils.respondWithError(context, org.eclipse.jetty.http.HttpStatus.Code.INTERNAL_SERVER_ERROR, "The mobile request could not be completed.");
      }
   }

   /***************************************************************************
    ** All routes use the core machine-readable error envelope.
    ***************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Invalid input", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "User sign-in required", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.FORBIDDEN, "Permission or device credential denied", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.CONFLICT, "Installation already enrolled", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed", BasicErrorResponseV1.class.getSimpleName()));
   }
}
