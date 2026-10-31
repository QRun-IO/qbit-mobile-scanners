# QQQ Mobile Scanners session state

Updated: 2026-10-31

Cross-chat coordination uses `R:\agent-comms\README.md` and the mobile master's owned `R:\agent-comms\qqq-mobile-scanner.md`, explicitly requested by the user. Record Files and Printing acknowledged the directory. Read the current summary at the top of each owner's mailbox before changing shared contracts.

## October 31 current checkpoint

Server working source passes37 root tests,2 HTTP acceptance tests and15 browser E2E cases; native navigation/session/photo acceptance is in progress. The optional `integrations/record-files` module verifies the frozen0238 bundle and has passing real authenticated upload/persisted-byte/identity denial tests. A subsequent independent review found stale scratch source in repeat builds and missing parent-permission negatives; fixes/tests are underway. The newer upstream operation guard exists in source but requires a qualified immutable handoff before adoption. See the module's session state for exact runtime hashes and compatibility limits.

The local emulator is closed after the user confirmed it caused workstation-wide stutters. TC22 is connected; do not restart the emulator automatically. Builds are serialized with bounded workers. Existing operator demo and handheld state remain preserved. Android85 JVM tests and zero-error/zero-warning lint pass at the last compiled checkpoint; newer changes must be verified again.

The active goal authorizes completing and publishing the project, superseding historical artifact-publication deferrals below. However, on October31 the owner explicitly deferred signing and distribution until the final step because the channel is undecided. Complete implementation, final audits, tests, documentation and ordinary hosted CI first. No signing key, production release, merge or deployment is claimed.

## Active: device sessions, live screen viewing and 1.0 audit

Additional user scope: unique short device names assigned at setup/approval, editable names/descriptions, independent approval/presence, online fleet view and foreground GPS with age/accuracy. Camera/gallery photos use the separately developed Record Files qbit. The Build QQQ Documentation agent owns it and `qbit-record-files/docs/INGESTION-CONTRACT.md` documents the implemented record-files1.0 semantic capabilities/create/status/chunk/finalize/cancel operations and durable receipts. The October31 checkpoint above supersedes earlier proposed-only contract status. Native photo acceptance and the final authorization guard remain pending; unavailable capabilities stay hidden.

The user requires all device/session history, live activity and actual application-screen viewing in QQQ administration, plus session termination. Implementation follows [DEVICE-SESSIONS.md](DEVICE-SESSIONS.md): ordinary QQQ tables/widgets/processes, server-owned authenticated bindings, an additive 1.1 semantic extension and demand-based bounded screen frames. Native and browser actions require full E2E coverage, including record and bulk entry points.

The user also requires independent in-depth security, architecture, coding-standards and coupling/cohesion reviews of both complete repositories before a production-ready 1.0. [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md) tracks the gates and exact baseline evidence. Plan: complete the session feature and tests; freeze final revisions for independent audits; remediate and verify findings; complete physical and release/operations acceptance. Deployment/distribution details have been requested. No 1.0 readiness or release is claimed.

## Current: semantic API migration

The qbit registers mobile-scanners 1.0.0 through QQQ's extension registry. Device enrollment/current status, branding and connectivity use `/qqq/v1/qbit/mobile-scanners`; discovery advertises the relative prefix `/qbit/mobile-scanners`. The generated OpenAPI includes typed device and scanner event/acknowledgment schemas, stable operation IDs and standard errors. Legacy standalone mobile routes are removed. [UI-API.md](UI-API.md) describes the contract and versioning policy.

Root `verify install` passed 23 tests, including the extension contract baseline checks; demo `verify` passed one real HTTP discovery/schema/error/lifecycle scenario. All six migrated browser E2E scenarios passed in 2.9 minutes. Native integration exposed an unregistered Android client channel returning HTTP400; the qbit now registers `android` without replacing existing channel policies. The real HTTP regression sends `X-QQQ-Client: android/qqq-mobile` on all requests and passes. Android requires `android` in discovery's `clientChannels`; its combined JVM suites passed 53 tests (core 21/mobileapp 23/scanner 9), with six shared native UI tests also passing. Final lint passed with zero errors and six warnings; debug and E2E APK builds passed. Five native scanner scenarios passed with the OpenAPI request audit: incompatible core version, missing extension, missing Android channel, baseline scan flow and additive future metadata. The final native administration form scenario passed in 255.917 seconds after correcting completion handling for a COMPLETE response without `nextStep`: approval, revocation and cancellation completed, with all 31 requests matched to 13 published operations. All six native scenarios passed across the original and focused reruns; this is not a claim of one uninterrupted six-case run. All hosted checks passed for published server commit `7dd02b8874882ca2edcdcd89109b3dee6b4b0c88`: CircleCI setup/verify, CodeQL, Windows helpers and the demo HTTP/browser E2E job.

