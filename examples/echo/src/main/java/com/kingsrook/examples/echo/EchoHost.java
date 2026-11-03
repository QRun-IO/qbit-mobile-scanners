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
    ** maximumOutputBytes must be the renderer's own configured output cap, which
    ** Generation sends with every render request; the owner bridge refuses a larger
    ** request before it renders. Null keeps the owner's default and is only correct
    ** when the renderer accepts that default.
    ***************************************************************************/
   public record Deployment(String printingBackend, String generationBackend, String echoBackend, DataSource echoState, PrintingService printing,
      GenerationPorts.IsolatedRenderer renderer, Integer maximumOutputBytes)
   {
      /** The owner's inclusive bounds for a configured output cap. */
      static final int MAXIMUM_OUTPUT_BYTES_LIMIT = 100 * 1024 * 1024;



      /***********************************************************************
       ** Refuse an absent deployment-owned part or an out-of-range output cap
       ** before any composition.
       ***********************************************************************/
      public Deployment
      {
         if(maximumOutputBytes != null && (maximumOutputBytes < 1 || maximumOutputBytes > MAXIMUM_OUTPUT_BYTES_LIMIT))
         {
            throw new IllegalArgumentException("maximumOutputBytes must be between 1 and " + MAXIMUM_OUTPUT_BYTES_LIMIT + ".");
         }
         Objects.requireNonNull(printing);
         Objects.requireNonNull(renderer);
      }



      /***********************************************************************
       ** Owner default output cap, for a renderer that accepts it.
       ***********************************************************************/
      public Deployment(String printingBackend, String generationBackend, String echoBackend, DataSource echoState, PrintingService printing,
         GenerationPorts.IsolatedRenderer renderer)
      {
         this(printingBackend, generationBackend, echoBackend, echoState, printing, renderer, null);
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
      if(deployment.maximumOutputBytes() != null)
      {
         generation.withMaximumOutputBytes(deployment.maximumOutputBytes());
      }
      new PrintingQBitProducer().withConfig(new PrintingQBitConfig().withBackendName(deployment.printingBackend()).withService(deployment.printing())
         .withRetainedSourceAuthority(source)).produce(instance, "printing");
      EchoConfig config = EchoOwnerAdapters.config(instance, deployment.echoBackend(), deployment.echoState(), new DocumentGenerationService(generation),
         GenerationPorts.Format.ZPL);
      new EchoQBitProducer().produce(instance, config);
      // Generation validates its configured source tables, so echoScan must be registered first.
      new DocumentGenerationQBitProducer().withQBitConfig(generation).produce(instance);
      return config;
   }
}
