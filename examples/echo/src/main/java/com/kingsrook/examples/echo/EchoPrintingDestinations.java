/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.documentgeneration.GenerationTables;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qbits.printing.PrintingMediaProfile;
import com.kingsrook.qbits.printing.PrintingOperatorAuthorization;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionEnforcement;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.json.JSONArray;
import org.json.JSONObject;


/*******************************************************************************
 ** Production destinations from the Printing owner's operator authorization and
 ** reported queue media, plus the Generation owner's published revision rows.
 ** No printer, stock or template is invented; unsupported choices are refused.
 *******************************************************************************/
public final class EchoPrintingDestinations implements EchoDestinations
{
   private static final int MAXIMUM_DOCUMENTS = 200;
   private static final String UNAVAILABLE = "The chosen document is not available for this printer and paper or label size. Leave Echo and choose again.";

   private final GenerationPorts.Format format;



   /***************************************************************************
    ** The output format is fixed by the host and checked against each printer.
    ** Documents are discovered from the actor's published Generation templates.
    ***************************************************************************/
   public EchoPrintingDestinations(GenerationPorts.Format format)
   {
      this.format = Objects.requireNonNull(format);
   }



   /***************************************************************************
    ** Current actor, printer row, device and format are authorized by Printing on
    ** every call; the exact reported media revision is then pinned.
    ***************************************************************************/
   @Override
   public Destination authorize(MobileDeviceAuthorization.Identity identity, String printerRecordId, String mediaId) throws QException
   {
      try
      {
         if(!identity.actorId().equals(PrintingOperatorAuthorization.principal()))
         {
            throw new QException("Scanner authorization changed. Leave Echo and sign in again.");
         }
         QRecord printer = PrintingOperatorAuthorization.printer(printerRecordId, format.name());
         return destination(printerRecordId, printer.getValueString("media_profiles_json"), mediaId);
      }
      catch(RuntimeException failure)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
   }



   /***************************************************************************
    ** The destination is reauthorized, then the chosen template must be a
    ** published, compatible one; the run pins its current revision.
    ***************************************************************************/
   @Override
   public Document authorizeDocument(MobileDeviceAuthorization.Identity identity, Destination destination, Integer templateId) throws QException
   {
      if(templateId == null || !destination.equals(authorize(identity, destination.printerRecordId(), destination.mediaId())))
      {
         throw new QException(UNAVAILABLE);
      }
      return resolve(destination, templateId).document();
   }



