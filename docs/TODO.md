# QQQ Mobile Scanners TODO

- [x] Upgrade the normal local demo to qualified API1.9/mobile1.1 with fresh closed backup, original-data invariants, recovery qualification and live verification of both existing accounts and preserved devices/scans.
- [x] Implement/review additive extension1.2 whole-device source with explicit Android consent, mode-bound leases and automated controls, preserving1.1 app-content compatibility.
- [ ] Complete actual Android projection/notification/Home/Stop/lock/rotation/expiry/remote-logout acceptance; normal server remains mobile1.1.

- [x] Qualify the demo BOM import and managed JUnit engine with fresh37 server/five demo tests, exact runtime module/Guava/H2 provenance and all15 single-record/bulk browser cases; see [acceptance evidence](API19-CONSUMER-ACCEPTANCE.md). The operator runtime remains separate.
- [x] Preserve legacy timestamp instants in the canonical supplied H2 schema with an explicit UTC migration connection; verify non-UTC file-backed upgrade, unchanged passwords/approval/scans and repeated initialization. The operator upgrade is preserved with UTC; a no-install reactor passes 37 base tests and five demo tests using the freshly built schema.
- [x] Implement unique short names, editable names/descriptions, additive H2 migration and protected profile editing; qualify the preserving upgrade of the operator database before deployment.
- [x] Implement separate approval/presence states, online-only fleet query and foreground location with accuracy/age and permission-denied behavior; actual GPS-fix acceptance remains in the release gates.
- [x] Implement the optional Record Files contract/native capture/association path and verify18 HTTP consumer cases on the transitional custom-filesystem tuple.
- [ ] Qualify coherent immutable/default RF dependencies and actual TC22 camera/gallery permissions and durable authorized association; no normal-runtime adoption is implied.

- [x] Device/session history, live activity and demand-based actual app-screen viewing through native QQQ administration and semantic extension 1.1.
- [x] Single-record and bulk End Session process with server-enforced invalidation, audit and browser/native E2E coverage.
- [x] Complete and seal the full server deep static security review of `0926550`; no reportable findings, with external implementation/deployment limitations retained.
- [ ] Complete final revision/scope reconciliation of historical security reports and remaining external deployment/provider gates. Independent architecture A1–A11 source closure and the filesystem regression correction are recorded; do not relabel partial/historical reports as a new exhaustive scan.
- [ ] Complete production 1.0 gates in [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md), including physical acceptance and deployment/release requirements.

- [x] Native qbit device/scan tables and permission boundaries.
- [x] Authenticated enrollment, approval and revocation.
- [x] Native Scan Capture process and idempotent scan storage.
- [x] Android login, device gate, app picker and current trusted EMDK scanner path; prior DataWedge handling is historical.
- [x] Persistent local demo with separate scanner/admin accounts.
- [x] Automated HTTP acceptance: pending denial, approval, capture, duplicate retry, revoke denial.
- [x] Android build and meaningful unit checks.
- [x] Standard repository files, current shared CircleCI profile, demo/Windows CI, CodeQL and Dependabot.
- [x] Validate CircleCI, workflow syntax and verification-only publication policy locally.
- [x] Connect hosted CI and confirm actual successful status checks.
- [x] Define branch protection policy from observed hosted checks; enforcement lives in GitHub settings.
- [x] Server-configured customer branding for one shared APK.
- [x] Public status endpoint for the Android connection footer and diagnostics.
- [x] Android emulator installed; native UI tests passed.
- [x] Single-record and bulk approval/revocation with confirmation, stable selection and visible results.
- [x] HTTP acceptance for checked rows, filtered bulk selection, persisted results and unselected-device isolation.
- [x] Browser E2E acceptance for all administration processes from record and bulk menus (six scenarios passed).
- [x] Scanner-host native E2E acceptance against an isolated real demo server.
- [x] Register enrollment, branding and status as the documented mobile-scanners 1.0.0 semantic UI API extension; generate OpenAPI and check the versioned contract baseline.
- [x] Verify extension discovery, real HTTP payload schemas, standard errors, public stale-credential behavior and rejection of legacy routes (root 23 tests and demo HTTP verification passed).
- [x] Coordinate Android core/extension compatibility checks before login and native semantic process/form updates.
- [x] Register the Android client channel while preserving host channel policies; verify the real HTTP suite sends Android-channel requests successfully.
- [x] Rerun all six browser E2E scenarios against the migrated server (passed in 2.9 minutes).
- [x] Verify the migrated client/server pair's incompatible-version, missing-extension, missing-channel, baseline scan and additive-metadata native scenarios with the OpenAPI request audit.
- [x] Verify Android's 53 JVM tests, six shared native UI tests, lint and debug/E2E APK builds.
- [x] Complete native approval/revocation/cancellation forms after the completion-state fix (255.917 seconds; all 31 requests matched to 13 published operations).
- [x] Publish server semantic API migration commit `7dd02b8` and verify its hosted CircleCI, CodeQL, Windows helper and demo HTTP/browser checks all pass.
- [ ] Finish current EMDK physical acceptance: Code128 positive controls pass; current QR, lifecycle/ownership and failure/retry/leave matrix remain open.
- [x] Identify the physical TC22, install the debug app, sign in and enroll through USB forwarding.
- [x] Approve the TC22 through its browser record Actions menu and open its native app catalog and Scan Capture process; preserve login across APK update.
- [x] Configure the TC22 DataWedge profile with explicit package/signature delivery; verify two deliberate Code128 hardware captures persist with matching metadata.
- [x] Verify physical QR delivery and exact preservation of leading zeroes in `001234567890`, with matching persisted scan metadata.
- [ ] Finish physical connection-loss/retry and revocation-denial acceptance alongside the requested device/session controls.
- [x] TC22 install and DataWedge acceptance guide written and updated with observed results.

Future scope (not version one): offline queue, camera-based barcode decoding, kiosk/MDM provisioning, broader native screen coverage, fleet rollout. Optional record-photo capture is implemented; its remaining qualification is tracked above.
