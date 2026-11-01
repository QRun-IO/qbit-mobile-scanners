# Mobile scanners 1.0 release gates

Status: not release-ready. The user requested a full client/server audit and production-ready 1.0. This is an evidence ledger; a numerical quality score does not replace the gates below.

## Current release ledger — November 1

This section supersedes the older checkpoints below. Qualified source checkpoints are core `ecf0f550a0357cec6387c2b54efca128a405a629`, server `10416938724c9663d66f7848ff2b1a63fe635794` and Android `5ba87f49d2fc5ab7f52566727d0d2d147d9b0c9f`, in their existing draft pull requests. Android test-only follow-up `229fce30a1b859a7c443a3ad68e5d6cd3a7cc6de` is published after compilation and independent review; fresh hosted acceptance remains required. Source publication does not mean immutable Maven artifacts or a production APK have been released.

| Requirement | Current evidence | Remaining proof |
| --- | --- | --- |
| Semantic API and authentication discovery | Source-pinned UI API1.9 and mobile-scanners1.2 consumer:39 server, six demo,15 browser cases;195 hashed JARs,2,441 unique QQQ classes, zero duplicate classes. Core selected closure:4,507 discovered,16 existing skips, zero failures/errors. | Coherent immutable dependency publication; actual Authentik browser/native callback and shared-operator SSO acceptance. Default Maven dependencies still select RC.20; do not infer API1.9 from that default. |
| Devices, sessions, names, approval/presence, admin record/bulk actions | Server104 has eight successful hosted checks. Real browser suite covers record/bulk actions, device profiles, presence and session/screen controls. | Final supported deployment/configuration, real GPS permission/fix/disabled behavior and physical remote-session checks. |
| Native navigation and barcode capture | Reviewed development candidate passes137 JVM tests, package/lint/notice/DEX checks, and ordinary-UID/distinct-signer rejection of eight forged scans with a manual positive control. Normal TC22 updated in place with signer/data backup preserved; actual login and Apps/Scan Capture navigation confirmed. | Actual current EMDK Code128/QR/leading-zero/repeated-trigger/lifecycle/ownership acceptance, retry/error/leave walkthrough. Historical DataWedge scans do not qualify the new EMDK path. |
| Whole-device viewing | Additive1.2 server and consent-based Android source built/reviewed; mode/session-bound lease, memory/resource limits and automated server/browser controls verified. | Actual Android consent, notification, Home/other-app pixels, stop/lock/rotation, lease expiry and remote logout. Normal operator server remains the qualified1.1 runtime until a preserving upgrade is separately performed. |
| Camera/gallery and record association | Existing optional integration has18 real-server cases against frozen0515, plus synthetic native camera/gallery cases. | Matched API1.9-compatible immutable Record Files/attachment-owner/core/RDBMS tuple, fresh integration/provenance proof, actual TC22 lens/gallery permissions and durable record association. Do not mix frozen0515 with newer API1.9 artifacts merely because both tests pass separately. |
| Security and architecture | Full historical client/server reviews and verified client remediations are documented. Exact whole-device client diff received sealed static review and an independent follow-up; server1.2 changed-file review found no introduced issue. | Final revision/scope reconciliation and closure of release blockers; external providers/deployment remain outside those static reports. No blanket production clearance is claimed. |
| Hosted client verification | At Android5ba, CircleCI native/unit, CodeQL, GitHub JVM/lint/APK checks pass. GitHub PR run41297344726 passes35 shared native cases, PixelCopy and seven semantic scenarios. Both5ba native runs pass draft rotation/recreation. | Duplicate push run41297340376 fails later at fresh login after remote session termination. Test now waits for completed logout and an enabled button before clicking once; fresh native execution is required. No product fix is inferred from test synchronization. |
| Release and operations | Release workflow/build configuration independently reviewed with no actionable finding; actual APK packaging excludes EMDK implementation/test ingress and retains notices/storage restrictions. Normal server preserving migration/recovery/login checks pass. | GitHub environment/tag protections, organization signing identity and recovery, actual signed-APK upgrade/physical qualification, distribution decision and supported production deployment. Signing/distribution is owner-deferred until the final step. |

