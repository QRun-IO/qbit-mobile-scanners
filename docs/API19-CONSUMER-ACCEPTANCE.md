# API 1.9 consumer qualification

Verified October 31, 2026 using exact-source core `ecf0f550a0357cec6387c2b54efca128a405a629` and scanner server `b14d925b741e815d1cfa4f62301f19207f25b03f`. These are draft source revisions, not published Maven releases or an operator-runtime upgrade.

## Dependency coherence

The demo imports the selected QQQ BOM. Its `qqq.version` therefore governs transitive core, middleware, OpenAPI and RDBMS dependencies, rather than only direct dependencies. The demo test engine also follows that BOM; an independently pinned Jupiter aggregate mixed incompatible test API/engine versions.

The strict runtime verifier previously rejected the mixed RC.20/new-core fixture. After correction it verifies **195 runtime JAR hashes, 2,440 unique QQQ classes and zero duplicate QQQ classes**, all resolved from the private qualification repository. Every backend QQQ module has the exact CI revision. Guava resolves to **33.7.2-jre** and H2 to **2.2.224**. Qualified core JAR hashes remain unchanged through consumer verification.

## Executed checks

- Exact-source core selected reactor closure: **4,507 tests discovered, zero failures/errors, 16 existing skips**, plus configured coverage/static gates. Windows omits only the cosmetic shell coverage summary; hosted Ubuntu retains it.
- Fresh scanner server: **37 tests**, zero failures/errors.
- Fresh demo using the selected core: **five tests**, zero failures/errors.
- Actual public discovery: UI API **1.9.0**, mobile-scanners **1.1.0**, TABLE_BASED authentication. The OAuth-only `nativeSessionLogin` feature is correctly absent for this configuration.
- Real-browser process suite: **15 passed, zero skipped/flaky/unexpected**, in 60.3 seconds. Coverage includes single-record, checked-bulk, first-N and whole-query administration; cancellation; session access/replay; leased screen viewing; device names/descriptions; location attribution and independent presence expiry.

The fixture used fresh disposable databases and random credentials. Normal scanner data, services and USB forwarding were preserved. No emulator was started. Physical EMDK, actual configured Authentik, camera/GPS and production deployment acceptance remain separate gates.

The Android repository's `scripts/ci/verify-fixture-provenance.mjs` and source-pinned hosted workflow retain the classpath hashes, dependency tree, effective POM and test reports. These checks reject mixed runtime versions; they do not install replacement artifacts into a shared Maven repository.
