/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kingsrook.qbits.documentgeneration.DocumentGenerationService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Controlled owner facades qualify Echo's adapter mapping, exact pins and
 ** no-reprint recovery. They do not render or print anything.
 *******************************************************************************/
class EchoOwnerAdaptersTest
{
   private static final String REVISION = "a".repeat(64);
   private static final GenerationPorts.Stock STOCK = new GenerationPorts.Stock(101600, 152400, 3000, 3000, 3000, 3000, 300);
   private static final EchoDestinations.Destination DESTINATION = new EchoDestinations.Destination("printer-row", "4x6", REVISION, 101600, 152400, 300, 300);
   private static final EchoDestinations.Plan PLAN = new EchoDestinations.Plan(7, 11, STOCK, GenerationPorts.Format.ZPL);
   private static final String PROFILES = "[{\"id\":\"4x6\",\"revision\":\"" + REVISION + "\",\"name\":\"4 x 6\",\"widthMicrons\":101600,"
      + "\"heightMicrons\":152400,\"dpiX\":300,\"dpiY\":300,\"source\":\"WINDOWS_QUEUE_DEFAULT\",\"applyMode\":\"DRIVER_DEFAULT\"}]";



   /***************************************************************************
    ** The reported profile identity, revision and exact dimensions are used.
    ***************************************************************************/
   @Test
   void reportedProfileBecomesExactDestination() throws Exception
   {
      assertEquals(DESTINATION, EchoPrintingDestinations.destination("printer-row", PROFILES, "4x6"));
   }



   /***************************************************************************
    ** Unknown, malformed or absent media never receives guessed dimensions.
    ***************************************************************************/
   @Test
   void unknownOrMalformedMediaIsRefused()
   {
      assertThrows(QException.class, () -> EchoPrintingDestinations.destination("printer-row", PROFILES, "2x1"));
      assertThrows(QException.class, () -> EchoPrintingDestinations.destination("printer-row", "[{\"id\":\"4x6\"}]", "4x6"));
      assertThrows(QException.class, () -> EchoPrintingDestinations.destination("printer-row", "not json", "4x6"));
      assertThrows(QException.class, () -> EchoPrintingDestinations.destination("printer-row", null, "4x6"));
   }



   /***************************************************************************
    ** Template choice is exact for symbology, media and media revision.
    ***************************************************************************/
   @Test
   void templateMappingIsExact() throws Exception
   {
      EchoTemplates templates = EchoTemplates.of(Map.of(EchoTemplates.key("qr", "4x6", REVISION), 7));
      assertEquals(7, templates.templateId("qr", DESTINATION));
      assertThrows(QException.class, () -> templates.templateId("code128", DESTINATION));
      assertThrows(QException.class, () -> templates.templateId("qr", new EchoDestinations.Destination("printer-row", "4x6", "b".repeat(64), 101600, 152400, 300, 300)));
   }



   /***************************************************************************
    ** Generation receives the exact tuple, selected media and stable key.
    ***************************************************************************/
   @Test
   void acceptSendsExactPinsAndReturnsTheQueuedJob() throws Exception
   {
      List<DocumentGenerationService.Request> sent = new ArrayList<>();
      EchoDocumentGenerator generator = new EchoDocumentGenerator(owner(sent, "QUEUED", "job-1", Optional.empty()));
      assertEquals("job-1", generator.accept(PLAN, "scan-1", "echo:scan-1", DESTINATION));
      DocumentGenerationService.Request request = sent.getFirst();
      assertEquals(7, request.templateId());
      assertEquals(new GenerationPorts.Source("echoScan", "scan-1"), request.source());
      assertEquals(GenerationPorts.Format.ZPL, request.format());
      assertEquals("echo:scan-1", request.requestKey());
      assertEquals("printer-row", request.destination());
      assertEquals(new DocumentGenerationService.Expected(7, 11, STOCK), request.expected());
      assertEquals(new GenerationPorts.MediaProfile("4x6", REVISION), request.mediaProfile());
   }



   /***************************************************************************
    ** An incomplete or unqueued owner result is never reported as accepted.
    ***************************************************************************/
   @Test
   void unqueuedOrBlankJobIsRefused()
   {
      assertThrows(QException.class, () -> new EchoDocumentGenerator(owner(new ArrayList<>(), "INCOMPLETE", null, Optional.empty())).accept(PLAN, "s", "echo:s", DESTINATION));
      assertThrows(QException.class, () -> new EchoDocumentGenerator(owner(new ArrayList<>(), "QUEUED", " ", Optional.empty())).accept(PLAN, "s", "echo:s", DESTINATION));
   }



