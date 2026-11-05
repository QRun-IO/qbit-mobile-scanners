# Session state

Current as of **2026-11-04**. This is the one place for present status; keep it short. Open work is in [TODO](TODO.md), release gates in [production readiness](PRODUCTION-READINESS.md), and all earlier logs in [history](history/README.md). Nothing here claims a production 1.0.

## Source and publication

Active local feature work: GS1-128 duplication (barcode plus HRI), native table/record browsing and record photo attachment. See [implementation plan](GS1-RECORD-PHOTOS-PLAN.md). The GS1 profile covers the requested GTIN/production-date/serial structure and selected additional AIs. Original scans remain unchanged; canonical renderer input and HRI use additive nullable columns. Root/demo and the coherent Echo reactor passed; two synthetic outputs from the real renderer independently decoded with exact GS1 data and matching HRI. The scanner/demo/Echo reactor passed 135 tests. Disposable MC9400 native acceptance passed GS1 capture and saved-record browsing, plus camera/gallery attachment from record details with lifecycle cleanup. Android unit tests, lint and APK builds passed. The composed cohort also passed 188 tests (scanner 53, base demo 8, Record Files 18, Echo 74, Echo demo 35), with Checkstyle. No deployment, physical GS1 print/rescan or production qualification is claimed.

| Item | State |
| --- | --- |
| Source repository | Gitea `git.kof22.com/QRun-IO/qbit-mobile-scanners`, default branch `develop`. GitHub is a shadow/archive. |
| Scanner PR 12 (development snapshots) | Merged as `286e2e0`. Run 3645 succeeded. The snapshot `com.kingsrook.qbits:qbit-mobile-scanners:0.1.0-20261103.233448-3645` was read back anonymously (JAR, POM, sources, Javadocs); its 37 Java source files match the reviewed source. |
| Scanner PR 13 (Echo uses released Printing) | Merged as `1345197`. Both Echo POMs default to released Printing `1.0.0`. CI runs 4157 and 4158 succeeded. |
| Android host (`qqq-android`) | PRs 8 and 10 merged into `main` at `0ae2101`. SDK run 3634 passed and its artifacts were verified (`0.1.1-20261103.232145-3634`). Signing/documentation changes are merged through PR 14 (`464814f`), and signed preview `scanner-v1.0.0` is published. These are development libraries, not a production release. |
| Production 1.0 | Not released. All Maven publication switches other than development snapshots remain off. |

## Local runtime

One local server (the composed Echo launcher, see [local demo](LOCAL-DEMO.md)) runs Mobile Scanners/Echo, Collaboration/Record Files, Document Generation and Printing on loopback host port `57832`. It uses public QQQ `4.1.0-RC.21` and released Printing `1.0.0`, and preserves its existing database, accounts and printer configuration.

| Contract | Version |
| --- | --- |
| QQQ semantic UI/UX API | 1.10 |
| `mobile-scanners` extension | 1.5 |
| `printing` extension | 2.3 |
| `mobile-record-files` vocabulary | 1.0 |

The Android client reaches it through a device-side forward from port 8080 to the host port, so the app URL stays `http://127.0.0.1:8080`. This HTTP loopback demo is separate from any HTTPS production deployment.

## Verification of the current cohort

- **198 tests pass** with no failures, errors or skips: scanner 53, base demo 8, mobile Record Files 18, Printing bridge 16, Echo 68, Echo demo 35. Checkstyle passes.
- The 94 scanner Java source files match the reviewed source exactly.
- No private QQQ backend artifact is in this cohort; all QQQ backend dependencies resolve to public RC.21 and Printing is the released `1.0.0`.
- The earlier 377-test run plus 15 admin-browser E2E cases remains valid historical evidence. This session did not re-run the browser suite, so no new browser run is claimed.
- Not public yet: the document, Record Files and Scripting owner artifacts (Generation, its bridges and copies support) are still qualified owner inputs awaiting a canonical published cohort. The owner is releasing Core, Collaboration and Next UI foundations, but no completed public versions are recorded here and no POM was changed in anticipation of them.

## Handhelds

- **Zebra TC22 (original, unchanged).** Development APK (SHA-256 `38A11155BF6A9C089A1EE6D9E44401E8FF39497B7150B51A85F1F916148BE60D`, version 0.1.0, code 1). On November 4 the operator reported that all outstanding manual tests pass; that is accepted as an operator report, not new per-case evidence, and the checks are not to be requested again. See [TC22 acceptance](TC22-ACCEPTANCE.md).
- **Zebra MC9400 (Android 13, added).** A signed release 1.0 build was installed and launched, then at the user's request the device was configured like the TC22 for now: the same development APK, device port 8080 forwarded to the host server over paired wireless ADB, signed in as the scanner account, and approved through the ordinary device-approval process. A physical trigger scan and backend record check are still pending.
- **Signed APK.** A signed 1.0 (code 2) APK exists, verified with the new QRun key and its backup (see the Android repository's `RELEASING` documentation). There is no production rollout and no automatic APK-signing CI yet.

## Echo (scan, render, print)

Optional example app. Copies per scan defaults to 1 (range 1–100); one print job per scan, with printer and document chosen server-side at setup and the same value and symbology rendered within the supported profiles. Admitted and refused barcode profiles are listed in the [Echo README](../examples/echo/README.md#fidelity-and-limits); universal barcode fidelity is not claimed.

## Where things are

- Status and plans: this file, [TODO](TODO.md), [production readiness](PRODUCTION-READINESS.md).
- Run it: [local demo](LOCAL-DEMO.md), [TC22 acceptance](TC22-ACCEPTANCE.md), [tests](TESTING.md).
- Contract: [semantic UI API](UI-API.md), [device sessions](DEVICE-SESSIONS.md), [branding](BRANDING.md).
- Echo: [library](../examples/echo/README.md), [runnable demo](../examples/echo/demo/README.md).
- Evidence and old logs: [history](history/README.md); dated review documents stay in this directory.
