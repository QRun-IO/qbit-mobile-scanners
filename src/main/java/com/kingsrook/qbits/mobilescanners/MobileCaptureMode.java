/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;


/*******************************************************************************
 ** Explicit scope of consented screen pixels; omitted legacy fields are app-only.
 *******************************************************************************/
enum MobileCaptureMode
{
   APP_CONTENT, DEVICE_SCREEN;

   /***************************************************************************
    ** Unknown modes cannot broaden a client's capture permission.
    ***************************************************************************/
   static MobileCaptureMode parse(Object value) throws QBadRequestException
   {
      if(value == null)
      {
         return APP_CONTENT;
      }
      if(value instanceof String text)
      {
         for(MobileCaptureMode mode : values())
         {
            if(mode.name().equals(text))
            {
               return mode;
            }
         }
      }
      throw new QBadRequestException("Unsupported screen capture mode.");
   }
}
