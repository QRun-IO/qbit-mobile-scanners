/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.List;
import com.kingsrook.qbits.documentgeneration.DocumentDefinitionService;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.json.JSONArray;
import org.json.JSONObject;


/*******************************************************************************
 ** One-time operator action: publish the ZPL barcode template for one reported
 ** media profile through Generation's ordinary definition service. The renderer
 ** worker fits the exact scanned symbology and value at integral module sizes
 ** with quiet zones, and refuses what does not fit. Never run per scan.
 *******************************************************************************/
public final class EchoTemplatePublisher
{
   static final int STOCK_MARGIN_MICRONS = 3000;
   static final int INSET_MICRONS = 4000;
   static final int QUIET_MODULES = 10;



   /***************************************************************************
    ** Static operator action only.
    ***************************************************************************/
   private EchoTemplatePublisher()
   {
   }



   /***************************************************************************
    ** Stock exactly equal to the reported media, with fixed printable margins.
    ***************************************************************************/
   static GenerationPorts.Stock stock(EchoDestinations.Destination media) throws QException
   {
      if(!List.of(203, 300, 600).contains(media.dpiX()) || media.dpiX() != media.dpiY()
         || media.widthMicrons() <= 2 * INSET_MICRONS || media.heightMicrons() <= 2 * INSET_MICRONS)
      {
         throw new QException("ZPL Echo labels require 203, 300 or 600 dpi media larger than the printable inset.");
      }
      return new GenerationPorts.Stock(media.widthMicrons(), media.heightMicrons(), STOCK_MARGIN_MICRONS, STOCK_MARGIN_MICRONS,
         STOCK_MARGIN_MICRONS, STOCK_MARGIN_MICRONS, media.dpiX());
   }



   /***************************************************************************
    ** Dynamic type requires fit and an allowlist. Scanner attestation fields are
    ** omitted: client text is not trusted decoder configuration.
    ***************************************************************************/
   static String zplScene(EchoDestinations.Destination media) throws QException
   {
      stock(media);
      int dots = (int) Math.ceil(INSET_MICRONS * media.dpiX() / 25400.0);
      JSONObject element = new JSONObject().put("type", "barcode").put("symbologyPath", "/record/rendererSymbology")
         .put("allowedSymbologies", new JSONArray(EchoTemplates.SYMBOLOGIES)).put("valuePath", "/record/barcode")
         .put("x", dots).put("y", dots).put("quiet", QUIET_MODULES)
         .put("fit", new JSONObject().put("widthMicrons", media.widthMicrons() - 2 * INSET_MICRONS).put("heightMicrons", media.heightMicrons() - 2 * INSET_MICRONS));
      return new JSONObject().put("schema", "qqq-zpl-scene/1").put("elements", new JSONArray().put(element)).toString();
   }



   /***************************************************************************
    ** Publish immutable stock and source; the owner validates it in the isolated
    ** renderer. Returns the template id for EchoTemplates.forMedia.
    ***************************************************************************/
   public static Integer publish(DocumentDefinitionService definitions, EchoDestinations.Destination media) throws QException
   {
      return publish(definitions, media, null);
   }



   /***************************************************************************
    ** Publish one more document for the same media under its own operator-facing
    ** name; each published template becomes a separate choice in Echo's setup.
    ***************************************************************************/
   public static Integer publish(DocumentDefinitionService definitions, EchoDestinations.Destination media, String name) throws QException
   {
      GenerationPorts.Stock stock = stock(media);
      String scene = zplScene(media);
      String label = name == null || name.isBlank() ? media.mediaId() : name.strip();
      Integer type = definitions.createType(name == null || name.isBlank() ? "Echo scan label" : "Echo scan label " + label);
      Integer stockId = definitions.createStock("Echo " + label, stock);
      definitions.bind(type, EchoQBitProducer.SCAN_TABLE, false, true);
      Integer template = definitions.createTemplate("Echo " + label, type, stockId, EchoQBitProducer.SCAN_TABLE);
      definitions.saveSources(template, stockId, null, scene, definitions.loadDraft(template).fingerprint());
      definitions.publish(template, definitions.loadDraft(template).fingerprint());
      return template;
   }
}
