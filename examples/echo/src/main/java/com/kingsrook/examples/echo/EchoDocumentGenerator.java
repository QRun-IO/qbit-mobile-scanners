/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Production generation through the owner's public service. Every request pins
 ** the exact template, revision, stock and selected media profile. Recovery uses
 ** only the owner's read-only accepted-print lookup, never generate/reconcile.
 *******************************************************************************/
public final class EchoDocumentGenerator implements EchoGenerator
{
   private final Owner owner;



   /***************************************************************************
    ** Narrow delegate over the two owner operations Echo is allowed to use.
    ***************************************************************************/
   interface Owner
   {
      /***********************************************************************
       ** Idempotent generate, retain and queue for one request key.
       ***********************************************************************/
      DocumentGenerationService.Result generate(DocumentGenerationService.Request request) throws QException;

      /***********************************************************************
       ** Side-effect-free committed acceptance lookup.
       ***********************************************************************/
      Optional<DocumentGenerationService.AcceptedPrint> lookup(DocumentGenerationService.Request request) throws QException;
   }



   /***************************************************************************
    ** Compose with the host's configured Document Generation service.
    ***************************************************************************/
   public EchoDocumentGenerator(DocumentGenerationService service)
   {
      this(new Owner()
      {
         @Override
         public DocumentGenerationService.Result generate(DocumentGenerationService.Request request) throws QException
         {
            return service.generate(request);
         }

         @Override
         public Optional<DocumentGenerationService.AcceptedPrint> lookup(DocumentGenerationService.Request request) throws QException
         {
            return service.lookupAcceptedPrint(request);
         }
      });
   }



   /***************************************************************************
    ** Package-private composition lets tests supply controlled owner facades.
    ***************************************************************************/
   EchoDocumentGenerator(Owner owner)
   {
      this.owner = Objects.requireNonNull(owner);
   }



   /***************************************************************************
    ** Replays of the same key return the owner's existing outcome; an incomplete
    ** owner row refuses rather than rendering or printing again.
    ***************************************************************************/
   @Override
   public String accept(EchoDestinations.Plan plan, String scanId, String requestKey, EchoDestinations.Destination destination) throws QException
   {
      DocumentGenerationService.Request request = request(plan, scanId, requestKey, destination);
      DocumentGenerationService.Result result = owner.generate(request);
      if(result == null || !"QUEUED".equals(result.state()) || result.printJobId() == null || result.printJobId().isBlank()
         || result.retained() == null || !Boolean.TRUE.equals(result.retained().durable()) || !Boolean.TRUE.equals(result.retained().available())
         || !request.mediaProfile().equals(result.mediaProfile()) || (result.accepted() != null && !request.expected().equals(result.accepted())))
      {
         throw new QException("Print acceptance is unconfirmed. Retry the same scan or leave Echo.");
      }
      return result.printJobId();
   }



   /***************************************************************************
    ** Absence or an unavailable lookup is never proof; both refuse.
    ***************************************************************************/
   @Override
   public String lookupAccepted(EchoDestinations.Plan plan, String scanId, String requestKey, EchoDestinations.Destination destination) throws QException
   {
      DocumentGenerationService.Request request = request(plan, scanId, requestKey, destination);
      DocumentGenerationService.AcceptedPrint accepted = owner.lookup(request).orElseThrow(
         () -> new QException("Print acceptance requires owner reconciliation. Leave Echo to stop scanning; do not create a replacement print."));
      GenerationPorts.PrintAcceptance printing = accepted.printing();
      if(printing == null || printing.jobId() == null || printing.jobId().isBlank() || printing.request() == null
         || !request.mediaProfile().equals(printing.request().mediaProfile()) || !request.source().equals(printing.request().source())
         || !request.destination().equals(printing.request().destination()))
      {
         throw new QException("Print acceptance requires owner reconciliation. Leave Echo to stop scanning; do not create a replacement print.");
      }
      return printing.jobId();
   }



   /***************************************************************************
    ** Build the exact pinned intent; the same value is used for lookup.
    ***************************************************************************/
   static DocumentGenerationService.Request request(EchoDestinations.Plan plan, String scanId, String requestKey, EchoDestinations.Destination destination) throws QException
   {
      GenerationPorts.MediaProfile media;
      try
      {
         media = new GenerationPorts.MediaProfile(destination.mediaId(), destination.mediaRevision());
      }
      catch(IllegalArgumentException invalid)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      return new DocumentGenerationService.Request(plan.templateId(), new GenerationPorts.Source(EchoQBitProducer.SCAN_TABLE, scanId), plan.format(),
         requestKey, destination.printerRecordId(), new DocumentGenerationService.Expected(plan.templateId(), plan.templateRevisionId(), plan.stock()), media);
   }
}
