/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


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
      QRecord saved = MobileService.capture(input.getValues());
      output.addValue("acknowledgedEventId", saved.getValueString("eventId"));
      output.addValue("scanId", saved.getValueString("id"));
      output.addValue("scanSaved", true);
      output.addValue(MobileScannersQBitProducer.WIDGET, new MobileScannerWidget.ScannerWidgetData());
      output.getProcessState().setNextStepName("scan");
   }
}
