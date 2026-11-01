# Run the local demo on Windows

Install Java 21, PowerShell 7, and the Android SDK. Start the server from this repository:

```powershell
pwsh -File scripts/Start-Demo.ps1
```

The launcher verifies both Maven projects, then keeps the server running at <http://127.0.0.1:8080/>. Stop it with Ctrl+C. After a successful build, `-SkipBuild` starts it directly. If port 8080 is unavailable, use `-Port 18080` and open <http://127.0.0.1:18080/>. The database persists under `demo/data`; the server binds only to loopback.

Before upgrading an existing demo database, stop its JVM gracefully and back up the closed `demo/data` directory together with its protected credential file. The shared H2 schema begins with `SET TIME ZONE 'UTC'` before converting legacy `TIMESTAMP WITH TIME ZONE` columns to the current UTC `TIMESTAMP` representation. This protects both the demo initializer and hosts executing the supplied schema directly. It preserves the original instants independently of the workstation time zone; rerunning initialization preserves existing accounts, passwords, device approval and scans. Do not copy over an open H2 database or use an account reset as an upgrade step. The schema leaves its migration connection in UTC; hosts should use a dedicated migration connection or restore their desired connection zone after the entire script completes.

Use the matching Android build with the [mobile-scanners semantic API extension](UI-API.md). Public discovery is at `/qqq/v1/version`; the extension's connectivity endpoint is `/qqq/v1/qbit/mobile-scanners/status`. This migration removes the former standalone mobile routes, so upgrade the local server and APK together. Installing the APK with `adb install -r` and restarting the server preserves the existing application data and database; no account or device reset is required.

First startup creates random passwords for the `admin` and `scanner` accounts. The launcher stores them using Windows DPAPI under `%LOCALAPPDATA%/QRunIO/MobileScannersDemo/credentials.clixml`, readable only through the same Windows user/machine protection. They are never written to the repository or printed. Keep that credential file with this local database; the launcher never changes existing account passwords.

In another PowerShell window, copy the admin password when ready to log into the browser:

```powershell
pwsh -File scripts/Copy-DemoPassword.ps1 -Account admin
```

Sign in with username `admin`, paste the password, then clear the clipboard with `Set-Clipboard -Value ''`. Use `-Account scanner` to copy the scanner password for the Android login. These accounts belong only to this local demo. For another operating system, supply your own password environment variables as described in [TC22 acceptance](TC22-ACCEPTANCE.md).

## Emulator walkthrough

Follow the Android repository's [emulator guide](https://github.com/QRun-IO/qqq-android/blob/feature/mobile-scanner-host/docs/EMULATOR.md) to start a virtual device and install the debug APK. Forward its port to the host:

```powershell
adb reverse tcp:8080 tcp:8080
```

If the host server uses `-Port 18080`, use `adb reverse tcp:8080 tcp:18080` instead; the Android server URL still uses port 8080.

1. Open **QQQ Mobile** on the emulator and use server `http://127.0.0.1:8080`.
2. Sign in as `scanner` and enroll the device. It starts pending approval.
3. In the browser as `admin`, open **Mobile Scanner Administration → Mobile Devices**, open the device record, and choose **Actions → Approve Device**. Review the selected device, continue, and check the success result. For several devices, select their rows in the device list and run the same action from the bulk menu. The standalone sidebar process also accepts device IDs.
4. Refresh approval on Android, open **Scan Capture**, type a barcode such as `000123456789`, and submit it.
5. Inspect **Barcode Scans** in the browser and verify the exact text, user, device and timestamps.
6. Choose **Revoke Device** from the device record or bulk selection, confirm the selection, and verify subsequent capture is refused.

The emulator exercises native screens, login, approval, manual capture and persistence. Verify the physical trigger, DataWedge delivery and barcode decoding on the Zebra TC22 using [the hardware checklist](TC22-ACCEPTANCE.md).

Approval and revocation support checked rows, select-all matching rows, and first-N selections, up to 100 devices per run. The confirmation fixes the selected device IDs for that run, so a newly enrolled matching device is not unexpectedly included. Larger selections fail explicitly; choose a smaller batch. No approval changes occur until you continue past confirmation.

See [branding configuration](BRANDING.md) to test customer names, colors and logos with the same APK. Tap the Android connection footer to view or copy diagnostic events.
