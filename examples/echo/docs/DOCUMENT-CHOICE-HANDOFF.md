Root verification:45 Echo library +24 composed demo tests PASS, Checkstyle0. Android99 focused/host tests +8 Node guards/lint0errors0warnings pass and test APK compiles. Updated TC22 document-choice test passed all7 stages and112 published semantic requests against simulated printing; no physical-print claim.

# Echo required Document choice (server and native UI verified)

## Process contract (ordinary QQQ metadata; Android renders it generically)
`echoPrint` frontend step `setup`, form fields in order:
1. `printerId` - Printing's own printer possible values (unchanged).
2. `mediaId` - possible value source `echoMedia`, `dependsOn=[printerId]` (unchanged).
3. `templateId` - label **Document**, required, **INTEGER** (Generation template id), possible value source `echoDocument` (`idType=INTEGER`, custom `EchoDocumentChoices implements QCustomPossibleValueProvider<Integer>`, registered by `EchoQBitProducer` if absent), `dependsOn=[printerId, mediaId]`.

HTTP (published `searchProcessPossibleValues`): `POST /qqq/v1/processes/echoPrint/possibleValues/templateId`, JSON `{"values":{"printerId":"...","mediaId":"..."},"processValues":{},"useCase":"form"}`; optional `searchTerm`, `ids`. Returns `{"options":[{"id":<JSON integer>,"label":"<template name>"}]}`. Either dependency missing returns no options; an unauthorized printer or unknown size is refused. `ids` arrive as strings and are coerced strictly (plain digits or integers only, else 400).
Setup submit value: `templateId` as a JSON integer. `EchoSteps.documentId` accepts an integral number, or plain digits from a text form; fractions, exponents, blanks, overflow, booleans and values below 1 are refused. Persisted: `echo_run.templateId` and `templateRevisionId` are INT; the library HTTP fixture's `echoFixtureRun` table now exposes both as INTEGER fields (Android E2E reads `templateId`).
`EchoService.start/select` and `EchoDestinations.DocumentChoice` now use `Integer` ids.

## Server rules
- `EchoDestinations.authorizeDocument(identity, destination, templateId)` and `documents(printerId, mediaId)` (new). `EchoPrintingDestinations` reads Generation `document_generation_template`/`_revision` with `PermissionEnforcement.ENFORCE` (query for choices, get for authorization). A template is eligible only if: it is for `echoScan`, has a published non-PREVIEW revision for `echoScan`, the revision's stock width/height/dpi equal the chosen media, and (ZPL) its scene is `qqq-zpl-scene/1` with a barcode element using `symbologyPath=/record/rendererSymbology`, `valuePath=/record/barcode` and an allowlist covering all of `EchoTemplates.SYMBOLOGIES`. Static, wrong-symbology or other-size templates are never listed or accepted. Nothing else is substituted.
- Setup (`EchoService.select`, step `start`) validates a numeric `templateId`, authorizes printer+media then the document, and stores `templateId` and `templateRevisionId` on `echo_run` once. Replays with a different document are refused (same rule as printer/media).
- Every scan: `claim` returns the run's pinned document; `EchoDestinations.plan(identity, destination, document, scan)` reauthorizes the destination, re-reads the template and requires the current published revision to equal the pinned one, else fails visibly ("changed or was withdrawn", Leave stays available). Print/Scan steps never read `templateId` input. Single in-flight, idempotent same-event retry, pin compare-and-set and durable-receipt ordering from 101efc8 are unchanged. A run with no document (pre-migration row) refuses to print.
- `EchoTemplates` (host catalogue) and the `ECHO_TEMPLATE_*` demo pin no longer select anything (`EchoHost.Deployment` lost its `templates` argument, `EchoOwnerAdapters.config` lost `templates`; the helper class remains for the symbology list). `EchoTemplatePublisher.publish(definitions, media, name)` publishes additional named documents.

## Migration
`echo-h2.sql`: `ALTER TABLE echo_run ADD COLUMN IF NOT EXISTS templateId INT` and `templateRevisionId INT`. Idempotent; existing rows are kept with NULL. Other databases need the equivalent additive columns (not provided).

