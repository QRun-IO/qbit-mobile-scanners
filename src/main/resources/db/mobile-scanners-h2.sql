-- Copyright (c) 2026 Kingsrook, LLC
-- SPDX-License-Identifier: Apache-2.0

SET TIME ZONE 'UTC';

CREATE TABLE IF NOT EXISTS mobileDevice (
   id VARCHAR(36) PRIMARY KEY,
   installationId VARCHAR(200) NOT NULL, displayName VARCHAR(200), manufacturer VARCHAR(200),
   model VARCHAR(200), osVersion VARCHAR(200), appVersion VARCHAR(200), status VARCHAR(200) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED')),
   enrolledBy VARCHAR(200) NOT NULL, approvedBy VARCHAR(200), enrolledAt TIMESTAMP NOT NULL, approvedAt TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mobileDeviceCredential (
   id VARCHAR(36) PRIMARY KEY, tokenHash VARCHAR(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS mobileScan (
   id VARCHAR(64) PRIMARY KEY,
   deviceId VARCHAR(200) NOT NULL REFERENCES mobileDevice(id), eventId VARCHAR(200) NOT NULL, userId VARCHAR(200) NOT NULL,
   appId VARCHAR(200) NOT NULL, processName VARCHAR(200) NOT NULL,
   barcode VARCHAR(4096) NOT NULL, symbology VARCHAR(200), scanSource VARCHAR(200), manufacturer VARCHAR(200), model VARCHAR(200),
   osVersion VARCHAR(200), appVersion VARCHAR(200), capturedAt TIMESTAMP NOT NULL, receivedAt TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS mobileSession (
   id VARCHAR(36) PRIMARY KEY, deviceId VARCHAR(36) NOT NULL REFERENCES mobileDevice(id), userId VARCHAR(200) NOT NULL,
   status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ENDED')), startedAt TIMESTAMP NOT NULL,
   lastSeenAt TIMESTAMP NOT NULL, endedAt TIMESTAMP, endedBy VARCHAR(200), endReason VARCHAR(200),
   foreground BOOLEAN, screenAvailable BOOLEAN, captureBlockedReason VARCHAR(80), appName VARCHAR(200), processName VARCHAR(200),
   processInstanceId VARCHAR(200), stepName VARCHAR(200), screenLabel VARCHAR(200), lastAction VARCHAR(80), errorCode VARCHAR(80), traceId VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS mobileSession_device ON mobileSession(deviceId, startedAt);
CREATE TABLE IF NOT EXISTS mobileSessionBinding (
   id VARCHAR(64) PRIMARY KEY, sessionId VARCHAR(36) NOT NULL UNIQUE REFERENCES mobileSession(id), coreSessionUuid VARCHAR(200) NOT NULL
);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS shortName VARCHAR(32);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS description VARCHAR(2000);
ALTER TABLE mobileDevice ALTER COLUMN shortName DROP NOT NULL;
UPDATE mobileDevice d SET shortName = NULL
WHERE shortName = 'scanner-' || SUBSTRING(REPLACE(id, '-', ''), 1, 24)
AND EXISTS (SELECT 1 FROM mobileDevice earlier WHERE earlier.shortName = d.shortName AND earlier.id < d.id);
MERGE INTO mobileDevice d USING (
   WITH missing AS (
      SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS ordinal FROM mobileDevice WHERE shortName IS NULL
   ), candidates AS (
      SELECT X, 'scanner-' || CAST(X AS VARCHAR) AS candidate FROM SYSTEM_RANGE(1, (SELECT COUNT(*) FROM mobileDevice))
   ), available AS (
      SELECT candidate, ROW_NUMBER() OVER (ORDER BY X) AS ordinal FROM candidates
      WHERE NOT EXISTS (SELECT 1 FROM mobileDevice existing WHERE existing.shortName = candidates.candidate)
   )
   SELECT missing.id, available.candidate FROM missing JOIN available ON missing.ordinal = available.ordinal
) allocated ON d.id = allocated.id
WHEN MATCHED THEN UPDATE SET shortName = allocated.candidate;
ALTER TABLE mobileDevice ALTER COLUMN shortName SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS mobileDevice_shortName ON mobileDevice(shortName);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS lastSeenAt TIMESTAMP;
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS locationStatus VARCHAR(30);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS latitude NUMERIC(12,8);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS longitude NUMERIC(12,8);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS accuracyMeters NUMERIC(18,4);
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS locationCapturedAt TIMESTAMP;
ALTER TABLE mobileDevice ADD COLUMN IF NOT EXISTS locationReceivedAt TIMESTAMP;
CREATE INDEX IF NOT EXISTS mobileDevice_lastSeenAt ON mobileDevice(lastSeenAt);
ALTER TABLE mobileSession ADD COLUMN IF NOT EXISTS authCleanupStatus VARCHAR(80);
ALTER TABLE mobileSession ADD COLUMN IF NOT EXISTS authCleanupAt TIMESTAMP;
ALTER TABLE mobileSession ADD COLUMN IF NOT EXISTS captureMode VARCHAR(20) DEFAULT 'APP_CONTENT';
DELETE FROM mobileDeviceCredential WHERE NOT EXISTS (SELECT 1 FROM mobileDevice WHERE mobileDevice.id = mobileDeviceCredential.id);

ALTER TABLE mobileSession ALTER COLUMN startedAt TIMESTAMP;
ALTER TABLE mobileSession ALTER COLUMN lastSeenAt TIMESTAMP;
ALTER TABLE mobileSession ALTER COLUMN endedAt TIMESTAMP;
ALTER TABLE mobileSession ALTER COLUMN authCleanupAt TIMESTAMP;
ALTER TABLE mobileDevice ALTER COLUMN enrolledAt TIMESTAMP;
ALTER TABLE mobileDevice ALTER COLUMN approvedAt TIMESTAMP;
ALTER TABLE mobileDevice ALTER COLUMN lastSeenAt TIMESTAMP;
ALTER TABLE mobileDevice ALTER COLUMN locationCapturedAt TIMESTAMP;
ALTER TABLE mobileDevice ALTER COLUMN locationReceivedAt TIMESTAMP;
ALTER TABLE mobileScan ALTER COLUMN capturedAt TIMESTAMP;
ALTER TABLE mobileScan ALTER COLUMN receivedAt TIMESTAMP;
