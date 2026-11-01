/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.CompositeWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.image.ImageBlockData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.image.ImageStyles;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.image.ImageValues;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextBlockData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.blocks.text.TextValues;


/*******************************************************************************
 ** Native QQQ IMAGE/TEXT blocks refreshed by the standard widget API.
 *******************************************************************************/
public class MobileSessionScreenWidget extends AbstractWidgetRenderer
{
   /***************************************************************************
    ** Every refresh rechecks administrator permission and the target session.
    ***************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      String id = MobileService.required(input.getQueryParams(), "id", 36);
      var view = MobileSessionService.view(id);
      String status = view.status() + (view.receivedAt() == null ? "" : " — received " + view.receivedAt());
      CompositeWidgetData data = new CompositeWidgetData().withLayout(CompositeWidgetData.Layout.FLEX_COLUMN)
         .withBlock(new TextBlockData().withBlockId("screenStatus").withValues(new TextValues(status)));
      if(view.dataUri() != null)
      {
         data.withBlock(new ImageBlockData().withBlockId("screenFrame")
            .withValues(new ImageValues().withPath(view.dataUri()).withAlt(view.status()))
            .withStyles(new ImageStyles().withWidth("320px").withHeight("auto")));
      }
      return new RenderWidgetOutput(data);
   }
}