Active plan: publish the companion Android checkpoint pinned to verified server code `7dd02b8`, then add the requested device/session activity, session termination and on-demand app-screen viewing. Physical connection-loss/retry and revoke-denial checks are deferred to acceptance with those session controls; the persistent server and USB forwarding are restored. The local server and emulator restarted after the workstation crash with data preserved; the backend administration UI was visibly signed in. The physical TC22 is identified through authorized USB debugging, running Android 14 and DataWedge 13.0.325. The debug app is installed and signed in. Approval from the browser record's Actions menu visibly reported one device approved; the handheld then opened the native app catalog and Scan Capture process. Updating the APK preserved its login. DataWedge is configured for package `io.qrun.mobile.scanner`, activity `*`, barcode input enabled, keystroke output disabled and Intent Output enabled. Intent delivery uses Broadcast, action `io.qrun.mobile.scanner.SCAN`, category `android.intent.category.DEFAULT`, and an explicit target package with signature checking enabled. Two deliberate physical trigger presses were confirmed by the user and produced two persisted records visible in Barcode Scans. A stored row matched the TC22 receipt for `QQQ-TEST-123456`, CODE128, source `scanner`, user `scanner`, TC22/Android 14/app 0.1.0, app `mobileScanApp`, process `mobileScanCapture`, and capture/receipt timestamps. Basic physical Code128 delivery and persistence passed. A subsequent physical QR scan was acknowledged on the handheld and independently visible in the backend as `001234567890`, `LABEL-TYPE-QRCODE`, source `scanner`, with matching TC22/user/app/process and capture/receipt metadata. Three physical records are verified: two deliberate Code128 captures and one QR capture. Physical QR delivery and leading-zero preservation passed; physical connection-loss/retry and revoke-denial checks remain pending. The old semantic-gap note below is superseded by this implementation status and the current TODO.

## Verified: record and bulk device administration

- User reported Approve Device did not work from a device record and required proper E2E coverage for every process, including single-record and bulk entry points.
- Root and real HTTP regressions reproduced the missing selected device ID. Approval/revocation now prepare a stable selection, show confirmation, apply only after confirmation, and display a result/count. Explicit selected IDs and native select-all/first-N filters are supported up to 100 devices; oversized selections fail explicitly.
- The process stores selected IDs in native process records, ignores replacement IDs submitted at confirmation, rechecks administrator permission, and validates all targets before changing any. Standalone manual ID entry remains supported.
- Root verification/install passed 21 tests and Checkstyle. Demo verification passed the real HTTP scenario covering single-record approval, both bulk selection modes for approve/revoke, unselected-device isolation, visible results, scanner permission denial, scan persistence/retry, and revoked-device denial.
- Restarted the persistent local demo on port 57832; public status returned `ok`. Accounts, database and emulator forwarding were preserved. Dedicated scanner-host native E2E passed (one complete real-server scenario, 50.932 seconds); Android verification also passed 28 JVM tests, lint/build and workflow syntax. Test APKs were removed and the normal scanner restored.
- Browser E2E passed all six scenarios in 2.7 minutes: record approve/revoke with results and return navigation, checked-row bulk with untouched-device assertions, standalone manual entry, cancellation, full-query selection, and first-N selection with independent persisted-state checks. Tests use the packaged frontend and a temporary real backend. The fixture authenticates through the real API and supplies the nonsecret `qqq.sessionHint` marker so the UI resumes and validates its HttpOnly cookie. Browser checks now run inside the existing required demo CI job; native E2E runs in the Android push/PR emulator lane. New-head hosted results remain separate from these local passes.
- UI/UX API clarification: the Android app uses `/qqq/v1` metadata/process operations, but full semantic-client conformance is unfinished. Version/feature negotiation, broader native component handling and migration of separate `/mobile/v1` routes into a registered UI API extension are tracked in TODO; do not describe the current client as fully conformant.

2026-10-30 correction: native authentication metadata is served at `/qqq/v1/metaData/authentication`. The device-header guard now exempts that exact public path; the previous legacy-only exemption caused HTTP403 on app restart after enrollment when device headers were present before a user session was restored. A real HTTP regression failed before the fix and passed afterward with stale, pending, invalid-token and revoked device headers. Protected metadata/process/scan authorization still fails appropriately. Root13 tests, Checkstyle and demo HTTP acceptance passed; the local demo was restarted on57832 with its persistent database and accounts preserved.

## Current repository and demo state

