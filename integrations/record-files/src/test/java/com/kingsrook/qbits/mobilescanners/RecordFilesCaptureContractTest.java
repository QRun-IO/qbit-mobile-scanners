/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsrook.qbits.mobilescanners.recordfiles.MobileRecordFilesContract;
import com.kingsrook.qbits.mobilescanners.recordfiles.RecordFilesCaptureWidgetData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*******************************************************************************
 ** Shared schema conformance for actual HTTP widgets and invalid parent shapes.
 *******************************************************************************/
class RecordFilesCaptureContractTest
{
   private static final ObjectMapper JSON = new ObjectMapper();



   /***************************************************************************
    ** Opaque record IDs survive serialization, without name-based inference.
    ***************************************************************************/
   @Test
   void typedWidgetPreservesOpaqueParentAndRejectsMalformedValues() throws Exception
   {
      JsonNode schemas = JSON.valueToTree(new MobileRecordFilesContract().defineComponentSchemas());
      JsonNode widget = JSON.valueToTree(new RecordFilesCaptureWidgetData("anyConfiguredTable", "00123", null, null));
      assertConforms(widget, schemas.path("MobileRecordFilesCaptureWidgetV1"), schemas);
      assertEquals("00123", widget.at("/subBlocks/0/values/parent/recordId").asText());
      assertEquals("RECORD_FILES_CAPTURE", widget.at("/subBlocks/0/blockType").asText());
      assertTrue(widget.at("/subBlocks/0/values").has("parent"));
      for(String invalid : List.of("{}", "{\"tableName\":\"x\"}", "{\"tableName\":\"x\",\"recordId\":null}",
         "{\"tableName\":\"x\",\"recordId\":123}", "{\"tableName\":\"x\",\"recordId\":\" \"}"))
      {
         JsonNode parent = JSON.readTree(invalid);
         assertThrows(AssertionError.class, () -> assertConforms(parent, schemas.path("MobileRecordFilesCaptureParentV1"), schemas));
      }
   }



   /***************************************************************************
    ** Server renderers cannot publish invalid or oversized parent coordinates.
    ***************************************************************************/
   @Test
   void rendererRejectsInvalidParentBeforePublishingMetadata()
   {
      assertThrows(NullPointerException.class, () -> new RecordFilesCaptureWidgetData(null, "1", null, null));
      for(String invalid : List.of("", " ", "x".repeat(256), "bad\nrecord"))
      {
         assertThrows(IllegalArgumentException.class, () -> new RecordFilesCaptureWidgetData("table", invalid, null, null));
         assertThrows(IllegalArgumentException.class, () -> new RecordFilesCaptureWidgetData(invalid, "1", null, null));
      }
   }



   /***************************************************************************
    ** Validate the contract keywords actually published by this small vocabulary.
    ***************************************************************************/
   static void assertConforms(JsonNode value, JsonNode schema, JsonNode components)
   {
      assertTrue(schema.isObject() && !schema.isEmpty(), "A registered schema is required.");
      if(schema.hasNonNull("$ref"))
      {
         assertConforms(value, components.path(schema.path("$ref").asText().substring("#/components/schemas/".length())), components);
         return;
      }
      switch(schema.path("type").asText())
      {
         case "object" ->
         {
            assertTrue(value.isObject(), "Expected object.");
            schema.path("required").forEach(required -> assertTrue(value.has(required.asText()), "Missing required field."));
            schema.path("properties").fields().forEachRemaining(property ->
            {
               if(value.has(property.getKey()))
               {
                  assertConforms(value.get(property.getKey()), property.getValue(), components);
               }
            });
         }
         case "array" ->
         {
            assertTrue(value.isArray(), "Expected array.");
            value.forEach(item -> assertConforms(item, schema.path("items"), components));
         }
         case "string" ->
         {
            assertTrue(value.isTextual(), "Expected opaque string.");
            assertTrue(value.asText().length() >= schema.path("minLength").asInt(0));
            assertTrue(value.asText().length() <= schema.path("maxLength").asInt(Integer.MAX_VALUE));
            if(schema.hasNonNull("pattern"))
            {
               assertTrue(java.util.regex.Pattern.compile(schema.path("pattern").asText()).matcher(value.asText()).find());
            }
            if(schema.hasNonNull("enum"))
            {
               assertTrue(java.util.stream.StreamSupport.stream(schema.path("enum").spliterator(), false).anyMatch(value::equals));
            }
         }
         default -> throw new AssertionError("Unsupported contract schema type.");
      }
   }
}
