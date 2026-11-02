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
    ** Authorize the chosen document for this exact destination: a currently
    ** published, Echo-compatible template the actor may use, whose stock equals
    ** the media. The returned revision is what the run pins; ids from forms are
    ** never trusted and no other template is substituted.
    ***************************************************************************/
   Document authorizeDocument(MobileDeviceAuthorization.Identity identity, Destination destination, Integer templateId) throws QException;



   /***************************************************************************
    ** Reauthorize the selected destination and the run's pinned document, whose
    ** revision must still be the published one, and return the exact plan. The
    ** isolated renderer fits this value/type at integral module sizes; refuse
    ** unsupported types, withdrawn or changed documents.
    ***************************************************************************/
   Plan plan(MobileDeviceAuthorization.Identity identity, Destination destination, Document document, EchoService.Scan scan) throws QException;



   /***************************************************************************
    ** Documents the current actor may choose for this printer and paper or
    ** label size. Setup offers only these; authorizeDocument still rechecks.
    ***************************************************************************/
   default List<DocumentChoice> documents(String printerRecordId, String mediaId) throws QException
   {
      return List.of();
   }



   /***************************************************************************
    ** Public identifier and label of one eligible document, never its source.
    ***************************************************************************/
   record DocumentChoice(Integer id, String label)
   {
   }



   /***************************************************************************
    ** The document and published revision chosen once for the run.
    ***************************************************************************/
   record Document(Integer templateId, Integer templateRevisionId)
   {
   }



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
