/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.mobilescanners.MobileScannerWidget;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;


/*******************************************************************************
 ** Reuse the existing registered semantic SCANNER control, with Echo instructions.
 *******************************************************************************/
public final class EchoWidget extends AbstractWidgetRenderer
{
   /***************************************************************************
    ** No new client control, process-name inference or custom business endpoint.
    ***************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input)
   {
      return new RenderWidgetOutput(new Data());
   }



   /***************************************************************************
    ** Input geometry and event acknowledgments are the mobile extension contract.
    ***************************************************************************/
   public static final class Data extends MobileScannerWidget.ScannerWidgetData
   {
      /***********************************************************************
       ** Make queue versus paper semantics and leave behavior visible.
       ***********************************************************************/
      @Override
      public List<Map<String, Serializable>> getSubBlocks()
      {
         return List.of(Map.of("blockId", "echoScanner", "blockType", "SCANNER", "values",
            new java.util.HashMap<>(Map.of("title", "Echo — scan to print", "instructions",
               "Scan one barcode and wait for print queue acceptance. Leaving stops new scans; work already processing or accepted may still print.",
               "manualLabel", "Manual entry is not supported for faithful copying", "submitLabel", "Scan with the trigger", "maxLength", 4096))));
      }
   }
}