   /***************************************************************************
    ** QUEUED is reported only with an exact durable, available retained receipt.
    ***************************************************************************/
   @Test
   void missingOrUndurableReceiptIsNeverQueued()
   {
      GenerationPorts.Receipt undurable = new GenerationPorts.Receipt("file", "version", "e".repeat(64), 10L, false, true);
      GenerationPorts.Receipt quarantined = new GenerationPorts.Receipt("file", "version", "e".repeat(64), 10L, true, false);
      for(GenerationPorts.Receipt receipt : new GenerationPorts.Receipt[] { null, undurable, quarantined })
      {
         EchoDocumentGenerator generator = new EchoDocumentGenerator(owner(new ArrayList<>(), "QUEUED", "job-1", Optional.empty(), receipt));
         assertThrows(QException.class, () -> generator.accept(PLAN, "s", "echo:s", DESTINATION));
      }
   }



   /***************************************************************************
    ** Only the selected printer's sizes are offered, by id or plain-text search.
    ***************************************************************************/
   @Test
   void mediaChoicesAreFilteredByPrinterChoicesOnly() throws Exception
   {
      List<EchoDestinations.MediaChoice> choices = EchoPrintingDestinations.choices(PROFILES);
      assertEquals(1, choices.size());
      assertEquals("4x6", choices.getFirst().id());
      assertTrue(choices.getFirst().label().contains("101.6 x 152.4 mm, 300 dpi"));
      assertEquals(1, EchoMediaChoices.select(choices, null, "4 x", 0, 50).size());
      assertEquals(0, EchoMediaChoices.select(choices, null, "label", 0, 50).size());
      assertEquals(0, EchoMediaChoices.select(choices, List.of("2x1"), null, 0, 50).size());
      assertEquals("4x6", EchoMediaChoices.select(choices, List.of("4x6"), null, 0, 50).getFirst().getId());
      assertEquals(0, EchoMediaChoices.select(choices, null, null, 1, 50).size());
   }



   /***************************************************************************
    ** The dependency value selects the printer; no value means no choices.
    ***************************************************************************/
   @Test
   void printerDependencyValueIsRequiredAndBounded() throws Exception
   {
      assertEquals(null, EchoMediaChoices.printer(null));
      assertEquals(null, EchoMediaChoices.printer(Map.of("printerId", " ")));
      assertEquals("row-1", EchoMediaChoices.printer(Map.of("printerId", "row-1")));
      assertThrows(QException.class, () -> EchoMediaChoices.printer(Map.of("printerId", "x".repeat(201))));
   }



   /***************************************************************************
    ** The published scene fits inside the exact stock and allowlists only the
    ** symbologies Echo admits, with no client scanner attestation.
    ***************************************************************************/
   @Test
   void publishedSceneFitsTheExactStockAndAllowlist() throws Exception
   {
      org.json.JSONObject scene = new org.json.JSONObject(EchoTemplatePublisher.zplScene(DESTINATION));
      org.json.JSONObject element = scene.getJSONArray("elements").getJSONObject(0);
      assertEquals("qqq-zpl-scene/1", scene.getString("schema"));
      assertEquals("/record/rendererSymbology", element.getString("symbologyPath"));
      assertEquals("/record/barcode", element.getString("valuePath"));
      assertEquals(EchoTemplates.SYMBOLOGIES.size(), element.getJSONArray("allowedSymbologies").length());
      assertEquals(93600, element.getJSONObject("fit").getInt("widthMicrons"));
      assertEquals(144400, element.getJSONObject("fit").getInt("heightMicrons"));
      assertEquals(48, element.getInt("x"));
      assertTrue(!element.has("scannerSymbologyPath"));
      assertEquals(new GenerationPorts.Stock(101600, 152400, 3000, 3000, 3000, 3000, 300), EchoTemplatePublisher.stock(DESTINATION));
      assertThrows(QException.class, () -> EchoTemplatePublisher.stock(new EchoDestinations.Destination("p", "m", REVISION, 101600, 152400, 250, 250)));
      assertEquals(7, EchoTemplates.forMedia("4x6", REVISION, 7).templateId("aztec", DESTINATION));
      assertThrows(QException.class, () -> EchoTemplates.forMedia("4x6", REVISION, 7).templateId("maxicode", DESTINATION));
      assertThrows(QException.class, () -> EchoTemplates.forMedia("4x6", REVISION, 7).templateId("ean13", DESTINATION));
   }



