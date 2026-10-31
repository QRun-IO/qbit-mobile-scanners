# Optional Record Files integration

This standalone module connects the scanner's verified device/session authority to
Record Files' retained upload API. Neither base qbit depends on the other. A host
that installs both qbits may add this module and configure
`new QCodeReference(MobileCaptureIdentityProvider.class)` as the
`UploadConfiguration` capture identity provider. Install
`MobileScannersRouteProvider` on the same native QQQ server. Use the registered
Record Files API; this module creates no alternate upload endpoint.
Call `MobileRecordFilesContract.register(instance)` to opt into the independently
versioned `mobile-record-files` 1.0.0 widget vocabulary and schemas.

The provider reads only the server-verified request context or its retained
`VerifiedDeviceSession`. Each capture/revalidation checks current actor, private
authentication binding, public mobile session, durable termination and device
approval. A public identity DTO contains only provider key, actor, device and public
session IDs. It is never authorization proof. An authenticated, unbound desktop
session may return no device identity; an Android request or any already-bound
mobile session without verified device proof is refused.

## Semantic native widget

Host widget renderers may return `RecordFilesCaptureWidgetData` with an explicit,
server-selected parent record. The native extension vocabulary is:

```json
{
  "type": "composite",
  "blockType": "COMPOSITE",
  "subBlocks": [{
    "blockId": "recordFilesCapture",
    "blockType": "RECORD_FILES_CAPTURE",
    "values": {
      "parent": {"tableName": "document", "recordId": "1"},
      "title": "Record photos",
      "instructions": "Capture or choose a photo."
    }
  }]
}
```

Use an ordinary QQQ frontend `WIDGET` component and `widgetName`; clients must not
infer parent IDs from arbitrary process values or field names. Parent IDs remain
opaque strings. `title` and `instructions` are optional display text. Clients ignore
additive unknown properties, but refuse missing/invalid parent references. Photo
controls require compatible `record-files` extension discovery and successful
record-specific capabilities with `canUpload=true`. Every upload operation still
independently authorizes the parent. Metadata visibility never grants permission.

Before showing capture actions, clients verify `mobile-record-files` discovery and
`GET /qqq/v1/qbit/mobile-record-files/contract`, sending no credentials. Its static
response is `{"version":"1.0.0","blockType":"RECORD_FILES_CAPTURE","widgetType":"composite"}`;
OpenAPI operation ID is `mobileRecordFilesContract`. The descriptor spec registers
typed parent, values, block and widget schemas. The current core registry rejects
extensions without endpoint specs and provides no schema-only hook, so this small
descriptor supplies versioned vocabulary without changing any upload endpoint or
the frozen mobile-scanners 1.0 contract. A future schema-only hook can improve this.

This widget is self-contained: successful upload neither advances the surrounding
process nor mutates its fields. “Saved” requires a matching `COMPLETE` receipt;
retained files normally remain `QUARANTINED`. They are not thereby released for
download. Camera/gallery uploads use the exact immutable manifest, binary chunk,
hash, replay and finalize contract in the frozen Record Files documentation.

`/version` advertises name, version and path prefix, without operation IDs.
OpenAPI identifiers are `getQbitRecordFilesCapabilities`,
`postQbitRecordFilesUploads`, `getQbitRecordFilesUploadsByUploadId`,
`putQbitRecordFilesUploadsByUploadIdItemsByItemIdChunksByChunkIndex`,
`postQbitRecordFilesUploadsByUploadIdFinalize` and
`postQbitRecordFilesUploadsByUploadIdCancel`. Native invoker/credential-scope IDs
instead use `METHOD /qbit/record-files/...`; capability gating must distinguish them.

## Reproducible isolated verification

From this directory, run `./Verify-Isolated.ps1`. It verifies every checksum in the
retained bundle, seeds a private repository from the local Maven cache, copies and
builds the scanner base outside its normal target, installs frozen candidates only
into that private repository, then runs this module's tests and writes the full
runtime classpath and SHA-256 manifest under `target/`. A later run can use
`-SkipSeed`; `-SkipBaseBuild` is only for iterating module-only changes.
Full runs recreate a validated scratch source tree and clean both builds. Module
output lives in `target/module`, separate from the sibling private repository and
wrapper. `Test-IsolatedBuildPaths.ps1` verifies deleted Java/resource/class removal,
repeat synchronization, sibling preservation and refusal of unsafe/reparse paths.
Module-only runs explicitly hash the prior scanner jar and do not overlay new source.

The current verified bundle is `retained-contract-20261031-0352` from the documents
owner. All 20 root entries and seven source entries are verified. The script
uses the owner's exact resolved collaboration POM, preserving its original bytes.
Earlier 0238 evidence and its locally derived POM remain historical. This candidate
uses the frozen isolated core/RDBMS, RC.20 middleware, and H2 2.2.224. The fixture
found that H2 2.4.240 fails the shipped scanner `CHECK (status IN (...))` constraint
after the DDL connection closes (`ConditionInConstantSet` references its closed
session). A complete runtime manifest is necessary; matching only a core version
label is insufficient evidence of compatibility.

The HTTP fixture uses real QQQ table/password sessions, native enrollment and
record-screen approval processes, native Record Files capabilities/create/chunks/
finalize, disk-backed H2 metadata and the standard attachment owner with a
file-backed test storage adapter. It does not qualify a cloud object provider,
production malware scanning, production authentication or audit export. Its
upstream audit fixture is synthetic. Never deploy these test adapters/accounts.

After verification, `./Start-ConsumerFixture.ps1` prepares an owner-only Windows
directory and starts a timed, isolated fixture. Its returned object contains PID
and private credential-file path, never the password. Read the file locally for
the ephemeral base URL and synthetic account; enroll/approve through ordinary
native APIs. `mobileRecordPhotos` targets document/1. Stop only that PID or allow
its lifetime to expire. The launcher and native synthetic camera/gallery flow have
been exercised on TC22; `docs/SESSION-STATE.md` records exact verified scope.
Private ACLs apply only to a newly created run directory, preserving its parent's
ACL. The JVM is bounded to two visible processors and a 512 MiB heap. Startup waits
a bounded time for the private readiness file instead of returning immediately.

## Remaining release boundary

The adopted SPI executes operations through the scanner's `executeAuthorized`
guard, including expected absence for genuine desktop callers. Eighteen passing
integration/contract tests cover current identity, parent grants, actual retained
registration/association/receipt commits, concurrent revoke/end and rollback.
Evidence is retained under `evidence/20261031-094704Z`. The guard is scoped to one
JVM; clustered operation requires a separately qualified transactional strategy.
Immutable published dependency adoption, optional-module hosted CI and production
storage/content-processing qualification remain open release gates. Test adapters
and passing synthetic intake do not qualify a production storage deployment.
