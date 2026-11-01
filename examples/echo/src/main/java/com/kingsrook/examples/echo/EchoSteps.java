/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.HashMap;
import com.kingsrook.qbits.mobilescanners.MobileScannerActions;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** Ordinary server process steps own setup and loop state. The native client
 ** only renders existing semantic metadata and confirms matching event receipts.
 *******************************************************************************/
public final class EchoSteps
{
   /***************************************************************************
    ** Namespace for independently addressable metadata code references.
    ***************************************************************************/
   private EchoSteps()
   {
   }



   /***************************************************************************
    ** Create server-owned run state before setup, so cancellation also closes a
    ** run whose printer/media selection has not yet been completed.
    ***************************************************************************/
   public static final class Begin implements BackendStep
   {
      /***********************************************************************
       ** The protected framework identity is never read from process values.
       ***********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String run = processId(output);
         EchoConfig.current().service.begin(run);
         output.addValue("echoRunId", run);
      }
   }



   /***************************************************************************
    ** Setup runs once; subsequent scans loop directly back to the scan frontend.
    ***************************************************************************/
   public static final class Start implements BackendStep
   {
      /***********************************************************************
       ** Persist current authorized selection before the first scanner control.
       ***********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String run = boundRun(input, output);
         EchoConfig.current().service.select(run, input.getValueString("printerId"), input.getValueString("mediaId"));
         output.addValue(EchoQBitProducer.WIDGET, new EchoWidget.Data());
      }
   }



   /***************************************************************************
    ** Canonical capture precedes generation; saved-scan acknowledgment is delayed
    ** until the print job has a durable acceptance receipt.
    ***************************************************************************/
   public static final class Print implements BackendStep
   {
      /***********************************************************************
       ** Preserve actual type/context and let the same event reconcile failures.
       ***********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         String run = boundRun(input, output);
         var context = MobileScannerActions.capture(input, EchoQBitProducer.APP);
         EchoService.Acceptance accepted = EchoConfig.current().service.print(run, context);
         output.addValue("scanContext", new HashMap<>(context));
         output.addValue("scanId", context.get("scanId"));
         output.addValue("echoScanId", accepted.scanId());
         output.addValue("acknowledgedEventId", accepted.eventId());
         output.addValue("scanSaved", true);
         output.addValue("printJobId", accepted.printJobId());
         output.addValue("printStatus", "QUEUED");
         output.addValue(EchoQBitProducer.WIDGET, new EchoWidget.Data());
         output.getProcessState().setNextStepName("scan");
      }
   }



   /***************************************************************************
    ** Native Leave/Cancel closes admission without recalling accepted work.
    ***************************************************************************/
   public static final class Cancel implements BackendStep
   {
      /***********************************************************************
       ** Cancellation uses actual server state, never a caller's alternate run.
       ***********************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         EchoConfig.current().service.close(processId(output));
      }
   }



   /***************************************************************************
    ** Protect the printer/media run from substitution by the same signed-in user.
    ***************************************************************************/
   private static String boundRun(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String actual = processId(output);
      if(!actual.equals(input.getValueString("echoRunId")))
      {
         throw new QException("Echo run does not match this process. Leave Echo and reopen it.");
      }
      return actual;
   }



   /***************************************************************************
    ** Require the framework's protected session/process binding before using ID.
    ***************************************************************************/
   private static String processId(RunBackendStepOutput output) throws QException
   {
      var access = output.getProcessState().getStateAccess();
      if(access == null)
      {
         throw new QException("Echo requires protected native process state.");
      }
      return access.requireCurrentProcessUUID(EchoQBitProducer.PROCESS);
   }
}
