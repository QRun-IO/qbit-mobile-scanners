# QQQ Mobile Scanners

Server support for approved Android handhelds running native QQQ apps. The first app, **Scan Capture**, stores barcode events in a QQQ table. An optional example, **Echo**, scans a barcode and prints a copy of it ([Echo](examples/echo/README.md)).

Version one is online only. Devices require an authenticated user and administrator approval. Android renders existing QQQ app, process, frontend-step and widget metadata; this qbit adds the scanner widget and the device enrollment lifecycle. Not released as production 1.0.

## Where things live

Gitea is the primary forge for source, reviews and checks. GitHub is a shadow/archive that receives no ordinary writes; any GitHub link in an older document is history only.

- Server (this repository): [git.kof22.com/QRun-IO/qbit-mobile-scanners](https://git.kof22.com/QRun-IO/qbit-mobile-scanners), development branch `develop`.
- Android host: [git.kof22.com/QRun-IO/qqq-android](https://git.kof22.com/QRun-IO/qqq-android) (`scanner-host` module). Its reviews 8 and 10 are merged into `main`.
- Current status: [session state](docs/SESSION-STATE.md), [TODO](docs/TODO.md), [production readiness](docs/PRODUCTION-READINESS.md). Older logs are in [history](docs/history/README.md).

## Current versions

| Component | Version |
| --- | --- |
| Java / Maven | 21 / wrapper-pinned 3.9.9 |
| QQQ | `4.1.0-RC.21` (public) |
| QQQ semantic UI/UX API | 1.10 |
| `mobile-scanners` extension | 1.5 |
| `printing` extension (Echo) | 2.3, released Printing `1.0.0` |
| `mobile-record-files` vocabulary | 1.0 |
| Development snapshot | `0.1.0-20261103.233448-3645` (Central; development only) |

The 198-test cohort for this set passes with Checkstyle (scanner 53, demo 8, mobile Record Files 18, Printing bridge 16, Echo 68, Echo demo 35). The document, Record Files and Scripting owner artifacts used by Echo are not yet public; see [session state](docs/SESSION-STATE.md).

## Quick start

```powershell
.\mvnw.cmd verify install
.\mvnw.cmd -f demo/pom.xml verify
pwsh -File scripts/Start-Demo.ps1
```

On Unix use `./mvnw`. The last command starts the basic Scan Capture demo on loopback. [Local demo](docs/LOCAL-DEMO.md) explains the two launchers (basic Scan Capture versus the full Echo server), signing in without displaying passwords, connecting a device or emulator, and the enroll, approve, scan and revoke walkthrough. For a physical handheld follow [TC22 acceptance](docs/TC22-ACCEPTANCE.md); for test runners see [testing](docs/TESTING.md). Checkstyle runs during `validate`; do not disable it or skip tests.

## Architecture

The server uses QQQ's existing, versioned semantic UI/UX API; nothing parallel is added.

- App discovery and process execution go through `/qqq/v1`.
- Enrollment, branding and status are registered operations of the independently versioned `mobile-scanners` extension at `/qqq/v1/qbit/mobile-scanners`, with discovery, stable operation IDs and typed OpenAPI schemas. The Android host checks core and extension compatibility before login. See the [semantic API contract](docs/UI-API.md).
- The qbit contributes `mobileDevice`, private device credential storage, `mobileScan`, `mobileScanCapture`, the scanner widget and the device approve/revoke processes. Device sessions, live activity and consented screen viewing are in [device sessions](docs/DEVICE-SESSIONS.md) and [screen viewing](docs/DEVICE-SCREEN-VIEWING.md).
- Metadata changes that use supported native components reach devices from the server; a new required component type needs a shared Android update. This does not imply full web-client feature coverage.

```text
Android host --(/qqq/v1 + mobile-scanners extension)--> QQQ server + this qbit --> H2/RDBMS
                                                         |-- optional: Echo, Record Files, Document Generation, Printing
```

## Project layout

- `src/main`, `src/test`: the reusable qbit (enrollment routes, approval/revocation, scan process, table metadata) and its H2 behavior and permission tests.
- `demo`: basic local QQQ server with persistent H2 storage, native administration UI and password login. QQQ `4.0.0` lacks the password session exchange it needs.
- `e2e`: browser acceptance tests against the packaged frontend and an isolated real demo server.
- `examples/echo` and `examples/echo/demo`: the optional Echo library and its runnable composed server.
- `integrations/record-files`: optional camera/gallery upload bridge.
- `scripts`: Windows launcher and the password-copy helper.

## Supported features

- Single-barcode capture (Zebra EMDK service on the Android host, no DataWedge profile) and manual entry for Scan Capture, with QQQ table-based login.
- Device enrollment, approval and revocation from a record or from bulk selection (checked rows, select-all, first N up to 100), with confirmation and visible results.
- Idempotent scan events, device/session records, online activity, administrator session termination, own-app screen viewing, optional foreground location, and customer branding with connection diagnostics ([branding](docs/BRANDING.md)).
- Optional operator-consented device-screen viewing (extension 1.2), camera/gallery record uploads through the optional [Record Files bridge](integrations/record-files/README.md), and the Echo scan-to-print example.
- Additional apps are ordinary QQQ app/process/widget metadata; their server-side permission rules must explicitly protect their actions.

## Embed in a QQQ application

Enable `instance.setEnforcePermissionsInCoreActions(true)`, register `MobileScannersQBitProducer` with a host-owned persistent backend, and attach `MobileScannersRouteProvider` to the QQQ Javalin server. The qbit requires core permission enforcement so direct table actions cannot bypass its protections. Grant `mobile.scan` to operators and `mobile.admin` to administrators through the host's identity provider. The demo's fixed account roles stay inside the demo application.

Generic table mutations are blocked; device lifecycle and capture go through guarded operations. The API uses the normal QQQ user session plus `X-QQQ-Device-Id` and `X-QQQ-Device-Token`; device credentials appear only in those scoped headers and in private hashed storage, never in process inputs, responses beyond enrollment, fixtures or logs. Enrollment creates a pending device, approval enables app access and revocation prevents later captures. An installation identifier is descriptive, not a credential. Lost app data requires fresh enrollment and administrator review.

## Scan semantics

Each physical scan gets a new event UUID. A retry reuses that UUID and returns the original receipt; scanning the same barcode twice deliberately creates two events. Stored data includes barcode text (leading zeroes preserved), symbology, source, capture and receipt times, authenticated user, approved device, app and process, and reported hardware/app versions; extension 1.5 can add the scanner's AIM identifier and exact raw bytes. Client-reported timestamps and hardware details are provenance, not identity. The app shows success only after server acknowledgement, a timeout is an uncertain outcome to retry, and there is no offline queue.

## Limits and release status

- **Not production-ready.** The local demo works and the TC22 manual tests are operator-reported as passing; a QRun-signed Android 1.0 APK and its direct/MDM channel exist locally, but signed-build qualification on a device against an HTTPS server, distribution, HTTPS deployment and the other [readiness gates](docs/PRODUCTION-READINESS.md) are open. The HTTP loopback demo and an HTTPS production deployment are separate things.
- **Barcode fidelity.** Echo supports a documented set of symbologies and refuses GS1, ECI, structured and binary payloads ([limits](examples/echo/README.md#fidelity-and-limits)); no universal reproduction or all-symbology claim is made. Hardware AIM output has no separate evidence beyond the operator's group report.
- **Dependencies.** The development snapshot is published, but the document/files/scripting owner artifacts are not; Echo and the Record Files integration need a coherent local cohort until they are.
- **Outside this version:** offline workflows, camera-based decoding, a visual app designer and applied MDM rollout. The Android host implements managed configuration, a profile generator and a live wallpaper, but applied rollout is not qualified. Native OAuth2 login exists only for servers that advertise the semantic UI API 1.10 native-session and nonce-validation features; no native handheld test against a configured provider (such as Authentik) is claimed, and that acceptance plus production ingress hardening remain gates.
- **Database.** The supplied schema targets H2; migrations for other databases belong to the host application.

## Contributing and CI

Development pull requests target `develop`; `main` is reserved for reviewed releases. All Maven publication switches other than development snapshots stay disabled, and creating a repository or pushing a branch is not permission to publish. The CircleCI and GitHub workflow files describe the retained legacy shared QBit pipeline; [CI and release operation](docs/CI-CD.md) explains how to run the same checks locally, and the local demo does not depend on that pipeline. See [contributing](CONTRIBUTING.md), [security reporting](SECURITY.md) and the [code of conduct](CODE_OF_CONDUCT.md). Licensed under [Apache 2.0](LICENSE).
