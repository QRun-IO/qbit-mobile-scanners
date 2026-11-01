/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;


/*******************************************************************************
 ** Bounded, ephemeral, demand-driven frames. No images are written to disk.
 *******************************************************************************/
final class MobileScreenService
{
   static final int MAX_BYTES = 262144;
   private static final int MAX_BASE64 = 349528;
   private static final int MAX_LEASES = 64;
   private final Map<String, Lease> leases = new HashMap<>();
   private final Clock clock;

   /***************************************************************************
    ** Production clock.
    ***************************************************************************/
   MobileScreenService()
   {
      this(Clock.systemUTC());
   }

   /***************************************************************************
    ** Explicit clock makes expiry behavior independently testable.
    ***************************************************************************/
   MobileScreenService(Clock clock)
   {
      this.clock = clock;
   }

   /***************************************************************************
    ** Refreshing an authorized visible admin widget renews capture demand.
    ***************************************************************************/
   synchronized View view(String sessionId) throws QException
   {
      return view(sessionId, MobileCaptureMode.APP_CONTENT);
   }

   /***************************************************************************
    ** A change in capture scope invalidates every old pixel and lease identity.
    ***************************************************************************/
   synchronized View view(String sessionId, MobileCaptureMode mode) throws QException
   {
      prune();
      Lease lease = leases.get(sessionId);
      if(lease != null && lease.mode != mode)
      {
         leases.remove(sessionId);
         lease = null;
      }
      if(lease == null)
      {
         if(leases.size() >= MAX_LEASES)
         {
            throw new QBadRequestException("Too many active screen viewers. Close another view and retry.");
         }
         lease = new Lease(mode);
         leases.put(sessionId, lease);
      }
      lease.expiresAt = clock.instant().plusSeconds(30);
      if(lease.receivedAt == null || lease.receivedAt.plusSeconds(10).isBefore(clock.instant()))
      {
         lease.jpeg = null;
         return new View(mode == MobileCaptureMode.DEVICE_SCREEN ? "Waiting for a consented device screen" : "Waiting for a current app screen", null, null);
      }
      return new View(mode == MobileCaptureMode.DEVICE_SCREEN ? "Live device screen — operator consent" : "Live app screen", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(lease.jpeg), lease.receivedAt);
   }

   /***************************************************************************
    ** Return only current demand; the server clock defines lease lifetime.
    ***************************************************************************/
   synchronized Map<String, Object> activeLease(String sessionId)
   {
      prune();
      Lease lease = leases.get(sessionId);
      return lease == null ? null : Map.of("leaseId", lease.id, "expiresAt", lease.expiresAt.toString(), "frameIntervalMillis", 1000,
         "maxWidth", 640, "maxHeight", 1280, "maxBytes", MAX_BYTES, "nextSequence", lease.sequence + 1, "captureMode", lease.mode.name());
   }

   /***************************************************************************
    ** Reject oversize encoding before decoding and dimensions before pixels.
    ***************************************************************************/
   synchronized void upload(String sessionId, Map<String, Object> input) throws QException
   {
      prune();
      Lease lease = leases.get(sessionId);
      if(lease == null || !lease.id.equals(MobileService.required(input, "leaseId", 36)))
      {
         throw new QPermissionDeniedException("No active screen viewing lease.");
      }
      if(MobileCaptureMode.parse(input.get("captureMode")) != lease.mode)
      {
         throw new QPermissionDeniedException("Frame capture mode does not match the viewing lease.");
      }
      long sequence = integer(input, "sequence", 0, 9007199254740990L);
      if(sequence <= lease.sequence || (lease.receivedAt != null && lease.receivedAt.plusMillis(750).isAfter(clock.instant())))
      {
         throw new FrameConflictException();
      }
      int width = (int) integer(input, "width", 1, 640);
      int height = (int) integer(input, "height", 1, 1280);
      if(!"image/jpeg".equals(input.get("mimeType")))
      {
         throw new QBadRequestException("Only JPEG screen frames are supported.");
      }
      try
      {
         Instant.parse(MobileService.required(input, "capturedAt", 40));
      }
      catch(Exception exception)
      {
         throw new QBadRequestException("capturedAt must be an ISO-8601 timestamp.");
      }
      String encoded = MobileService.required(input, "imageBase64", MAX_BASE64);
      byte[] jpeg;
      try
      {
         jpeg = Base64.getDecoder().decode(encoded);
      }
      catch(IllegalArgumentException exception)
      {
         throw new QBadRequestException("Invalid image encoding.");
      }
      if(jpeg.length > MAX_BYTES || jpeg.length < 4 || jpeg[0] != (byte) 0xff || jpeg[1] != (byte) 0xd8)
      {
         throw new QBadRequestException("Invalid or oversized JPEG frame.");
      }
      validateJpeg(jpeg, width, height);
      lease.sequence = sequence;
      lease.jpeg = jpeg;
      lease.receivedAt = clock.instant();
   }

