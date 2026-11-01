# Echo — scan a barcode and print a copy

Echo is an optional QQQ example application, using the shared Android scanner host. Choose a currently authorized printer and its supported paper/label profile once, then scan repeatedly. Each scan preserves its string value and actual symbology and uses the existing Document Generation → retained original → native Printing path. One event is admitted per run at a time. A successful acknowledgment means **queued**, not confirmed physical paper.

The source implements ordinary QQQ metadata and processes; no Echo-specific Android screen, business endpoint, barcode encoder or print queue is added. It is not yet a turnkey demo: the printing/document owners are implementing the remaining authorized media, least-privilege submission, fit and immutable-revision/reconciliation seams. There is deliberately no fabricated printer, hardcoded stock, administrative-permission shortcut or in-process renderer fallback.

## Host composition

1. Install/configure the existing Mobile Scanners, Document Generation and retained-source Printing integration under their owner-qualified dependency tuple. Use the same backend/data source for Echo metadata and its state schema. Apply [`echo-h2.sql`](src/main/resources/db/echo-h2.sql) through host migrations; registration performs no DDL.
2. Configure Document Generation to allow `echoScan` as a source table. Publish appropriate immutable barcode templates through its ordinary definition service and isolated worker. Stock measurements are micrometres with explicit DPI. Do not generate or publish a new template on every scan.
3. Supply printing-owner authorized printer/media possible-value sources and an `EchoDestinations` implementation. `authorize` must check the actual current actor/device/tenant/destination; `plan` must reauthorize that exact profile and provide a qualified same-symbology layout that fits at integral module sizes with quiet zones. It must refuse unsupported or too-small media. App-selected document dimensions do not imply changing Windows printer stock.
4. Compose `EchoGenerator` using the document owner's accepted/pinned generation contract. The current generic generation service cannot take the exact pinned revision and selected media tuple, so Echo does not provide an unsafe adapter that silently ignores them. The composition interface carries those pins; its default read-only recovery method refuses until the owner provides exact acceptance lookup. Do not use the existing side-effecting generation `reconcile` call as a read-only lookup, or use plain byte submission to bypass retained provenance.
5. Register `new EchoQBitProducer().produce(instance, new EchoConfig(backendName, dataSource, printerPvs, mediaPvs, destinations, generator))`. Grant the operator `echo.use`, ordinary mobile scanning and the owner-approved generation/print-submit permissions. **Do not grant `printing_admin` to make Echo work.**

The process begins with server-owned run state, shows printer/media setup, then loops only between the semantic SCANNER frontend and the print backend. Its native Cancel step closes the run, including cancellation during setup. Already processing or accepted work may still print; leaving prevents new admission and retains receipts. Protected framework process identity, rather than an echoed run ID alone, binds the selected printer to that process.

## Fidelity and retries

The first explicit mapping covers CODE128, QRCODE, CODE39, EAN13/EAN8, UPCA/UPCE0, Data Matrix, PDF417 and Aztec scanner identifiers and their documented legacy label aliases. Leading zeros are never parsed as numbers. UNKNOWN, manual input, unsupported variants and control/binary/GS1 payloads are refused until their exact metadata/encoder profile is qualified. No symbology is inferred from value or substituted with QR/Code128.

Each canonical mobile scan has one durable `echoScan` source with the same ID and a stable `echo:<scanId>` generation request key across every run. A duplicate accepted event in the same run returns its existing job; moving that event into another run is rejected. A deliberate new trigger event with the same value is a new print intent. A changed value/type/source/timestamp cannot reuse the event. An uncertain/crashed call never automatically renders or prints again: recovery requires read-only exact owner acceptance evidence; absent/unavailable evidence remains visibly unconfirmed and the user may leave. Independent decoding and physical scanner/printer acceptance remain mandatory for any claimed barcode profile.

## Build and acceptance

From the repository root, use Java 21 and the included wrapper:

```text
./mvnw -f examples/echo/pom.xml verify
```

On Windows, run `mvnw.cmd -f examples/echo/pom.xml verify` from the repository root. From `examples/echo`, use `../../mvnw -f pom.xml verify` (Windows: `..\..\mvnw.cmd -f pom.xml verify`). Both forms use the checked-in wrapper; Checkstyle and resource paths resolve from the module directory.

This optional module is outside the root lifecycle until compatible immutable owner artifacts are available. For development, use a dedicated Maven repository and an explicitly qualified coherent QQQ version; never install cross-chat snapshots over the shared cache. The source requires the narrow protected-process-identity API and current `MobileScannerActions.capture(input, appName)` API. Current development-generation dependencies are not a production handoff.

Tests use actual QQQ process state, H2 persistence and the mobile QBit's real approval/current binding, with synthetic destination/layout/queue fixtures. They qualify orchestration, event identity and permission boundaries, not actual render output or physical printing. End-to-end completion still requires the owner's exact printer/media/template revisions, isolated renderer, canonical retained output, actual native Printing acceptance and TC22 rescan of representative labels. Keep all historical protected printer jobs and peer runtimes untouched.

## License packaging

Echo uses Apache License 2.0. Packaging includes the existing repository `LICENSE` and `NOTICE` unchanged under `META-INF/qqq-echo/`, alongside Echo's SQL resources. This namespace preserves dependency notices. No duplicate license source files or modified license text are maintained in the example.
