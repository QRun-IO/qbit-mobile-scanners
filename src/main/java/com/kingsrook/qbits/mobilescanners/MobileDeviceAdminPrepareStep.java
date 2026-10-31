/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** Resolve selected devices before the administrator confirms the action.
 *******************************************************************************/
public class MobileDeviceAdminPrepareStep implements BackendStep
{
   /***************************************************************************
    ** Share the native bounded record/filter selection implementation.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      MobileAdminSelection.prepare(input, output, MobileScannersQBitProducer.DEVICE, "deviceId", "device(s)");
   }

   /***************************************************************************
    ** Validate every target before device mutation.
    ***************************************************************************/
   static List<String> resolve(String value) throws QException
   {
      return MobileAdminSelection.resolve(value, MobileScannersQBitProducer.DEVICE);
   }
}