   /***************************************************************************
    ** Image metadata is checked before allocating a bounded decoded raster.
    ***************************************************************************/
   private static void validateJpeg(byte[] jpeg, int width, int height) throws QBadRequestException
   {
      try(MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg)))
      {
         var readers = ImageIO.getImageReaders(stream);
         if(!readers.hasNext())
         {
            throw new IllegalArgumentException();
         }
         ImageReader reader = readers.next();
         try
         {
            reader.setInput(stream, true, true);
            if(!"JPEG".equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) != width || reader.getHeight(0) != height)
            {
               throw new IllegalArgumentException();
            }
            if(reader.read(0) == null)
            {
               throw new IllegalArgumentException();
            }
         }
         finally
         {
            reader.dispose();
         }
      }
      catch(Exception exception)
      {
         throw new QBadRequestException("Invalid JPEG frame or dimensions.");
      }
   }

   /***************************************************************************
    ** Ending/revoking viewing drops all retained pixels and capture demand.
    ***************************************************************************/
   synchronized void remove(String id)
   {
      leases.remove(id);
   }

   /***************************************************************************
    ** Expiry is enforced on reads and writes, even without a cleanup scheduler.
    ***************************************************************************/
   private void prune()
   {
      leases.values().removeIf(lease -> lease.expiresAt == null || !lease.expiresAt.isAfter(clock.instant()));
   }

   /***************************************************************************
    ** Preserve integer semantics and reject overflow/floating-point coercion.
    ***************************************************************************/
   private static long integer(Map<String, Object> input, String field, long minimum, long maximum) throws QBadRequestException
   {
      Object value = input.get(field);
      if(!(value instanceof Number number) || number.doubleValue() != number.longValue() || number.longValue() < minimum || number.longValue() > maximum)
      {
         throw new QBadRequestException(field + " is outside the supported integer range.");
      }
      return number.longValue();
   }

   /***************************************************************************
    ** A safe widget payload containing only the most recent eligible image.
    ***************************************************************************/
   record View(String status, String dataUri, Instant receivedAt)
   {
   }

   /***************************************************************************
    ** At most one frame per active session, at most 64 sessions per instance.
    ***************************************************************************/
   private static final class Lease
   {
      private final String id = UUID.randomUUID().toString();
      private final MobileCaptureMode mode;
      private Instant expiresAt;
      private Instant receivedAt;
      private long sequence = -1;
      private byte[] jpeg;

      /***********************************************************************
       ** Scope is immutable for this lease.
       ***********************************************************************/
      private Lease(MobileCaptureMode mode)
      {
         this.mode = mode;
      }
   }

   /***************************************************************************
    ** Retry after the next heartbeat sequence/rate synchronization.
    ***************************************************************************/
   static class FrameConflictException extends QException
   {
      /***********************************************************************
       ** No frame or credential content is included in errors.
       ***********************************************************************/
      FrameConflictException()
      {
         super("Frame sequence or upload rate is out of date. Refresh the heartbeat.");
      }
   }
}