The local emulator remains off because it caused workstation-wide stalls. The handheld's black desktop mirror is not proof of a crash: direct ADB inspection on November1 found the normal app alive/resumed, no crash-buffer entries, and the connected sign-in screen. Restored login and reached Scan Capture without clearing app data or launching disposable instrumentation over the operator.

## Historical checkpoints

Latest correction: hosted Android testing found revoked heartbeats remained successful. Approval now precedes all heartbeat activity observation, including accepted trailing-slash aliases. Independent red reproductions and final 37-root/four-demo green tests are retained; new hosted native verification remains pending. Scanner authentication must follow configured Authentik through explicit native semantic authentication metadata; that implementation and real-provider acceptance are now required release gates.

The optional consumer has subsequently qualified frozen0515 with37 fresh base and18 integration tests, including the matched core logging correction and runtime integrity checks. This remains an isolated private candidate; immutable dependency publication and optional hosted CI are required before production adoption. Separate OAuth2 log-redaction work in the native-auth core branch is not included in0515.

## Scope and checkpoints

Latest verified checkpoint: server `0926550` includes the standalone file-backed presence regression and the passing browser-to-native
viewing regression after guarded-upload commit `84ffd7c`. Base 37, demo HTTP two,
browser 15 and optional guarded integration 18 cases pass. Android's local reports
show 91 JVM tests; TC22 navigation (12 cases), orientation, offline licenses,
synthetic camera/gallery/rotation/logout, and the complete real-screen/session-end
scenario pass. Native photo/session requests match published semantic operations.
The full server deep static security review of `0926550` is sealed with no reportable findings. It does not inspect external dependencies or production configuration and does not replace runtime acceptance. Android's full scan and the later single-hamburger UX correction remain pending.
These are checkpoint results; final Android audits, fresh Android hosted CI,
production provider/immutable dependency qualification and actual lens acceptance
remain open. Signing/distribution is owner-deferred until the final step.

- Server semantic baseline: `7dd02b8874882ca2edcdcd89109b3dee6b4b0c88`, with all hosted checks passing. Documentation checkpoint: `a49724384f569a4b4b66ce2cd4aa69332ee1e0e8`.
- Android semantic baseline: `6574ec4991dbb859b60c3a0be462af96918aee32`; hosted JVM/lint/APK, CodeQL and CircleCI checks passed, but both hosted native E2E jobs failed at compatible login before any credential request reached the server. Login/form reachability fixes are under verification; this baseline is not fully green.
- Active implementation: [device/session history, live activity, actual app screen viewing, session termination, identity/presence/location and Record Files integration](DEVICE-SESSIONS.md).
- Final audit scope: both complete repositories, including shared Android core/mobileapp libraries, scanner host, qbit, demo, build/release configuration, tests and documentation. Exclude local secrets and operator data from published evidence.

## Required gates

| Gate | Status / required evidence |
| --- | --- |
| Feature completion | Session administration, screen viewing and device naming/presence/location have passing server/browser coverage. Native navigation/session and Record Files photo integration are in progress. Actual ingestion routes exist; atomic revocation/commit protection and compatible runtime qualification remain open. |
| Security | Independent multi-pass full scans of the frozen client and server; validate findings, fix release blockers, verify fixes and retain coverage/limitations. |
| Architecture | Review boundaries, coupling/cohesion, session ownership, semantic API evolution, concurrency, resource limits and deployment assumptions. |
| Coding standards | Repository conventions, public API compatibility, lifecycle/error handling, duplication, dead code and maintainability review. |
| Automated tests | Unit, HTTP contract, schema/version compatibility, browser record/bulk processes and native UI-to-real-server tests on the final revision. |
| Physical acceptance | TC22 capture, mirroring, remote logout, failure/retry, restart, data preservation, location permission/age, camera capture and gallery upload to an authorized record. |
| Delivery | Release build, signing/update approach, reproducible artifacts, dependencies/licenses, secrets checks and final hosted CI. |
| Operations | Supported deployment/database migration, HTTPS/authentication, retention/resource limits, diagnostics and recovery documentation. |
| Final review | No unresolved release-blocking findings; evidence identifies exact revisions and remaining supported-use boundaries. |

