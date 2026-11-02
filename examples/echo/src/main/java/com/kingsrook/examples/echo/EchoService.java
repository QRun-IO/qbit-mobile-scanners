/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** One approved scanner run repeatedly invokes existing generation/printing
 ** services. Only durable queue acceptance acknowledges a scan to the client.
 *******************************************************************************/
public final class EchoService
{
   private final EchoStore store;
   private final EchoDestinations destinations;
   private final EchoGenerator generator;
   private final EchoConfig.IdentitySource identities;



   /***************************************************************************
    ** Composition is host-owned and never constructed from process values.
    ***************************************************************************/
   EchoService(DataSource dataSource, EchoDestinations destinations, EchoGenerator generator, EchoConfig.IdentitySource identities)
   {
      store = new EchoStore(dataSource);
      this.destinations = Objects.requireNonNull(destinations);
      this.generator = Objects.requireNonNull(generator);
      this.identities = Objects.requireNonNull(identities);
   }



   /***************************************************************************
    ** Resolve both choices under current authority, then bind them once per run.
    ***************************************************************************/
   public String start(String printerId, String mediaId) throws QException
   {
      String run = UUID.randomUUID().toString();
      begin(run);
      select(run, printerId, mediaId);
      return run;
   }



   /***************************************************************************
    ** The backend passes only its protected native process UUID here.
    ***************************************************************************/
   public void begin(String processId) throws QException
   {
      requireText(processId, 36);
      store.begin(processId, identity());
   }



   /***************************************************************************
    ** A setup choice cannot be silently changed by repeating the setup step.
    ***************************************************************************/
   public void select(String runId, String printerId, String mediaId) throws QException
   {
      MobileDeviceAuthorization.Identity identity = identity();
      requireText(printerId, 200);
      requireText(mediaId, 200);
      EchoDestinations.Destination destination = destinations.authorize(identity, printerId, mediaId);
      if(destination == null || !printerId.equals(destination.printerRecordId()) || !mediaId.equals(destination.mediaId())
         || destination.mediaRevision() == null || !destination.mediaRevision().matches("[a-f0-9]{64}")
         || destination.widthMicrons() < 1000 || destination.heightMicrons() < 1000 || destination.widthMicrons() > 2000000 || destination.heightMicrons() > 2000000
         || destination.dpiX() < 72 || destination.dpiY() < 72 || destination.dpiX() > 1200 || destination.dpiY() > 1200 || destination.dpiX() != destination.dpiY())
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      check(identity);
      store.select(runId, identity, destination);
   }



   /***************************************************************************
    ** Capture context is already canonical server persistence, not a client DTO.
    ** A stable event is admitted once and retains the same generation request key.
    ***************************************************************************/
   public Acceptance print(String runId, Map<String, Serializable> context) throws QException
   {
      requireText(runId, 36);
      MobileDeviceAuthorization.Identity identity = identity();
      Scan scan = Scan.from(context);
      if(!identity.actorId().equals(context.get("userId")) || !identity.deviceId().equals(context.get("deviceId"))
         || !EchoQBitProducer.APP.equals(context.get("appId")) || !EchoQBitProducer.PROCESS.equals(context.get("processName")))
      {
         throw new QException("Scan context does not match this approved scanner sign-in.");
      }
      EchoStore.Admission admission = store.claim(runId, identity, scan);
      if(admission.acceptedJob() != null)
      {
         return new Acceptance(admission.scanId(), scan.eventId(), admission.acceptedJob());
      }
      if(admission.reconciliationOnly())
      {
         if(admission.pinnedPlan() == null)
         {
            throw new QException("This scan stopped before its rendering intent was pinned. Leave Echo; no automatic replacement print is allowed.");
         }
         check(identity);
         String recovered = generator.lookupAccepted(admission.pinnedPlan(), admission.scanId(), "echo:" + admission.scanId(), admission.destination());
         requireText(recovered, 200);
         check(identity);
         store.accepted(runId, admission.scanId(), scan.eventId(), recovered);
         return new Acceptance(admission.scanId(), scan.eventId(), recovered);
      }
      try
      {
         EchoDestinations.Destination destination = admission.destination();
         EchoDestinations.Destination current = destinations.authorize(identity, destination.printerRecordId(), destination.mediaId());
         if(!destination.equals(current))
         {
            throw new QException("The selected printer or media is no longer available.");
         }
         EchoDestinations.Plan plan = destinations.plan(identity, destination, scan);
         if(plan == null || plan.templateId() == null || plan.templateId() < 1 || plan.templateRevisionId() == null || plan.templateRevisionId() < 1
            || plan.format() == null || !stockMatches(plan.stock(), destination))
         {
            throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
         }
         store.pin(admission.scanId(), plan);
         check(identity);
         String job = generator.accept(plan, admission.scanId(), "echo:" + admission.scanId(), destination);
         requireText(job, 200);
         check(identity);
         store.accepted(runId, admission.scanId(), scan.eventId(), job);
         return new Acceptance(admission.scanId(), scan.eventId(), job);
      }
      catch(QException failure)
      {
         try
         {
            store.attention(admission.scanId());
         }
         catch(QException stateFailure)
         {
            failure.addSuppressed(stateFailure);
         }
         throw failure;
      }
      catch(RuntimeException failure)
      {
         store.attention(admission.scanId());
         throw new QException("Print acceptance is unconfirmed. Retry the same scan or leave Echo.");
      }
   }



