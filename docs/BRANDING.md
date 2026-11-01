# Customer branding and connection status

The shared Android APK checks the selected server's semantic API compatibility, then loads appearance before login. A customer can configure the in-app display name, primary color and optional logo without compiling another APK. The installed application's Android launcher name, icon and package identity remain properties of the APK. Branding and connectivity are documented operations in the [mobile-scanners semantic UI API extension](UI-API.md).

## Host configuration

Configure the existing qbit before producing its metadata:

```java
new MobileScannersQBitConfig()
   .withBackendName("mobileDb")
   .withDisplayName("Warehouse Mobile")
   .withPrimaryColor("#007A55")
   .withLogoUrl("https://assets.example.com/mobile-logo.png");
```

`displayName` must be 1–80 trimmed plain-text characters with no control characters or angle brackets. `primaryColor` must be `#RRGGBB`; the server normalizes hex letters to uppercase. `logoUrl` is optional (`null` clears it), at most 2048 characters, and must be an absolute HTTPS URL without user credentials or a fragment. Invalid settings fail configuration. Use a public static PNG or JPEG image; the server does not download or proxy it. The client must load it without forwarding QQQ session cookies, device credentials or authorization headers, and use its default visual treatment if loading fails. Branding is presentation, not verified customer identity or an authorization policy.

For the local demo, set any of these before launching `scripts/Start-Demo.ps1`:

```powershell
$env:MOBILE_BRAND_DISPLAY_NAME = 'Warehouse Mobile'
$env:MOBILE_BRAND_PRIMARY_COLOR = '#007A55'
$env:MOBILE_BRAND_LOGO_URL = 'https://assets.example.com/mobile-logo.png'
```

Unset variables use defaults: `QQQ Mobile Scanners`, `#1565C0`, and no logo. Restart the server after configuration changes, then reconnect the client. The existing Windows launcher passes these environment variables through to Java. Use only public logo assets. Branding is public; keep private credentials and sensitive tenant details out.

## Public client contract

`GET /qqq/v1/qbit/mobile-scanners/branding` returns HTTP 200 with `Cache-Control: no-store` and JSON:

```json
{
  "displayName": "Warehouse Mobile",
  "primaryColor": "#007A55",
  "logoUrl": "https://assets.example.com/mobile-logo.png"
}
```

The `logoUrl` property is omitted when unset. The endpoint requires no session or approved device and ignores stale device headers. It exposes only those allowlisted appearance fields, never qbit configuration, backend names, secrets or enrollment records. A compatible server's unavailable or malformed branding uses the client's default theme. A server missing the required extension is blocked before login with an upgrade/configuration message.

`GET /qqq/v1/qbit/mobile-scanners/status` likewise returns HTTP 200, `Cache-Control: no-store`, and `{"status":"ok"}` without authentication. This means the HTTP service is reachable; it does not test database readiness, authenticate a user, or approve a device. Clients must continue using authenticated device status and normal process responses for those decisions. No diagnostics, database details or credentials are returned.
