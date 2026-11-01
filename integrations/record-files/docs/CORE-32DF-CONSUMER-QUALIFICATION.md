# API 1.9 Record Files consumer qualification

Status: isolated consumer qualification passed on November 1, 2026. This is not
adoption of a new optional default, publication of immutable files-owner
dependencies, or deployment to the normal scanner runtime.

## Qualified cohort

| Input | Identity |
| --- | --- |
| Core/BOM/filesystem source | `32df5d76c8728145d4b7b417a7f56b352893d1f2` |
| Scanner source | `1a3e33ba280edf93bcb1b7b66dfd45660a9a1a68` |
| All QQQ backend modules | `4.1.0-ci.32df5d76c8728145d4b7b417a7f56b352893d1f2` |
| Independent frontend pin | Existing `qqq-frontend-next:1.0.0-RC.14`, unchanged from the tested BOM |
| Record Files | Frozen API-registration 0801 main/test JARs and POM |
| Attachment owner | Frozen collaboration 218 main JAR and resolved POM |
| Dependency pins | Guava 33.7.2-jre, H2 2.2.224, Jimfs 1.3.2 |

The optional consumer copy imports the coherent QQQ BOM with no separate
filesystem override. Its JUnit engine and params are versionless test
dependencies; API/engine/params resolve consistently to 6.0.2. The historical
optional POM/default bundle remains unchanged in the repository.

## Fresh consumer results

All **78** cases passed with zero failures, errors or skips:

| Gate | Passed |
| --- | ---: |
| Scanner unit/integration tests | 39 |
| Demo HTTP tests | 6 |
| Optional Record Files integration/contract tests | 18 |
| Real browser process tests | 15 |

The browser suite covers single-record actions, selected bulk records,
filter-based selection and first-N selection, confirmation/cancellation,
persisted results and unaffected unselected records. Public discovery returned
core API **1.9.0**, mobile extension **1.2.0**, TABLE authentication and no
`nativeSessionLogin` advertisement for that authentication provider.

The Record Files suite comprises 16 integration executions and two typed-widget
contract cases. It covers actual login/enrollment/approval, persistent file and
parent association, identity substitution, ended/revoked sessions, parent and
attachment authorization, and 12 operation-guard concurrency cases. Runtime
provenance retained **214 JAR hashes**, **3,163 unique QQQ/QBit classes** and
**zero duplicate classes**. Frozen Record Files/owner main, test and POM bytes
remained unchanged.

## Core verification basis

The preceding coherent core source `a1366c154712c85140cdad89efdc3dc3c495f342`
passed its full configured reactor gates: 4,649 discovered tests, 4,629 passed,
20 existing skips, zero failures/errors. A later published sample test exposed
an unchecked `InvalidPathException` for a NUL-containing storage reference.
The narrow correction maps only untrusted-reference parsing to a fixed-message
`QBadRequestException`, without retaining the supplied path or exception cause.

The correction passed the complete filesystem gate again: 142 discovered,
138 passed, four existing skips, zero failures/errors. Actual `StorageAction`
probes reproduced the old error and verified the corrected error for read,
write, download URL and delete. Independent review closed the reported defect.

The final signed core32 artifacts were freshly built in a separate private
repository. For that artifact build, tests, coverage and static reanalysis were
explicitly not repeated. Exhaustive comparison of **2,772 compiled classes**
across every installed main/test JAR found exactly one changed class,
`FilesystemStorageAction`, byte-identical to the fully tested corrected JAR.
Resources, version metadata and POM differences were retained separately. This
is a differential verification claim, not a fresh 4,649-test pass on core32.
Hosted final-source acceptance is tracked separately.

## Key artifact SHA-256 values

| Artifact | SHA-256 |
| --- | --- |
| Scanner | `dfc66567276ddb837d1388560c7aaf3dae53ab0287b943742024a0837aeec38c` |
| Core | `08907e809a6e2cdcd64497e482c6f890ad05b811bdc125d354c9584218e4af04` |
| RDBMS | `a029e797a0bbcd5fc410eba903c085d9d856f9b39d45aea954da20061f658b31` |
| Filesystem | `25851e33ad25044bc8a3a14753c87253101f449fc5fe63e0ef68da86d418a9bb` |
| Record Files main | `ca20530b133e0423fbb56b9e34afe316adba73cde8c9d1321a983a5de0ca769b` |
| Record Files tests | `1b75563128086b55845aaefd6ca7db6ee5f975f3a82e58f9806049bd74ce671b` |
| Attachment owner | `87c6a9aac52a0821edd66dad2924a014d2c70aa929ead011dcf7a89a0f94c854` |

These identify retained local qualification artifacts. Historical SNAPSHOT
coordinates alone do not identify immutable release bytes.

## Remaining adoption boundary

The files owner must still supply its complete matching source/artifact handoff
and qualify its full Record Files/attachment-owner suites and migrations against
the same core32 source/BOM revision. The 18 scanner consumer cases do not replace
those owner gates. Immutable accessible publication inputs are required before
optional hosted CI/default adoption can claim reproducibility.

Actual TC22 camera/gallery, permissions and durable authorized association on
this final cohort remain separate acceptance work. A disposable fixture may use
the qualified cohort for that work; it does not upgrade the normal database,
normal APK, existing device enrollment, or USB forwarding. Prior frozen bundles
and runtime evidence remain retained.
