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
         try
         {
            String run = boundRun(input, output);
            EchoConfig.current().service.select(run, input.getValueString("printerId"), input.getValueString("mediaId"), documentId(input.getValue("templateId")));
            output.addValue(EchoQBitProducer.WIDGET, new EchoWidget.Data());
         }
         catch(QException failure)
         {
            recoverable(output, failure);
         }
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
         try
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
         catch(QException failure)
         {
            recoverable(output, failure);
         }
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
    ** An ordinary business refusal (invalid choice, changed document, printer
    ** or authorization problem) is reported to the client as the normal process
    ** error, but must not be thrown out of the step: QQQ replaces the stored
    ** process state with an empty one when step code throws, which drops its
    ** ownership record and then denies every later request, including Leave.
    ** Recording the exception on the output keeps the state, so the operator
    ** can correct the choice, retry the same scan, or leave.
    ***************************************************************************/
   private static void recoverable(RunBackendStepOutput output, QException failure)
   {
      output.setException(failure);
   }



   /***************************************************************************
    ** Strict integer Document id: an integral number, or plain digits from a
    ** legacy text form. Fractions, exponents, blanks and overflow are refused.
    ***************************************************************************/
   static Integer documentId(java.io.Serializable raw) throws QException
   {
      Long value = null;
      if(raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte)
      {
         value = ((Number) raw).longValue();
      }
      else if(raw instanceof String text && text.matches("[0-9]{1,10}"))
      {
         value = Long.parseLong(text);
      }
      if(value == null || value < 1 || value > Integer.MAX_VALUE)
      {
         throw new QException("Choose an available document for this printer and paper or label size.");
      }
      return value.intValue();
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
