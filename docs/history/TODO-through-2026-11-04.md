> Historical record, superseded by [current status](../SESSION-STATE.md). Do not treat as current.

# QQQ Mobile Scanners TODO

Current checkpoint (November 4; supersedes historical notes below):

- [x] Scanner PR 12 merged `286e2e0`; full run 3645 succeeded. Central snapshot `0.1.0-20261103.233448-3645` verified anonymously, including all 37 published Java source files.
- [x] Android PRs 10 and 8 merged into main `0ae2101`; full main lifecycle 3634 succeeded. Shared CI's independent registry readback matched all ten primary library files at `0.1.1-20261103.232145-3634`.
- [x] Qualify released Printing `1.0.0` with public QQQ RC.21: 198 tests pass, Checkstyle passes, all 94 scanner Java files match reviewed source, and no private QQQ backend artifacts remain in that cohort.
- [x] Adopt that cohort on local port 57832 while retaining the database/settings. Confirm API health, TC22 Echo setup/Copies 1/Connected and Print Agent Connected and ready after restart.
- [x] Adopt and review Printing `1.0.0` in both Echo POMs and matching READMEs. Both Maven validate/Checkstyle checks pass; 94 Java inputs match the 198-test qualification.
- [ ] Document owner: extract reviewed code from host candidate `b6fb238` with provenance, set public dependency pins and standard standalone release wiring, then publish coherent document/files/scripting Maven artifacts including Generation copies. The local standalone directories are not Git repositories and have no workflows; the website image pipeline does not publish these artifacts. Claude access is restored; the dependency owner's actual Claude standalone packaging is active as of November 4, not finished.
- [x] Outstanding manual acceptance group: user reported on November 4, "i have ran the manual tests - it all works". Recorded as operator acceptance in [TC22 acceptance](../TC22-ACCEPTANCE.md); no duplicate manual checks requested. Separate per-case artifacts were not supplied.
- [ ] Resolve or explicitly scope the unsupported GS1/ECI/binary scanner profiles before claiming universal barcode reproduction.
- [ ] Production Android signing/distribution last, as requested. Development publication and demo availability are not production 1.0 acceptance.

Historical checkpoint (November 3):

