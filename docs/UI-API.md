# Mobile Scanners semantic UI API

Mobile Scanners is an independently versioned QQQ UI API extension. `MobileScannersQBitProducer` registers the `mobile-scanners` contract, currently **1.3.0**, through `MiddlewareSpecExtensionsMetaData`. The framework generates its routes, discovery advertisement and merged OpenAPI from the same endpoint specs. Optional operator-consented device-screen viewing is described in [DEVICE-SCREEN-VIEWING.md](DEVICE-SCREEN-VIEWING.md); app-content remains the default. There is no separate `/mobile/v1` API or legacy alias.

The host must also install `MobileScannersRouteProvider` on the same server. This provider binds verified device headers to the current request; it does not register additional business routes. Keep the versioned QQQ routes enabled. The qbit registers the `android` client channel while preserving the host's existing channel policies. The demo disables legacy unversioned middleware routes.

## Discover before sending credentials

1. Fetch `GET /qqq/v1/version` without user or device credentials.
2. The current Android client requires core semantic version `>=1.8.0 <2.0.0`, with `version`, `metaData`, `processes`, `processCancel`, `extensions` and `widgets` features, and `android` in `clientChannels`.
3. Find `extensions[].name == "mobile-scanners"`, require extension version `>=1.0.0 <2.0.0`, and verify `pathPrefix == "/qbit/mobile-scanners"`.
4. Resolve the extension prefix under the advertised core base path `/qqq/v1`. Fetch public branding, discover authentication through the core API, and use the existing QQQ session flow.
5. Discover apps, processes, frontend steps, fields and widgets through the core metadata/process API. Treat process/widget names as metadata, not protocol selectors.

An incompatible or missing contract blocks the client with an upgrade/configuration explanation. Do not silently fall back to undocumented routes. Additive unknown JSON properties are safe to ignore. Unknown device states never mean approved; unsupported required UI components must not be silently skipped.

The discovery document advertises the relative extension `pathPrefix`; merged OpenAPI at `/qqq/v1/openapi.json` and `/qqq/v1/openapi.yaml` contains full paths.

## Operations

Every path below is relative to `/qqq/v1/qbit/mobile-scanners`.

| Method and path | OpenAPI operationId | Authentication | Success |
| --- | --- | --- | --- |
| `GET /branding` | `mobileScannersBranding` | Public | 200, `MobileScannersBrandingV1` |
| `GET /status` | `mobileScannersStatus` | Public | 200, `MobileScannersStatusV1` |
| `POST /devices/enroll` | `mobileScannersEnrollDevice` | QQQ user session, `mobile.scan` | 201, `MobileScannersEnrollmentV1` |
| `GET /devices/current` | `mobileScannersCurrentDevice` | QQQ user session, `mobile.scan`, both device headers | 200, `MobileScannersDeviceV1` |

Public discovery, OpenAPI, branding and connectivity ignore stale device headers. Authentication discovery/session/logout also remain available when a previously enrolled credential is no longer valid. Responses from extension handlers use `Cache-Control: no-store`.

Enrollment takes `MobileScannersEnrollRequestV1`: required canonical UUID `installationId`, optional `displayName`, `manufacturer`, `model`, `osVersion` and `appVersion` strings, each at most 200 characters. The JSON request body is limited to 4096 characters. A duplicate installation returns HTTP 409 and never rotates or discloses its existing credential. A new installation starts `PENDING` and receives its private `deviceToken` once.

Send `X-QQQ-Device-Id` and `X-QQQ-Device-Token` on authenticated device/core metadata, widget and process requests to this same origin. The device ID is a UUID; the token is private and at most 128 characters. These headers supplement the authenticated user session. They never replace user permission checks. Never put a token in metadata, URLs, process values, screenshots, logs or fixtures; never forward credentials to a branding asset origin. Store the issued credential privately. Only its digest is stored server-side.

`devices/current` returns `deviceId`, `displayName` and `status`, never a token. `PENDING` and `REVOKED` devices may read their status. Scanner access still requires `APPROVED`, checked when launching and saving, including asynchronous/resumed execution. Device approval/revocation uses the ordinary QQQ processes, including single-record and bulk selections.

Errors use core `BasicErrorResponseV1`, including `error`, `message`, `code` and `traceId`; `X-QQQ-Trace-Id` matches the body. Invalid enrollment input is 400/`BAD_REQUEST`, missing login 401/`UNAUTHENTICATED`, denied permission or credential 403/`PERMISSION_DENIED`, duplicate installation 409/`CONFLICT`, unexpected server failure 500/`INTERNAL_ERROR`. Error payloads contain no credential or backend details.

## SCANNER widget vocabulary

Extension 1.x defines `MobileScannersScannerBlockV1`, a `SCANNER` child of the core `COMPOSITE` widget. The standard core process/widget operations transport it. There is no scanner-specific run endpoint. QQQ RC20's closed backend block enum predates `SCANNER`, so `MobileScannerWidget` emits its documented JSON through ordinary `QWidgetData`.

