/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationQBitConfig;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationQBitProducer;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationService;
import com.kingsrook.qbits.documentgeneration.GenerationAudit;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.documentgeneration.printing.PrintingDispatch;
import com.kingsrook.qbits.documentgeneration.printing.RecordFilesPrintSource;
import com.kingsrook.qbits.documentgeneration.recordfiles.RecordFilesOutputIntake;
import com.kingsrook.qbits.printing.PrintingQBitConfig;
import com.kingsrook.qbits.printing.PrintingQBitProducer;
import com.kingsrook.qbits.printing.PrintingService;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** Complete host composition from the owners' public production classes: Record
 ** Files retained output and print source, Printing's retained facade with its
 ** media enforcement, Generation with its durable audit, and Echo itself. The
 ** isolated renderer and the Printing service are deployment-owned inputs; a
 ** missing one is refused, never replaced by an in-process fallback.
 *******************************************************************************/
public final class EchoHost
{
   /***************************************************************************
    ** Static composition only.
    ***************************************************************************/
   private EchoHost()
   {
   }



   /***************************************************************************
    ** Deployment-owned parts. printingBackend and generationBackend name backends
    ** registered in the instance; echoState shares the Echo state schema's database.
    ** Media enforcement comes only from the owner's explicit adoption factory.
    ***************************************************************************/
   public record Deployment(String printingBackend, String generationBackend, String echoBackend, DataSource echoState, PrintingService printing,
      GenerationPorts.IsolatedRenderer renderer, EchoTemplates templates)
   {
      /***********************************************************************
       ** Refuse an absent deployment-owned part before any composition.
       ***********************************************************************/
      public Deployment
      {
         Objects.requireNonNull(printing);
         Objects.requireNonNull(renderer);
         Objects.requireNonNull(templates);
      }
   }



   /***************************************************************************
    ** Register Record Files-backed Generation and Printing, then Echo. The mobile
    ** scanner QBit and Record Files metadata must already be installed.
    ***************************************************************************/
   public static EchoConfig install(QInstance instance, Deployment deployment) throws QException
   {
      RecordFilesPrintSource source = new RecordFilesPrintSource();
      RecordFilesOutputIntake intake = new RecordFilesOutputIntake();
      PrintingDispatch dispatch = PrintingDispatch.forOwnerMediaEnforcement(source);
      DocumentGenerationQBitConfig generation = new DocumentGenerationQBitConfig().withBackendName(deployment.generationBackend())
         .withSourceTables(List.of(EchoQBitProducer.SCAN_TABLE)).withRenderer(deployment.renderer()).withIntake(intake).withRetainedAccess(intake)
         .withPrinting(dispatch).withAudit(new GenerationAudit());
      new PrintingQBitProducer().withConfig(new PrintingQBitConfig().withBackendName(deployment.printingBackend()).withService(deployment.printing())
         .withRetainedSourceAuthority(source)).produce(instance, "printing");
      EchoConfig config = EchoOwnerAdapters.config(instance, deployment.echoBackend(), deployment.echoState(), new DocumentGenerationService(generation),
         deployment.templates(), GenerationPorts.Format.ZPL);
      new EchoQBitProducer().produce(instance, config);
      // Generation validates its configured source tables, so echoScan must be registered first.
      new DocumentGenerationQBitProducer().withQBitConfig(generation).produce(instance);
      return config;
   }
}
