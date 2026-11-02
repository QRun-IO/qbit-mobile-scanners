# Echo runnable local demo

Optional module (`qqq-echo-demo`) that turns the Echo example into a server you can start. The library module `examples/echo` does not depend on it. It reuses `MobileScannersDemo` (database, `scanner`/`admin` accounts, native server) and adds Collaboration, Record Files, Printing, Document Generation and Echo from their public production classes. Nothing is simulated: the renderer is the owner's cold Docker bridge, output is retained by Record Files, and printing is Printing's own service. The server binds `127.0.0.1` only.

Status (2026-11-02): 22 demo tests and 38 Echo library tests pass, Checkstyle 0. The composed acceptance test passes: an authenticated approved scanner, two scans, two retained originals and two accepted Printing jobs on one printer, an idempotent retry, and leaving closes the run. It uses a controlled synthetic renderer and a simulated Printing queue, so it is **not physical output**. The production `EchoDemo` default is unchanged and fails closed (the owner's cold bridge needs `runsc`; Windows Docker Desktop reports `runc`). A local run prints its URL in the startup log (`ECHO_DEMO_READY port=<port>`, then `http://127.0.0.1:<port>`; the port is chosen per run). Not production-ready and not a public SDK release. See `CLAUDE-HANDOFF.md` for status and what remains.

## Trusted local development launcher

`TrustedLocalEchoDemo` is a separate, explicit main for a developer machine. It starts the same server, accounts, permissions and loopback binding as `EchoDemo`, but builds the owner's `IsolatedRendererBridge(exchange.limits(), RetainedAssetReader, exchange)` over the renderer owner's supported public `TrustedLocalDockerExchange`, built from `TrustedLocalDockerExchange.configuration(...)`. This is **not** a production sandbox qualification: it has no repository digest, uses the owner's literal unqualified local qualification id, and is not gVisor. The production `EchoDemo` default is unchanged and fails closed, and neither launcher falls back to the other. The owner's exchange and its tests are the transport boundary; this demo adds no transport of its own.

The exchange requires the renderer artifact `qbit-document-generation-renderer` at a version that contains it. The demo POM's `renderer.version` property defaults to `${generation.version}`; override it for the private cohort, for example `-Drenderer.version=0.1.0-echo.gen64c9pu507.tl1`. Only the direct renderer dependency uses that property.

The launcher passes the owner factory: the Docker executable and exact image id from the environment below, a state directory `<ECHO_DEMO_ROOT>\renderer-state` (created if absent, absolute, not a symbolic link), the fixed owner id `echo-local`, the deadline, maximum bytes and pages, and a maximum concurrency of 1. Use a **fresh private `ECHO_DEMO_ROOT` for each runtime**: the journal there must belong to this process alone. At startup it calls the exchange's `reapExpired()` once, which reclaims only this owner's expired leases left by a killed process and logs a warning with the count of any it could not reclaim.

Extra environment (in addition to the shared settings below, except the `ECHO_RENDERER_*` cold-bridge variables, which this launcher does not use):

| Variable | Meaning |
| --- | --- |
| `ECHO_LOCAL_RENDERER` | Must be exactly `TRUSTED_LOCAL_DEVELOPMENT`; anything else refuses startup. |
| `ECHO_LOCAL_RENDERER_DOCKER` | Absolute path of an existing Docker executable. |
| `ECHO_LOCAL_RENDERER_IMAGE_ID` | The exact local image id, `sha256:` plus 64 lowercase hex characters. Tags and repository digests are refused. |
| `ECHO_LOCAL_RENDERER_DEADLINE_SECONDS` | Optional, 1-30, default 10. |

`ECHO_RENDERER_MAXIMUM_BYTES` (default 1048576) and `ECHO_RENDERER_MAXIMUM_PAGES` (default 1) also apply. The owner's exchange refuses the render unless the daemon reports runc, linux and exactly the configured image id. Launch exactly as in "Build and start" but with this main class:

```powershell
java -cp "examples\echo\demo\target\classes;$(Get-Content examples\echo\demo\target\classpath.txt)" com.kingsrook.examples.echo.demo.TrustedLocalEchoDemo 48123
```

The log line reports `renderer=trusted-local-development`. The unit tests (`TrustedLocalRendererSettingsTest`) never start Docker.

## Build and start

From the repository root, with the checked-in wrapper (`mvnw.cmd` on Windows) and the tuple installed in your Maven repository:

```powershell
.\mvnw.cmd -B -f examples\echo\demo\pom.xml verify
.\mvnw.cmd -B -f examples\echo\demo\pom.xml dependency:build-classpath "-Dmdep.outputFile=examples\echo\demo\target\classpath.txt"
```

Set the configuration below in the environment (never on the command line), then:

```powershell
java -cp "examples\echo\demo\target\classes;$(Get-Content examples\echo\demo\target\classpath.txt)" com.kingsrook.examples.echo.demo.EchoDemo 48123
```

The optional argument is the port; otherwise `ECHO_DEMO_PORT`. There is no default port. Ports 1024-65535 are accepted except the protected local runtimes listed in `EchoDemoSettings`. `mvnw.cmd -f examples\echo\demo\pom.xml exec:java` is equivalent when the environment is set.

## Configuration (environment only)

Required:

| Variable | Meaning |
| --- | --- |
| `ECHO_DEMO_ROOT` | Existing private absolute directory (not a symlink). The H2 file database lives in `<root>\db\echo`. |
| `ECHO_DEMO_PORT` | Loopback port, unless passed as the argument. |
| `ECHO_PRINTING_TENANT` | Printing tenant label. Operator sessions are locked to it. |
| `ECHO_PRINTING_ADMIN_TOKEN` | 32-1024 character bearer token for Printing's administration routes. |
| `ECHO_RENDERER_DOCKER` | Absolute path of the Docker executable. |
| `ECHO_RENDERER_IMAGE` | Digest-pinned image `repo@sha256:...` (the qualified catalog-9 cold image). |
| `ECHO_RENDERER_IMAGE_ID` | `sha256:...` image id. |
| `ECHO_RENDERER_QUALIFICATION_ID` | Owner qualification evidence id. |
| `ECHO_RENDERER_REQUIRED_QUALIFICATION_ID` | Must equal the evidence id above. |
| `ECHO_RENDERER_OWNER_ID` | Deployment owner id for renderer admission. |
| `ECHO_RENDERER_STATE_DIR` | Existing private absolute directory for renderer state. |
| `MOBILE_DEMO_SCANNER_PASSWORD`, `MOBILE_DEMO_ADMIN_PASSWORD` | First startup only, 12+ characters, as in the mobile demo. Existing accounts are never changed. |

Optional: `ECHO_RENDERER_DEADLINE_SECONDS` (10, max 30), `ECHO_RENDERER_MAXIMUM_BYTES` (1048576), `ECHO_RENDERER_MAXIMUM_PAGES` (1), `MOBILE_BRAND_*` (as the mobile demo), `ECHO_RELEASE_GENERATED_FILES=true`.

Retained files are stored by `LocalFilesystemAttachmentStorage` (the attachment owner's own storage SPI) under `<root>\objects`. Keys are hashed into file names, writes are temp-file-then-link and never replace an object. It adds no policy: Record Files and the attachment owner still decide quarantine, authorization and lifecycle. Single host, demo only.

`ECHO_RELEASE_GENERATED_FILES=true` sets Record Files' generated-file quarantine policy to off. Without it generated output is quarantined, Echo sees it as unavailable, and every print is refused. Turn it on only if you trust every producer allowed to retain generated output in this deployment.

Template pin, all three or none: `ECHO_TEMPLATE_ID`, `ECHO_TEMPLATE_MEDIA_ID`, `ECHO_TEMPLATE_MEDIA_REVISION`. Without it the host starts and Echo refuses every scan with "No qualified same-symbology layout".

## Accounts and permissions

`scanner` receives exactly: `echo.use`, `printing_operator`, `document-generation.view|generate|print`, `record-files.view|manage` (manage is the owner's intake permission) and the `printing_tenant` row key. `admin` receives the same plus `printing_admin` (Printing's native pairing/approval/setup processes) and `document-generation.configure|publish` to prepare templates. The scanner never gets these, and nobody gets Record Files release/configure/audit. Scans still need an enrolled and approved device, so use the existing mobile enrollment flow.

## Operator order

1. Start the host. Enroll the Windows printing agent against `/printing/v1` and let it report printer media.
2. As `admin`, publish the ZPL template for the reported media in Document Generation (scene per `EchoTemplatePublisher`). Record the template id, the media id and the media revision.
3. Restart with the template pin.

## Existing databases

Startup applies each owner schema with "already exists" tolerated, plus the three shipped Generation H2 migrations. It does not migrate a database created by a different version. Plan migration separately for anything beyond a fresh demo root.