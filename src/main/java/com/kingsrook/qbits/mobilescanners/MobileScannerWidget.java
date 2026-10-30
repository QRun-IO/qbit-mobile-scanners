/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;


/*******************************************************************************
 ** Native Android renders this scanner block within the ordinary QQQ process.
 *******************************************************************************/
public class MobileScannerWidget extends AbstractWidgetRenderer
{
   /***************************************************************************
    ** Return a typed scanner input; capture metadata is supplied by the device.
    ***************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      return new RenderWidgetOutput(new ScannerWidgetData());
   }

   /***************************************************************************
    ** Native widget wire shape. RC20's closed WidgetBlock enum predates SCANNER,
    ** so this extension supplies its wire fields directly as QWidgetData.
    ***************************************************************************/
   public static class ScannerWidgetData extends QWidgetData
   {
      /***********************************************************************
       ** Preserve the standard composite widget metadata type.
       ***********************************************************************/
      @Override
      public String getType()
      {
         return "composite";
      }

      /***********************************************************************
       ** Android's native DataWedge-enabled renderer.
       ***********************************************************************/
      public String getBlockType()
      {
         return "COMPOSITE";
      }

      /***********************************************************************
       ** One scanner component; the surrounding process owns its actions.
       ***********************************************************************/
      public List<Map<String, Serializable>> getSubBlocks()
      {
         return List.of(Map.of("blockId", "barcodeScanner", "blockType", "SCANNER", "values",
            new java.util.HashMap<>(Map.of("title", "Scan a barcode", "instructions", "Scan with the trigger or enter a barcode. Saved scans appear in Mobile Scanner Administration.",
               "manualLabel", "Manual barcode", "submitLabel", "Submit barcode", "maxLength", 4096))));
      }
   }
}
