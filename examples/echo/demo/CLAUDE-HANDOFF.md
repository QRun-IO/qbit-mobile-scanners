# Echo runnable demo checkpoint

Date: 2026-11-02. Scope: `examples/echo/demo/**`. Source checkpoint, not a release.

## Verified status

- Demo module `verify`: **22 tests pass, Checkstyle 0** (acceptance 1, settings 5, startup 1, object files 6, trusted-local renderer settings 9), against the real new Echo read checker and the renderer owner's TL1 factory. Parent `examples/echo` `verify`: 38 tests pass.
- The renderer owner's own tests for the TL1 trusted-local exchange pass (52 owner tests). The demo uses the owner's public factory and copies no transport.
- **Complete composed acceptance (`EchoDemoAcceptanceTest`) passes** over a fresh private file database: an authenticated, approved scanner runs the real `echoPrint` process through real Generation, Record Files retention and Printing job acceptance. Two scans (Code 128 `001234567890`, QR `QR-VALUE-0099`) give exactly two retained originals and two accepted Printing jobs on the one selected printer and media; a retry of the same event returns the same job and renders nothing again; leaving closes the run (`CLOSED`).
- **Controlled inputs, not physical output:** the renderer returns fixed synthetic ZPL built from the exact context Generation passes it, and the "printer" is a Printing device and queue enrolled through the owner's public `PrintingService` API. No Docker, agent, printer or device is touched.
- A private fresh-JVM run of the demo was observed live on a local ephemeral loopback port (take the URL from the startup log line `ECHO_DEMO_READY port=<port>`, `http://127.0.0.1:<port>`): scanner and admin native login and metadata returned 200. No printer or template exists on it, and no physical print has been attempted.

## What the demo does

`EchoDemo` extends `MobileScannersDemo` (database, `scanner`/`admin` accounts, native server) and composes Collaboration, Record Files, Printing, Document Generation and `EchoHost.install` from their public classes on one private H2 file (`WRITE_DELAY=0`). Composition runs under a scoped system context that is restored afterwards, then `QInstanceEnricher` enriches the instance, because the server only validates and the enricher registers Collaboration's record-feature permissions. `LocalFilesystemAttachmentStorage` implements the attachment owner's storage SPI over `PrivateObjectFiles` (hashed keys, temp-file-then-link, no overwrite) and adds no policy. `TrustedLocalEchoDemo` is the explicit developer launcher; the production `EchoDemo` default fails closed. See `README.md` for build, run and environment.

Authentication is the existing demo password/session module plus exact grants. `scanner`: `echo.use`, `printing_operator`, `document-generation.view|generate|print`, `record-files.view|manage`, `echoScan.attach` and the `printing_tenant` row key (replaced, never appended, on each validation); then `CollaborationSecurity.applyToSession`. `admin` additionally: `printing_admin`, `document-generation.configure|publish`. No Record Files release/configure/audit for anyone.

## Test isolation

The RDBMS connection providers are cached per backend name for the whole JVM and every composed host here names its backend `mobileDb`. The startup and acceptance tests reset them before and after each test so neither depends on test order.

## Remaining (not done, not claimed)

- Actual native Printing client (Windows agent) connection to the composed server and the current stock/media handoff.
- A physical scan, print and rescan of Code 128 and QR labels, with independent decode.
- Full AIM, EAN/UPC, GS1 and raw-payload fidelity. Today the six admitted types are printable ASCII only; EAN13/EAN8/UPCA/UPCE0, UNKNOWN, manual, control/binary and GS1 payloads fail explicitly as unsupported before admission.
- Public immutable SDK publication, Gitea CI and distribution, and signing, last. The cohort is private and unpublished. Source repository PR 7 is on Gitea; earlier GitHub references are historical.
- Not claimed: production readiness, a published Maven artifact, or any physical output.