## Tests (root verified)
- `EchoTest`: setupRequiresAnAuthorizedCompatibleDocument, documentChoicesComeFromTheNativePossibleValueSearch (real SearchPossibleValueSourceAction), chosenDocumentIsUsedForEveryScanAndCannotBeRetargeted (repeated scans, forged process value, setup replay), changedOrWithdrawnDocumentFailsVisiblyWithoutSubstitution, documentMigrationPreservesExistingRuns; existing tests updated to pass a document. Library suite:45 tests pass (29 process/persistence,15 adapters,1 native HTTP fixture). Full composed demo:24 tests pass. Both enforce Checkstyle0.
- `EchoOwnerAdaptersTest`: onlyDynamicSameSymbologyScenesAreEligibleDocuments (15 tests).
- `EchoHttpFixtureTest`: real HTTP possible-value search plus selected document.
- `EchoDemoAcceptanceTest` (real HTTP, real Printing/Generation/Record Files): two compatible published documents plus one other-size document; the PVS offers exactly the two; missing/unknown/malformed/wrong-size documents refused; document B chosen, used by every render across scans, replayed setup and a forged `templateId` on a later scan do not retarget.

Added in the typed-contract pass: `EchoDemoAcceptanceTest.republishedDocumentFailsVisiblyWithoutReplacementAndLeaveStillWorks` (real owner `DocumentDefinitionService` republish of the chosen template after setup: the next scan and its retry fail visibly, one render and one Printing job in total, Leave closes the run, and a fresh run offers and prints the republished document); integer-id and strict-coercion assertions in `EchoTest`; all possible-value requests in the fixture and demo tests use the JSON `{values, processValues, useCase}` POST, not multipart. Each demo test runs under the existing before/after connection-provider reset.

## Process-state trap (diagnosed from current core source; fix is in Echo, not core)
Both composed failures had one cause. In core, `RunBackendStepAction.runStepCode` catches any exception thrown by step code and replaces the result with `new RunBackendStepOutput()` (an empty `ProcessState`); `RunProcessAction` (line ~810) then stores that state under the process key before rethrowing. The new state has no `ProcessStateAccess`, so the next USER request fails `primeProcessState` -> `ProcessStateAccess.requireAccess(null, ...)` -> "Permission denied for process state" (the 403 on Leave). So a thrown business refusal (replayed/invalid setup, republished document at scan time) trapped the operator, including their own mistakes. No auth check was weakened.
Fix (example only): `EchoSteps.Start` and `Print` now record a `QException` with `output.setException(...)` instead of throwing. The output then shares the input's `ProcessState` (`seedFromRequest`), so the stored state, ownership record and values survive, and core still raises the exception to the client as the ordinary process error. Operators can correct a setup choice in the same process, retry the same scan, or Leave.
Core proposal (not applied, peer-owned): in `runStepCode`'s catch, keep the input's process state (call `runBackendStepOutput.seedFromRequest(runBackendStepInput)` before `setException`) so a thrown step exception cannot drop ownership for any process. Evidence: `RunBackendStepAction.java` lines 325-330 and `RunProcessAction.java` lines 810 and 646-652.
Test truth: `EchoDemoAcceptanceTest` setup-replay and republish cases are unchanged and now rely on this; new `EchoTest.businessRefusalsNeverTrapTheProcessAndLeaveStillWorks` proves correction in the same process, a refused scan, and Leave. Not run.

## Not covered / gaps
- No test for an unauthorized-to-read template: Generation has no template-specific grant, only table permissions, so it needs a new security grant that was not invented here.
- A true withdrawal is not tested: the owner's `DocumentDefinitionService` has no withdraw/unpublish call, only republish. Republish (changed revision) is the real owner path covered.
- Android: no change needed; the new field is ordinary dependent metadata. Not exercised on Android here.

## Gate (root)
`./mvnw.cmd verify install` from the repo root is unaffected; run the Echo example verify and the Echo demo verify through the existing private-cohort gate script.
