# CI and release operation

This repository follows the current QRun-IO QBit pipeline structure: a canonical CircleCI setup caller and a small `.qqq-ci.yml` manifest. The default development branch is `develop`; `main` is reserved for reviewed releases.

## Verification

| Check | What runs |
| --- | --- |
| CircleCI `setup` and generated `verify` | Shared `kingsrook/qqq-orb@0.8` QBit verification, including Maven tests and reports |
| GitHub `Demo acceptance` / `Verify demo HTTP contract` | Wrapper-pinned root verification/install, then the real demo HTTP acceptance suite |
| GitHub `Demo acceptance` / `Check Windows demo helpers` | PSScriptAnalyzer checks for the local Windows launcher and credential helpers |
| GitHub `CodeQL` | QRun-IO's shared QBit source analysis worker, pinned to an immutable commit |
| Dependabot | Weekly grouped Maven and GitHub Actions update pull requests targeting `develop` |

The three orb version declarations must match exactly. The `0.8` minor track receives centrally managed patch updates, matching the live QRun-IO QBit fleet. Use an exact published patch in all three positions when reproducing a historical pipeline or rolling back. The canonical setup caller does not accept extra jobs; the project-specific demo check therefore lives in GitHub Actions.

Run the same project checks locally with `./mvnw verify install` and `./mvnw -f demo/pom.xml verify`. The wrapper pins Maven 3.9.9 and validates its distribution checksum. Java 21 is required. The demo uses no external database or user credentials in CI.

For workflow syntax, run `actionlint .github/workflows/codeql.yml .github/workflows/demo-acceptance.yml`. Validate the setup caller with `circleci config validate --org gh/QRun-IO .circleci/config.yml`, using your existing CircleCI authentication. The shared orb is private; without organization scope and authorization, the compiler can report that it cannot find the orb. Do not place tokens in config files or command arguments.

## Initial publishing policy

Every `publish` switch and `release_completion` is explicitly false. Branch pushes, including initial `main`/`develop` pushes, and `vX.Y.Z` tags perform verification without deploying Maven artifacts. There is no automatic RC-cut workflow in this bootstrap configuration. GitHub test-report artifacts are diagnostic outputs, not releases.

To enable publishing later, submit a reviewed change to the manifest after TC22 acceptance and production readiness work are complete. Choose the intended snapshot/RC/GA policy; verify the organization registry context, signing configuration and Maven coordinates. Add the then-current shared `qbit-release-rc.yml` caller pinned to an immutable commit before enabling `release_completion`. Update protections and documentation using observed checks from successful hosted runs. Creating a repository does not itself authorize artifact publication.

## Repository settings

These are external settings; files alone cannot enable them:

1. Set the default branch to `develop`; protect `develop`, `main` and `release/*`. Require pull requests, signed commits, resolved conversations and successful applicable checks; block force pushes/deletion. Bootstrap allows a pull request with zero mandatory human approvals, matching a maintainer-operated project. CODEOWNERS identifies the responsible maintainer; add mandatory reviewers when the organization assigns a review team. Protect release tags when releases are enabled.
2. Connect the repository to the QRun-IO CircleCI organization and enable dynamic configuration. The shared QBit job expects its established `qqq-maven-registry-credentials` context even for test-only verification; grant only the intended trusted repositories and actors access. Do not put its values in this repository.
3. Require CircleCI `setup` and `verify` plus demo acceptance and CodeQL using the actual check names/apps emitted by the first successful hosted run. Do not guess status contexts and leave every pull request permanently blocked.
4. Enable Actions with read-only default workflow permissions, the dependency graph, Dependabot security updates, secret scanning/push protection and private vulnerability reporting when supported.
5. Limit merges to the agreed strategy and configure automatic branch deletion after merges. Keep `@KofTwentyTwo` as the code owner unless the organization assigns a team.

## Sources

The bootstrap was checked against live QRun-IO sources on 2026-10-29:

Local verification passed: CircleCI registry-backed config validation, Actionlint, root Checkstyle and 11 tests, and demo Checkstyle and one HTTP acceptance scenario. The live shared generator also accepted the manifest and selected verification-only jobs for develop, main, feature, release, hotfix and tag cases. The authenticated registry resolved the managed track to `0.8.32` during inspection. Hosted runs remain the authority for actual status-check names.

- [QBit canonical caller](https://github.com/QRun-IO/qbit-webhooks/blob/develop/.circleci/config.yml) and [manifest](https://github.com/QRun-IO/qbit-webhooks/blob/develop/.qqq-ci.yml).
- [Generated pipeline contract](https://github.com/QRun-IO/qqq-orb/blob/develop/docs/GENERATED-PIPELINES.md) and [managed patch policy](https://github.com/QRun-IO/qqq-orb/blob/develop/docs/managed-patch-updates.md).
- [Shared CodeQL caller](https://github.com/QRun-IO/qbit-webhooks/blob/develop/.github/workflows/codeql.yml).

Local older templates may still show orb `0.6.x`; they are not the current CI contract.
