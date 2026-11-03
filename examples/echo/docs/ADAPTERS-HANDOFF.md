# Echo owner adapters and host composition handoff (Claude adapters role)

Updated: 2026-11-01. Scope: `examples/echo` library module. Primary forge: Gitea. This handoff makes no claim that Echo has printed, that anything is published to a public Maven repository, or that it is production-ready.

## Verified state (reported by root; I ran nothing)

- `mvnw verify` for this module: **38 tests pass**, **Checkstyle 0** (2026-11-02), against a complete private owner cohort: Generation and bridges `0.1.0-echo.gen64c9pu507`, Printing protected main `64c9ebf`, core `4.1.0-pu.507b0d21e09d7eebb76782d14660483c45c77b11`, 15 artifacts. The POM defaults (`qqq.version` 4.1.0-RC.20, `generation.version` 0.1.0-SNAPSHOT, `printing.version` 0.4.0-SNAPSHOT) are not that cohort.
- Scanner source `707e1ab` (Gitea PR 7): scanner 50, demo 7 and Record Files 18 tests pass. Android `c55d300` is retained inside migration merge `a2f0080` (identical tree).
- Runnable demo (`demo/`): **22 tests pass, Checkstyle 0**, with the new Echo read checker (it accepts Collaboration's and Record Files' plain capability probe for sessions with `echo.use`) and the renderer owner's TL1 trusted-local factory (52 owner tests pass; the demo copies no transport). Its clean composed acceptance passes: authenticated approved scanner, real `echoPrint`, real Generation, Record Files retention and Printing job acceptance; two scans give two retained originals and two accepted jobs on one printer, a retry gives the same job, and leaving closes the run. It uses a controlled synthetic renderer and a simulated Printing queue. A local run was observed live on an ephemeral loopback port (scanner and admin native login and metadata 200) with no printer or template. The production `EchoDemo` default still fails closed on Windows Docker Desktop (`runc`, not `runsc`).
- TC22 native five-stage flow and 86 semantic API calls pass **against simulated printing only**.
- No real-renderer template publication, independent decode or physical print has happened. Remaining: the actual native Printing client connection and stock handoff, physical scan/print/rescan, full AIM/EAN/UPC/GS1/raw fidelity (the six types are printable ASCII only and the rest fail explicitly as unsupported), then public immutable SDK, Gitea CI/distribution and signing, last.

## What exists (all under `examples/echo`)