- [x] Server `45f9cbd` is signed and pushed (Gitea PR 7). Android `f72b90d` is signed and pushed to the existing AGit PR 10 head and Gitea CI 3480 succeeded on it (pull requests #8 and #10 still open; not merged, not released). Updated normal APK installed without clearing data; fresh disposable TC22 Echo run passed 109/109 semantic requests (simulated inputs/printing).
- [x] Final RC.21 gate PASS: 377 unique tests (Generation 179, Printing bridge 16, scanner 53, demo 8, mobile Record Files 18, Echo 68, Echo demo 35), zero failures/errors/skips, Checkstyle PASS, plus 15 real admin-browser E2E on public RC.21. The earlier MySQL timeouts did not recur (focused 31, full 179). Evidence: `C:/Users/james/.cache/qqq-rc21-copies-gate-076d059d8d0641d9a85874ec64008def/verified-summary.json`.
- [x] Verified dependency update: five RC.21 POM pins and the record-files POM test fix (BOM-managed Jupiter engine/params, all 6.0.2).
- [ ] This is public core RC.21 plus qualified owner inputs, not a fully public or released cohort: the 16 Printing-bridge tests still resolve three private `pu507` QQQ libraries through the owner Printing POM, and the other owner qbits remain qualified private inputs. Public reproducible owner inputs, primary source CI, merge and release remain open. No normal-runtime core upgrade was done.
- [x] Generation copies: signed `aac68907e67906aeef4e0cd687d28bc98b738f8c` on `feature/echo-generation-copies` (base PR 343 head `8f473fa`) in `Website-Backend/third-party-src/qbit-document-generation`; 13 paths, 21/21 manifest match; real PostgreSQL 17 migration passes. [ ] Owner integration and public artifact publication not yet acknowledged.
- [x] Fresh real Authentik desktop acceptance passes; native harness integrated (8 guards pass). [ ] Native TC22 run needs the temporary CA and PIN (pending); no trust bypass.
- [ ] Manual physical checks: trigger, AIM, fidelity, label count, photo, GPS, projection. [ ] Signing and distribution last, by user decision.

Previous checkpoint (November 2; historical, superseded by the current checkpoint above):

- [x] Optional Copies per scan defaults to 1, accepts 1–100 and remains server-pinned for every scan/retry. Live on the original local server57832; normal TC22 setup shows 1.
- [x] Verify 377 unique server/owner tests across the module runs, plus real TC22 ten-stage/116-request semantic acceptance with simulated scans/printing.
- [x] Adopt mobile-scanners1.5 and actual mobile-record-files1.0/Echo photo composition locally, preserving the original database and printer settings. Photo authorization tests pass.
- [ ] Confirm physical label count/fidelity and real camera/GPS/projection/provider behavior; operator currently remote. Temporary Authentik CA installation is unconfirmed.
- [ ] Obtain canonical owner adoption/publication of the verified Generation copies handoff: ten hash-checked files for the Kof22 release repository's `third-party-src/qbit-document-generation` (base verified identical), then a published Generation coherent with Echo's `generation.version`; complete coherent artifacts/CI. Signing/distribution stays last.

Current demo path (November2; supersedes historical runtime/pairing notes below):

- [x] Implement and verify additive mobile1.5 raw-byte/AIM transport, exact retry binding, optional context and preserving H2 migration:135 scanner/demo/Echo tests pass. Android counterpart is implemented; native capture and installed-runtime adoption remain open.
- [ ] Qualify actual TC22 raw-byte delivery, AIM extraction and decoder settings; finish renderer scanner-mode/extended-symbology integration. Transport support alone does not establish same-symbol reproduction.
- [x] Fix and adopt the Generation/renderer byte-limit mismatch in both demo launchers; three failures with old wiring, 74 passing library/demo tests with the fix. One clearly synthetic native HTTP event now reaches the actual Windows printer spooler through Echo, with process cancellation verified after failure and acceptance. Paper output, TC22 trigger capture and rescan remain separate open checks.
- [x] Render the actual published 4x1 document through the real renderer and independently decode its Code128 and leading-zero QR pixels; both preserve exact value/symbology and fit the configured stock. See [evidence and test images](../../examples/echo/demo/docs/ECHO-RENDERER-ACCEPTANCE.md). Physical printing remains unverified.
- [x] Correct the five inert test-token secret-scan findings using generated fixtures and exact historical fingerprints; local historical/staged scans and all 27 demo tests pass. Current primary CI remains an open gate.
- [x] Run all real scanner/Echo, files, document and printing qbits in the single local server57832; original accounts work; full demo27 and live forwarded-host checks pass.
- [x] Connect the existing installed print client to57832 and approve its available ZPL printer through the native QQQ process; no job/default changes.
- [x] Verify the Android same-origin header (Android `fb3cc5d`, 174 JVM tests) and TC22 sign-in, heartbeat, printer/media/document selection and Cancel/Leave against the strict local host, preserving data.
- [x] Resolve the earlier 4x6 media report versus loaded 4x1 stock with the Printing owner (101600 x 25400 microns, 300 dpi) and publish the real Echo 4x1 barcode document with its matching immutable revision 1.
- [ ] Complete one physical Code128 (QQQ-TEST-123456) scan, print and rescan on the TC22, then leading-zero QR. One synthetic HTTP test job reached `SPOOLED`; physical paper output and handheld acceptance are not yet proven.
- [ ] Finish the existing production-readiness matrix and publication gates; local demo acceptance alone does not complete 1.0. Public coherent artifacts, primary Gitea CI and final signing remain open, with signing last by user decision.

- [x] Implement explicit required INTEGER Document choice with printer/media dependency, server-pinned revision and recoverable errors:45 library/24 composed tests and real TC22 seven-stage/112-request semantic acceptance pass (simulated printing).
- [x] Roll out the document-selection feature and connect the installed print client to the canonical local server. Real4x1 publication and physical scan/print/rescan remain tracked above.

- [x] Verify the per-extension device-header scope fix (50 base,7 base-demo and composed HTTP regressions), then complete actual native printer enrollment/binding on the local server.

- [x] Publish reviewed Echo retry/receipt fixes as signed Gitea commit912040d;38 library/22 demo tests pass. (Historical: the then-pending Windows approval of printer pairing is superseded; the print client is now connected to the single local server.) Physical output and public CI/artifacts are still open.

- [x] Verify real composed Echo native HTTP pipeline:38example/22demo, retained files, accepted jobs, retry identity and Leave closure; simulated printer/controlled renderer only.
- [x] Complete native printer connection on the current loaded stock (done on the single local server). Physical scan/print/rescan is tracked above; published dependencies and primary CI remain open.

- [x] Verify effective security-key set comparison and early Collaboration authentication initialization: scanner50/native Record Files18, Checkstyle0 on private coherent cohort.
- [x] Compose the live Echo renderer, Printing and document publication in the single local server. Physical TC22 print/rescan is tracked above; startup smoke and simulated printing do not close that gate.

- [ ] Deliver Echo example: choose an authorized printer, media and Document (all three required) once, then one scan/render/accepted-print job at a time until leave; reproduce exact value and symbology with worker-owned integer-module fitting and quiet zones. Source, composition, Document choice and native setup are done. Still open: the physical print and rescan above, and full-fidelity work beyond the six admitted printable-ASCII types (EAN/UPC, GS1, AIM identifier and raw payloads), tracked in `examples/echo/README.md`.
- [ ] Coordinate qualified renderer fit, authorized printer/media discovery, least-privilege submit and retained-print adapter contracts with their owners via shared communications.
- [x] Verify scanContext 1.3 with root44/demo7/optional18 and Checkstyle: following-step QR/Code 128 branching, legacy omission isolation, trusted attribution and exact retry binding; physical scanner acceptance remains separate.

- [x] Apply the full scoped standards review corrections and verify server39/demo6 with Checkstyle; see [review](../CODING-STANDARDS-REVIEW-2026-11-01.md).
- [ ] Finish final hosted checks and remaining physical/provider acceptance; source compliance is not production release approval.

- [x] Upgrade the normal local demo to qualified API1.9/mobile1.1 with fresh closed backup, original-data invariants, recovery qualification and live verification of both existing accounts and preserved devices/scans.
- [x] Implement/review additive extension1.2 whole-device source with explicit Android consent, mode-bound leases and automated controls, preserving1.1 app-content compatibility.
- [ ] Complete actual Android projection/notification/Home/Stop/lock/rotation/expiry/remote-logout acceptance; normal server remains mobile1.1.

- [x] Qualify the demo BOM import and managed JUnit engine with fresh37 server/five demo tests, exact runtime module/Guava/H2 provenance and all15 single-record/bulk browser cases; see [acceptance evidence](../API19-CONSUMER-ACCEPTANCE.md). The operator runtime remains separate.
- [x] Preserve legacy timestamp instants in the canonical supplied H2 schema with an explicit UTC migration connection; verify non-UTC file-backed upgrade, unchanged passwords/approval/scans and repeated initialization. The operator upgrade is preserved with UTC; a no-install reactor passes 37 base tests and five demo tests using the freshly built schema.
- [x] Implement unique short names, editable names/descriptions, additive H2 migration and protected profile editing; qualify the preserving upgrade of the operator database before deployment.
- [x] Implement separate approval/presence states, online-only fleet query and foreground location with accuracy/age and permission-denied behavior; actual GPS-fix acceptance remains in the release gates.
- [x] Implement the optional Record Files contract/native capture/association path and qualify the final coherent core32/scanner1a3 consumer cohort: 39 scanner, six demo HTTP, 18 Record Files and 15 browser cases. See [qualification scope](../../integrations/record-files/docs/CORE-32DF-CONSUMER-QUALIFICATION.md); this is not a files-owner full-suite or default-adoption claim.
- [ ] Qualify coherent immutable/default RF dependencies and actual TC22 camera/gallery permissions and durable authorized association; no normal-runtime adoption is implied.

- [x] Device/session history, live activity and demand-based actual app-screen viewing through native QQQ administration and semantic extension 1.1.
- [x] Single-record and bulk End Session process with server-enforced invalidation, audit and browser/native E2E coverage.
- [x] Complete and seal the full server deep static security review of `0926550`; no reportable findings, with external implementation/deployment limitations retained.
- [ ] Complete final revision/scope reconciliation of historical security reports and remaining external deployment/provider gates. Independent architecture A1–A11 source closure and the filesystem regression correction are recorded; do not relabel partial/historical reports as a new exhaustive scan.
- [ ] Complete production 1.0 gates in [PRODUCTION-READINESS.md](../PRODUCTION-READINESS.md), including physical acceptance and deployment/release requirements.

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

- [ ] Complete the optional semantic API1.10 provider-logout capability and client AppAuth end-session handling; keep local cleanup reliable on provider failure, qualify real A-to-B handoff, and apply the user's pending sign-out policy. Existing prompt-only retained-SSO handoff is not accepted.

- [ ] Validate native OIDC ID-token claims and original request nonce before creating the QQQ session. Review confirmed request continuity and S256 PKCE exist, but expected nonce is not currently carried to the server exchange. Add explicit semantic capability/wire compatibility and rejection tests; retain real-provider nonce acceptance as pending.

- [x] Retain unchanged verified capture session identity with explicit current authority/reference checks;49root+7demo tests andCheckstylepass.
- [ ] Qualify exact ownerRF18 upload/finalize using the frozen identity patch before final coherent host adoption.
