/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.examples.echo;


import java.util.List;
import com.kingsrook.qbits.documentgeneration.GenerationPorts;
import com.kingsrook.qbits.mobilescanners.MobileDeviceAuthorization;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Application composition with the printing owner's authorized printer/media
 ** selector and the renderer owner's qualified fit capability. No queue or
 ** barcode encoder is implemented by Echo.
 *******************************************************************************/
public interface EchoDestinations
{
   /***************************************************************************
    ** Resolve current operator/device access and actual supported printer stock.
    ** Names from forms are identifiers, never asserted printer capabilities.
    ***************************************************************************/
   Destination authorize(MobileDeviceAuthorization.Identity identity, String printerRecordId, String mediaId) throws QException;



   /***************************************************************************
    ** Reauthorize the selected destination and provide a published revision whose
    ** isolated renderer fits this exact value/type at integral module sizes.
    ** Refuse unsupported types or media too small for safe quiet zones.
    ***************************************************************************/
   Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, EchoService.Scan scan) throws QException;



   /***************************************************************************
    ** Paper or label sizes the current actor may select for exactly this printer.
    ** Setup offers only these; authorize still rechecks the exact pair.
    ***************************************************************************/
   default List<MediaChoice> media(String printerRecordId) throws QException
   {
      return List.of();
   }



   /***************************************************************************
    ** Public identifier and label of one supported size, never its private pin.
    ***************************************************************************/
   record MediaChoice(String id, String label)
   {
   }



   /***************************************************************************
    ** Exact setup choice remains bound to the run, independent of later inputs.
    ***************************************************************************/
   record Destination(String printerRecordId, String mediaId, String mediaRevision, int widthMicrons, int heightMicrons, int dpiX, int dpiY)
   {
   }



   /***************************************************************************
    ** A generation-owned immutable template supplies stock and fitting policy.
    ***************************************************************************/
   record Plan(Integer templateId, Integer templateRevisionId, GenerationPorts.Stock stock, GenerationPorts.Format format)
   {
   }
}