   /***************************************************************************
    ** The scene has exactly the key shape of the renderer owner's own dynamic
    ** fit fixture (BarcodeFitTest.dynamicZplFitIsBoundedByTemplateAllowlist) and
    ** satisfies the worker's static rules, with its integer dot arithmetic, for
    ** 4 x 6 in at 300 dpi: allowlisted fit types, quiet >= 10 for linear types,
    ** x/y at or past the left/top margin, and the fitted box inside the right and
    ** bottom printable limits. Worker acceptance itself needs the renderer.
    ***************************************************************************/
   @Test
   void sceneMatchesOwnerFixtureShapeAndWorkerGeometryRules() throws Exception
   {
      org.json.JSONObject scene = new org.json.JSONObject(EchoTemplatePublisher.zplScene(DESTINATION));
      org.json.JSONObject element = scene.getJSONArray("elements").getJSONObject(0);
      assertEquals(java.util.Set.of("schema", "elements"), scene.keySet());
      assertEquals(java.util.Set.of("type", "symbologyPath", "allowedSymbologies", "valuePath", "x", "y", "quiet", "fit"), element.keySet());
      assertEquals(java.util.Set.of("widthMicrons", "heightMicrons"), element.getJSONObject("fit").keySet());
      java.util.List<String> fitTypes = java.util.List.of("code128", "code39", "code93", "itf", "gs1-128", "qr", "ean13", "ean8", "upca", "upce", "datamatrix", "pdf417", "aztec");
      for(Object type : element.getJSONArray("allowedSymbologies"))
      {
         assertTrue(fitTypes.contains(type));
      }
      assertTrue(element.getJSONArray("allowedSymbologies").toList().containsAll(java.util.List.of("code128", "qr")));
      assertTrue(element.getInt("quiet") >= 10 && element.getInt("quiet") <= 64);
      GenerationPorts.Stock stock = EchoTemplatePublisher.stock(DESTINATION);
      int dpi = stock.dpi();
      long minimumX = ((long) stock.leftMicrons() * dpi + 25399) / 25400;
      long minimumY = ((long) stock.topMicrons() * dpi + 25399) / 25400;
      long boxWidth = (long) element.getJSONObject("fit").getInt("widthMicrons") * dpi / 25400;
      long boxHeight = (long) element.getJSONObject("fit").getInt("heightMicrons") * dpi / 25400;
      long rightLimit = (long) (stock.widthMicrons() - stock.rightMicrons()) * dpi / 25400;
      long bottomLimit = (long) (stock.heightMicrons() - stock.bottomMicrons()) * dpi / 25400;
      assertTrue(element.getInt("x") >= minimumX && element.getInt("y") >= minimumY);
      assertTrue(element.getInt("x") + boxWidth <= rightLimit && element.getInt("y") + boxHeight <= bottomLimit);
      assertTrue(element.getJSONObject("fit").getInt("widthMicrons") <= stock.widthMicrons() - stock.leftMicrons() - stock.rightMicrons());
      assertTrue(element.getJSONObject("fit").getInt("heightMicrons") <= stock.heightMicrons() - stock.topMicrons() - stock.bottomMicrons());
   }



   /***************************************************************************
    ** Only a scene that draws the scanned value with the scanned type, allowing
    ** every admitted symbology, is an eligible document - never a static one.
    ***************************************************************************/
   @Test
   void onlyDynamicSameSymbologyScenesAreEligibleDocuments() throws Exception
   {
      assertTrue(EchoPrintingDestinations.reproducesScannedSymbology(EchoTemplatePublisher.zplScene(DESTINATION)));
      org.json.JSONObject scene = new org.json.JSONObject(EchoTemplatePublisher.zplScene(DESTINATION));
      org.json.JSONObject element = scene.getJSONArray("elements").getJSONObject(0);
      org.json.JSONObject fixedType = new org.json.JSONObject(scene.toString());
      fixedType.getJSONArray("elements").getJSONObject(0).remove("symbologyPath");
      fixedType.getJSONArray("elements").getJSONObject(0).put("symbology", "code128");
      assertTrue(!EchoPrintingDestinations.reproducesScannedSymbology(fixedType.toString()));
      org.json.JSONObject fixedValue = new org.json.JSONObject(scene.toString());
      fixedValue.getJSONArray("elements").getJSONObject(0).put("valuePath", "/record/other");
      assertTrue(!EchoPrintingDestinations.reproducesScannedSymbology(fixedValue.toString()));
      org.json.JSONObject narrow = new org.json.JSONObject(scene.toString());
      narrow.getJSONArray("elements").getJSONObject(0).put("allowedSymbologies", new org.json.JSONArray(List.of("code128")));
      assertTrue(!EchoPrintingDestinations.reproducesScannedSymbology(narrow.toString()));
      assertTrue(!EchoPrintingDestinations.reproducesScannedSymbology("not json"));
      assertTrue(!EchoPrintingDestinations.reproducesScannedSymbology("{\"schema\":\"other\",\"elements\":[]}"));
      assertEquals("barcode", element.getString("type"));
   }



