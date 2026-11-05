# Production readiness

**Status (2026-11-04): not released as production 1.0.** The local demo works and is accepted by the operator; the gates below decide whether the project can be called production-ready. This is a checklist, not a score. The detailed evidence ledger, with per-gate tables and the architecture review findings A1–A11, is archived in [history](history/PRODUCTION-READINESS-through-2026-11-04.md); present status is in [session state](SESSION-STATE.md).

## Delivered and verified

| Area | Evidence (see session state for revisions and counts) |
| --- | --- |
| Server source and development publication | `develop` at the merge of PR 13; PR 12 snapshot `0.1.0-20261103.233448-3645` read back anonymously and matches reviewed source. |
| Android host libraries | `main` at `0ae2101`; SDK run 3634 passed and artifacts verified. Development libraries only. |
| Current cohort tests | 198 tests pass (scanner 53, base demo 8, mobile Record Files 18, Printing bridge 16, Echo 68, Echo demo 35); Checkstyle passes; no private QQQ backend in the cohort. |
| Local runtime | One server on public QQQ RC.21 and Printing 1.0.0; API 1.10, mobile 1.5, printing 2.3, mobile-record-files 1.0; existing data preserved. |
| Device and admin processes | Enrollment, approval and revocation work from a single record and from bulk selection (checked rows, select-all, first N), with confirmation and visible results, covered by HTTP and browser E2E tests. Earlier browser run: 15 cases. |
| Hardware | TC22 manual tests: operator-reported pass on November 4 (no per-case artifacts). MC9400 configured and approved; trigger check pending. |

## Open gates

Local GS1-128 and native record/photo changes passed 188 server tests, Android unit/lint/build gates, and disposable native Echo/record-photo/process-photo acceptance. They remain uncommitted and undeployed. Synthetic GS1 renderer/decoder evidence does not close physical print/rescan acceptance or the signed-build deployment gates below.

| Gate | What is still required |
| --- | --- |
| Public dependency cohort | Public, coherent Generation/bridges/Record Files/Scripting artifacts from their owner; adoption of the Core/Collaboration/Next UI foundations when released. Today these are qualified owner inputs. |
| Release | Normal Gitea CI on the final revisions, a reviewed release manifest, and release/RC publication, which stays disabled until then. Redesign of the shared CI pipeline is outside this scope. |
| Signing and distribution | Done: QRun key with backup, signed 1.0 APK, direct/MDM channel. Open: qualification of the signed build on a device against an HTTPS server, then distribution and update path. A signed preview is published; it is not a qualified production rollout. No signing CI. Deferred by the user to the very end. |
| Deployment | HTTPS and the host's identity provider, supported database migration beyond the supplied H2 schema, retention and recovery documentation. The HTTP loopback demo is not a production deployment. |
| Provider authentication | Native OAuth2 requires UI API 1.10 native-session and nonce-validation support on the server (verified in the Android source). Still open: acceptance against a configured provider (such as Authentik) on a native handheld, including logout and account switching. No such handheld test is claimed. |
| Further device qualification | MC9400 physical trigger check and MDM-applied managed configuration remain unverified. The TC22 camera, GPS and screen-viewing checks are covered by the accepted operator group report; no separate per-case evidence is claimed or required again for that demo. |
| Barcode fidelity | Echo's supported profiles are documented ([limits](../examples/echo/README.md#fidelity-and-limits)); The local branch supports bounded GS1-128; physical print/rescan remains open. Other GS1, ECI, structured and binary profiles remain refused. No universal barcode-reproduction claim. |
| Final reviews | Reconcile the dated security, architecture and standards reports to the final revision; they are scoped historical reviews, not a fresh exhaustive scan. |

## Rules that stay in force

- Do not describe a passing build, an emulator run or a demo as production acceptance.
- Never reset or overwrite the user's demo or scanner data to run a test; use isolated accounts, databases and application IDs.
- Keep device credentials, passwords, device identifiers and workstation paths out of the repository.
