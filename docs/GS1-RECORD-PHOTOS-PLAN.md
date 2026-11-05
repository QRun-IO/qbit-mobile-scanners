# GS1 duplication and mobile records

Active work requested November 4, 2026. Implementation and isolated acceptance are underway; deployment and physical qualification remain separate.

## First delivery: GS1-128 duplication

- Preserve the original scanned payload, AIM identifier and available raw evidence.
- Convert supported GS1 element strings to the renderer's canonical GS1 input without guessing missing separators.
- Print a GS1-128 symbol and its human-readable interpretation underneath, preserving leading zeroes and application identifiers.
- Verify independent decoding of the rendered output, retry behavior, permissions and refusal of ambiguous inputs.
- Qualify the actual label/media/printer and handheld path before claiming tomorrow's workflow ready.

## Native records and photos

- Discover permitted tables through existing semantic metadata.
- Browse records and open record details through existing versioned query/read operations.
- Offer camera/gallery attachment only when Record Files authorizes that exact record.
- Reuse the native upload implementation and committed receipt checks; cover denied records, cancellation and retries with isolated native/server E2E tests.

## Remaining readiness work

1. Establish the intended HTTPS host and standalone connectivity.
2. Qualify the existing signed APK and an enrollment-preserving update path.
3. Verify an actual MC9400 trigger and persisted record (operator action required).
4. Adopt published, coherent owner dependencies when available; do not invent versions.
5. Qualify native provider login, logout and account switching against the configured provider.
6. Document and exercise database migration, backup/recovery and retention for the selected deployment.
7. Run final server/browser/native gates on the resulting revision and reconcile release documentation.
8. Deliver the GS1 profile above before considering broader barcode profiles.

## Constraints

Preserve the user's database, accounts, enrollment and printer setup. Use isolated test databases and application IDs. No offline queue or replacement screen framework. Source checks cannot establish physical scanning/printing. Do not publish artifacts or promote a stable release as part of a test run.

## Verified November 4

- Scanner/base-demo/Echo reactor: 135 tests passed, including GS1 raw evidence, retry identity, derived HRI, metadata discovery and read-only permission regressions. Checkstyle passed.
- Final composed cohort: 188 tests passed (scanner 53, base demo 8, Record Files 18, Echo 74, Echo demo 35), with Checkstyle, using the previously qualified owner dependency versions in an isolated copy. No POM publication coordinates were changed.
- Android core/mobileapp/host unit tests, lint and debug/E2E APK builds passed; eight test-runner guard tests passed.
- Disposable MC9400 native Echo acceptance passed with synthetic Code128, QR and GS1-128 events, one job per event, rotation/recreation, run cancellation and saved-record browsing. All requests matched the published semantic API. Printing was simulated; this does not qualify the physical trigger.
- Disposable MC9400 record-details acceptance saved synthetic camera and gallery photos to the selected record through the real Record Files APIs. Pending-photo rotation, navigation blocking and remote-logout cleanup passed. The existing process-based photo flow also passed the same camera/gallery and lifecycle regression.
- Actual renderer output independently decoded for synthetic GTIN/date/serial and separator-bearing batch/serial examples on 4-by-1-inch, 300-dpi media; HRI matched and did not overlap the symbol.

## Deployment and migration gate

Before updating the maintained demo, stop its owning process cleanly and back up the closed database, attachment files and configuration together. Preserve accounts, device enrollment, printer settings and template revisions. Apply the additive nullable `rendererValue` and `humanReadable` columns in `echo-h2.sql`; existing scans keep their original values and are not backfilled or reprinted. Verify the backup can be restored into an isolated directory and start only that restored copy for the recovery check.

Publish a separately named GS1 document using `EchoTemplatePublisher.publishGs1` for the printer's reported media profile. Existing plain documents remain valid for their original profiles. Use the matching GS1 document for the sample label, print one copy, then scan that output and compare exact GS1 data and visible HRI. Do not declare physical acceptance from a synthetic broadcast or renderer-only result. Printer/media confirmation, deployment authorization, HTTPS configuration and the remaining readiness gates above are outstanding.