## Verified baseline evidence

October 31 working-source evidence: 37 root tests, two real HTTP acceptance tests and all 15 browser E2E cases pass. Browser cases include single/selected-bulk/full-query session end, cancellation, live image/clear, device profile edits, location timestamps and online expiry/filtering. The full browser run completed in 59.4 seconds after correcting file-H2 connection lifetime. A subsequent virtual-online projection fix passes the 37-test suite; its combined native acceptance remains pending. Android's intermediate 76 JVM tests and zero-error/zero-warning lint pass; shared native and real-server session tests remain in progress. These are local intermediate results, not a frozen release or fresh hosted qualification.

Server: 23 root tests plus the real HTTP lifecycle scenario and six packaged-frontend browser E2E scenarios. Android: 53 JVM tests, six shared native UI tests, lint with zero errors/six warnings, debug/E2E APK builds, and six semantic native scenarios across focused runs. The final admin scenario passed in 255.917 seconds; 31 of 31 requests matched 13 published operations, including cancellation. These results are baseline evidence and must not be represented as verification of later session/mirroring changes.

Physical TC22 (Android 14, DataWedge 13.0.325, app 0.1.0): installed with existing state preserved, signed in and approved through the actual device-record process. A dedicated DataWedge profile uses barcode input, explicit broadcast delivery and package/signature verification, with keyboard output disabled. Two deliberate Code128 trigger scans produced two records. One QR trigger scan preserved `001234567890`; backend records confirmed symbology, source, user/device/app/process and timestamps. Outage/retry and remote-session controls remain to be tested on the completed feature.

## Review findings

The initial full-source architecture/maintainability review covered Android `6574ec4` and server `7dd02b8`; it does not cover the later session/device features. Findings are being remediated, with final independent review still required:

| Finding | Remediation owner / evidence pending |
| --- | --- |
| A1: scan retry cleared on an incomplete receipt | Android host: require matching acknowledgement ID, `scanSaved=true` and nonblank scan ID. |
| A2: invalid scanner length metadata silently defaulted/truncated | Android host: reject invalid or out-of-range metadata. |
| A3: legacy INPUT_FIELD bypassed typed field semantics and interpreted input as action code | Shared client: typed field handling and explicit action capability. |
| A4: ordinary asynchronous status failures lacked a safe retry | Shared client: retry status GET without replaying submission. |
| A5: cancellation during initialization could leave a late process orphaned | Shared client: retain cancellation intent and cancel a returned process ID. |
| A6: login/forms could place actions outside the viewport or behind the keyboard | Shared client: scrolling/IME handling and constrained-viewport native tests. Hosted failure cause remains unproven until the regression evidence is complete. |
| A7: explicit null displayed a default while submitting null | Shared client: distinguish absent keys from explicit null consistently. |
| A8: business JSON collided with class-based serialization envelopes | Shared client: ordinary JSON values without class loading. |
| A9: multi-record mutations and enrollment could persist partially | Server: transactions for credential/device and session/binding pairs and bulk state changes; provider logout after durable tombstones with explicit cleanup outcome. |
| A10: media ownership/disposal could leak resources | Shared client: sound/image lifecycle ownership. |
| A11: navigation title/state and native back stack could drift | Android host: derive navigation state from the active NavController. |

The completed TC22 session scenario delivered and decoded real application JPEG pixels, cleared background frames, renewed viewing on resume, honored operator Stop viewing, ended the session remotely and accepted a fresh login. Android packaging now verifies 130 notice entries, nine license texts and eight native binary hashes in each host variant. The full server static scan is complete; Android review, external core logging remediation/adoption, production signing/update path, supported database migration and final hosted checks remain required before 1.0.
