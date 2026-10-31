# Hosted CI dependency handoff

Read-only publication investigation, 2026-10-31. No build, publication, workflow
activation, repository creation or upstream edit was performed.

The optional module passes locally against immutable **bundle bytes**, but its
Maven coordinates are mutable development snapshots. A clean hosted runner cannot
reconstruct this verified runtime from published dependencies. The module's hosted
gate must remain unclaimed until the owner supplies supported, immutable releases.

## Missing published inputs

| Current verified input | Required owner handoff |
| --- | --- |
| `com.kingsrook.qqq:qqq-parent-project:4.1.0-isolated-record-files-SNAPSHOT` | Immutable flattened parent POM with every referenced parent resolvable. |
| `com.kingsrook.qqq:qqq-backend-core:4.1.0-isolated-record-files-SNAPSHOT` | Immutable core release containing the qualified transaction/Jobs contracts and session-log correction. |
| `com.kingsrook.qqq:qqq-backend-module-rdbms:4.1.0-isolated-record-files-SNAPSHOT` | Matching immutable RDBMS release with an explicitly supported core pair. |
| `com.kingsrook.qbits:qbit-collaboration:0.1.0-record-files-SNAPSHOT` | Immutable standard attachment-owner release containing the retained lifecycle, transaction/journal and storage contracts. Published POM must resolve its version without local substitution. |
| `com.kingsrook.qbits:qbit-record-files:0.1.0-SNAPSHOT` | Immutable main jar and resolvable POM containing guarded ingestion, native routes, typed contracts and parent authorization. |
| Same Record Files GAV, `tests` classifier, `test-jar` type | Published integration-fixture jar containing `RecordFilesFixture` and its required resources. It is an actual compile/runtime dependency of the 18 consumer tests. |

The owner chooses release versions; this document does not reserve or invent them.
The complete supported tuple must identify the middleware/BOM version as well.
The currently qualified mixed runtime uses middleware4.1.0-RC.20 and H2 2.2.224;
new release coordinates require a fresh consumer run and complete runtime manifest.
Changing artifact version/provenance resources changes hashes, so require a new
owner manifest plus explicit source mapping rather than demanding old snapshot
jar hashes from newly versioned releases.

## What is actually published or staged

Direct Maven Central metadata requests returned:

- [Record Files metadata](https://repo.maven.apache.org/maven2/com/kingsrook/qbits/qbit-record-files/maven-metadata.xml): HTTP404.
- [Collaboration metadata](https://repo.maven.apache.org/maven2/com/kingsrook/qbits/qbit-collaboration/maven-metadata.xml): latest/release `0.1.0-RC.6`.
- [Core metadata](https://repo.maven.apache.org/maven2/com/kingsrook/qqq/qqq-backend-core/maven-metadata.xml), [RDBMS metadata](https://repo.maven.apache.org/maven2/com/kingsrook/qqq/qqq-backend-module-rdbms/maven-metadata.xml), [parent metadata](https://repo.maven.apache.org/maven2/com/kingsrook/qqq/qqq-parent-project/maven-metadata.xml): latest/release `4.1.0-RC.20`.

These public releases are not qualified substitutes for the frozen0515 changes.
The GitHub releases also show immutable [Collaboration RC.6](https://github.com/QRun-IO/qbit-collaboration/releases/tag/v0.1.0-RC.6)
and [QQQ RC.20](https://github.com/QRun-IO/qqq/releases/tag/v4.1.0-RC.20).

The local owner worktrees are `qbit-collaboration-wt-record-files`, branch
`feat/record-file-retention`, and `qqq-wt-isolated-scripting`, branch
`feat/isolated-scripting-boundaries`. Both contain uncommitted required changes.
GitHub PR queries for those exact branch names returned no results. Existing
[Collaboration #45](https://github.com/QRun-IO/qbit-collaboration/pull/45) and
[#51](https://github.com/QRun-IO/qbit-collaboration/pull/51) concern attachment
transactions/integration on other branches; neither was established as the source
of the qualified0515 owner artifact. Do not conflate them with publication approval.

The local `qbit-record-files` directory has no Git metadata or CI configuration.
Its POM names `https://github.com/QRun-IO/qbit-record-files`; the connected GitHub
lookup returned404, meaning absent or not visible to that connection. The owner
must confirm its canonical repository/access and source publication path.

Collaboration already has the standard CircleCI release/snapshot publication
workflow and reusable RC preparation workflow. That machinery alone does not
publish uncommitted work or prove that any future release includes the candidate.

## Minimal owner response

1. Canonical repository URLs, reviewed PRs, immutable source commit/tag for each
   changed owner/core component, and mapping from frozen0515 to those sources.
2. New immutable GAVs and all jar/POM/classifier download URLs; repository URL and
   a documented CI read-access requirement if not public. Never send credentials.
3. Complete SHA-256 manifest, published signatures/provenance when available,
   flattened/resolved POMs, and confirmation that the parent/BOM and transitive
   dependency closure resolve on a clean runner without sibling checkouts or
   `install-file` repairs. Include the Record Files `tests` classifier.
4. Owner/core build and compatibility evidence, preserved Jobs additive migration
   requirements, supported middleware/H2/provider tuple, and any delta from0515.
5. Confirmation of release status and remaining deployment gates. A candidate
   publication must not be represented as production1.0 approval.

## Minimal scanner CI once inputs exist

The existing `demo-acceptance.yml` uses Ubuntu24.04, Java21 and read-only contents
permissions, installs the scanner from the checked-out commit, then runs demo and
browser acceptance. It never builds this standalone optional module today.

Add one independent, required optional-integration job using the same pinned
checkout/Java actions and a fresh Maven repository. First build/install the
scanner from that exact checkout, then run ordinary Maven `clean verify` on
`integrations/record-files/pom.xml` with the published pins. The locally built
scanner snapshot is the checked-out component under test, not an unpublished
third-party input. A scanner release still needs its own immutable version.

The Java fixture is loopback HTTP plus fresh file-H2 and file-backed test storage;
the 18 cases need neither a TC22 nor Android tooling. Keep the Java resource bound,
retain safe test summaries and full dependency/class hashes, and fail on a missing
fixture classifier, changed expected artifact hash, duplicate QQQ class or test
failure. Do not seed from a developer's Maven cache or upload private fixture logs.

`Verify-Isolated.ps1` is a Windows local bundle-adoption harness (`mvnw.cmd`,
semicolon classpaths and local frozen files); it is not a portable hosted runner.
Use normal Maven for published resolution, with a small portable manifest check
when implementing that job. No workflow with fabricated pins, skipped required
tests or workstation-only paths has been added. Optional-module publication stays
disabled until the dependency gate is actually satisfied.