- GitHub repository created at https://github.com/QRun-IO/qbit-mobile-scanners with `develop` as default. Initial signed commit `abbcc39` is published. Companion Android implementation is in https://github.com/QRun-IO/qqq-android/pull/8.
- Hosted initial-commit checks passed: CircleCI `setup`/`verify`, CodeQL `analyze / Analyze branch source`, demo HTTP acceptance and Windows helper analysis. CircleCI dynamic configuration is enabled. Maven artifact publication remains disabled.
- Runtime branding is configurable on the server before login; the shared Android APK uses the customer name, primary color and optional HTTPS logo. Public branding/status endpoints carry no credentials. Root tests now pass 13 cases; demo HTTP acceptance also passes.
- Windows demo helpers store generated demo passwords using per-user Windows DPAPI outside the repository. An Android 15/API35 emulator is running with WHPX; six native UI instrumentation tests passed. Physical TC22 acceptance is still pending.
- The current workstation demo uses loopback port 57832 because other services occupy the conventional ports. `adb reverse tcp:8080 tcp:57832` preserves the Android server URL `http://127.0.0.1:8080/`. Start it again with `pwsh -File scripts/Start-Demo.ps1 -SkipBuild -Port 57832` after stopping the existing instance. See `LOCAL-DEMO.md` for login helpers.
- The earlier verification/handoff notes below describe the initial implementation before repository publication and emulator setup.

## Confirmed scope

- Native Android host for Zebra TC22 and other handheld scanners, extending `qqq-android`.
- Server-defined native screens and QQQ process actions, using existing QQQ metadata.
- Online only for version one; no offline scan queue.
- Local demo server with real QQQ login and required administrator device approval.
- First app: barcode capture into a QQQ table with scan, user, device and timestamp metadata.
- User authorized Codex agents for implementation and subsequently requested GitHub repositories and standard project files. Repository creation and pushes are authorized; artifact publication remains disabled.

## Completed plan

1. Established enrollment and scan contract using existing QQQ APIs.
2. Implemented qbit tables, native metadata/processes, approval/revocation and guarded routes.
3. Added Android scanner host, table-based login/enrollment gate and Zebra DataWedge input.
4. Added a file-backed H2 demo and verified native login, approval, scan, retry and revocation over HTTP.
5. Built the Android debug APK and documented TC22 hardware acceptance.
6. Added standard repository/community files, current shared CircleCI QBit configuration, CodeQL, Dependabot, demo HTTP and Windows-script CI, and a documented publishing policy.

## Baseline and ownership

- QQQ baseline: 4.1.0-RC.20. The local 4.0.0 middleware does not support Basic sign-in through `manageSession`; RC.20 does.
- Backend agent: qbit main source, unit/integration tests and root POM.
- Android agent: `qqq-android` source and scanner application module.
- Toolchain agent: Android SDK installed after explicit user approval of required Google terms.
- Parent: local demo, cross-component verification and documentation.

## Verification

- Qbit: `./mvnw.cmd -q verify install` passed 11 tests (7 H2 behavioral/security tests and 4 inherited guardrails), with Checkstyle passing.
- Demo: `../mvnw.cmd -B verify` passed Checkstyle and one real HTTP acceptance scenario. It verifies QQQ login, anonymous and pending-device denial, operator/admin separation, approval, async native process capture, same-event deduplication, SQL provenance, bad/missing device credentials, revocation while a process is open, logout and session replay rejection.
- HTTP fixtures verify the actual native `composite`/`SCANNER` widget payload. The Android tests deserialize these real metadata/process/init responses.
- Android: core/mobileapp/scanner JVM suites passed 19 tests, no failures or skips. `lintDebug` and `assembleDebug` passed. Lint has 0 errors and 5 warnings: 3 inherited Navigation custom checks skipped due to API incompatibility and 2 dependency/version notices.
- Debug APK signature verified. Path: `../../qqq-android/scanner-host/build/outputs/apk/debug/scanner-host-debug.apk`.
- APK SHA256: `B3E160CE3279C369CEDB425AC6E777A2729CE8A2E430E7D6F46D35C6376F8214`.
- Android SDK location: `C:/Users/james/AppData/Local/Android/Sdk`; SDK 35, build-tools 34/35, Gradle 8.7.
- `adb devices -l` returned no attached devices. No physical TC22, camera, or emulator acceptance performed.
- Repository bootstrap verification: CircleCI setup validated against the private QRun-IO orb with explicit organization scope; Actionlint passed for both workflows. Current shared generator accepted the manifest and selected verification only for seven branch/tag cases. All publication switches are false. Root 11 tests and demo one scenario were rerun successfully after repository metadata changes.

## Handoff and remaining scope

- Follow `TC22-ACCEPTANCE.md` for local credentials, loopback demo, USB forwarding, DataWedge profile and device tests.
- Runtime demo server is not left running. No user credentials were created outside temporary test databases.
- First host supports QQQ table-based authentication; other providers show an explicit unsupported message pending configuration.
- Server enforces approved device plus authenticated user, including async execution. Enable QQQ core permission enforcement when embedding the qbit.
- Only a current unacknowledged scan is retained in memory for explicit retry. Closing/resetting the app is not an offline queue.
- The H2 schema is supplied; migrations for other databases and deployment authentication remain host-owned.
- Pending physical acceptance is tracked in TODO. GitHub bootstrap and hosted checks are separate from artifact release or production deployment; use `CI-CD.md` for the repository settings and release enablement policy.
