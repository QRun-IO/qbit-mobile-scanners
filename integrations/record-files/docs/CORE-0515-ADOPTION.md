# Explicit candidate adoption: retained-contract-20261031-0515

Status: explicitly accepted as the isolated consumer default after successful
verification at 2026-10-31 12:46:32 UTC and root review. The default cache is the
separate `isolated-m2-0515`; the 0352 cache and evidence are preserved. No shared
Maven repository, existing operator runtime, or database was changed.

All 33 entries in the candidate's root manifest and all seven guarded-source
entries match SHA-256. Record Files main/tests and the canonical attachment owner
are byte-identical to 0352. The changed matched pair is:

| Artifact | SHA-256 |
| --- | --- |
| Core | `c7e9da29afe886ada550d0590b10491b8f3b1a621a1b3b79ca3fb5f807dcf894` |
| RDBMS | `562ec0f2653dd156d06b7b7df7268c40ac87a2c8a79c4f2f7811666507338728` |
| Flattened parent POM | `0158658f28d868b2ac5b323c7ede46b7075d8e5f33a2da9a49c34402abe24afe` |

The owner reports 177 Record Files tests including 11 MySQL tests against this
pair. That upstream evidence does not substitute for this consumer's tests.
The frozen core/RDBMS version strings did not change; hashes distinguish adoption.

## Session logging correction

The isolated core source's `TableBasedAuthenticationModule` now logs fixed
revalidation text rather than concatenating `session.getIdReference()`.
`testRevalidationDoesNotExposeSessionIdentifier` attaches a real DEBUG appender,
requires the revalidation branch to log, and rejects the synthetic session ID in
the message. Inspection of the exact frozen core with `javap -c -p` confirms the
fixed string is passed directly to `QLogger.debug(String)`, with no interpolation
on that call. This verifies the specific reported leak; it is not a complete
logging audit of upstream dependencies.

## Jobs migration boundary

The candidate also includes durable Jobs metadata changes. Before upgrading any
existing Jobs host, add the nullable columns `jobs_job.enlistment_key VARCHAR(64)`,
`intent_fingerprint VARCHAR(64)`, and `process_revision VARCHAR(128)`, plus a unique
constraint on `enlistment_key`. This is required even when strict transactional
admission stays disabled because ordinary metadata queries select the columns.

The supplied H2/MySQL scripts additionally create `jobs_admission(id)` and seed
row 1 for strict admission. They assume the complete baseline Jobs schema exists;
they are additive migrations, not idempotent bootstrap scripts. MySQL columns use
`utf8mb4_0900_bin` and the guard table uses InnoDB. Strict operation additionally
requires the owner's provider, timestamp, durability, isolation, bounded-lock,
namespace/signing and all-writer rollout qualifications. PostgreSQL requires an
equivalent additive migration but remains unqualified for strict mode.

This scanner consumer fixture does not install Durable Jobs or `jobs_job`.
It creates fresh file-H2 metadata for its configured qbits and does not have an
existing Jobs schema to migrate. No migration should be applied to the operator
database during this compatibility test. Deployments that add Jobs must perform
the owner migrations before adopting the new pair.

## Reproduction

The completed bounded run used these explicit arguments (now also the defaults):

```powershell
$env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=2 -Xmx768m'
./Verify-Isolated.ps1 `
  -Bundle R:/Git.Local/QRunIO/qbit-documents/artifacts/retained-contract-20261031-0515 `
  -RepositoryName isolated-m2-0515
```

This creates a separate cache under ignored `target/`, builds the current scanner
base cleanly, installs the exact flattened parent and matched dependency pair,
then runs the optional module's 18 HTTP/identity/commit/contract cases. The existing
0352 cache is retained. The classpath is regenerated so Maven cannot reuse paths
from a different cache. A complete runtime manifest and duplicate-class checks
remain required. Later reruns may use `-SkipSeed`; module-only reruns must identify
the exact prior scanner binary and do not qualify unbuilt base changes.

## Consumer verification result

Fresh scanner clean verification/install passed 37 tests. Optional clean verify
passed 18 tests (16 HTTP/identity/concurrency cases and two contract cases), zero
failures/errors/skips, with Checkstyle. Runtime verification hashed all 214 jars,
confirmed the exact matched pair and owner/RF bytes, and found zero duplicate QQQ
classes across 3,180 classes. Explicit H2 remains 2.2.224.

The tested scanner jar is
`e9504632ec308cbad080afcce7da0fb7037f7d9b153359d1653729cb8e33af17`.
Its freshly copied source contains the normalized trailing-slash heartbeat path
and approval check. Evidence under `../evidence/20261031-124632Z/` retains base and
module source hashes, passing test summaries, complete actual runtime and compiled
manifests, bundle/source manifests, safe receipt/widget and success-marker hashes.
The two default argument values were changed only after the passing explicit run
was reviewed; no Java or dependency bytes changed after verification.

This compatibility run qualifies only the isolated consumer. Published
immutable coordinates, hosted optional-module CI, production providers, migration
rollout, and deployment approval remain separate gates.
