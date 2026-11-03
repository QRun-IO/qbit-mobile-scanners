/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** Persist a physical scan and return to the scanner only after acknowledgment.
 *******************************************************************************/
public class MobileScanStep implements BackendStep
{
   /***************************************************************************
    ** Approval is checked again here even if the process was launched earlier.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      Map<String, Serializable> context = MobileScannerActions.capture(input);
      output.addValue("scanContext", new HashMap<>(context));
      output.addValue("acknowledgedEventId", context.get("eventId"));
      output.addValue("scanId", context.get("scanId"));
      output.addValue("scanSaved", true);
      output.addValue(MobileScannersQBitProducer.WIDGET, new MobileScannerWidget.ScannerWidgetData());
      if(input.getProcess().getStepList().getLast().getName().equals(input.getStepName()))
      {
         output.getProcessState().setNextStepName("scan");
      }
   }
}
