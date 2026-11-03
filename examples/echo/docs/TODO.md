# Echo remaining work

## Current checkpoint — November 2, copies live

- [x] Printer, paper/label size and Document selection, real installed Printing client connection and 4x1 document publication are complete on the normal local server57832.
- [x] Copies per scan is optional, defaults to 1 and accepts 1–100; pinned for the run and carried as one job's copy quantity. Null/blank/default, selected 3, mutation refusal, retry identity and additive migration tests pass.
- [x] Root gates pass: 68 Echo and 35 composed demo tests; 377 unique tests including the Generation/Printing/scanner/files dependencies across verified module runs.
- [x] Real TC22 semantic acceptance passes ten stages/116 requests using simulated scans and printing. The normal connected TC22 is left on setup showing Copies per scan = 1; normal server advertises mobile1.5.
- [ ] Physical quantity, trigger/print/rescan and AIM/EAN/UPC fidelity checks remain unqualified. The operator is remote.
- [ ] Canonical Generation copies adoption/publication and coherent public CI remain pending; qualified local source/handoff lives in C:/Users/james/.cache/qqq-generation-copies-20261102 and qqq-generation-copies-handoff.md.

The older ledger below is historical; its unrun-test, setup and mobile1.4 notes are superseded by this checkpoint.

- [ ] Root gate for the required Document choice (printer, paper or label size, then Document at run start; see DOCUMENT-CHOICE-HANDOFF.md): module and demo verify, then on-device check that the generic form shows the dependent Document field.

Verified by root (2026-11-02): module `verify` 33 tests, Checkstyle 0, against the complete private cohort `0.1.0-echo.gen64c9pu507`. Runnable demo `verify` 22 tests, Checkstyle 0, including the composed acceptance below, with the real new Echo read checker and the renderer owner's TL1 factory (52 owner tests pass, no copied transport). A local demo run was observed live on an ephemeral loopback port (scanner and admin native login and metadata 200). No printer or template on it, and no physical printing.

## Done

- [x] Process, state, mobile-approval and H2 tests against the protected-process-identity core API; concurrency, crash recovery, cancellation and run-substitution regressions.
- [x] Test-only isolated semantic HTTP fixture (simulated printing) and split read-only `echoScan` permission with actor-lock, revoked, anonymous and write negatives; the read rule also accepts Collaboration's and Record Files' plain capability probe for sessions with `echo.use`.
- [x] Owner adapters from actual public APIs: authorized printer/media from Printing, exact pinned template/revision/stock/media generation, read-only accepted-print lookup, `QUEUED` only for a durable available receipt.
- [x] Media choices filtered by the selected printer through the semantic dependency value.
- [x] Host composition `EchoHost.install` using `PrintingDispatch.forOwnerMediaEnforcement(source)`.
- [x] Publisher scene reviewed against the worker source and owner fixture shape; EAN13/EAN8/UPCA/UPCE0 refused before admission; six types admitted.
- [x] Runnable demo module and the owner's trusted-local renderer factory integration.
- [x] **Clean composed acceptance** (demo `EchoDemoAcceptanceTest`): authenticated approved scanner, real `echoPrint`, real Generation, Record Files retention and Printing job acceptance; two scans give two retained originals and two accepted jobs on one printer, a retry gives the same job, and leaving closes the run. Controlled synthetic renderer and simulated Printing queue; not physical.

## Open

- [ ] Actual native Printing client (Windows agent) connection to the composed server and the current stock/media handoff.
- [ ] Publish the ZPL template through the real renderer, then preview and independently decode Code 128 and a leading-zero QR (`001234567890`) at 4 x 6 in / 300 dpi.
- [ ] Real-server native TC22 run: setup once, scan twice, rotate/recreate, leave; one stable print identity after transport loss.
- [ ] Physical print and TC22 rescan with exact value, symbology, quiet zones and stock dimensions; native Printing acceptance. A queue acknowledgment is not paper.
- [x] Source: AIM persistence (`echo_scan.aimIdentifier`), EAN13/EAN8/UPCA/UPCE0 validation, and a scanner-fidelity document through the renderer's scanner mode; candidate Android AIM output profile (`AimOutputProfile`, driver config/readback/restore). Not yet run (root builds) and not physically qualified.
- [x] Source: Copies per scan (setup field, server-pinned `echo_run.copies`, scan-stage forgery refusal, widget text). Library tests added, not run.
- [x] Source: copies reach Generation's request and the accepted-lookup binding (needs the Generation copies cohort from `C:/Users/james/.cache/qqq-generation-copies-handoff.md`, unbuilt). Optional field with 1..100 core limits and real HTTP tests for default 1, chosen 3 and mutation rejection; not run.
- [ ] Root gate with the Generation copies cohort, the `20261102-print-copies-h2.sql` migration in the demo's `GENERATION_MIGRATIONS`, a Printing owner that honors `options.copies`, and a physical count check on the printer.
- [ ] TC22 pulls proving what `getData()` and `getRawData()` contain under `codeIdType=AIM`, then a physical EAN/UPC scan, print and rescan on the scanner-fidelity document. Normal server 1.5 adoption (the live server is 1.4 and drops AIM and raw bytes).
- [ ] GS1, ECI, structured, binary and non-ASCII payloads stay refused: the existing public renderer has no scanner-mode profile for them (see `C:/Users/james/.cache/qqq-finish-barcode-handoff.md`).
- [ ] Original-actor source reauthorization policy confirmed for delayed byte release (Printing retained source authority).
- [ ] Deployment: Windows agent exposing `printer-media-v1`, ZPL printer, tenant, operator permissions without `printing_admin` (the scanner never receives it).
- [ ] Public immutable SDK publication, Gitea CI and distribution, then signing, last. The private cohort is unpublished; PR 7 is on Gitea and earlier GitHub references are historical.
- [ ] Release and production readiness: not done and not claimed.
