# Run the local demo on Windows

The local demo runs on your workstation, binds only to loopback and is independent of the shared CI pipeline. There are two launchers; pick by what you want to try.

| | Basic Scan Capture demo | Full Echo demo (all qbits) |
| --- | --- | --- |
| Starts | `scripts/Start-Demo.ps1` | `EchoDemo` or `TrustedLocalEchoDemo` from [`examples/echo/demo`](../examples/echo/demo/README.md) |
| Contains | Mobile Scanners only: enrollment, approval, the **Scan Capture** app, device sessions, barcode scan table | Mobile Scanners plus Echo, Collaboration/Record Files, Document Generation and Printing in one server |
| Try it for | Login, device approval, scanning and storing barcodes | Scan, render and print a label (the Echo app), scan photos |
| Port | `-Port` parameter, script default `8080` | No default: pass a port argument or set `ECHO_DEMO_PORT` |
| Setup | Java 21 and PowerShell 7 | Also a Printing tenant, a printing agent and printer, Docker for the renderer and published Echo templates; see its README |
| Accounts | Generated on first start, kept in a per-user protected file | Reuses the basic demo's accounts when started on an existing database |

The maintained local server in this project uses the full Echo launcher in its existing-database mode. That mode is the only one that accepts host port `57832`; fresh demos refuse it. See [adopting the existing local server](../examples/echo/demo/docs/LOCAL-SERVER-ADOPTION.md).

## Basic Scan Capture demo

Install Java 21, PowerShell 7 and the Android SDK, then from this repository:

```powershell
pwsh -File scripts/Start-Demo.ps1
```

The launcher verifies both Maven projects, then keeps the server running at <http://127.0.0.1:8080/>. Stop it with Ctrl+C. After a successful build, `-SkipBuild` starts it directly. If port 8080 is unavailable, use `-Port 18080` (or another port) and open that address. The database persists under `demo/data`.

First startup creates random passwords for the `admin` and `scanner` accounts. They are protected with Windows DPAPI under `%LOCALAPPDATA%/QRunIO/MobileScannersDemo/credentials.clixml`, readable only by the same Windows user, and are never written to the repository or printed. The launcher never changes existing passwords. If a database exists without its credential file, it refuses to start rather than reset accounts.

To sign in without ever displaying a password, use the helper from another PowerShell window:

```powershell
pwsh -File scripts/Copy-DemoPassword.ps1 -Account admin
```

Paste into the browser login for user `admin`, then clear the clipboard with `Set-Clipboard -Value ''`. Use `-Account scanner` for the handheld login. Never paste passwords into an issue, chat or log. These accounts belong only to the local demo; on another operating system, supply your own password environment variables as described in [TC22 acceptance](TC22-ACCEPTANCE.md).

## Connect a device or emulator

Install the matching debug build of the [Android scanner host](https://git.kof22.com/QRun-IO/qqq-android) (the `scanner-host` module; its `docs/EMULATOR.md` covers the emulator). Always pick the target explicitly when more than one device is attached. USB or paired wireless ADB both work; do not put device serials or addresses in notes or commits.

```powershell
adb devices -l
$serial = '<serial or address from adb devices>'
adb -s $serial install -r <path to scanner-host-debug.apk>
adb -s $serial reverse tcp:8080 tcp:<host port>
```

`install -r` updates the app without clearing its login or enrollment. Use the host port the server actually listens on (8080, the `-Port` value, or the Echo launcher's port). The app URL on the device stays `http://127.0.0.1:8080` because the forward maps device port 8080 to the host port. The debug build permits HTTP only for this local use; deployed builds require HTTPS.

## Walkthrough: enroll, approve, scan, revoke

1. Open **QQQ Mobile** on the device and use server `http://127.0.0.1:8080`.
2. Sign in as `scanner` and enroll. The device starts pending approval.
3. In the browser as `admin`, open **Mobile Scanner Administration → Mobile Devices**, open the device record and choose **Actions → Approve Device**. Review the selected device, continue and check the success result. For several devices, select their rows and run the same action from the bulk menu; the standalone sidebar process also accepts device IDs.
4. Refresh approval on the device, open **Scan Capture**, scan or type a barcode such as `000123456789` and submit it.
5. Inspect **Barcode Scans** in the browser and verify the exact text, user, device and timestamps.
6. Choose **Revoke Device** from the record or a bulk selection, confirm, and verify that later capture is refused.

Approval and revocation accept checked rows, select-all matching rows and first-N selections, up to 100 devices per run. The confirmation fixes the selected IDs for that run, so a device that enrolls afterwards is not included; larger selections fail explicitly, and nothing changes until you continue past confirmation.

An emulator exercises native screens, login, approval, manual capture and persistence. The physical trigger, Zebra scanner delivery and decoding need real hardware: use the [hardware checklist](TC22-ACCEPTANCE.md).

## Full Echo demo

Follow the [Echo demo README](../examples/echo/demo/README.md) for build, environment variables (set in the environment, never on the command line), accounts and permissions, and operator order. Use the trusted-local launcher for a developer machine and the production `EchoDemo` only with the owner's qualified renderer sandbox. The Android flow is the same as above, then open **Echo**, choose the printer, paper size, document and optional copies, and scan.

## Upgrading an existing demo database

Stop its JVM gracefully and back up the closed `demo/data` directory together with its protected credential file. The shared H2 schema begins with `SET TIME ZONE 'UTC'` before converting legacy `TIMESTAMP WITH TIME ZONE` columns to the current UTC `TIMESTAMP`, preserving original instants regardless of the workstation time zone. Re-running initialization preserves accounts, passwords, device approval and scans. Do not copy over an open H2 database, and do not use an account reset as an upgrade step. The schema leaves its migration connection in UTC, so hosts should use a dedicated migration connection or restore their own zone afterwards.

Use the matching Android build with the [mobile-scanners semantic API extension](UI-API.md). Public discovery is at `/qqq/v1/version`; the connectivity endpoint is `/qqq/v1/qbit/mobile-scanners/status`. Upgrade the server and the app together: the former standalone mobile routes are gone.

See [branding configuration](BRANDING.md) to try customer names, colors and logos with the same APK. Tap the Android connection footer to view or copy diagnostic events.
