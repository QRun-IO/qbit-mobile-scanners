# Zebra TC22 local acceptance

This checklist records steps to run on physical hardware. A passing JVM test or APK build does not establish that a TC22 scan succeeded.

## Start the server

From `qbit-mobile-scanners`, build and install the qbit, then build the demo:

```powershell
.\mvnw.cmd verify install
.\mvnw.cmd -f demo/pom.xml verify
```

On first startup, provide `MOBILE_DEMO_SCANNER_PASSWORD` and `MOBILE_DEMO_ADMIN_PASSWORD` through your local environment, with different values of at least 12 characters. Do not commit or paste passwords into an issue or transcript. The fixed usernames are `scanner` and `admin`. Password hashes and data persist in the H2 database; later startups preserve existing accounts.

Run from the demo directory so the database stays in `demo/data`:

```powershell
cd demo
..\mvnw.cmd exec:java
```

The administration UI is at `http://127.0.0.1:8080/`. Sign in as `admin`. The server binds only to loopback. Keep this local HTTP demo behind USB forwarding; use trusted HTTPS and the host application's identity provider for deployment.

## Build and install Android

Install Android SDK platform 35 and its build tools, then from `qqq-android`:

```powershell
.\gradlew.bat :scanner-host:testDebugUnitTest :scanner-host:assembleDebug
adb devices
adb install -r scanner-host/build/outputs/apk/debug/scanner-host-debug.apk
adb reverse tcp:8080 tcp:8080
```

Enable USB debugging on the TC22 and approve the workstation on the handheld when prompted. In QQQ Mobile, use `http://127.0.0.1:8080` as the local server. HTTP is permitted only for local testing by the debug build; deployed builds require HTTPS.

## Configure Zebra DataWedge

Create a dedicated profile associated with package `io.qrun.mobile.scanner` and activity `*`. Enable barcode input and Intent Output, disable Keystroke Output to avoid double delivery, and choose Broadcast Intent delivery. Set Intent action to `io.qrun.mobile.scanner.SCAN` and category to `android.intent.category.DEFAULT`.

In Intent Output's Component Information, restrict delivery to `io.qrun.mobile.scanner` and enable its application signature check. Use single-decode mode. Zebra documents barcode text, symbology and source in its [Intent Output guide](https://techdocs.zebra.com/datawedge/14-0/guide/output/intent/).

## Acceptance sequence

1. Record TC22 Android version, DataWedge version and installed APK version.
2. Sign in as `scanner`. Enroll with a recognizable device name. Confirm the pending device cannot run Scan Capture.
3. In the browser, sign in as `admin`, open Mobile Scanner Administration and locate that device. Copy its device ID, run Approve Device, and enter the ID in the form. Refresh status on the handheld.
4. Select Scan Capture. Scan a Code128 barcode and a QR code. Confirm one success receipt per scan.
5. Inspect Barcode Scans in the admin UI. Verify exact barcode text, symbology, source, user, device, capture/receipt timestamps and app/process identity. Include a barcode with leading zeroes.
6. Scan the same barcode twice deliberately. Confirm two events. Retry one retained event after a transport interruption and confirm one stored event for its UUID.
7. Stop the server. Attempt a scan and confirm no false success. Restart and explicitly retry the retained event. Verify the acknowledged row exists exactly once.
8. Run Revoke Device with the same device ID while its scan screen remains open. Confirm the next capture is refused and no row is added.
9. Sign out. Confirm the scanner cannot save under the previous user and the previous user session is ended.
10. Restart the server and application. Confirm saved scans and device approval persist. Reinstallation/data reset must not silently inherit approval.

Record pass/fail and observations in `SESSION-STATE.md`. Hardware acceptance remains pending until these steps are actually performed on the TC22.
