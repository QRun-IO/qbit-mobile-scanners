# Running all qbits in the local test server

> The combined server is the maintained local server (host port 57832 in this mode only; public QQQ RC.21, Printing 1.0.0; API 1.10, mobile 1.5, printing 2.3). The "now responds ... Mobile Scanners 1.4" sentence at the end is a November 2 note; see [session state](../../../../docs/SESSION-STATE.md) for the current versions.

One server, one database: the existing `MobileScannersDemo` data file is opened in place by the combined `EchoDemo` (Mobile Scanners/Echo, Collaboration/Record Files, Document Generation, Printing). No second server, network bridge or reset.

## Environment contract (public values only; no secrets are read from or written to this file)
Use the existing `TrustedLocalEchoDemo` (or `EchoDemo`) launcher plus the normal Echo variables, and add:
- `ECHO_EXISTING_DATABASE` - absolute path of the existing H2 file, ending `.mv.db` (for the basic demo's database: `demo/data/mobile.mv.db` under your checkout, as an absolute path). Setting it is the explicit existing-local-server mode.
- `ECHO_DEMO_PORT` - `57832` is accepted only in this mode. Every other protected port (including peer Printing 18081) is still refused, and fresh/private demos still refuse 57832.
- `ECHO_DEMO_ROOT` - existing absolute directory for Record Files objects and renderer state (`objects`, `renderer-state`); the database is NOT taken from it in this mode.
- Unchanged: `ECHO_PRINTING_TENANT`, `ECHO_PRINTING_ADMIN_TOKEN` (32+ chars), `ECHO_RELEASE_GENERATED_FILES`, renderer variables (`ECHO_LOCAL_RENDERER*` or `ECHO_RENDERER_*`), `MOBILE_BRAND_*`.

## Behavior
- The database is opened with `IFEXISTS=TRUE;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000;WRITE_DELAY=0`. A missing file, a name not ending `.mv.db`, a relative path, a directory, a symbolic link or a reparse point is refused at startup and nothing is created.
- Startup adds only missing schema (`CREATE TABLE IF NOT EXISTS`, owner migrations). Accounts, password hashes, devices, device credentials, approvals and scans are never seeded, changed or reset; the `MOBILE_DEMO_*_PASSWORD` values are ignored in this mode.
- Authentication remains `EchoDemoAuthentication` over the existing demo accounts, native provider and device authorization. Restarts are idempotent.
- Requires the same H2 runtime (2.2.224) that wrote the file.

## Test
`EchoDemoAdoptionTest` uses temporary directories and fresh ports: baseline `MobileScannersDemo` with accounts, an approved device and a scan; the same file opened through `EchoDemo`; hashes, credentials, approval and scans unchanged; old logins work and new passwords do not; the one `QInstance` exposes Mobile, Echo, Record Files, Collaboration, Generation and Printing; idempotent restart; missing/wrong-suffix/relative/directory refusal; port guards. Both adoption tests passed in the full 26-test demo verification, with zero Checkstyle violations.

## Local development
Stop the existing development server and start the combined launcher with the variables above. This is a normal greenfield development restart. Do not run two servers against the same file concurrently (H2 file lock).

The TC22's existing `adb reverse tcp:8080 tcp:57832` connection sends the device-side `Host: 127.0.0.1:8080`. The loopback hostname check accepts a valid forwarded port while retaining exact same-origin and cross-site request checks. The scanner account still requires its approved device proof before Echo appears in its app catalog.

The combined server now responds on 57832 with API 1.10, Mobile Scanners 1.4 and Printing 2.3. Both original accounts authenticate, and authenticated metadata includes Printing, Document Generation and Record Files. The remaining demo acceptance is the installed print client's connection, a compatible published document, and physical scan/print/rescan on the TC22.
