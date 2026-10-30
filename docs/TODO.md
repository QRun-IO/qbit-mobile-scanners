# QQQ Mobile Scanners TODO

- [x] Native qbit device/scan tables and permission boundaries.
- [x] Authenticated enrollment, approval and revocation.
- [x] Native Scan Capture process and idempotent scan storage.
- [x] Android login, device gate, app picker and DataWedge scan handling.
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
- [ ] Complete the native approval/revocation/cancellation form scenario after the completion-state fix.
- [ ] Publish and verify hosted checks for the semantic API migration; previous successful hosted heads do not verify this change.
- [ ] TC22 physical scanner acceptance.
- [x] Identify the physical TC22, install the debug app, sign in and enroll through USB forwarding.
- [x] Approve the TC22 through its browser record Actions menu and open its native app catalog and Scan Capture process; preserve login across APK update.
- [x] TC22 install and DataWedge acceptance guide written; physical execution remains pending.

Future scope (not version one): offline queue, camera fallback, kiosk/MDM provisioning, broader native screen coverage, fleet rollout.
