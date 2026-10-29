/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** Explicit administrator approval and revocation, never raw table mutation.
 *******************************************************************************/
public class MobileDeviceAdminStep implements BackendStep
{
   /***************************************************************************
    ** Restrict behavior to the two registered administrative processes.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String process = input.getProcessName();
      if(!MobileScannersQBitProducer.APPROVE.equals(process) && !MobileScannersQBitProducer.REVOKE.equals(process))
      {
         throw new QException("Unknown mobile administration process.");
      }
      String id = MobileService.required(input.getValues(), "deviceId", 36);
      boolean approved = MobileScannersQBitProducer.APPROVE.equals(process);
      MobileService.setApproval(id, approved);
      output.addValue("deviceId", id);
      output.addValue("status", approved ? "APPROVED" : "REVOKED");
   }
}