- `EchoPrintingDestinations`: `authorize` requires the scanner identity's actor to equal Printing's current principal (`PrintingOperatorAuthorization`), checks the printer row, device and ZPL format, and pins the exact reported media profile id and revision (`PrintingMediaProfile`). `plan` reauthorizes, picks the template through `EchoTemplates`, reads the published template and revision with enforced native permissions, and requires its stock to equal the media. `media(printerId)` lists only that printer's authorized sizes.
- `EchoMediaChoices`: semantic custom possible-value provider. It reads the posted `printerId` dependency from `SearchPossibleValueSourceInput.getOtherValues()` and returns that printer's sizes only; no printer means no choices; a bare `getPossibleValue` refuses. The public id is the owner's profile id and the private revision pin stays in `authorize`.
- `EchoQBitProducer`: setup's printer field uses Printing's own table possible values (`printing_printer`) with `enabled` and `available` filters.
- `EchoTemplates`: exact key of renderer symbology, media id and media revision; `forMedia` maps the admitted symbologies to one template.
- `EchoTemplatePublisher.publish(definitions, media)`: one-time operator action creating type, stock equal to the reported media, ZPL-only binding for `echoScan`, and the template. Scene `qqq-zpl-scene/1`: `symbologyPath=/record/rendererSymbology`, `valuePath=/record/barcode`, allowlist, `quiet=10`, `fit` box inside the stock, no scanner-attestation paths.
- `EchoDocumentGenerator`: Generation's public `generate` and read-only `lookupAcceptedPrint` with exact `Expected` tuple and `MediaProfile` under key `echo:<scanId>`. `QUEUED` only for a durable, available retained receipt with a job id and the same media profile. Lookup must match source, destination and media; absence refuses; `reconcile` is never used.
- `EchoHost.install(instance, Deployment)`: `RecordFilesPrintSource`, `RecordFilesOutputIntake`, **`PrintingDispatch.forOwnerMediaEnforcement(source)`** (the owner's explicit adoption factory in the printing bridge; the plain constructor still denies selected media), `DocumentGenerationQBitConfig` with `echoScan` as source table and `GenerationAudit`, `PrintingQBitProducer`, then `EchoOwnerAdapters.config` and `EchoQBitProducer`, then `DocumentGenerationQBitProducer`. That order is required: Generation validates its source tables, and an earlier order failed the demo with "Unknown generation process source table". `Deployment` carries `printingBackend`, `generationBackend`, `echoBackend`, `echoState`, `printing`, `renderer` and `templates`; null `printing`, `renderer` or `templates` are refused.
- `EchoService`: admits scanner identifiers `CODE128`, `QRCODE`, `CODE39`, `DATAMATRIX`, `PDF417` and `AZTEC` (and their `LABEL-TYPE-` aliases). `EchoTemplates.SYMBOLOGIES` is exactly `code128, qr, code39, datamatrix, pdf417, aztec`.

## Fidelity: what is and is not claimed

- Six types are admitted; **EAN13, EAN8, UPCA and UPCE0 are refused before `EchoStore.claim` or generation** (regression in `EchoTest.unsupportedAndControlPayloadsFailWithoutPrinting`: generator never called, zero `echoScan` rows). The worker converts their full-length scans only in scanner mode, which needs the AIM identifier Echo does not record.
- Not supported and not claimed: EAN/UPC, GS1, raw or binary payloads, control characters, UNKNOWN or manual input. Completing the original barcode-fidelity scope (AIM evidence, then EAN/UPC) is open work after the first Code 128 and QR pilot; the six admitted types are not claimed complete.
- The published scene was checked by file review against the worker source (`ZplRenderer`, `BarcodeFit`, the owner's `BarcodeFitTest` fixture shape) and the worker's integer dot arithmetic for 4 x 6 in at 300 dpi (x=y=48 dots, fit 93600 x 144400 µm). `EchoOwnerAdaptersTest` asserts that shape and arithmetic. **No test runs the real worker on the Echo template.**

## Owner and deployment boundaries still open

1. **Renderer transport.** An owner-provided explicit cold trusted-local Java transport for Windows Docker Desktop; `catalog-9` image qualification is also listed open in `renderer/BARCODE-FIT.md`. `EchoHost` refuses to start without an `IsolatedRenderer`.
2. **Template publication through the real worker.** `freeze` calls `renderer.validate` on the exact scene, so a publish that returns a template id is the worker's acceptance. Then `DocumentGenerationService.preview(new Request(templateId, new Source("echoScan", rowId), ZPL, key, null), draftFingerprint)` renders a retained original with no print destination, for geometry inspection and independent decode of a Code 128 value and a leading-zero QR value such as `001234567890`.
3. **Printing service and agent.** A `PrintingService` for a tenant, a Windows agent exposing `printer-media-v1`, and a ZPL-capable printer at 203, 300 or 600 dpi are deployment items.
4. **Permissions** (no `printing_admin` on the operator): `echo.use`, an approved scanner, Printing's operator catalog permission and `PrintingOperatorProcesses.SUBMIT`, Generation view/generate/print, read on Generation template and revision tables (for `plan`), and Record Files release and read grants for the original actor.
5. **Original-actor source reauthorization** for delayed byte release belongs to Printing's retained source authority (`RecordFilesPrintSource`), and acceptance is still queue acceptance, not paper.
6. **Format.** The publisher is ZPL-only; the printer must report ZPL. A PDF variant is not written.
7. **Distribution.** The cohort is private and unpublished. Public Maven publication, hosted CI, signing and release are not done.

## Test inventory

24 core and HTTP-fixture tests (`EchoTest` 23, `EchoHttpFixtureTest` 1) and 14 adapter tests (`EchoOwnerAdaptersTest`): exact profile mapping, unknown or malformed media refusal, template keys, exact request pins, no-queue results, undurable or unavailable or missing receipt refusal, read-only lookup, mismatched-media lookup, printer-filtered media choices, bounded printer dependency, absent deployment parts refused, and the published scene geometry. Total 38, verified by the coordinator.

## Next gates (in order)

1. Renderer owner's cold trusted-local transport; start the demo against it.
2. Publish the template through the real worker; preview and independently decode Code 128 and QR with leading zeros.
3. Real-server TC22 run: setup once, scan twice, leave.
4. Physical print and TC22 rescan of exact value, symbology, quiet zones and 4 x 6 in stock, then AIM evidence and EAN/UPC.