   /***************************************************************************
    ** Physical stock and margins belong to the exact qualified template pin.
    ***************************************************************************/
   private static boolean stockMatches(com.kingsrook.qbits.documentgeneration.GenerationPorts.Stock stock, EchoDestinations.Destination destination)
   {
      return stock != null && Integer.valueOf(destination.widthMicrons()).equals(stock.widthMicrons())
         && Integer.valueOf(destination.heightMicrons()).equals(stock.heightMicrons()) && Integer.valueOf(destination.dpiX()).equals(stock.dpi())
         && stock.topMicrons() != null && stock.rightMicrons() != null && stock.bottomMicrons() != null && stock.leftMicrons() != null
         && stock.topMicrons() >= 0 && stock.rightMicrons() >= 0 && stock.bottomMicrons() >= 0 && stock.leftMicrons() >= 0
         && (long) stock.topMicrons() + stock.bottomMicrons() < destination.heightMicrons()
         && (long) stock.leftMicrons() + stock.rightMicrons() < destination.widthMicrons();
   }



   /***************************************************************************
    ** Run closure does not claim to recall a job already accepted for printing.
    ***************************************************************************/
   public void close(String runId) throws QException
   {
      store.close(runId, identity());
   }



   /***************************************************************************
    ** Never continue using a public identity after its private binding changes.
    ***************************************************************************/
   private void check(MobileDeviceAuthorization.Identity identity) throws QException
   {
      if(!identity.equals(identity()))
      {
         throw new QException("Scanner authorization changed. Leave Echo and sign in again.");
      }
   }



   /***************************************************************************
    ** Direct service calls retain the same Echo-use and mobile authorization.
    ***************************************************************************/
   private MobileDeviceAuthorization.Identity identity() throws QException
   {
      var session = com.kingsrook.qqq.backend.core.context.QContext.getQSession();
      if(session == null || !session.hasPermission(EchoQBitProducer.PERMISSION))
      {
         throw new QException("Echo access is required.");
      }
      return identities.current();
   }



   /***************************************************************************
    ** Bounded identifiers are distinct from exact untrimmed barcode values.
    ***************************************************************************/
   private static String requireText(Object value, int maximum) throws QException
   {
      if(!(value instanceof String text) || text.isBlank() || text.length() > maximum)
      {
         throw new QException("A required Echo value is missing or exceeds its supported size.");
      }
      return text;
   }



   /***************************************************************************
    ** Same-value/type reproduction is explicit. GS1, binary and unknown scanner
    ** variants need qualified metadata and renderer support before admission.
    ***************************************************************************/
   public record Scan(String eventId, String barcode, String symbology, String rendererSymbology, String scanSource, String capturedAt, String mobileScanId, String receivedAt)
   {
      /***********************************************************************
       ** Preserve exact payload/type; map only known scanner enum aliases.
       ***********************************************************************/
      static Scan from(Map<String, Serializable> context) throws QException
      {
         if(context == null)
         {
            throw new QException("Canonical scan context is required.");
         }
         String event = requireText(context.get("eventId"), 36);
         String value = requireText(context.get("barcode"), 4096);
         String type = requireText(context.get("symbology"), 200);
         String source = requireText(context.get("scanSource"), 200);
         String captured = requireText(context.get("capturedAt"), 40);
         String mobileScan = requireText(context.get("scanId"), 64);
         String received = requireText(context.get("receivedAt"), 40);
         try
         {
            UUID.fromString(event);
            Instant.parse(captured);
            Instant.parse(received);
         }
         catch(IllegalArgumentException | java.time.format.DateTimeParseException failure)
         {
            throw new QException("Scan event identity and timestamp must be valid.");
         }
         String renderer = switch(type)
         {
            case "CODE128", "LABEL-TYPE-CODE128" -> "code128";
            case "QRCODE", "LABEL-TYPE-QRCODE" -> "qr";
            case "CODE39", "LABEL-TYPE-CODE39" -> "code39";
            case "DATAMATRIX", "LABEL-TYPE-DATAMATRIX" -> "datamatrix";
            case "PDF417", "LABEL-TYPE-PDF417" -> "pdf417";
            case "AZTEC", "LABEL-TYPE-AZTEC" -> "aztec";
            default -> throw new QException("This barcode type cannot yet be reproduced faithfully by Echo. No print was requested.");
         };
         ///////////////////////////////////////////////////////////////////////////////////////
         // the qualified first-physical profile is printable ASCII 0x20..0x7E for all types; //
         // other characters can decode differently under the scanner charset. Never convert. //
         ///////////////////////////////////////////////////////////////////////////////////////
         if(value.chars().anyMatch(character -> character < 0x20 || character > 0x7E))
         {
            throw new QException("This scan needs control/binary/GS1 or non-ASCII payload handling that Echo has not qualified; only printable ASCII is supported. No print was requested.");
         }
         return new Scan(event, value, type, renderer, source, captured, mobileScan, received);
      }
   }



   /***************************************************************************
    ** Only these queue-confirmed values enable the SCANNER acknowledgment.
    ***************************************************************************/
   public record Acceptance(String scanId, String eventId, String printJobId)
   {
   }
}
