/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** Populate the native process widget before its first frontend step.
 *******************************************************************************/
public class MobilePrepareStep implements BackendStep
{
   /***************************************************************************
    ** Require the trusted request session even on a direct backend invocation.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      String deviceId = QContext.getQSession() instanceof VerifiedDeviceSession verified ? verified.deviceId() : null;
      MobileService.requireApproved(deviceId);
      output.addValue(MobileScannersQBitProducer.WIDGET, new MobileScannerWidget.ScannerWidgetData());
   }
}
