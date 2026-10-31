/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Versioned JSON shapes for transport and native SCANNER widget semantics.
 *******************************************************************************/
public final class MobileScannersSchemas
{
   /***************************************************************************
    ** Utility class.
    ***************************************************************************/
   private MobileScannersSchemas()
   {
   }

   /***************************************************************************
    ** Shared components are also merged into the core UI API OpenAPI document.
    ***************************************************************************/
   public static Map<String, Schema> components()
   {
      Map<String, Schema> schemas = new TreeMap<>();
      schemas.put("MobileScannersBrandingV1", object("Customer appearance; displayName and primaryColor are always present; logoUrl is optional.", Map.of(
         "displayName", text(80), "primaryColor", text(7).withDescription("Six-digit CSS hex color, including #."),
         "logoUrl", text(2048).withFormat("uri").withDescription("Optional HTTPS image URL, no credentials or fragment.")), "displayName", "primaryColor"));
      schemas.put("MobileScannersStatusV1", object("Connectivity only; status is always present.", Map.of(
         "status", text(2).withEnumValues(List.of("ok"))), "status"));
      schemas.put("MobileScannersEnrollRequestV1", object("installationId is required. Other strings are optional. JSON body maximum is 4096 characters.", Map.of(
         "installationId", uuid(), "displayName", text(200), "manufacturer", text(200), "model", text(200),
         "osVersion", text(200), "appVersion", text(200), "shortName", text(32), "description", text(2000)), "installationId"));
      schemas.put("MobileScannersEnrollmentV1", object("All properties present. deviceToken is a secret returned only once; never log or include it in metadata/process values.", Map.of(
         "deviceId", uuid(), "deviceToken", text(128), "status", status(), "shortName", text(32), "displayName", text(200), "description", text(2000)), "deviceId", "deviceToken", "status"));
      schemas.put("MobileScannersDeviceV1", object("All properties present. No credential material is returned.", Map.of(
         "deviceId", uuid(), "displayName", text(200), "status", status(), "shortName", text(32), "description", text(2000)), "deviceId", "displayName", "status"));
      schemas.put("MobileScannersScannerValuesV1", object("SCANNER presentation values. Optional manualLabel defaults to Manual barcode; submitLabel defaults to Submit barcode; maxLength defaults to 4096. Unknown additive properties may be ignored.", Map.of(
         "title", text(200), "instructions", text(2000), "manualLabel", text(200), "submitLabel", text(200),
         "maxLength", new ScannerMaximumSchema().withType(Type.INTEGER).withDescription("Inclusive supported range 1..4096; reject invalid server values.")), "title", "instructions"));
      schemas.put("MobileScannersScannerBlockV1", object("SCANNER child block of a core COMPOSITE widget, returned by core process/widget operations. Requires mobile-scanners extension major 1. All properties present; do not infer this contract from process or widget names.", Map.of(
         "blockId", text(200), "blockType", text(7).withEnumValues(List.of("SCANNER")),
         "values", ref("MobileScannersScannerValuesV1")), "blockId", "blockType", "values"));
      schemas.put("MobileScannersScanEventV1", object("Values submitted to the current core process frontend step. barcode, eventId and capturedAt required. eventId remains unchanged on retry; identity comes only from verified user/device context, never these values. Barcode is a nonempty string, preserving leading zeroes.", Map.of(
         "barcode", text(4096), "eventId", uuid(), "capturedAt", text(40).withFormat("date-time"),
         "symbology", text(200), "scanSource", text(200)), "barcode", "eventId", "capturedAt"));
      schemas.put("MobileScannersScanAcknowledgmentV1", object("Properties in core process response values after persistence. All are required to acknowledge success: scanSaved=true, acknowledgedEventId matches submitted eventId and scanId is nonempty. Async polling must complete first. A repeated event returns its original scanId.", Map.of(
         "acknowledgedEventId", uuid(), "scanId", text(64), "scanSaved", new Schema().withType(Type.BOOLEAN)), "acknowledgedEventId", "scanId", "scanSaved"));
      schemas.putAll(MobileSessionSchemas.components());
      return schemas;
   }

   /***************************************************************************
    ** Explicit object properties permit future additive fields.
    ***************************************************************************/
   static Schema object(String description, Map<String, Schema> properties, String... required)
   {
      return new RequiredObjectSchema(List.of(required)).withType(Type.OBJECT).withDescription(description).withProperties(new TreeMap<>(properties));
   }

   /***************************************************************************
    ** Bounded strings retain their native wire type.
    ***************************************************************************/
   static Schema text(int maximum)
   {
      return new Schema().withType(Type.STRING).withMaxLength(maximum);
   }

   /***************************************************************************
    ** Canonical installation and event identifiers.
    ***************************************************************************/
   static Schema uuid()
   {
      return text(36).withFormat("uuid");
   }

   /***************************************************************************
    ** Unknown future device states must be treated as unapproved by clients.
    ***************************************************************************/
   private static Schema status()
   {
      return text(200).withEnumValues(List.of("PENDING", "APPROVED", "REVOKED"));
   }

   /***************************************************************************
    ** Link reusable versioned components.
    ***************************************************************************/
   static Schema ref(String name)
   {
      return new Schema().withRefToSchema(name);
   }

   /***************************************************************************
    ** RC20's Schema lacks required; expose the standard OpenAPI keyword.
    ***************************************************************************/
   public static class RequiredObjectSchema extends Schema
   {
      private final List<String> required;

      /***********************************************************************
       ** Bind the required properties of this versioned object.
       ***********************************************************************/
      public RequiredObjectSchema(List<String> required)
      {
         this.required = required;
      }

      /***********************************************************************
       ** Serialized by the same Jackson mapper as the core schema fields.
       ***********************************************************************/
      public List<String> getRequired()
      {
         return required;
      }
   }

   /***************************************************************************
    ** Standard numeric bounds missing from RC20's base Schema model.
    ***************************************************************************/
   public static class ScannerMaximumSchema extends Schema
   {
      /***********************************************************************
       ** Scanner input accepts at least one character.
       ***********************************************************************/
      public int getMinimum()
      {
         return 1;
      }

      /***********************************************************************
       ** Never exceed the persistence and process input limit.
       ***********************************************************************/
      public int getMaximum()
      {
         return 4096;
      }
   }
}
