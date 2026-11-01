-- Copyright (c) 2026 Kingsrook, LLC. SPDX-License-Identifier: Apache-2.0
-- Host migration input, not automatically executed by metadata registration.
CREATE TABLE IF NOT EXISTS echo_run (
   id VARCHAR(36) PRIMARY KEY, actorId VARCHAR(200) NOT NULL, deviceId VARCHAR(36) NOT NULL,
   publicSessionId VARCHAR(36) NOT NULL, printerRecordId VARCHAR(200) NOT NULL, mediaId VARCHAR(200) NOT NULL,
   mediaRevision VARCHAR(64) NOT NULL DEFAULT '', widthMicrons INT NOT NULL DEFAULT 0, heightMicrons INT NOT NULL DEFAULT 0,
   dpiX INT NOT NULL DEFAULT 0, dpiY INT NOT NULL DEFAULT 0,
   state VARCHAR(30) NOT NULL, pendingEventId VARCHAR(36)
);
CREATE TABLE IF NOT EXISTS echo_scan (
   id VARCHAR(64) PRIMARY KEY, runId VARCHAR(36) NOT NULL REFERENCES echo_run(id),
   eventId VARCHAR(36) NOT NULL, actorId VARCHAR(200) NOT NULL, deviceId VARCHAR(36) NOT NULL, mobileScanId VARCHAR(64) NOT NULL,
   barcode VARCHAR(4096) NOT NULL, symbology VARCHAR(200) NOT NULL, rendererSymbology VARCHAR(100) NOT NULL,
   scanSource VARCHAR(200) NOT NULL, capturedAt VARCHAR(40) NOT NULL, receivedAt VARCHAR(40) NOT NULL, state VARCHAR(30) NOT NULL,
   printJobId VARCHAR(200), UNIQUE(runId, eventId)
   ,templateId INT, templateRevisionId INT, format VARCHAR(10), topMicrons INT, rightMicrons INT, bottomMicrons INT, leftMicrons INT
);
