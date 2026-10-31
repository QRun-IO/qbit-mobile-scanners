/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessMetaDataAdjustment;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Freeze native single/bulk record selections for guarded admin processes.
 *******************************************************************************/
final class MobileAdminSelection
{
   /***************************************************************************
    ** Standalone launches retain manual entry; selections never apply early.
    ***************************************************************************/
   static void prepare(RunBackendStepInput input, RunBackendStepOutput output, String selectedTable, String idField, String noun) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      String selection = input.getValueString("recordsParam");
      if(selection == null || selection.isBlank())
      {
         output.setRecords(new ArrayList<>());
         output.addValue("selectionSummary", "Enter the record IDs to process, separated by commas.");
         return;
      }
      String table = input.getValueString("tableName");
      if(table != null && !selectedTable.equals(table))
      {
         throw new QException("Select records from the process table.");
      }
      List<String> selected;
      if("recordIds".equals(selection))
      {
         selected = resolve(MobileService.required(input.getValues(), "recordIds", 3700), selectedTable);
      }
      else if("filterJSON".equals(selection))
      {
         selected = resolveFilter(input, selectedTable);
      }
      else
      {
         throw new QException("Select mobile records to process.");
      }
      List<String> labels = new ArrayList<>();
      List<QRecord> snapshot = new ArrayList<>();
      for(String id : selected)
      {
         var device = MobileService.get(selectedTable, id);
         labels.add((device.getValueString("displayName") == null ? device.getValueString("userId") : device.getValueString("displayName")) + " (" + id + ")");
         snapshot.add(new QRecord().withValue("id", id));
      }
      output.setRecords(snapshot);
      output.addValue(idField, String.join(",", selected));
      output.addValue("selectionSummary", selected.size() + " " + noun + " selected: " + String.join("; ", labels));
      output.setProcessMetaDataAdjustment(new ProcessMetaDataAdjustment().withUpdatedField(
         new QFieldMetaData(idField, QFieldType.STRING).withLabel("Record IDs").withMaxLength(3700).withIsHidden(true).withIsRequired(false)));
   }

   /***************************************************************************
    ** Native select-all/first-N filters become a fixed, bounded record snapshot.
    ***************************************************************************/
   private static List<String> resolveFilter(RunBackendStepInput input, String selectedTable) throws QException
   {
      QQueryFilter filter;
      try
      {
         filter = JsonUtils.toObject(MobileService.required(input.getValues(), "filterJSON", 100000), QQueryFilter.class);
      }
      catch(Exception e)
      {
         throw new QException("The selected device filter is invalid.", e);
      }
      if(filter == null || (filter.getLimit() != null && filter.getLimit() < 1))
      {
         throw new QException("Select between 1 and 100 records.");
      }
      filter.setLimit(filter.getLimit() == null ? 101 : Math.min(filter.getLimit(), 101));
      var records = new QueryAction().execute(new QueryInput().withTableName(selectedTable).withFilter(filter)).getRecords();
      if(records.isEmpty() || records.size() > 100)
      {
         throw new QException("Select between 1 and 100 records.");
      }
      return resolve(String.join(",", records.stream().map(record -> record.getValueString("id")).toList()), selectedTable);
   }

   /***************************************************************************
    ** Validate the entire bounded selection before any record is changed.
    ***************************************************************************/
   static List<String> resolve(String value, String selectedTable) throws QException
   {
      if(value == null || value.isBlank() || value.length() > 3700)
      {
         throw new QException("Enter between 1 and 100 record IDs.");
      }
      String[] parts = value.split(",", -1);
      if(parts.length > 100)
      {
         throw new QException("Select at most 100 records at a time.");
      }
      Set<String> ids = new LinkedHashSet<>();
      for(String part : parts)
      {
         String id = part.trim();
         try
         {
            if(!UUID.fromString(id).toString().equals(id))
            {
               throw new IllegalArgumentException();
            }
         }
         catch(IllegalArgumentException e)
         {
            throw new QException("Each record ID must be a valid UUID.");
         }
         if(MobileService.get(selectedTable, id) == null)
         {
            throw new QException("A selected mobile record no longer exists.");
         }
         ids.add(id);
      }
      return new ArrayList<>(ids);
   }
}
