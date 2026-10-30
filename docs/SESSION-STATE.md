# QQQ Mobile Scanners session state

Updated: 2026-10-29

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