   /***************************************************************************
    ** Reauthorize the same destination and the pinned document. A withdrawn or
    ** republished template fails visibly; no other revision is substituted.
    ***************************************************************************/
   @Override
   public Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, Document document, EchoService.Scan scan) throws QException
   {
      if(!destination.equals(authorize(identity, destination.printerRecordId(), destination.mediaId())))
      {
         throw new QException("The selected printer or media is no longer available.");
      }
      if(document == null || document.templateId() == null)
      {
         throw new QException("This Echo run has no chosen document. Leave Echo and choose again.");
      }
      Resolved current = resolve(destination, document.templateId());
      if(!current.document().equals(document))
      {
         throw new QException("The chosen document changed or was withdrawn. Leave Echo and choose again.");
      }
      return current.plan();
   }



   /***************************************************************************
    ** Published Echo-compatible templates for the selected printer and size.
    ** Anything the actor cannot read or that does not reproduce the scanned
    ** symbology dynamically at this exact stock is simply not listed.
    ***************************************************************************/
   @Override
   public List<DocumentChoice> documents(String printerRecordId, String mediaId) throws QException
   {
      Destination destination;
      try
      {
         destination = destination(printerRecordId, PrintingOperatorAuthorization.printer(printerRecordId, format.name()).getValueString("media_profiles_json"), mediaId);
      }
      catch(RuntimeException failure)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      QueryInput input = new QueryInput();
      input.setTableName(GenerationTables.TEMPLATE);
      input.setPermissionEnforcement(PermissionEnforcement.ENFORCE);
      input.setFilter(new QQueryFilter(new QFilterCriteria("tableName", QCriteriaOperator.EQUALS, EchoQBitProducer.SCAN_TABLE))
         .withCriteria(new QFilterCriteria("publishedRevisionId", QCriteriaOperator.IS_NOT_BLANK)).withOrderBy(new QFilterOrderBy("name")).withLimit(MAXIMUM_DOCUMENTS));
      List<DocumentChoice> choices = new ArrayList<>();
      for(QRecord template : new QueryAction().execute(input).getRecords())
      {
         try
         {
            resolve(destination, template.getValueInteger("id"));
            choices.add(new DocumentChoice(template.getValueInteger("id"), template.getValueString("name")));
         }
         catch(QException skipped)
         {
            // not compatible with this printer media, or not readable by this actor
         }
      }
      return choices;
   }



   /***************************************************************************
    ** Current published revision, stock and dynamic scene contract of one
    ** template, read under the actor's enforced permissions.
    ***************************************************************************/
   private Resolved resolve(Destination destination, Integer templateId) throws QException
   {
      QRecord template = get(GenerationTables.TEMPLATE, templateId);
      Integer revisionId = template.getValueInteger("publishedRevisionId");
      if(revisionId == null || !EchoQBitProducer.SCAN_TABLE.equals(template.getValueString("tableName")))
      {
         throw new QException(UNAVAILABLE);
      }
      QRecord revision = get(GenerationTables.REVISION, revisionId);
      String source = format == GenerationPorts.Format.PDF ? revision.getValueString("pdfSource") : revision.getValueString("zplSource");
      GenerationPorts.Stock stock = stock(revision);
      if(!templateId.equals(revision.getValueInteger("templateId")) || !EchoQBitProducer.SCAN_TABLE.equals(revision.getValueString("tableName"))
         || "PREVIEW".equals(revision.getValueString("purpose")) || source == null || source.isBlank()
         || !Integer.valueOf(destination.widthMicrons()).equals(stock.widthMicrons()) || !Integer.valueOf(destination.heightMicrons()).equals(stock.heightMicrons())
         || !Integer.valueOf(destination.dpiX()).equals(stock.dpi()) || (format == GenerationPorts.Format.ZPL && !reproducesScannedSymbology(source)))
      {
         throw new QException(UNAVAILABLE);
      }
      return new Resolved(new Document(templateId, revisionId), new Plan(templateId, revisionId, stock, format));
   }



   /***************************************************************************
    ** The published scene must draw the scanned value with the scanned type via
    ** dynamic binding, and allow every symbology Echo admits - never a fixed one.
    ***************************************************************************/
   static boolean reproducesScannedSymbology(String zplScene)
   {
      try
      {
         JSONObject scene = new JSONObject(zplScene);
         if(!"qqq-zpl-scene/1".equals(scene.optString("schema")))
         {
            return false;
         }
         for(Object item : scene.getJSONArray("elements"))
         {
            JSONObject element = (JSONObject) item;
            if("barcode".equals(element.optString("type")) && "/record/rendererSymbology".equals(element.optString("symbologyPath"))
               && "/record/barcode".equals(element.optString("valuePath")) && element.optJSONArray("allowedSymbologies") != null
               && element.getJSONArray("allowedSymbologies").toList().containsAll(EchoTemplates.SYMBOLOGIES))
            {
               return true;
            }
         }
         return false;
      }
      catch(RuntimeException failure)
      {
         return false;
      }
   }



   /***************************************************************************
    ** One template's pinned document and the plan derived from it.
    ***************************************************************************/
   private record Resolved(Document document, Plan plan)
   {
   }



   /***************************************************************************
    ** The selected printer's own reported sizes, after Printing's authorization
    ** of the actor, printer row, device and host output format.
    ***************************************************************************/
   @Override
   public List<MediaChoice> media(String printerRecordId) throws QException
   {
      QRecord printer;
      try
      {
         printer = PrintingOperatorAuthorization.printer(printerRecordId, format.name());
      }
      catch(RuntimeException failure)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      return choices(printer.getValueString("media_profiles_json"));
   }



   /***************************************************************************
    ** Public id and a label with the exact physical size and resolution.
    ***************************************************************************/
   static List<MediaChoice> choices(String mediaProfilesJson) throws QException
   {
      List<MediaChoice> choices = new ArrayList<>();
      try
      {
         for(Object item : new JSONArray(mediaProfilesJson == null ? "[]" : mediaProfilesJson))
         {
            PrintingMediaProfile profile = PrintingMediaProfile.parse((JSONObject) item);
            choices.add(new MediaChoice(profile.id(), profile.name() + " (" + String.format(Locale.ROOT, "%.1f x %.1f mm, %d dpi",
               profile.widthMicrons() / 1000.0, profile.heightMicrons() / 1000.0, profile.dpiX()) + ")"));
         }
      }
      catch(RuntimeException failure)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      return choices;
   }



   /***************************************************************************
    ** Select one reported media profile by identity; never guess dimensions.
    ***************************************************************************/
   static Destination destination(String printerRecordId, String mediaProfilesJson, String mediaId) throws QException
   {
      try
      {
         for(Object item : new JSONArray(mediaProfilesJson == null ? "[]" : mediaProfilesJson))
         {
            PrintingMediaProfile profile = PrintingMediaProfile.parse((JSONObject) item);
            if(profile.id().equals(mediaId))
            {
               return new Destination(printerRecordId, profile.id(), profile.revision(), profile.widthMicrons(), profile.heightMicrons(), profile.dpiX(), profile.dpiY());
            }
         }
      }
      catch(RuntimeException failure)
      {
         throw new QException("Choose an authorized printer and supported paper or label size.");
      }
      throw new QException("Choose an authorized printer and supported paper or label size.");
   }



   /***************************************************************************
    ** Immutable published stock is read from its revision row, not a draft.
    ***************************************************************************/
   static GenerationPorts.Stock stock(QRecord revision)
   {
      return new GenerationPorts.Stock(revision.getValueInteger("widthMicrons"), revision.getValueInteger("heightMicrons"),
         revision.getValueInteger("topMicrons"), revision.getValueInteger("rightMicrons"), revision.getValueInteger("bottomMicrons"),
         revision.getValueInteger("leftMicrons"), revision.getValueInteger("dpi"));
   }



   /***************************************************************************
    ** Native read under the caller's enforced permissions; missing rows refuse.
    ***************************************************************************/
   private static QRecord get(String table, Integer id) throws QException
   {
      GetInput input = new GetInput();
      input.setTableName(table);
      input.setPrimaryKey(id);
      input.setPermissionEnforcement(PermissionEnforcement.ENFORCE);
      QRecord record = new GetAction().execute(input).getRecord();
      if(record == null)
      {
         throw new QException(UNAVAILABLE);
      }
      return record;
   }
}
