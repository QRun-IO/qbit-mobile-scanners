/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


/*******************************************************************************
 ** Behavioral limits, sequence, demand expiry and immediate pixel removal.
 *******************************************************************************/
class MobileScreenServiceTest
{
   /***************************************************************************
    ** A viewer lease is required, and closing it eventually ends capture demand.
    ***************************************************************************/
   @Test
   void leaseExpiryAndBlockedCaptureDiscardFrames() throws Exception
   {
      MutableClock clock = new MutableClock();
      MobileScreenService screens = new MobileScreenService(clock);
      assertNull(screens.activeLease("session"));
      assertThrows(QException.class, () -> screens.upload("session", frame(UUID.randomUUID().toString(), 0)));
      screens.view("session");
      var lease = screens.activeLease("session");
      assertEquals(0L, lease.get("nextSequence"));
      screens.upload("session", frame((String) lease.get("leaseId"), 0));
      assertNotNull(screens.view("session").dataUri());
      screens.remove("session");
      assertNull(screens.view("session").dataUri());
      assertEquals(0L, screens.activeLease("session").get("nextSequence"));
      clock.advance(31);
      assertNull(screens.activeLease("session"));
      assertThrows(QException.class, () -> screens.upload("session", frame((String) lease.get("leaseId"), 1)));
   }

   /***************************************************************************
    ** Oversize encodings and misleading image dimensions fail before retention.
    ***************************************************************************/
   @Test
   void rejectsOversizeMalformedAndMismatchedFrames() throws Exception
   {
      MobileScreenService screens = new MobileScreenService();
      screens.view("session");
      String lease = (String) screens.activeLease("session").get("leaseId");
      Map<String, Object> frame = frame(lease, 0);
      frame.put("imageBase64", "A".repeat(349529));
      assertThrows(QException.class, () -> screens.upload("session", frame));
      frame.put("imageBase64", "not base64");
      assertThrows(QException.class, () -> screens.upload("session", frame));
      frame.put("imageBase64", frame(lease, 0).get("imageBase64"));
      frame.put("width", 640);
      assertThrows(QException.class, () -> screens.upload("session", frame));
      frame.put("width", 2);
      frame.put("mimeType", "image/svg+xml");
      assertThrows(QException.class, () -> screens.upload("session", frame));
      assertNull(screens.view("session").dataUri());
   }

   /***************************************************************************
    ** Sequence is scoped to the lease and server limits prevent upload flooding.
    ***************************************************************************/
   @Test
   void rejectsOldSequenceAndRateAndDropsEndedSession() throws Exception
   {
      MutableClock clock = new MutableClock();
      MobileScreenService screens = new MobileScreenService(clock);
      screens.view("session");
      String lease = (String) screens.activeLease("session").get("leaseId");
      screens.upload("session", frame(lease, 5));
      assertThrows(MobileScreenService.FrameConflictException.class, () -> screens.upload("session", frame(lease, 6)));
      clock.advance(1);
      assertThrows(MobileScreenService.FrameConflictException.class, () -> screens.upload("session", frame(lease, 5)));
      screens.upload("session", frame(lease, 6));
      assertEquals(7L, screens.activeLease("session").get("nextSequence"));
      screens.remove("session");
      assertNull(screens.activeLease("session"));
      screens.view("session");
      assertEquals(0L, screens.activeLease("session").get("nextSequence"));
      assertNull(screens.view("session").dataUri());
   }

   /***************************************************************************
    ** Generated non-user pixels keep test artifacts free of screen data.
    ***************************************************************************/
   private static Map<String, Object> frame(String lease, long sequence) throws Exception
   {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", output);
      return new HashMap<>(Map.of("leaseId", lease, "sequence", sequence, "capturedAt", Instant.now().toString(),
         "width", 2, "height", 2, "mimeType", "image/jpeg", "imageBase64", Base64.getEncoder().encodeToString(output.toByteArray())));
   }

   /***************************************************************************
    ** Deterministic expiry tests without sleeping.
    ***************************************************************************/
   private static class MutableClock extends Clock
   {
      private Instant now = Instant.parse("2026-10-30T12:00:00Z");

      /***********************************************************************
       ** Advance only this test's clock.
       ***********************************************************************/
      void advance(long seconds)
      {
         now = now.plusSeconds(seconds);
      }

      /***********************************************************************
       ** Server time is always UTC.
       ***********************************************************************/
      @Override
      public ZoneId getZone()
      {
         return ZoneOffset.UTC;
      }

      /***********************************************************************
       ** Zone has no effect on instant comparisons.
       ***********************************************************************/
      @Override
      public Clock withZone(ZoneId zone)
      {
         return this;
      }

      /***********************************************************************
       ** Return the explicitly controlled instant.
       ***********************************************************************/
      @Override
      public Instant instant()
      {
         return now;
      }
   }
}
