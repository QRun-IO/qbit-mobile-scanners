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
      MobilePermissions.require(MobilePermissions.ADMIN);
      String id = input.getRecords() != null && !input.getRecords().isEmpty()
         ? String.join(",", input.getRecords().stream().map(record -> record.getValueString("id")).toList())
         : MobileService.required(input.getValues(), "deviceId", 3700);
      var ids = MobileDeviceAdminPrepareStep.resolve(id);
      boolean approved = MobileScannersQBitProducer.APPROVE.equals(process);
      for(String deviceId : ids)
      {
         MobileService.setApproval(deviceId, approved);
      }
      output.addValue("deviceId", String.join(",", ids));
      output.addValue("status", approved ? "APPROVED" : "REVOKED");
      output.addValue("processedDeviceCount", ids.size());
      output.addValue("result", ids.size() + (ids.size() == 1 ? " device " : " devices ") + (approved ? "approved." : "revoked."));
   }
}