   /***************************************************************************
    ** Absent deployment-owned printing, renderer or templates are refused, and
    ** the owner's plain dispatch constructor still denies selected profiles.
    ***************************************************************************/
   @Test
   void absentDeploymentPartsAreRefused()
   {
      assertThrows(NullPointerException.class, () -> new EchoHost.Deployment("p", "g", "e", null, null, null));
      assertTrue(!com.kingsrook.qbits.documentgeneration.printing.PrintingDispatch.MediaCapability.UNSUPPORTED.enforcesSelectedProfile());
   }



   /***************************************************************************
    ** Lookup is read-only; absence refuses and presence returns the owner job.
    ***************************************************************************/
   @Test
   void lookupNeverGeneratesAndRefusesAbsence() throws Exception
   {
      List<DocumentGenerationService.Request> sent = new ArrayList<>();
      EchoDocumentGenerator absent = new EchoDocumentGenerator(owner(sent, "QUEUED", "job-1", Optional.empty()));
      assertThrows(QException.class, () -> absent.lookupAccepted(PLAN, "scan-1", "echo:scan-1", DESTINATION));
      assertTrue(sent.isEmpty());
      DocumentGenerationService.Request request = EchoDocumentGenerator.request(PLAN, "scan-1", "echo:scan-1", DESTINATION);
      GenerationPorts.PrintAcceptance accepted = new GenerationPorts.PrintAcceptance(
         new GenerationPorts.PrintRequest("op", "actor", request.source(), null, "printer-row", request.mediaProfile()), "job-9", "event-9", Instant.now());
      EchoDocumentGenerator present = new EchoDocumentGenerator(owner(sent, "QUEUED", "job-1",
         Optional.of(new DocumentGenerationService.AcceptedPrint(null, accepted))));
      assertEquals("job-9", present.lookupAccepted(PLAN, "scan-1", "echo:scan-1", DESTINATION));
      assertTrue(sent.isEmpty());
   }



   /***************************************************************************
    ** A lookup bound to different media is not accepted evidence.
    ***************************************************************************/
   @Test
   void lookupWithDifferentMediaIsRefused()
   {
      GenerationPorts.PrintAcceptance other = new GenerationPorts.PrintAcceptance(new GenerationPorts.PrintRequest("op", "actor",
         new GenerationPorts.Source("echoScan", "scan-1"), null, "printer-row", new GenerationPorts.MediaProfile("4x6", "c".repeat(64))), "job-9", "event-9", Instant.now());
      EchoDocumentGenerator generator = new EchoDocumentGenerator(owner(new ArrayList<>(), "QUEUED", "job-1",
         Optional.of(new DocumentGenerationService.AcceptedPrint(null, other))));
      assertThrows(QException.class, () -> generator.lookupAccepted(PLAN, "scan-1", "echo:scan-1", DESTINATION));
   }



   /***************************************************************************
    ** Media identifiers outside Printing's grammar fail as a normal refusal.
    ***************************************************************************/
   @Test
   void invalidMediaIdentityIsAQException()
   {
      EchoDestinations.Destination bad = new EchoDestinations.Destination("printer-row", "bad media!", REVISION, 101600, 152400, 300, 300);
      assertThrows(QException.class, () -> EchoDocumentGenerator.request(PLAN, "s", "echo:s", bad));
   }



   /***************************************************************************
    ** Controlled owner returning the given state and recording requests.
    ***************************************************************************/
   private static EchoDocumentGenerator.Owner owner(List<DocumentGenerationService.Request> sent, String state, String job,
      Optional<DocumentGenerationService.AcceptedPrint> lookup)
   {
      return owner(sent, state, job, lookup, new GenerationPorts.Receipt("file", "version", "e".repeat(64), 10L, true, true));
   }



   /***************************************************************************
    ** Controlled owner with an explicit retained receipt.
    ***************************************************************************/
   private static EchoDocumentGenerator.Owner owner(List<DocumentGenerationService.Request> sent, String state, String job,
      Optional<DocumentGenerationService.AcceptedPrint> lookup, GenerationPorts.Receipt receipt)
   {
      return new EchoDocumentGenerator.Owner()
      {
         @Override
         public DocumentGenerationService.Result generate(DocumentGenerationService.Request request)
         {
            sent.add(request);
            return new DocumentGenerationService.Result(1, state, receipt, job, request.expected(), "d".repeat(64), request.mediaProfile(), null);
         }

         @Override
         public Optional<DocumentGenerationService.AcceptedPrint> lookup(DocumentGenerationService.Request request)
         {
            return lookup;
         }
      };
   }
}
