# QQQ Mobile Scanners

Server support for approved Android handhelds running native QQQ apps. The first app, **Scan Capture**, stores barcode events in a QQQ table. The companion Android application is [QQQ Android's scanner host](https://github.com/QRun-IO/qqq-android/tree/feature/mobile-scanner-host/scanner-host), currently in [pull request #8](https://github.com/QRun-IO/qqq-android/pull/8).

Version one is online only. Devices require an authenticated user and administrator approval. Android renders existing QQQ app, process, frontend-step and widget metadata; this qbit adds the scanner widget and enrollment lifecycle.

## Project layout

- `src/main`: reusable qbit, device enrollment routes, device approval/revocation, scan process and table metadata.
- `src/test`: server behavior and permission checks.
- `demo`: local QQQ server with persistent H2 storage, native administration UI and password login.
- `docs/TC22-ACCEPTANCE.md`: build, install and hardware verification.

## Build

Use Java 21 and Maven 3.9+. The implementation targets QQQ `4.1.0-RC.20`; QQQ `4.0.0` does not provide the password session exchange needed by this demo.

```powershell
.\mvnw.cmd verify install
.\mvnw.cmd -f demo/pom.xml verify
```

The wrapper pins Maven 3.9.9. On Unix, use `./mvnw` in place of `mvnw.cmd`. This is an initial implementation, with no published Maven artifact. Consult [session state](docs/SESSION-STATE.md) for actual verification status.

For the Windows demo, follow [local startup and credentials](docs/LOCAL-DEMO.md). For the scanner, follow [TC22 installation and acceptance](docs/TC22-ACCEPTANCE.md).

## Customer branding and connection diagnostics

Configure the customer name, primary color and optional HTTPS logo on the server; the same Android APK applies them before login and throughout the mobile screens. See [branding configuration](docs/BRANDING.md). The shared launcher remains QQQ Mobile.

The app footer checks server reachability while foregrounded. Tap it to inspect and copy recent safe diagnostic events. The public status endpoint reports reachability independently of user authentication and device approval.

## Contributing and CI

Development pull requests target `develop`. CircleCI uses the shared QRun-IO QBit pipeline; GitHub Actions runs the demo HTTP contract and CodeQL. All Maven publication switches start disabled. See [contributing](CONTRIBUTING.md), [CI and release operation](docs/CI-CD.md), [security reporting](SECURITY.md) and the [code of conduct](CODE_OF_CONDUCT.md). This project is licensed under [Apache 2.0](LICENSE).

## Embed in a QQQ application

Enable `instance.setEnforcePermissionsInCoreActions(true)`, register `MobileScannersQBitProducer` with a host-owned persistent backend, and attach `MobileScannersRouteProvider` to the QQQ Javalin server. The qbit requires core permission enforcement so direct table actions cannot bypass its protections. Grant `mobile.scan` to operators and `mobile.admin` to administrators through the host's identity provider/permission integration. The demo's fixed account roles are confined to the demo application.

The qbit contributes `mobileDevice`, private device credential storage, `mobileScan`, `mobileScanCapture`, and device approve/revoke processes. Generic table mutations are blocked; device lifecycle and capture go through guarded operations. An installation identifier is descriptive, not an authentication credential.

The API uses the normal QQQ user session plus `X-QQQ-Device-Id` and `X-QQQ-Device-Token`. Device credentials are not process input fields. Enrollment creates a pending device; approval enables app access, and revocation prevents subsequent captures. Lost local app data requires fresh enrollment and administrator review.

## Scan semantics

Each physical scan receives a new event UUID. A retry of that event uses the same UUID and returns the original receipt rather than inserting a second record. Scanning the same barcode twice intentionally produces two events.

Stored data includes barcode text, symbology, source, device capture time, server receipt time, authenticated user, approved device, app/process and reported hardware/app versions. Barcode text is preserved, including leading zeroes. Client-reported timestamps and hardware information are provenance, not trusted identity.

The app must show server acknowledgement before reporting a save. A network timeout has an uncertain outcome; retry the retained event. There is no persistent offline queue in this version.

## Scope

Initial support is single-barcode Zebra DataWedge intent delivery and QQQ table-based username/password login. Other identity providers need Android host configuration before use. Additional apps can use QQQ's existing native process and widget metadata; their server-side permission rules must explicitly protect their actions. A visual mobile app designer, offline workflows, camera scanning, MDM rollout and remote fleet management are separate future work. The supplied SQL schema targets H2; other database migrations belong to the host application.
