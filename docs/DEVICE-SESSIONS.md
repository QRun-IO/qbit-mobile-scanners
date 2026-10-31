# Device sessions and live administration

Status: implementation in progress. This document records the agreed contract, not completed acceptance.

## Requirements

Administrators can list all enrolled devices and their current and past sessions, inspect live app/process/screen activity, view the actual QQQ application screen, and end one or several sessions. Device approval remains separate from user-session termination. Every administration process must work from a record and from selected bulk records, with real browser and native E2E coverage.

## Design

Use ordinary QQQ tables, record widgets and processes. A public `mobileSession` record stores device/user attribution, server timestamps, bounded activity names and termination audit. A private binding maps the authenticated core QQQ session to its public record; authentication identifiers never enter public records or telemetry. The server derives identity from QQQ authentication and verified device credentials.

`mobileSessionEnd` uses the standard prepare/confirm/apply/result flow. It preserves the reviewed selection, validates every target before changing any, records who ended it, revokes the core session through QQQ's existing logout executor, and clears cached authentication. An independent ended-session check also protects requests and asynchronous scan writes, including cookie replay without device headers.

The registered `mobile-scanners` semantic extension advances additively to 1.1.0. Existing 1.0 contracts and their baseline remain intact. Older scanner clients are observed through their existing authenticated device calls; no new mandatory credential header is introduced. New clients negotiate 1.1 before login.

| Operation | Purpose |
| --- | --- |
| `POST /qqq/v1/qbit/mobile-scanners/sessions/heartbeat` | Start or refresh the current authenticated device session and return capture demand. |
| `POST /qqq/v1/qbit/mobile-scanners/sessions/current/end` | Record normal sign-out/server change before core logout. |
| `POST /qqq/v1/qbit/mobile-scanners/sessions/current/screen` | Upload one bounded JPEG for the current session and active viewer lease. |
| Standard QQQ widget/process APIs | View live session records/screens and run single/bulk termination. |

Heartbeats run about every five seconds and contain app/process/step identifiers, a bounded screen label, foreground/capture availability, and safe action/error identifiers. They exclude passwords, form values, barcode contents and raw logs. Server time determines presence and lease expiry. The client derives a monotonic lease deadline from the server's remaining duration so device clock skew cannot extend viewing.

The session record's ordinary composite IMAGE widget refreshes every two seconds while visible and renews a 30-second viewer lease. The Android host uses PixelCopy on its own application content only while authenticated, approved, foregrounded, focused and leased. It excludes authentication/password screens, checks the same screen/session generation after asynchronous capture, and shows a visible viewing indicator. Backgrounding, logout, termination and unavailable capture clear the current frame.

Frames are limited to 640 by 1280 pixels and 256 KiB, with a 360 KiB JSON body limit. The server validates encoded length before decoding and checks JPEG dimensions before pixel allocation. Lease-scoped ordering rejects stale uploads; heartbeat returns `nextSequence` for client restart recovery. Only the latest frame is held transiently in bounded memory. No screen history or remote input control is included.

## Device identity, presence and location

Devices have an administrator-editable display name and description, plus a unique short name. Enrollment assigns a default short name; setup/approval can assign a meaningful name. Short names are canonical lowercase, 3–32 characters, using letters, digits and hyphens, with a database uniqueness constraint. Existing devices require a preserving migration before the constraint is enabled. Profile edits cannot change approval or credentials.

Approval and online presence are separate. Presence is based on server-observed heartbeat age and must expire even when a device stops sending requests. Administrators need an accurate online-only query. Location reports include coordinates, accuracy, capture time and server receipt time; unavailable or denied location never blocks scanning. The Android client requests foreground permission explicitly. Last-known location must show its age and must not be presented as a current GPS fix. No coordinates are sent to third-party maps by default.

## Record photos

The separate Record Files qbit owns retained bytes, associations, audit and upload finalization. The scanner owns native camera/gallery handling and bounded private temporary files, stable retry identity and a durable-save receipt. The optional `integrations/record-files` module adopts frozen bundle `retained-contract-20261031-0352` in an isolated dependency cache. Its operation guard protects authorization through the tested single-JVM commits and rollbacks against concurrent revocation/session termination; 18 integration/contract tests pass. Native capture uses explicit `RECORD_FILES_CAPTURE` metadata with `values.parent.tableName` and `values.parent.recordId`, checks discovery/capabilities and never infers the parent from a process name. Synthetic TC22 camera/gallery, rotation and logout cleanup pass. Production storage/content processing, immutable dependency CI and actual lens/operator acceptance remain open. Shared coordination is recorded in the mobile and Record Files owner mailboxes under `R:\agent-comms`.

## Deployment boundary

The initial live-view frame/lease store belongs to one server instance. Clustered deployments need shared ephemeral storage or explicit instance affinity. Durable device/session records use the configured QQQ backend. Schema changes must preserve the existing demo database; release documentation must state supported database migrations and deployment assumptions.

## Verification plan

- Server: authenticated identity binding, private fields, permission denial, ended cookie replay, asynchronous write races, expiry, image limits/order and existing 1.0 compatibility.
- Browser: device/session navigation, refreshed image widget, single/bulk termination, confirmation/cancellation, persisted audit and unaffected sessions.
- Android: truthful activity state, capture lifecycle/password gating, delayed-capture races, remote logout and a fresh session after login.
- TC22: actual PixelCopy screen visible in admin, no capture outside the eligible app state, remote termination, reconnect/retry and persistence.

Production readiness is tracked in [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md).
