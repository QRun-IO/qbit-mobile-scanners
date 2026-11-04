# TODO

Open work only, as of **2026-11-04**. Present status is in [session state](SESSION-STATE.md); release gates are in [production readiness](PRODUCTION-READINESS.md). The earlier ledger, including every completed item, is archived in [history](history/TODO-through-2026-11-04.md).

## Needs an owner outside this repository

- [ ] **Public document/files/scripting artifacts.** The document owner publishes a coherent set (Generation, its Printing and Record Files bridges including copies support, Record Files, Scripting) as public Maven artifacts. Until then the Echo modules and the optional Record Files integration build against qualified owner inputs and only `qqq.version` and `printing.version` resolve publicly. The owner's packaging is in progress; no completed version is known, so do not invent coordinates or change POMs ahead of it.
- [ ] **Core, Collaboration and Next UI foundations.** Adopt the public versions when the owner releases them, as a normal reviewed dependency change with a fresh full gate.

Note: redesign of the shared CI pipeline is outside this scanner/local Echo scope and is not tracked here; the local demo does not depend on it.

## This repository

- [ ] Re-run the full gate (root, demo, Echo, Echo demo, Record Files, browser E2E) on whatever dependency set is adopted next, and record the actual counts.
- [ ] Keep the Gitea-first documentation accurate when the Android release/signing pull request merges: link the released host version and the verified signed artifact.
- [ ] Decide whether to support, or keep refusing with a documented message, GS1, ECI, structured and binary scanner profiles in Echo ([limits](../examples/echo/README.md#fidelity-and-limits)).

## Hardware and device follow-up

- [ ] **MC9400:** one physical trigger scan, then confirm the backend record. The device is installed, signed in and approved ([session state](SESSION-STATE.md#handhelds)).
- [ ] Optional: capture per-case evidence (photos, logs, a scanned-back label) on a future run. The November 4 operator pass is accepted as is and is not to be requested again.

## Release (deliberately last)

- [ ] Signed-build qualification on a device against an HTTPS server (enrollment, scanner path, navigation, update), then distribution. The QRun key, signed 1.0 APK and direct/MDM channel are done; nothing is published or distributed, and there is no automatic APK-signing CI.
- [ ] HTTPS deployment, host-owned database migration beyond H2, and the remaining gates in [production readiness](PRODUCTION-READINESS.md).

## Not in version one

Offline queue, camera-based barcode decoding, kiosk/MDM provisioning beyond the implemented managed configuration, broader native screen coverage and fleet rollout.
