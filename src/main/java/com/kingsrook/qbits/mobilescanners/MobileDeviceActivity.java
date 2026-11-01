/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QVirtualFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Queryable device connectivity and optional foreground location observations.
 *******************************************************************************/
final class MobileDeviceActivity
{
   /***************************************************************************
    ** No independent timers or background location tracking.
    ***************************************************************************/
   private MobileDeviceActivity()
   {
   }



   /***************************************************************************
    ** Ordinary QQQ fields support native filtering and coordinate inspection.
    ***************************************************************************/
   static void addFields(QTableMetaData table)
   {
      table.withVirtualField(new QVirtualFieldMetaData("online", QFieldType.BOOLEAN).withLabel("Online").withIsEditable(false)
         .withIsQuerySelectable(true).withCriteriaExpander(new QCodeReference(MobileDeviceOnline.class)));
      table.withCustomizer(TableCustomizers.POST_QUERY_RECORD, new QCodeReference(MobileDeviceOnline.class));
      table.withField(new QFieldMetaData("locationStatus", QFieldType.STRING).withMaxLength(30));
      for(String name : List.of("lastSeenAt", "locationCapturedAt", "locationReceivedAt"))
      {
         table.withField(new QFieldMetaData(name, QFieldType.DATE_TIME));
      }
      for(String name : List.of("latitude", "longitude", "accuracyMeters"))
      {
         table.withField(new QFieldMetaData(name, QFieldType.DECIMAL));
      }
   }



   /***************************************************************************
    ** Presence reflects authenticated traffic independently of approval status.
    ***************************************************************************/
   static void seen(String id) throws QException
   {
      update(new QRecord().withValue("id", id).withValue("lastSeenAt", Instant.now()));
   }



   /***************************************************************************
    ** Preserve last known coordinates with explicit availability and timestamps.
    ***************************************************************************/
   static void heartbeat(String id, Map<String, Object> input, boolean foreground) throws QException
   {
      QRecord changes = new QRecord().withValue("id", id).withValue("lastSeenAt", Instant.now());
      String status = MobileService.optional(input, "locationStatus", 30);
      if(status != null && !List.of("AVAILABLE", "PERMISSION_DENIED", "DISABLED", "UNAVAILABLE").contains(status))
      {
         throw new QBadRequestException("Unknown location status.");
      }
      if("AVAILABLE".equals(status) && input.get("location") == null)
      {
         throw new QBadRequestException("Available location requires coordinates and a capture timestamp.");
      }
      changes.setValue("locationStatus", status == null ? "UNAVAILABLE" : status);
      if(input.get("location") != null)
      {
         if(!foreground || !"AVAILABLE".equals(status) || !(input.get("location") instanceof Map<?, ?> location))
         {
            throw new QBadRequestException("Location requires an available foreground observation.");
         }
         changes.setValue("latitude", decimal(location, "latitude", -90, 90));
         changes.setValue("longitude", decimal(location, "longitude", -180, 180));
         changes.setValue("accuracyMeters", decimal(location, "accuracyMeters", 0, 10000000));
         try
         {
            Object captured = location.get("capturedAt");
            if(!(captured instanceof String text) || text.length() > 40)
            {
               throw new IllegalArgumentException();
            }
            changes.setValue("locationCapturedAt", Instant.parse(text));
         }
         catch(Exception failure)
         {
            throw new QBadRequestException("Location capturedAt must be an ISO-8601 timestamp.");
         }
         changes.setValue("locationReceivedAt", Instant.now());
      }
      update(changes);
   }



   /***************************************************************************
    ** Reject nonnumeric, nonfinite and out-of-range coordinates before storage.
    ***************************************************************************/
   private static BigDecimal decimal(Map<?, ?> input, String key, int minimum, int maximum) throws QException
   {
      if(!(input.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue())
         || number.doubleValue() < minimum || number.doubleValue() > maximum)
      {
         throw new QBadRequestException("Invalid location " + key + ".");
      }
      return new BigDecimal(number.toString());
   }



   /***************************************************************************
    ** Only trusted telemetry writes may update presence and location fields.
    ***************************************************************************/
   private static void update(QRecord record) throws QException
   {
      MobileService.internally(() ->
      {
         var saved = new UpdateAction().execute(new UpdateInput().withTableName(MobileScannersQBitProducer.DEVICE)
            .withTransaction(MobileService.TRANSACTION.get()).withRecord(record)).getRecords();
         if(saved == null || saved.size() != 1 || (saved.getFirst().getErrors() != null && !saved.getFirst().getErrors().isEmpty()))
         {
            throw new QException("Device activity could not be updated.");
         }
         return null;
      });
   }
}
