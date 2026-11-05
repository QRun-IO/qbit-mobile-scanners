/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import javax.sql.DataSource;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
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
   public String start(String printerId, String mediaId, Integer templateId) throws QException
   {
      return start(printerId, mediaId, templateId, null);
   }



   /***************************************************************************
    ** As above, with the copies printed for every scan of this run (default 1).
    ***************************************************************************/
   public String start(String printerId, String mediaId, Integer templateId, Integer copies) throws QException
   {
      String run = UUID.randomUUID().toString();
      begin(run);
      select(run, printerId, mediaId, templateId, copies);
      return run;
   }



   /***************************************************************************
    ** Printing's own copies-per-job bounds (PrintingService.printOptions and the
    ** agent): an integer from 1 to 100, expressed as one job option.
    ***************************************************************************/
   public static final int MAXIMUM_COPIES = 100;



   /***************************************************************************
    ** The count this run pinned at setup (one before setup completes), read only
    ** for the run's own signed-in scanner.
    ***************************************************************************/
   public int pinnedCopies(String runId) throws QException
   {
      requireText(runId, 36);
      return store.copies(runId, identity());
   }



   /***************************************************************************
    ** Absent means the default of one. Anything outside Printing's 1..100 is
    ** refused during setup, before the run is pinned. Printing's cap is the only
    ** bound: Echo adds none of its own.
    ***************************************************************************/
   private int copies(Integer requested) throws QException
   {
      int count = requested == null ? 1 : requested;
      if(count < 1 || count > MAXIMUM_COPIES)
      {
         throw new QException("Copies per scan must be a whole number from 1 to " + MAXIMUM_COPIES + ".");
      }
      return count;
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
   public void select(String runId, String printerId, String mediaId, Integer templateId) throws QException
   {
      select(runId, printerId, mediaId, templateId, null);
   }



   /***************************************************************************
    ** The copies per scan are validated and frozen with the printer, media and
    ** document; repeating setup can confirm them but never change them.
    ***************************************************************************/
   public void select(String runId, String printerId, String mediaId, Integer templateId, Integer copies) throws QException
   {
      MobileDeviceAuthorization.Identity identity = identity();
      requireText(printerId, 200);
      requireText(mediaId, 200);
      int count = copies(copies);
      Integer template = EchoSteps.documentId(templateId);
      EchoDestinations.Destination destination = destinations.authorize(identity, printerId, mediaId);
      if(destination == null || !printerId.equals(destination.printerRecordId()) || !mediaId.equals(destination.mediaId())
         || destination.mediaRevision() == null || !destination.mediaRevision().matches("[a-f0-9]{64}")
         || destination.widthMicrons() < 1000 || destination.heightMicrons() < 1000 || destination.widthMicrons() > 2000000 || destination.heightMicrons() > 2000000
         || destination.dpiX() < 72 || destination.dpiY() < 72 || destination.dpiX() > 1200 || destination.dpiY() > 1200 || destination.dpiX() != destination.dpiY())
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      EchoDestinations.Document document = destinations.authorizeDocument(identity, destination, template);
      if(document == null || !template.equals(document.templateId()) || document.templateRevisionId() == null || document.templateRevisionId() < 1)
      {
         throw new QException("Choose an available document for this printer and paper or label size.");
      }
      check(identity);
      store.select(runId, identity, destination, document, count);
   }



   /***************************************************************************
    ** Capture context is already canonical server persistence, not a client DTO.
    ** A stable event is admitted once and retains the same generation request key.
    ***************************************************************************/
   public Acceptance print(String runId, Map<String, Serializable> context) throws QException
   {
      return print(runId, context, null);
   }



   /***************************************************************************
    ** A scan-stage copies value may only repeat the setup choice; the server's
    ** pinned count, never the value, decides what is printed.
    ***************************************************************************/
   public Acceptance print(String runId, Map<String, Serializable> context, Integer claimedCopies) throws QException
   {
      requireText(runId, 36);
      MobileDeviceAuthorization.Identity identity = identity();
      Scan scan = Scan.from(context);
      if(!identity.actorId().equals(context.get("userId")) || !identity.deviceId().equals(context.get("deviceId"))
         || !EchoQBitProducer.APP.equals(context.get("appId")) || !EchoQBitProducer.PROCESS.equals(context.get("processName")))
      {
         throw new QException("Scan context does not match this approved scanner sign-in.");
      }
      EchoStore.Admission admission = store.claim(runId, identity, scan, claimedCopies);
      if(admission.acceptedJob() != null)
      {
         return new Acceptance(admission.scanId(), scan.eventId(), admission.acceptedJob(), admission.copies());
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
         //////////////////////////////////////////////////////////////////////////////////////
         // the owner's returned receipt is durable fact: record it, then recheck authority //
         // before acknowledging, so a late revocation never loses an accepted queue job     //
         //////////////////////////////////////////////////////////////////////////////////////
         store.accepted(runId, admission.scanId(), scan.eventId(), recovered);
         check(identity);
         return new Acceptance(admission.scanId(), scan.eventId(), recovered, admission.copies());
      }
      try
      {
         EchoDestinations.Destination destination = admission.destination();
         EchoDestinations.Destination current = destinations.authorize(identity, destination.printerRecordId(), destination.mediaId());
         if(!destination.equals(current))
         {
            throw new QException("The selected printer or media is no longer available.");
         }
         if(admission.document() == null)
         {
            throw new QException("This Echo run has no chosen document. Leave Echo and choose again.");
         }
         EchoDestinations.Plan plan = destinations.plan(identity, destination, admission.document(), scan);
         if(plan == null || plan.templateId() == null || plan.templateId() < 1 || plan.templateRevisionId() == null || plan.templateRevisionId() < 1
            || plan.format() == null || !stockMatches(plan.stock(), destination))
         {
            throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
         }
         plan = plan.withCopies(admission.copies());
         store.pin(admission.scanId(), plan);
         check(identity);
         String job = generator.accept(plan, admission.scanId(), "echo:" + admission.scanId(), destination);
         requireText(job, 200);
         store.accepted(runId, admission.scanId(), scan.eventId(), job);
         check(identity);
         return new Acceptance(admission.scanId(), scan.eventId(), job, admission.copies());
      }
      catch(EchoStore.PinNotWon failure)
      {
         throw failure;
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
   public record Scan(String eventId, String barcode, String symbology, String rendererSymbology, String scanSource, String capturedAt, String mobileScanId, String receivedAt, String aimIdentifier)
   {
      /////////////////////////////////////////////////////////////////////////////////////
      // the renderer's own plain-mode AIM identifier per type (ScannerSymbology.AIM);   //
      // any other modifier means a GS1/ECI/structured mode the plain encoder would lose //
      /////////////////////////////////////////////////////////////////////////////////////
      private static final Map<String, String> PLAIN_AIM = Map.of("code128", "]C0", "code39", "]A0", "qr", "]Q1", "datamatrix", "]d1",
         "pdf417", "]L0", "aztec", "]z0", "ean13", "]E0", "ean8", "]E4", "upca", "]E0", "upce", "]E0");


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
            case "EAN128", "LABEL-TYPE-EAN128", "GS1_128", "GS1-128" -> "gs1-128";
            case "QRCODE", "LABEL-TYPE-QRCODE" -> "qr";
            case "CODE39", "LABEL-TYPE-CODE39" -> "code39";
            case "DATAMATRIX", "LABEL-TYPE-DATAMATRIX" -> "datamatrix";
            case "PDF417", "LABEL-TYPE-PDF417" -> "pdf417";
            case "AZTEC", "LABEL-TYPE-AZTEC" -> "aztec";
            case "EAN13", "LABEL-TYPE-EAN13" -> "ean13";
            case "EAN8", "LABEL-TYPE-EAN8" -> "ean8";
            case "UPCA", "LABEL-TYPE-UPCA" -> "upca";
            case "UPCE0", "LABEL-TYPE-UPCE0" -> "upce";
            default -> throw new QException("This barcode type cannot yet be reproduced faithfully by Echo. No print was requested.");
         };
         ///////////////////////////////////////////////////////////////////////////////////////
         // the qualified first-physical profile is printable ASCII 0x20..0x7E for all types; //
         // other characters can decode differently under the scanner charset. Never convert. //
         ///////////////////////////////////////////////////////////////////////////////////////
         boolean gs1 = "]C1".equals(context.get("aimIdentifier")) && ("code128".equals(renderer) || "gs1-128".equals(renderer));
         if(gs1)
         {
            renderer = "gs1-128";
            EchoGs1.parse(value);
         }
         else if("gs1-128".equals(renderer) || value.chars().anyMatch(character -> character < 0x20 || character > 0x7E))
         {
            throw new QException("This scan needs control/binary/GS1 or non-ASCII payload handling that Echo has not qualified; only printable ASCII is supported. No print was requested.");
         }
         String aim = requireFidelityMetadata(context, renderer, value);
         return new Scan(event, value, type, renderer, source, captured, mobileScan, received, aim);
      }



      /***********************************************************************
       ** Optional mobile-scanners 1.5 evidence may only confirm the plain profile.
       ** Absence is legacy/manual capture and proves nothing, so it is not trusted.
       ** EAN/UPC need it, with a valid check digit, because the renderer converts
       ** their full-length values only in scanner mode. Returns the AIM, if any.
       ***********************************************************************/
      private static String requireFidelityMetadata(Map<String, Serializable> context, String renderer, String value) throws QException
      {
         String aim = context.get("aimIdentifier") == null ? null : requireText(context.get("aimIdentifier"), 3);
         boolean gs1 = "gs1-128".equals(renderer);
         if(aim != null && !(gs1 ? "]C1" : PLAIN_AIM.get(renderer)).equals(aim))
         {
            throw new QException("This scan reports a barcode mode (GS1, ECI, structured or full-ASCII) that Echo cannot reproduce faithfully. No print was requested.");
         }
         if(EchoTemplates.SCANNER_SYMBOLOGIES.contains(renderer))
         {
            if(aim == null)
            {
               throw new QException("EAN and UPC need the scanner's AIM identifier to be reproduced faithfully, and this scan has none. No print was requested.");
            }
            if(!validRetailValue(renderer, value))
            {
               throw new QException("This EAN or UPC value has the wrong length, number system or check digit. No print was requested.");
            }
         }
         Object raw = context.get("rawDataBase64");
         if(gs1 && raw == null)
         {
            throw new QException("GS1-128 duplication needs the scanner's original raw bytes and AIM identifier. No print was requested.");
         }
         if(raw != null)
         {
            byte[] bytes;
            try
            {
               bytes = Base64.getDecoder().decode(requireText(raw, 10924));
            }
            catch(IllegalArgumentException failure)
            {
               throw new QException("The scanner's raw bytes are malformed. No print was requested.");
            }
            ///////////////////////////////////////////////////////////////////////////////////
            // SDK bytes are the value itself or the value behind the configured AIM prefix; //
            // both are exact comparisons, and anything else cannot be tied to the value.    //
            ///////////////////////////////////////////////////////////////////////////////////
            byte[] unframed = value.getBytes(StandardCharsets.US_ASCII);
            byte[] framed = aim == null ? null : (aim + value).getBytes(StandardCharsets.US_ASCII);
            if(!Arrays.equals(bytes, unframed) && (framed == null || !Arrays.equals(bytes, framed)))
            {
               throw new QException("The scanner's raw bytes differ from its display value, so the original cannot be reproduced exactly. No print was requested.");
            }
         }
         return aim;
      }



      /***********************************************************************
       ** Exact length, digits, UPC-E number system 0 and GS1 check digit, so the
       ** renderer is never asked to print a value it would reject or recompute.
       ***********************************************************************/
      static boolean validRetailValue(String renderer, String value)
      {
         int length = switch(renderer)
         {
            case "ean13" -> 13;
            case "ean8", "upce" -> 8;
            case "upca" -> 12;
            default -> 0;
         };
         if(length == 0 || value.length() != length || !value.chars().allMatch(character -> character >= '0' && character <= '9'))
         {
            return false;
         }
         String full = value;
         if("upce".equals(renderer))
         {
            if(value.charAt(0) != '0')
            {
               return false;
            }
            String digits = value.substring(1, 7);
            String body = switch(digits.charAt(5))
            {
               case '0', '1', '2' -> digits.substring(0, 2) + digits.charAt(5) + "0000" + digits.substring(2, 5);
               case '3' -> digits.substring(0, 3) + "00000" + digits.substring(3, 5);
               case '4' -> digits.substring(0, 4) + "00000" + digits.charAt(4);
               default -> digits.substring(0, 5) + "0000" + digits.charAt(5);
            };
            full = "0" + body + value.charAt(7);
         }
         int sum = 0;
         for(int index = full.length() - 2, weight = 3; index >= 0; index--, weight = 4 - weight)
         {
            sum += (full.charAt(index) - '0') * weight;
         }
         return (10 - sum % 10) % 10 == full.charAt(full.length() - 1) - '0';
      }
   }



   /***************************************************************************
    ** Only these queue-confirmed values enable the SCANNER acknowledgment.
    ***************************************************************************/
   public record Acceptance(String scanId, String eventId, String printJobId, int copies)
   {
   }
}
