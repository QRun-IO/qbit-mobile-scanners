# Atomic capture authorization contract requested from Record Files

Status: the adopted, verified `retained-contract-20261031-0238` binary has only
`capture` and `revalidate`. A later read of upstream working source found the exact
operation guard below already being implemented, with matching wrap points and
new tests. That source is not an adopted artifact or a completed qualification.
Please hand off a newly frozen bundle with complete checksums and verification.

## Minimal SPI

Keep existing methods and add this synchronous, defaulted Java SPI:

```java
default <T> T executeAuthorized(Optional<CaptureIdentity> expected,
   Operation<T> operation) throws QException
{
   if(expected == null || expected.isPresent() || capture().isPresent())
   {
      throw new QException("Capture identity requires an authorization operation guard.");
   }
   return operation.run();
}

@FunctionalInterface
interface Operation<T>
{
   T run() throws QException;
}
```

This default preserves a genuinely desktop-only provider and fails closed for
every legacy device provider. It must never silently implement device execution
as `revalidate(); operation.run()`.

The provider override acquires the same authorization exclusion used by approval
changes, session termination and first device/session binding. Under that guard
it re-derives current authenticated actor, private session binding, current device
approval and public mobile session, compares them with the expected identity, and
then runs the callback. The guard remains held through all synchronous writes,
audit outcomes and actual transaction commits, and releases in `finally` on every
path. No deferred future/job/stream may escape while relying on released authority.
Missing authority, interruption and lookup failure must reject the operation.

`Optional.empty()` is an expected absence, not a bypass. Our mixed mobile/desktop
adapter must hold the scanner guard even for empty identity and verify that the
current authenticated request remains genuinely unbound desktop. Otherwise a
concurrent first device binding could race an upload begun as desktop.

## Proposed scanner adapter patch, after new frozen SPI adoption

Add a public overload to the scanner facade (no Record Files dependency):

```java
public static <T> T executeAuthorized(Optional<Identity> expected,
   Operation<T> operation) throws QException
{
   return MobileSessionService.withActiveSession(() ->
   {
      if(expected == null || !expected.equals(captureCurrent()))
      {
         throw new QPermissionDeniedException("Device attribution no longer matches this sign-in.");
      }
      return operation.run();
   });
}
```

Preserve the existing `Identity` overload's rejection of null; do not turn null
into desktop absence. Then override the new SPI in the optional adapter:

```java
@Override
public <T> T executeAuthorized(Optional<CaptureIdentity> expected,
   CaptureIdentityProvider.Operation<T> operation) throws QException
{
   if(expected == null || expected.filter(value -> !PROVIDER_KEY.equals(value.providerKey())).isPresent())
   {
      throw new QPermissionDeniedException("Unsupported mobile capture identity.");
   }
   Optional<MobileDeviceAuthorization.Identity> identity = expected.map(value ->
      new MobileDeviceAuthorization.Identity(value.actorId(), value.deviceId(), value.publicSessionId()));
   return MobileDeviceAuthorization.executeAuthorized(identity, operation::run);
}
```

The current scanner exclusion is one reentrant monitor per QInstance in one JVM.
Its approval and termination operations use that same monitor. This qualifies
only a single-instance host; multi-node production requires shared exclusion or
transactional fencing used by every mutator. A row lock taken after a separate
snapshot read is not, by itself, that strategy.

## Required owner-side wrap points

Acquire capture authority before Record Files/attachment transactions or row
locks. Re-read persisted upload identity and parent permissions inside the guard.
Reject an ambient host transaction whose commit could occur after callback return.
`RecordFilesStore.atomic` must return only after its owned commit completes.

| Operation | Work protected through completion |
| --- | --- |
| Create | Identity comparison, parent reauthorization, quota initialization/reservation, upload row and creation audit commit. |
| Status | Persisted identity check and any expiry state/audit mutation; GET may write. |
| Chunk | After bounded input reading, reauthorize before staged object write, chunk row and outcome commit. |
| Cancel | Terminal-state change and mandatory audit commit. |
| Finalizer claim/renew | Collection creation, lease acquisition/renewal and state/audit commits. |
| Canonical intake | Entire standard attachment-owner `files.intake`, including object effects and owned persistence commits. Guarding only checkpoint append is too late. |
| Append checkpoint | Collection association and checkpoint commit. |
| Finish/fail | Final receipt/state/audit commit or guarded recovery state. |
| Owner cleanup | User-driven expiry, staging cleanup cursor and quota changes. |

Trusted `cleanupBatch` is a separate explicitly privileged maintenance operation.
It may clean abandoned staging after the original actor/device is revoked, but
must not read/release new content, resurrect intake or delete retained originals.

The new working source has these wrap points, guarded cleanupOwn and guarded
canonical intake. It rejects ambient transactions via `withAuthorized`; create's
early `actor()` also rejects ambient QContext transactions before guard acquisition.

## Linearization and regression evidence

If revoke/end wins the shared guard first, the later operation must produce no
new retained object/association/checkpoint/complete receipt. If intake wins first,
its commit is allowed and revoke/end waits; once revocation commits, subsequent
items/checkpoints/receipts must be refused. Earlier authorized originals can remain
retained/quarantined in a partial upload. This does not promise upload-wide rollback
or delete previously committed originals.

Required deterministic regressions, using latches rather than timing assumptions:

1. Revoke and end between preflight and guard acquisition: callback/storage/commit
   do not execute, including a fully staged upload ready to finalize.
2. Pause inside canonical attachment-owner intake/commit and attempt revoke/end
   on another thread: termination waits, the first commit finishes while guarded,
   then later mutation is denied. Check persistence independently after commit.
3. Repeat at collection association and COMPLETE receipt commit; no guard may end
   after event insertion but before SQL commit. Cover failure/rollback release.
4. Fail-closed legacy provider creates no upload or quota row. Null/foreign provider,
   actor, device, session, core-session mismatch and unavailable authority reject.
5. Empty desktop identity succeeds only while unbound; concurrent first binding
   uses the same exclusion and cannot cross the desktop operation.
6. New login cannot resume old captured identity; asynchronous retries require the
   retained trusted context plus current binding/approval/tombstone validation.
7. Parent tenant visibility, configured record type, attach feature, permission and
   credential/channel ceilings remain independently enforced before finalization.

Working-source tests currently demonstrate preflight-to-chunk-guard revocation,
guard depth during owner intake/audit, legacy-provider rejection and a concurrent
cancellation commit. Our combined runtime still needs the actual canonical intake
race with real scanner approval/session-end operations after bundle adoption.

## Existing protection before this change

The frozen code already enforces immutable request/manifest identity, parent/tenant
authorization, configured record types, chunk bounds/hashes, same actor/device/
public-session identity on resume, current approval/tombstones, owner transactions,
lease fencing, durable receipts, quarantine and idempotent retention. Our HTTP
fixture proves sequential revocation/end/new-login rejection and persisted
association/bytes. These checks narrow the race but cannot close the interval
between successful authorization and a later owner commit without this guard.
