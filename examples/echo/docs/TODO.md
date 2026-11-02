# Echo remaining work

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
- [ ] After the first Code 128/QR pilot: verified AIM evidence, EAN13/EAN8/UPCA/UPCE0, GS1, raw and binary payloads. Until then the six admitted types are printable ASCII only and the rest fail explicitly as unsupported.
- [ ] Original-actor source reauthorization policy confirmed for delayed byte release (Printing retained source authority).
- [ ] Deployment: Windows agent exposing `printer-media-v1`, ZPL printer, tenant, operator permissions without `printing_admin` (the scanner never receives it).
- [ ] Public immutable SDK publication, Gitea CI and distribution, then signing, last. The private cohort is unpublished; PR 7 is on Gitea and earlier GitHub references are historical.
- [ ] Release and production readiness: not done and not claimed.
