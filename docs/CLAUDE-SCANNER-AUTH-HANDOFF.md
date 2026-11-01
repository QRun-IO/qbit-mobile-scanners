# Claude scanner-auth handoff (unbuilt, uncommitted)

## Source facts (core qqq-wt-native-provider-logout, read-only)
- QSession is fully mutable; `QContext.setQSession` aside, any code holding the session can change permissions, user, uuid, channel, security keys, variants, credentialScope in place. Nothing in core guarantees a retained wrapper still has the authority that was checked.
- QCredentialScope has no equals; its getters return unmodifiable views; `null` set = unrestricted (distinct from empty).
- `QSession.hasPermission` ignores credential scope, so `MobilePermissions.require(SCAN)` does not evaluate ceilings.

## Change
- (first round; see follow-up below for the current API) `VerifiedDeviceSession`: at construction stores an immutable `Authority` snapshot (userId, uuid, clientChannel, permissions, security keys, backend variants, and a `Scope` copy: credentialId, readOnly, apps/tables/processes/reports/widgets/operationIds, null preserved). `authorityUnchanged()` re-derives it and compares by value.
- `MobileDeviceAuthorization.captureCurrent`: an existing wrapper whose authority differs from its snapshot throws `QPermissionDeniedException` (fail closed) before any other use; otherwise all existing checks run and the same wrapper is retained (no `setQSession`). First capture still wraps. Values/valuesForFrontend are deliberately excluded (non-grant data).
- Tests (`MobileScannersTest`): `repeatedCaptureRetainsSameVerifiedSession`, `retainedSessionRejectsChangedAuthority` (device header, user, uuid/binding, permissions, approval revoke), `retainedSessionRejectsCredentialScopeAndSelectorChanges` (each ceiling set widened/nulled/narrowed, readOnly, credentialId, scope removed, channel, security key, backend variant, extra permission; each rejected with wrapper kept, then passes after restore).

## Follow-up round (post-review; unbuilt, so the 35-pass result predates it)
- `captureCurrent` now computes the expected authority once at the start (wrapper: its checked snapshot; plain session: a fresh snapshot) and calls `requireCurrent` twice: at the start, and again after ALL checks (approval, observe, binding re-read) immediately before retaining or wrapping. `requireCurrent` fails closed unless `QContext.getQSession()` is still the very same reference AND its authority equals the expected one. Core replacing the session or a host hook changing grants/ceilings during validation is rejected; first capture is not wrapped in that case.
- Immutability assessment (core source): `QSession.clone` copies security-key lists and backend variants one level (elements shared); `QCredentialScope.clone` copies its sets (String elements). Core offers nothing deeper. So `authority()` copies collections and treats element values as safe only if they are known immutable types (String, boxed numerics, BigInteger/BigDecimal, Boolean, Character, enum, UUID, java.time Temporal). Any other element makes the snapshot carry a random marker so it never equals anything: fail closed, no custom serialization.
- New tests: `authorityChangedDuringChecksIsNotRetained` (scoped first capture copies exact ceilings/grants/channel; mid-check ceiling widening, grant addition, session replacement on a retained wrapper; mid-check widening on first capture leaves the plain session unwrapped; a test-only POST_QUERY_RECORD hook on the device table fires during the approval read and each case asserts the hook was consumed) and `mutableSessionValuesFailClosed`. If the hook never fires, the `assertNull(hook)` lines fail loudly rather than pass vacuously.
- Serialized-session compatibility: `VerifiedDeviceSession` declares no `serialVersionUID`, so its computed UID already changed with any prior class edit and changes again now (new fields, nested records). Evidence searched: no ObjectOutputStream/serialVersionUID use in this repo; core `StateSerialization` (Java serialization with an ObjectInputFilter allowing `com.kingsrook.*`) has no QSession reference in the `state` package. I found no source showing a VerifiedDeviceSession is persisted across deployments, so no migration is claimed or added; the wrapper is created per request. If a host persists sessions through a state provider, in-flight old wrappers would fail to deserialize after upgrade and re-authenticate. This is unverified for hosts outside this repo.

## Verification fix
- `authorityChangedDuringChecksIsNotRetained` failed with "second customizer with role postQueryRecord" because the device table already registers `MobileDeviceOnline`. The test now asserts that production customizer is registered, then replaces the map entry with `SessionMutator` (a subclass that runs the one-shot hook and then `super.apply`, i.e. the real behavior). Production code is unchanged. The `assertNull(SessionMutator.hook)` lines after each case still prove the hook ran during the checks (a hook that never fired would leave it non-null and fail).
- Unverified: not rebuilt after this edit (no shell). `MobileBrandingTest` loopback error is environmental per root.

## Limits / blockers
- Not compiled or run (no shell). Scope test assumes a scope listing all instance tables/apps lets enroll/bind/capture work; if core denies for another reason, adjust the baseline.
- Behavior: a legitimate in-place refresh that changes permissions now rejects until re-verified rather than being silently adopted (owner-required fail closed).
- Credential ceilings are still not independently enforced by `captureCurrent` (SCAN uses hasPermission); they are only preserved/unchanged. Needs owner decision.
- Out of scope, same bug: `MobilePermissions.checkPermissionsThrowing` (~line 69) still replaces the wrapper on RunProcessInput for scanner processes. Needs root coordination.

## Gate (private frozen caches, run by coordinator)
1. `./mvnw.cmd -Dtest=MobileScannersTest verify`
2. `./mvnw.cmd verify install`
3. `./mvnw.cmd -f demo/pom.xml verify`

Coordinator verification: fullscanner49tests and realdemoHTTP7tests pass, zeroCheckstyleviolations, privatePU/f708 core. ExactRFowner18 remains pending. No productioncache/runtimeadoption.
