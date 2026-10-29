# Run the local demo on Windows

Install Java 21, PowerShell 7, and the Android SDK. Start the server from this repository:

```powershell
pwsh -File scripts/Start-Demo.ps1
```

The launcher verifies both Maven projects, then keeps the server running at <http://127.0.0.1:8080/>. Stop it with Ctrl+C. After a successful build, `-SkipBuild` starts it directly. If port 8080 is unavailable, use `-Port 18080` and open <http://127.0.0.1:18080/>. The database persists under `demo/data`; the server binds only to loopback.

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
3. In the browser as `admin`, open **Mobile Scanner Administration**, locate the device, copy its ID, and run **Approve Device** with that ID.
4. Refresh approval on Android, open **Scan Capture**, type a barcode such as `000123456789`, and submit it.
5. Inspect **Barcode Scans** in the browser and verify the exact text, user, device and timestamps.
6. Revoke the device and confirm subsequent capture is refused.

The emulator exercises native screens, login, approval, manual capture and persistence. Verify the physical trigger, DataWedge delivery and barcode decoding on the Zebra TC22 using [the hardware checklist](TC22-ACCEPTANCE.md).

See [branding configuration](BRANDING.md) to test customer names, colors and logos with the same APK. Tap the Android connection footer to view or copy diagnostic events.
