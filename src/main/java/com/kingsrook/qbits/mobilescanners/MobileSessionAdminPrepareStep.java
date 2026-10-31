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
 ** Freeze single/bulk session selections before confirmation.
 *******************************************************************************/
public class MobileSessionAdminPrepareStep implements BackendStep
{
   /***************************************************************************
    ** Reuse the same selection semantics as device approval and revocation.
    ***************************************************************************/
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      MobileAdminSelection.prepare(input, output, MobileScannersQBitProducer.SESSION, "sessionId", "session(s)");
   }
}
