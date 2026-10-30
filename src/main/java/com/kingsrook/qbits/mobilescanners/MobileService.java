/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.get.GetInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Persistence and authorization shared by routes and native process steps.
 *******************************************************************************/
public final class MobileService
{
   /***************************************************************************
    ** No service instances or global mutable storage.
    ***************************************************************************/
   private MobileService()
   {
   }

   /***************************************************************************
    ** Register once; a guessed installation ID never rotates an approved token.
    ***************************************************************************/
   public static Map<String, Object> enroll(Map<String, Object> input) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      String id = uuid(required(input, "installationId", 36));
      if(get(MobileScannersQBitProducer.DEVICE, id) != null || get(MobileScannersQBitProducer.CREDENTIAL, id) != null)
      {
         throw new EnrollmentConflictException();
      }
      byte[] entropy = new byte[32];
      new SecureRandom().nextBytes(entropy);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
      QRecord credential = new QRecord().withValue("id", id).withValue("tokenHash", hash(token));
      QRecord device = new QRecord().withValue("id", id).withValue("installationId", id).withValue("status", "PENDING")
         .withValue("enrolledBy", userId()).withValue("enrolledAt", Instant.now());
      for(String name : new String[] { "displayName", "manufacturer", "model", "osVersion", "appVersion" })
      {
         device.setValue(name, optional(input, name, 200));
      }
      // Credential first: a failed second insert leaves no usable approved device.
      insert(MobileScannersQBitProducer.CREDENTIAL, credential);
      insert(MobileScannersQBitProducer.DEVICE, device);
      return Map.of("deviceId", id, "deviceToken", token, "status", "PENDING");
   }

   /***************************************************************************
    ** Compare a token digest in constant time; return no credential material.
    ***************************************************************************/
   static QRecord authenticateDevice(String id, String token) throws QException
   {
      if(id == null || token == null || token.length() > 128)
      {
         throw new QPermissionDeniedException("Invalid device credential.");
      }
      QRecord credential = get(MobileScannersQBitProducer.CREDENTIAL, id);
      String stored = credential == null ? "" : credential.getValueString("tokenHash");
      if(!MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), hash(token).getBytes(StandardCharsets.UTF_8)))
      {
         throw new QPermissionDeniedException("Invalid device credential.");
      }
      QRecord device = get(MobileScannersQBitProducer.DEVICE, id);
      if(device == null)
      {
         throw new QPermissionDeniedException("Invalid device credential.");
      }
      return device;
   }

   /***************************************************************************
    ** Re-read status for every operation, including resumed asynchronous steps.
    ***************************************************************************/
   static QRecord requireApproved(String id) throws QException
   {
      QRecord device = id == null ? null : get(MobileScannersQBitProducer.DEVICE, id);
      if(device == null || !"APPROVED".equals(device.getValueString("status")))
      {
         throw new QPermissionDeniedException("This device is not approved.");
      }
      return device;
   }

   /***************************************************************************
    ** Only admin processes may change device approval.
    ***************************************************************************/
   public static void setApproval(String deviceId, boolean approved) throws QException
   {
      MobilePermissions.require(MobilePermissions.ADMIN);
      if(get(MobileScannersQBitProducer.DEVICE, deviceId) == null)
      {
         throw new QException("Device not found.");
      }
      QRecord record = new QRecord().withValue("id", deviceId).withValue("status", approved ? "APPROVED" : "REVOKED")
         .withValue("approvedBy", userId()).withValue("approvedAt", Instant.now());
      internally(() ->
      {
         var output = new UpdateAction().execute(new UpdateInput().withTableName(MobileScannersQBitProducer.DEVICE).withRecord(record));
         check(output.getRecords().getFirst());
         return null;
      });
   }

   /***************************************************************************
    ** A deterministic primary key deduplicates request retries across servers.
    ***************************************************************************/
   static QRecord capture(Map<String, Serializable> input) throws QException
   {
      MobilePermissions.require(MobilePermissions.SCAN);
      String deviceId = QContext.getQSession() instanceof VerifiedDeviceSession verified ? verified.deviceId() : null;
      QRecord device = requireApproved(deviceId);
      String eventId = uuid(required(input, "eventId", 36));
      String barcode = required(input, "barcode", 4096);
      String capturedAt = required(input, "capturedAt", 40);
      Instant captured;
      try
      {
         captured = Instant.parse(capturedAt);
      }
      catch(Exception e)
      {
         throw new QException("capturedAt must be an ISO-8601 timestamp.");
      }
      String id = hash(deviceId + ":" + eventId);
      QRecord record = new QRecord().withValue("id", id).withValue("deviceId", deviceId).withValue("eventId", eventId)
         .withValue("barcode", barcode).withValue("symbology", optional(input, "symbology", 200)).withValue("scanSource", optional(input, "scanSource", 200))
         .withValue("capturedAt", captured).withValue("receivedAt", Instant.now()).withValue("userId", userId())
         .withValue("appId", MobileScannersQBitProducer.APP).withValue("processName", MobileScannersQBitProducer.CAPTURE);
      for(String field : new String[] { "manufacturer", "model", "osVersion", "appVersion" })
      {
         record.setValue(field, device.getValue(field));
      }
      QRecord existing = get(MobileScannersQBitProducer.SCAN, id);
      if(existing != null)
      {
         return sameEvent(existing, record);
      }
      try
      {
         return insert(MobileScannersQBitProducer.SCAN, record);
      }
      catch(QException failure)
      {
         // A concurrent retry may win the primary-key insert; verify its payload.
         existing = get(MobileScannersQBitProducer.SCAN, id);
         if(existing != null)
         {
            return sameEvent(existing, record);
         }
         throw failure;
      }
   }

   /***************************************************************************
    ** Reusing a request ID for another scan is an error, not a silent overwrite.
    ***************************************************************************/
   private static QRecord sameEvent(QRecord existing, QRecord requested) throws QException
   {
      for(String field : new String[] { "barcode", "symbology", "scanSource", "userId" })
      {
         if(!Objects.equals(existing.getValueString(field), requested.getValueString(field)))
         {
            throw new QException("This eventId was already used for a different scan.");
         }
      }
      return existing;
   }

   /***************************************************************************
    ** Read only through QQQ and the narrowly scoped internal permission grant.
    ***************************************************************************/
   static QRecord get(String table, String id) throws QException
   {
      return internally(() ->
      {
         GetInput input = new GetInput();
         input.setTableName(table);
         input.setPrimaryKey(id);
         input.setShouldOmitHiddenFields(false);
         return new GetAction().execute(input).getRecord();
      });
   }

   /***************************************************************************
    ** Row-level errors must be checked before acknowledging a scan.
    ***************************************************************************/
   private static QRecord insert(String table, QRecord record) throws QException
   {
      return internally(() ->
      {
         var output = new InsertAction().execute(new InsertInput().withTableName(table).withRecord(record));
         if(output.getRecords() == null || output.getRecords().size() != 1)
         {
            throw new QException("The record could not be saved.");
         }
         QRecord saved = output.getRecords().getFirst();
         check(saved);
         return saved;
      });
   }

   /***************************************************************************
    ** Never expose persistence details or credentials in a client error.
    ***************************************************************************/
   private static void check(QRecord record) throws QException
   {
      if(record.getErrors() != null && !record.getErrors().isEmpty())
      {
         throw new QException("The record could not be saved.");
      }
   }

   /***************************************************************************
    ** Restore the permission scope even when a backend throws.
    ***************************************************************************/
   private static <T> T internally(Operation<T> operation) throws QException
   {
      Boolean previous = MobilePermissions.INTERNAL.get();
      MobilePermissions.INTERNAL.set(true);
      try
      {
         return operation.run();
      }
      finally
      {
         if(previous == null)
         {
            MobilePermissions.INTERNAL.remove();
         }
         else
         {
            MobilePermissions.INTERNAL.set(previous);
         }
      }
   }

   /***************************************************************************
    ** QQQ operation with checked failure.
    ***************************************************************************/
   private interface Operation<T>
   {
      /***********************************************************************
       ** Execute one internal persistence operation.
       ***********************************************************************/
      T run() throws QException;
   }

   /***************************************************************************
    ** Server-authenticated identity only.
    ***************************************************************************/
   private static String userId()
   {
      return QContext.getQSession().getUser().getIdReference();
   }

   /***************************************************************************
    ** Required bounded strings preserve leading zeroes in barcodes.
    ***************************************************************************/
   static String required(Map<String, ?> input, String name, int maximum) throws QException
   {
      String value = optional(input, name, maximum);
      if(value == null || value.isEmpty())
      {
         throw new QBadRequestException(name + " is required.");
      }
      return value;
   }

   /***************************************************************************
    ** Reject non-string and oversized metadata.
    ***************************************************************************/
   private static String optional(Map<String, ?> input, String name, int maximum) throws QException
   {
      Object value = input.get(name);
      if(value == null)
      {
         return null;
      }
      if(!(value instanceof String text) || text.length() > maximum)
      {
         throw new QBadRequestException(name + " must be a string of at most " + maximum + " characters.");
      }
      return text;
   }

   /***************************************************************************
    ** Enforce canonical UUIDs before using them as identifiers.
    ***************************************************************************/
   private static String uuid(String value) throws QException
   {
      try
      {
         String canonical = UUID.fromString(value).toString();
         if(!canonical.equalsIgnoreCase(value))
         {
            throw new IllegalArgumentException();
         }
         return canonical;
      }
      catch(IllegalArgumentException e)
      {
         throw new QBadRequestException("A canonical UUID is required.");
      }
   }

   /***************************************************************************
    ** SHA-256 of high-entropy tokens, never plaintext storage.
    ***************************************************************************/
   private static String hash(String value)
   {
      try
      {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
      }
      catch(java.security.NoSuchAlgorithmException e)
      {
         throw new IllegalStateException(e);
      }
   }

   /***************************************************************************
    ** Explicit conflict so callers can restart enrollment with a new identity.
    ***************************************************************************/
   public static class EnrollmentConflictException extends QException
   {
      /***********************************************************************
       ** No existing device secret is disclosed or rotated.
       ***********************************************************************/
      public EnrollmentConflictException()
      {
         super("This installation is already enrolled. Recover its credential or enroll a new installation for approval.");
      }
   }
}