The block contains required `blockId`, `blockType: "SCANNER"` and `values`. `MobileScannersScannerValuesV1` defines required `title` and `instructions`. Optional `manualLabel` and `submitLabel` default to `Manual barcode` and `Submit barcode`. Optional integer `maxLength` defaults to 4096 and must be within 1–4096. The current server supplies all five values. Native barcode input and manual entry use the same semantics.

`MobileScannersScanEventV1` describes values submitted to the current core frontend step: required nonempty string `barcode` (maximum 4096), canonical UUID `eventId` and ISO-8601 `capturedAt`; optional bounded strings `symbology` and `scanSource`. Leading zeroes and nonblank decoder identifiers are retained exactly, including vendor names such as `LABEL-TYPE-CODE128`; barcode text never implies a symbology. Missing or blank type becomes `UNKNOWN`, or `MANUAL` for explicit manual source. Missing or blank source becomes `UNKNOWN`. A retry keeps the same event ID; changing its barcode, type, source, actor, application or executing process is rejected. User/device identity is derived from verified context, not from supplied values.

After core asynchronous polling completes, `MobileScannersScanAcknowledgmentV1` describes the response values: `scanSaved` must be true, `acknowledgedEventId` must match the submitted event and `scanId` must be nonempty. A successful HTTP response alone is not a persistence acknowledgment. A retry returns the original scan ID. The process returns its next screen and widget through the same core response.

### Accepted scan context and backend actions

Extension 1.3 additionally supplies `values.scanContext`, described by `MobileScannersScanContextV1`. It contains `scanId`, `barcode`, `symbology`, `scanSource`, `eventId`, `capturedAt`, `receivedAt`, `userId`, `deviceId`, `appId` and `processName`. Dates are ISO-8601 strings. Context comes from the accepted persisted event, rather than a submitted context map. The application comes from server configuration and the process from registered executing metadata; neither comes from request values. This additive property stays optional in the acknowledgment schema for compatibility with older 1.x servers.

Hosts can append ordinary backend steps after the default process's `save` step. They read `input.getValue("scanContext")` as a map and branch on its `symbology` without querying the scan table. The same text may therefore take separate QR and Code 128 actions. The final host step explicitly returns to `scan`; the unextended default process still loops automatically. Scan-row deduplication does not make arbitrary host actions execute exactly once: those actions must use the stable `scanId` or `eventId` for their own idempotency.

Host-owned processes use `MobileScannerActions.capture(input, appName)` with their static registered application name, and publish the returned immutable map as `scanContext`. The default overload uses the scanner application. Its permission checker must call the existing `MobileDeviceAuthorization.captureCurrent()` before async dispatch, reject empty device attribution and retain `mobile.scan` permission enforcement. Capture rechecks current authority and approval; a client-provided context never authorizes work.

Raw event fields and old receipts are consumed before capture, including rejected attempts, so the next submission cannot inherit a preceding type. Following steps use the nested accepted context. Historical NULL type/source values normalize for comparison and returned context without rewriting those rows or accepting a newly claimed known type on retry. Pre-1.3 process state containing a receipt without context fails closed: leave/cancel and reopen the scanner process. Logout and device re-enrollment are unnecessary.

## Contract checks and compatibility

The published fragment is [mobile-scanners.yaml](../src/main/resources/openapi/qqq-v1/mobile-scanners.yaml); the initial release baseline is [mobile-scanners-1.0.0.yaml](../src/test/resources/openapi/mobile-scanners-1.0.0.yaml). `MobileScannersApiContractTest` rejects generated-document drift, unresolved contract structure, incompatible baseline changes and missing version bumps using QQQ's shared contract checker. Object required fields and scanner numeric bounds are standard OpenAPI keywords, added by small Schema subclasses because RC20's base model does not expose those setters.

For an intentional contract edit, update specs/schemas and the extension semantic version, then regenerate only the current fragment:

```powershell
.\mvnw.cmd '-Dtest=MobileScannersApiContractTest' '-Dmobile.openapi.update=true' test
```

Do not refresh the release baseline to hide a compatibility failure. Additive compatible changes require a minor version increment; fixes require a patch increment. Breaking contracts require the corresponding new major contract and an explicit client migration. A server can change supported metadata/screens without rebuilding customer APKs; introducing a new required native component needs a shared client update and compatible contract negotiation.

Root `verify` checks the committed fragment/baseline and public stale-credential behavior. Demo `verify` runs the real HTTP/authentication/process/H2 lifecycle, checks discovery and merged operation IDs/security/schema references, validates actual enrollment/device/SCANNER/event/ack payloads against served schemas, and checks structured errors and rejection of legacy routes. It writes sanitized core and extension discovery fixtures under `demo/target/android-contract` for Android contract tests. Browser/native end-to-end suites provide the final rendered UI and persistence checks.
