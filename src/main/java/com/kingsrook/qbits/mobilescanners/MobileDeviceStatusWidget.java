/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.CompositeWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextBlockData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextValues;


/*******************************************************************************
 ** Native refreshing status distinguishes approval, connectivity and location age.
 *******************************************************************************/
public class MobileDeviceStatusWidget extends AbstractWidgetRenderer
{
   /***************************************************************************
    ** Only administrators can view live device observations.
    ***************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      String id = MobileService.required(input.getQueryParams(), "id", 36);
      var device = MobileService.get(MobileScannersQBitProducer.DEVICE, id);
      if(device == null)
      {
         throw new QBadRequestException("Device not found.");
      }
      Instant now = Instant.now();
      Instant seen = device.getValueInstant("lastSeenAt");
      CompositeWidgetData data = new CompositeWidgetData().withLayout(CompositeWidgetData.Layout.FLEX_COLUMN);
      add(data, "Approval Status: " + device.getValueString("status"));
      add(data, "Connection: " + (seen != null && !seen.plusSeconds(15).isBefore(now) ? "Online" : "Offline")
         + " | Last seen: " + age(seen, now));
      add(data, "Location status: " + Objects.toString(device.getValueString("locationStatus"), "UNAVAILABLE"));
      Instant received = device.getValueInstant("locationReceivedAt");
      if(received == null)
      {
         add(data, "No location has been reported.");
      }
      else
      {
         add(data, "Last known location: " + device.getValueString("latitude") + ", " + device.getValueString("longitude")
            + " | Accuracy: " + device.getValueString("accuracyMeters") + " meters");
         add(data, "Location received: " + age(received, now) + " | Captured at: " + device.getValueString("locationCapturedAt"));
      }
      return new RenderWidgetOutput(data);
   }

   /***************************************************************************
    ** Server receipt age remains meaningful despite device clock skew.
    ***************************************************************************/
   private static String age(Instant timestamp, Instant now)
   {
      return timestamp == null ? "Never" : Math.max(0, Duration.between(timestamp, now).getSeconds()) + " seconds ago";
   }

   /***************************************************************************
    ** Standard typed TEXT blocks avoid generated HTML and custom wire objects.
    ***************************************************************************/
   private static void add(CompositeWidgetData data, String text)
   {
      data.withBlock(new TextBlockData().withBlockId("status" + data.getBlocks().size()).withValues(new TextValues(text)));
   }
}
