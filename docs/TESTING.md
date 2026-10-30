# Process acceptance tests

Every table-based process must be exercised through both the record screen and the bulk-selection UI. HTTP-only tests cannot establish that the menus, confirmation forms, results, or return navigation work. Mobile processes also require native UI tests against the real server.

| Process | Entry points | Required observable result |
| --- | --- | --- |
| Approve Device | Record Actions, checked rows, select-all/first-N, standalone IDs | Review selected devices; no early mutation; selected devices become approved; unselected devices stay unchanged; visible success result |
| Revoke Device | Record Actions, checked rows, select-all/first-N, standalone IDs | Selected devices become revoked; visible result; further capture is denied |
| Scan Capture | Android app picker and native process screen | Login, enrollment and approval gate; exact barcode stored with device/user metadata; acknowledgment; capture denied after revocation |

Scan Capture creates new scan records. It is not a process over existing table rows, so its entry point is the native scanner UI.

## Server and HTTP checks

With Java 21:

```powershell
.\mvnw.cmd verify install
.\mvnw.cmd -f demo/pom.xml verify
```

The HTTP suite uses an isolated H2 database and generated accounts. It sends the Android client-channel header on every request and exercises real login and process endpoints, asynchronous polling, confirmation/result steps, scan retry idempotency and authorization. Discovery must advertise the Android channel and the registered mobile-scanners extension. SQL assertions independently check persisted values and unaffected devices. Root process tests additionally cover malformed/oversized selections, first-N limits, and stable selection snapshots despite later enrollment or changed submitted values.

## Browser E2E

Build the server above, then generate the demo runtime classpath and install the pinned browser-test dependency:

```powershell
.\mvnw.cmd -f demo/pom.xml dependency:build-classpath '-Dmdep.outputFile=target/e2e-classpath.txt'
cd e2e
npm ci
npx playwright install chromium
npm test
```

The suite starts the packaged QQQ frontend and backend against a fresh temporary database. It signs in through the real session API and uses that session in a dedicated test browser, not your open browser or persistent demo. Native E2E separately covers the login UI. Port 57933 is the default; set `MOBILE_E2E_PORT` to choose another unused port. An already running server is never reused. Traces are disabled to avoid recording authentication headers. Test reports are ignored by Git.

## Native Android E2E

The companion Android repository provides `scripts/ci/scanner-e2e.mjs`. After building the server runtime classpath above, use Java 21, Android SDK 35, Node.js and a running emulator:

```powershell
cd ..\qqq-android
.\gradlew.bat :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest
$env:ANDROID_SERIAL = 'emulator-5554'
node scripts/ci/scanner-e2e.mjs ..\qbit-mobile-scanners
```

The runner uses a separate `io.qrun.mobile.scanner.e2e` package, a temporary server database and generated credentials. It preserves the normal scanner app's data and refuses physical devices. The test drives the actual login, pending approval, app picker and Scan Capture screens, then independently checks the saved row and revocation denial.

The semantic migration expands this to six scenarios: incompatible core version, missing extension, missing Android channel, baseline scan flow, additive future metadata, and native approval/revocation/cancellation forms. A test-only forwarding proxy matches requests to the real server's published OpenAPI operations and checks that rejected servers receive no credentials. Current verification results and remaining hardware checks are recorded in [session state](SESSION-STATE.md).

Hardware trigger, decoding and DataWedge delivery still require [TC22 acceptance](TC22-ACCEPTANCE.md). Emulator success does not establish physical scanner acceptance.
