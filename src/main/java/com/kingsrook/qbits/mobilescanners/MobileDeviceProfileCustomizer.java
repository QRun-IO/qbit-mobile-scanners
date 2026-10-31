/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.customizers.AbstractPreUpdateCustomizer;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Restrict ordinary record editing to the three administrator profile fields.
 *******************************************************************************/
public class MobileDeviceProfileCustomizer extends AbstractPreUpdateCustomizer
{
   private static final Set<String> EDITABLE = Set.of("shortName", "displayName", "description");

   /***************************************************************************
    ** Both permission entry and persistence lifecycle apply this same allowlist.
    ***************************************************************************/
   static void sanitize(List<QRecord> records) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      MobileSessionService.requireNotEnded();
      if(records == null)
      {
         throw new QBadRequestException("Device records are required.");
      }
      for(QRecord record : records)
      {
         QRecord stored = MobileService.get(MobileScannersQBitProducer.DEVICE, record.getValueString("id"));
         if(stored == null)
         {
            throw new QBadRequestException("Device not found.");
         }
         for(String key : new ArrayList<>(record.getValues().keySet()))
         {
            if(!"id".equals(key) && !EDITABLE.contains(key))
            {
               if(!Objects.equals(record.getValue(key), stored.getValue(key)))
               {
                  throw new QPermissionDeniedException("Only device short name, display name and description can be edited.");
               }
               record.getValues().remove(key);
            }
         }
         if(record.getValues().containsKey("shortName"))
         {
            record.setValue("shortName", canonical(MobileService.required(record.getValues(), "shortName", 32)));
         }
         for(String key : List.of("displayName", "description"))
         {
            MobileService.optional(record.getValues(), key, "description".equals(key) ? 2000 : 200);
         }
      }
   }

   /***************************************************************************
    ** Canonical values have a database unique index, including concurrent edits.
    ***************************************************************************/
   static String canonical(String value) throws QBadRequestException
   {
      String canonical = value.trim().toLowerCase(Locale.ROOT);
      if(!canonical.matches("[a-z0-9][a-z0-9-]{2,31}"))
      {
         throw new QBadRequestException("Short name must be 3 to 32 lowercase letters, digits or hyphens, starting with a letter or digit.");
      }
      return canonical;
   }

   /***************************************************************************
    ** Internal approval and heartbeat writes remain distinct from generic edits.
    ***************************************************************************/
   @Override
   public List<QRecord> apply(List<QRecord> records) throws QException
   {
      if(!Boolean.TRUE.equals(MobilePermissions.INTERNAL.get()))
      {
         sanitize(records);
      }
      return records;
   }
}
