/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners.recordfiles;


import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;


/*******************************************************************************
 ** Host renderers supply an authorized record reference, never inferred fields.
 ** Record Files still reauthorizes that parent independently for every operation.
 *******************************************************************************/
public final class RecordFilesCaptureWidgetData extends QWidgetData
{
   private final String tableName;
   private final String recordId;
   private final String title;
   private final String instructions;

   /***************************************************************************
    ** Values are display/selection metadata, never proof of authorization.
    ***************************************************************************/
   public RecordFilesCaptureWidgetData(String tableName, String recordId, String title, String instructions)
   {
      this.tableName = Objects.requireNonNull(tableName);
      this.recordId = Objects.requireNonNull(recordId);
      if(tableName.isBlank() || recordId.isBlank() || tableName.length() > 255 || recordId.length() > 255
         || tableName.codePoints().anyMatch(Character::isISOControl) || recordId.codePoints().anyMatch(Character::isISOControl))
      {
         throw new IllegalArgumentException("A record reference is required.");
      }
      if(title != null && title.length() > 200 || instructions != null && instructions.length() > 2000)
      {
         throw new IllegalArgumentException("Capture display text exceeds its declared bounds.");
      }
      this.title = title;
      this.instructions = instructions;
   }

   /***************************************************************************
    ** Standard composite widget envelope used by native semantic clients.
    ***************************************************************************/
   @Override
   public String getType()
   {
      return "composite";
   }

   /***************************************************************************
    ** Standard composite block discriminator.
    ***************************************************************************/
   public String getBlockType()
   {
      return "COMPOSITE";
   }

   /***************************************************************************
    ** Native capture controls require compatible extension and record capability.
    ***************************************************************************/
   public List<Map<String, Serializable>> getSubBlocks()
   {
      HashMap<String, Serializable> values = new HashMap<>();
      values.put("parent", new HashMap<>(Map.of("tableName", tableName, "recordId", recordId)));
      if(title != null)
      {
         values.put("title", title);
      }
      if(instructions != null)
      {
         values.put("instructions", instructions);
      }
      return List.of(Map.of("blockId", "recordFilesCapture", "blockType", "RECORD_FILES_CAPTURE", "values", values));
   }
}
