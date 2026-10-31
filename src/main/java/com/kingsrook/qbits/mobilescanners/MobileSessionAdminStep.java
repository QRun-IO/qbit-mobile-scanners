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
 ** Administrator termination with durable history and QQQ auth invalidation.
 *******************************************************************************/
public class MobileSessionAdminStep implements BackendStep
{
   /***************************************************************************
    ** Recheck permission and the full stable selection before changing anything.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      if(!MobileScannersQBitProducer.SESSION_END.equals(input.getProcessName()))
      {
         throw new QException("Unknown session administration process.");
      }
      String selected = input.getRecords() != null && !input.getRecords().isEmpty()
         ? String.join(",", input.getRecords().stream().map(record -> record.getValueString("id")).toList())
         : MobileService.required(input.getValues(), "sessionId", 3700);
      var ids = MobileAdminSelection.resolve(selected, MobileScannersQBitProducer.SESSION);
      String reason = MobileService.optional(input.getValues(), "reason", 200);
      MobileSessionService.endAll(ids, reason);
      output.addValue("processedSessionCount", ids.size());
      output.addValue("result", ids.size() + (ids.size() == 1 ? " session ended." : " sessions ended."));
   }
}
