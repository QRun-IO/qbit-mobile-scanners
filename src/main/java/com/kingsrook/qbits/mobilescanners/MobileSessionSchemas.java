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
import static com.kingsrook.qbits.mobilescanners.MobileScannersSchemas.object;
import static com.kingsrook.qbits.mobilescanners.MobileScannersSchemas.ref;
import static com.kingsrook.qbits.mobilescanners.MobileScannersSchemas.text;
import static com.kingsrook.qbits.mobilescanners.MobileScannersSchemas.uuid;


/*******************************************************************************
 ** Additive extension 1.1 session/activity/screen vocabulary.
 *******************************************************************************/
final class MobileSessionSchemas
{
   /***************************************************************************
    ** Static schema producer.
    ***************************************************************************/
   private MobileSessionSchemas()
   {
   }



   /***************************************************************************
    ** Core auth identifiers and device tokens never appear in these shapes.
    ***************************************************************************/
   static Map<String, Schema> components()
   {
      Map<String, Schema> schemas = new TreeMap<>();
      Map<String, Schema> activity = new TreeMap<>();
      activity.put("foreground", new Schema().withType(Type.BOOLEAN));
      activity.put("screenAvailable", new Schema().withType(Type.BOOLEAN));
      activity.put("captureMode", captureMode());
      for(String field : List.of("appName", "processName", "processInstanceId", "stepName", "screenLabel"))
      {
         activity.put(field, nullableText(200));
      }
      for(String field : List.of("lastAction", "errorCode", "captureBlockedReason"))
      {
         activity.put(field, nullableText(80));
      }
      activity.put("traceId", nullableText(100));
      activity.put("locationStatus", text(30).withEnumValues(List.of("AVAILABLE", "PERMISSION_DENIED", "DISABLED", "UNAVAILABLE")));
      activity.put("location", ref("MobileScannersLocationV1").withNullable(true));
      schemas.put("MobileScannersLocationV1", object("Optional foreground observation with runtime permission. Coordinates are last known, not a guarantee of current position; the server separately records receipt time.", Map.of(
         "latitude", new BoundedIntegerSchema(-90, 90).withType(Type.NUMBER), "longitude", new BoundedIntegerSchema(-180, 180).withType(Type.NUMBER),
         "accuracyMeters", new BoundedIntegerSchema(0, 10000000).withType(Type.NUMBER), "capturedAt", timestamp()), "latitude", "longitude", "accuracyMeters", "capturedAt"));
      schemas.put("MobileScannersHeartbeatRequestV1", object("Display activity only. Never include form values, barcodes, passwords or raw error messages. JSON maximum 4096 bytes. Omitted captureMode means APP_CONTENT, requiring foreground eligible app pixels. DEVICE_SCREEN requires explicit per-session Android operator consent and may remain available in background. False screenAvailable clears pixels and lease; changing mode invalidates old demand.", activity, "foreground", "screenAvailable"));
      schemas.put("MobileScannersScreenLeaseV1", object("Server-authorized capture demand. Compute remaining duration from expiresAt minus heartbeat serverTime. Sequence is scoped to this lease and resumes at nextSequence after client restart.", Map.of(
         "leaseId", uuid(), "expiresAt", timestamp(), "frameIntervalMillis", integer(1000, 1000), "maxWidth", integer(640, 640),
         "maxHeight", integer(1280, 1280), "maxBytes", integer(262144, 262144), "nextSequence", integer(0, 9007199254740990L), "captureMode", captureMode()),
         "leaseId", "expiresAt", "frameIntervalMillis", "maxWidth", "maxHeight", "maxBytes", "nextSequence"));
      schemas.put("MobileScannersHeartbeatV1", object("Current authenticated binding. screenMirror is omitted unless capture is eligible and an administrator is actively viewing. An ended or expired sign-in returns 401; the client must clear authentication.", Map.of(
         "sessionId", uuid(), "status", text(20).withEnumValues(List.of("ACTIVE")), "serverTime", timestamp(), "heartbeatIntervalMillis", integer(5000, 5000),
         "screenMirror", ref("MobileScannersScreenLeaseV1"), "supportedCaptureModes", new Schema().withType(Type.ARRAY).withItems(captureMode())), "sessionId", "status", "serverTime", "heartbeatIntervalMillis"));
      schemas.put("MobileScannersEndSessionRequestV1", object("Operator intent for their own authenticated session; no target ID is accepted.", Map.of(
         "reason", text(30).withEnumValues(List.of("SIGN_OUT", "SERVER_CHANGE"))), "reason"));
      schemas.put("MobileScannersSessionEndedV1", object("Public tracking identity only. Device approval is unchanged.", Map.of(
         "sessionId", uuid(), "status", text(20).withEnumValues(List.of("ENDED")), "endedAt", timestamp()), "sessionId", "status", "endedAt"));
      schemas.put("MobileScannersScreenRequestV1", object("Mode must match the current session and viewing lease. Omitted mode is APP_CONTENT: never login/password/other-app/OS pixels. DEVICE_SCREEN is optional1.2 and requires per-session operator system consent. Total JSON maximum368640 bytes; decoded JPEG maximum262144 bytes, width<=640, height<=1280. Frames are ephemeral, latest-only and cleared on blocked capture, mode change, expired demand or session end.", Map.of(
         "leaseId", uuid(), "sequence", integer(0, 9007199254740990L), "capturedAt", timestamp(), "width", integer(1, 640), "height", integer(1, 1280),
         "mimeType", text(10).withEnumValues(List.of("image/jpeg")), "imageBase64", text(349528).withFormat("byte"), "captureMode", captureMode()),
         "leaseId", "sequence", "capturedAt", "width", "height", "mimeType", "imageBase64"));
      return schemas;
   }



   /***************************************************************************
    ** Additive 1.2 scope; absent legacy values always mean app content.
    ***************************************************************************/
   private static Schema captureMode()
   {
      return text(20).withEnumValues(List.of("APP_CONTENT", "DEVICE_SCREEN"));
   }



   /***************************************************************************
    ** Nullable activity fields explicitly clear older display state.
    ***************************************************************************/
   private static Schema nullableText(int maximum)
   {
      return text(maximum).withNullable(true);
   }



   /***************************************************************************
    ** UTC instant representation.
    ***************************************************************************/
   private static Schema timestamp()
   {
      return text(40).withFormat("date-time");
   }



   /***************************************************************************
    ** RC20 extensions expose numeric schema limits through normal serialization.
    ***************************************************************************/
   private static Schema integer(long minimum, long maximum)
   {
      return new BoundedIntegerSchema(minimum, maximum).withType(Type.INTEGER);
   }



   /***************************************************************************
    ** Standard JSON integer bounds.
    ***************************************************************************/
   public static class BoundedIntegerSchema extends Schema
   {
      private final long minimum;
      private final long maximum;



      /***********************************************************************
       ** Capture declared limits once.
       ***********************************************************************/
      BoundedIntegerSchema(long minimum, long maximum)
      {
         this.minimum = minimum;
         this.maximum = maximum;
      }



      /***********************************************************************
       ** Inclusive minimum.
       ***********************************************************************/
      public long getMinimum()
      {
         return minimum;
      }



      /***********************************************************************
       ** Inclusive maximum.
       ***********************************************************************/
      public long getMaximum()
      {
         return maximum;
      }
   }
}
