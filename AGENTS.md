# QQQ Mobile Scanners

This is an application QBit and a separate local demo, using Java 21 and QQQ 4.1.0-RC.20.
The Android host is maintained in [QRun-IO/qqq-android](https://github.com/QRun-IO/qqq-android).

## Build and verify

- Use the checked-in Maven wrapper (3.9.9), not an arbitrary system Maven.
- From this directory: `./mvnw verify install`, then `./mvnw -f demo/pom.xml verify`.
- Windows equivalents use `mvnw.cmd`. Checkstyle runs during `validate`; do not disable it or skip tests for a completed change.
- Root tests cover H2 persistence, permissions, concurrent retries and async identity. Demo tests cover the real native HTTP protocol. Hardware acceptance is separate: `docs/TC22-ACCEPTANCE.md`.

## Implementation conventions

- Keep ordinary QQQ app/process/widget metadata and existing native API paths. Do not create a second generic mobile-screen framework.
- Kingsrook Java formatting: three spaces, Allman braces, banner documentation, explicit imports, Apache SPDX headers. `checkstyle/checkstyle.xml` is the enforced authority.
- Preserve online-only semantics, idempotent event retries and server-derived user/device attribution.
- Device credentials belong only in scoped HTTP headers and private hashed storage, never process inputs, responses beyond enrollment, fixtures, logs or notes.
- Require core permission enforcement. Keep raw table writes and credential reads blocked; recheck approval for every scan, including async execution and resumed processes.
- Schema changes affect the host-owned database. The supplied schema is H2; document migration needs rather than assuming every backend is equivalent.

## Repository workflow

- `develop` is the development default; make feature/fix branches and pull requests into it. `main` is reserved for reviewed releases.
- CircleCI uses the central qbit manifest profile on the managed `0.8` track. Keep the three version declarations identical. Extra demo verification belongs in its dedicated GitHub workflow, not the canonical setup caller.
- All publication switches start disabled. Creating a repo or pushing a branch is not permission to publish Maven artifacts or cut a release.
- Preserve unrelated work. Update `docs/SESSION-STATE.md` and `docs/TODO.md` when project state changes. Commit, push, release, deployment and external communication require authorization in the active task.
