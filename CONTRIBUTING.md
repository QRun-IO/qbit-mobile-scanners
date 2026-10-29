# Contributing

Use Java 21 and the checked-in Maven wrapper. Clone this repository and the companion [qqq-android](https://github.com/QRun-IO/qqq-android) when changing the mobile/server contract. No Android SDK is needed for server-only changes.

Create `feature/`, `fix/`, `docs/` or `chore/` branches from `develop` and open pull requests into `develop`. Use Conventional Commit subjects and describe the resulting behavior, relevant verification and compatibility changes. Request review from the code owner; do not merge with failing required checks.

## Local checks

From the repository root:

```sh
./mvnw --batch-mode verify install
./mvnw --batch-mode -f demo/pom.xml verify
```

On Windows, use `.\mvnw.cmd`. The root build verifies Checkstyle, H2 behavior and QBit guardrails; the separate demo verifies native login, enrollment, approval, scanner metadata, asynchronous capture, retries and revocation over HTTP. Tests use synthetic data and isolated databases.

For a physical handheld, follow [TC22 acceptance](docs/TC22-ACCEPTANCE.md). State clearly whether a change was tested on a device, in an emulator, or only through automated tests.

## Style and design

Follow the existing Kingsrook Java style: three-space indentation, Allman braces, banner documentation, explicit imports and Apache-2.0 SPDX headers. The checked-in Checkstyle configuration owns the formatting rules. Prefer ordinary QQQ metadata, actions and authentication integrations over duplicate infrastructure.

Retain the online-only contract and acknowledge scans only after persistence succeeds. Add meaningful regression coverage for permission, device identity, retry, schema or protocol changes. Keep device tokens out of process values, logs and fixtures. Use generated examples instead of production barcodes or identities.

The [CI and release guide](docs/CI-CD.md) explains the shared CircleCI profile and publication enablement. Report vulnerabilities privately through [SECURITY.md](SECURITY.md), and follow the [Code of Conduct](CODE_OF_CONDUCT.md).
