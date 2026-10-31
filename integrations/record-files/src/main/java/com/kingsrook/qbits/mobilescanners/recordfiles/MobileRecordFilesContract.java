/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.recordfiles;


import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qbits.mobilescanners.MobileScannersSchemas;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Optional widget vocabulary, independently versioned from both base qbits.
 ** The current core extension registry has no schema-only registration hook.
 *******************************************************************************/
public class MobileRecordFilesContract extends AbstractJsonEndpointSpec
{
   public static final String VERSION = "1.0.0";

   /***************************************************************************
    ** Host opt-in advertises the native block and contributes its typed schemas.
    ***************************************************************************/
   public static void register(QInstance instance)
   {
      MiddlewareSpecExtensionsMetaData.ofOrWithNew(instance).withExtension(new MiddlewareSpecExtension()
         .withName("mobile-record-files").withSemanticVersion(VERSION)
         .withDescription("Native RECORD_FILES_CAPTURE widget vocabulary; record-files independently authorizes ingestion.")
         .withSpec(MobileRecordFilesContract.class));
   }

   /***************************************************************************
    ** Native clients verify this static descriptor before enabling capture UI.
    ***************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return new BasicOperation().withHttpMethod(HttpMethod.GET).withPath("/qbit/mobile-record-files/contract")
         .withOperationId("mobileRecordFilesContract").withShortSummary("Discover native record photo widget semantics")
         .withLongDescription("Public static vocabulary only. No parent reference or upload authority is disclosed. Clients must separately verify record-files capabilities for the widget's server-selected parent.");
   }

   /***************************************************************************
    ** Static semantics disclose neither records nor authenticated identity.
    ***************************************************************************/
   @Override
   public boolean isSecured()
   {
      return false;
   }

   /***************************************************************************
    ** A versioned, typed descriptor rather than inferred widget/process names.
    ***************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return "MobileRecordFilesContractV1";
   }

   /***************************************************************************
    ** These values describe rendering only and cannot authorize an upload.
    ***************************************************************************/
   @Override
   protected Object handle(JsonInput input)
   {
      return Map.of("version", VERSION, "blockType", "RECORD_FILES_CAPTURE", "widgetType", "composite");
   }

   /***************************************************************************
    ** Ordinary native widget responses reference these additive block schemas.
    ***************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      Map<String, Schema> schemas = new TreeMap<>();
      schemas.put("MobileRecordFilesContractV1", object(Map.of("version", literal(VERSION),
         "blockType", literal("RECORD_FILES_CAPTURE"), "widgetType", literal("composite")), "version", "blockType", "widgetType"));
      schemas.put("MobileRecordFilesCaptureParentV1", object(Map.of("tableName", new NonblankString().withType(Type.STRING).withMaxLength(255),
         "recordId", new NonblankString().withType(Type.STRING).withMaxLength(255).withDescription("Opaque server-selected record ID; never coerce to a number.")), "tableName", "recordId"));
      schemas.put("MobileRecordFilesCaptureValuesV1", object(Map.of("parent", ref("MobileRecordFilesCaptureParentV1"),
         "title", new Schema().withType(Type.STRING).withMaxLength(200),
         "instructions", new Schema().withType(Type.STRING).withMaxLength(2000)), "parent"));
      schemas.put("MobileRecordFilesCaptureBlockV1", object(Map.of("blockId", new NonblankString().withType(Type.STRING).withMaxLength(200),
         "blockType", literal("RECORD_FILES_CAPTURE"), "values", ref("MobileRecordFilesCaptureValuesV1")), "blockId", "blockType", "values"));
      schemas.put("MobileRecordFilesCaptureWidgetV1", object(Map.of("type", literal("composite"), "blockType", literal("COMPOSITE"),
         "subBlocks", new Schema().withType(Type.ARRAY).withItems(ref("MobileRecordFilesCaptureBlockV1"))), "type", "blockType", "subBlocks"));
      return schemas;
   }

   /***************************************************************************
    ** Known fields are typed; additive future fields remain compatible.
    ***************************************************************************/
   private static Schema object(Map<String, Schema> properties, String... required)
   {
      return new MobileScannersSchemas.RequiredObjectSchema(List.of(required)).withType(Type.OBJECT).withProperties(new TreeMap<>(properties));
   }

   /***************************************************************************
    ** Fixed wire discriminator, never a localized label or process name.
    ***************************************************************************/
   private static Schema literal(String value)
   {
      return new Schema().withType(Type.STRING).withEnumValues(List.of(value));
   }

   /***************************************************************************
    ** Reuse typed parent and values components.
    ***************************************************************************/
   private static Schema ref(String name)
   {
      return new Schema().withRefToSchema(name);
   }

   /***************************************************************************
    ** Standard JSON Schema keywords absent from the frozen core Schema model.
    ***************************************************************************/
   public static class NonblankString extends Schema
   {
      /***********************************************************************
       ** Require at least one character.
       ***********************************************************************/
      public int getMinLength()
      {
         return 1;
      }

      /***********************************************************************
       ** Require a non-whitespace character without altering opaque IDs.
       ***********************************************************************/
      public String getPattern()
      {
         return "\\S";
      }
   }
}
