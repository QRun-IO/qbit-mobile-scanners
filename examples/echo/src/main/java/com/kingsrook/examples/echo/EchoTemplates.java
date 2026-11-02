/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Host-owned choice of a published Document Generation template for one exact
 ** symbology and reported printer media revision. Templates carry their own
 ** stock, so a changed media revision needs a newly qualified mapping.
 *******************************************************************************/
@FunctionalInterface
public interface EchoTemplates
{
   /***************************************************************************
    ** Return the template whose published revision was qualified for this tuple.
    ***************************************************************************/
   Integer templateId(String rendererSymbology, EchoDestinations.Destination destination) throws QException;



   /***************************************************************************
    ** Exact lookup by renderer symbology, media identity and media revision.
    ***************************************************************************/
   static EchoTemplates of(Map<String, Integer> templates)
   {
      Map<String, Integer> copy = Map.copyOf(templates);
      return (symbology, destination) ->
      {
         Integer id = copy.get(key(symbology, destination.mediaId(), destination.mediaRevision()));
         if(id == null)
         {
            throw new QException("No qualified same-symbology layout fits the selected paper or label size.");
         }
         return id;
      };
   }



   /***************************************************************************
    ** One published template, whose renderer allowlist covers every symbology
    ** Echo admits, for exactly this reported media identity and revision.
    ***************************************************************************/
   static EchoTemplates forMedia(String mediaId, String mediaRevision, Integer templateId)
   {
      Map<String, Integer> map = new java.util.HashMap<>();
      for(String symbology : SYMBOLOGIES)
      {
         map.put(key(symbology, mediaId, mediaRevision), templateId);
      }
      return of(map);
   }



   /***************************************************************************
    ** Fit-profile symbologies the template reproduces from the exact scanned text.
    ** EAN/UPC are excluded: the worker converts their full-length scanned values
    ** only in scanner mode, which needs the AIM identifier Echo does not record.
    ***************************************************************************/
   java.util.List<String> SYMBOLOGIES = java.util.List.of("code128", "qr", "code39", "datamatrix", "pdf417", "aztec");



   /***************************************************************************
    ** Stable map key shared by hosts and the lookup above.
    ***************************************************************************/
   static String key(String rendererSymbology, String mediaId, String mediaRevision)
   {
      return rendererSymbology + ":" + mediaId + ":" + mediaRevision;
   }
}
