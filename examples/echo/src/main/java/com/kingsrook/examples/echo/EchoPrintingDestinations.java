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
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
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
   private final EchoTemplates templates;
   private final GenerationPorts.Format format;



   /***************************************************************************
    ** The output format is fixed by the host and checked against each printer.
    ***************************************************************************/
   public EchoPrintingDestinations(EchoTemplates templates, GenerationPorts.Format format)
   {
      this.templates = Objects.requireNonNull(templates);
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
    ** Reauthorize the same destination, then pin the published revision whose
    ** stock equals it. Generation repeats this comparison at admission.
    ***************************************************************************/
   @Override
   public Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, EchoService.Scan scan) throws QException
   {
      if(!destination.equals(authorize(identity, destination.printerRecordId(), destination.mediaId())))
      {
         throw new QException("The selected printer or media is no longer available.");
      }
      Integer templateId = templates.templateId(scan.rendererSymbology(), destination);
      QRecord template = get(GenerationTables.TEMPLATE, templateId);
      Integer revisionId = template.getValueInteger("publishedRevisionId");
      if(revisionId == null)
      {
         throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
      }
      QRecord revision = get(GenerationTables.REVISION, revisionId);
      String source = format == GenerationPorts.Format.PDF ? revision.getValueString("pdfSource") : revision.getValueString("zplSource");
      if(!templateId.equals(revision.getValueInteger("templateId")) || !EchoQBitProducer.SCAN_TABLE.equals(revision.getValueString("tableName"))
         || "PREVIEW".equals(revision.getValueString("purpose")) || source == null || source.isBlank())
      {
         throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
      }
      return new Plan(templateId, revisionId, stock(revision), format);
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
         throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
      }
      return record;
   }
}
