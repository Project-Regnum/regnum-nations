# Obelisk relocation and settlement integrity — v0.8.0

An obelisk is an anchor for an existing settlement or state. Moving it preserves
its placed UUID and the existing settlement/kingdom UUIDs. It never founds a new
city/state, transfers ownership, dissolves a settlement or reallocates claims.

## Branch and implementation audit

At implementation start, GitHub PR #6 (citizenship, `cc78238`) and PR #7 (roles,
`ffb848d`) were both open. PR #7 targets `feat/city-citizenship-v0.7` and already
contains that branch. This milestone starts from `feat/city-roles-v0.7.1` and targets
that integration branch. Check PR status again before merging the stack.

The position/identity consumers inspected were both obelisk blocks/entities,
`SettlementService` founding/resolution/state elevation, `KingdomService` founding,
capital resolution, deletion tombstones and teleport, `KingdomSavedData` anchor
indexes, migrations and capital claim reconciliation, `ClaimService` capital
release checks, territory events/placement/bucket hooks, citizenship/affiliation
invitations, management payloads/screens and political map/atlas markers. Map,
teleport and management read current saved records; they need no parallel anchor
registry. Claims refer to settlement UUIDs and invitations to city/state UUIDs,
so neither is rewritten during relocation.

### Confirmed defects in the baseline

Two regression GameTests were added and run against v0.7.1 before implementing
fixes. Owner mining of an active city was allowed. A privileged copy of a city
obelisk's identity into an unbound state obelisk could found a second kingdom;
that invalid combined identity then caused reload validation failures in two later
shared-registry tests. These are reproducible failures, not inferred risks.

Inspection also confirmed that breaking/replacing an anchor gives the replacement
a fresh UUID, leaving the old record missing without a recovery command. City and
state removal already retained records/claims and only marked status missing.
Existing invitations were not canceled by that removal. Pistons were already
blocked. No baseline reproduction of lost citizens, roles, claims or invitations
was established.

### Risks investigated separately

Chunk NBT and SavedData are separate disk writes. Power loss can preserve different
sides of a transfer; the tests simulate these mismatches, not an actual power
failure or full process restart. Arbitrary third-party world editors can copy or
remove blocks outside normal hooks. Unknown world copies cannot be located safely
without a world scan; copied/stale anchors are refused for founding and cannot
become a second operational anchor. Ambiguous registry ownership is never repaired
automatically. Unreadable SavedData still fails closed.

## Owner workflow

Use **Relocate obelisk** in your existing City or Kingdom management folio. Enter
an empty block's X/Y/Z coordinates, validate, then explicitly confirm. Feedback is
available in English and Ukrainian. No Atlas redesign is included.

The equivalent commands are:

```text
/nations relocate city <x> <y> <z>
/nations relocate state <x> <y> <z>
/nations relocate confirm <token>
```

The selection message includes a clickable confirmation. `/reigns` redirects to
the same commands and authorization. There are no permission or Creative bypasses.

- Only the city owner may move a city anchor. A state anchor requires both its
  founder and capital city's owner. Officers and citizens cannot relocate.
- The destination must be air with no block entity or registered anchor, in a
  loaded chunk inside the world bounds/border and within six blocks of the player.
  Stand near the destination before opening the menu; no remote chunk loading is
  performed by validation. A preplaced obelisk is a conflict; no item is required.
- Source and destination must be in the same dimension. Both claims must belong
  to the same city. Missing/foreign capital ownership and historical multiple
  domains reject pending administrator review.
- State anchors and their capital city's anchor remain in the existing state
  capital chunk. This is physical movement within the capital, not a general
  capital relocation feature. Native city and state blocks remain distinct.
  Historical combined anchors move both saved references together.
- Selections expire after **30 elapsed seconds**, are tied to the player entity
  and session, and are single-use. Confirmation consumes invalid/expired tokens.
  A live selection cannot be replaced or extended. Source/destination reservations
  prevent competing previews. Closing the form leaves its reservation until expiry.
- Confirm rechecks permissions, current records, dimension, proximity, claims,
  source block type/UUID/binding and destination. It prepares the destination
  and removes the original without drops before updating saved references.
  Destination validation/creation failure preserves the operational source.
- No items, new city/kingdom records or claim changes are produced. Existing
  citizenship and affiliation invitations keep their original expiry and rules.

## Destruction and explicit recovery

| Action | Behavior |
| --- | --- |
| Survival mining / Creative removal | Active canonical positions reject the server break event, regardless of owner role and missing/invalid block entity. |
| Explosions | Active obelisk blocks ignore explosion destruction and drops. |
| Pistons | Both types retain `PushReaction.BLOCK`. |
| Relocation | Service transfers the block without an item drop or inventory change. |
| `/setblock`, `/fill`, external administrative edits | May remove an anchor; city/state, membership, roles, claims and affiliation remain. Bound/registered anchor drops are suppressed. |
| Missing/wrong block entity or copied UUID | Normal relocation refuses it; no silent adoption of a conflicting binding. |
| Clean restart | Saved positions and identities resolve normally; unconfirmed requests are discarded. |
| Interrupted save | SavedData's canonical position remains authoritative; stale copies cannot found a city/state. Explicit recovery is required if that position is missing. |

An administrator with permission level **3** can inspect `/nations admin settlements`,
`/nations admin settlement <cityUUID>` or `/nations admin info <kingdomUUID>`, then run:

```text
/nations admin recover city <cityUUID>
/nations admin recover state <kingdomUUID>
```

Recovery restores only the saved canonical position, dimension and anchor UUID.
It does not move claims or choose a new capital. Load the canonical chunk first.
The target must be air or the correct obelisk type with a missing entity, an
unbound/unregistered identity, or the exact expected identity/binding. Recovery
refuses solid replacements, foreign bindings, another registered identity,
canonical position collisions, ownership conflicts and multiple historical domains.
Conflicts require explicit administrator investigation; commands do not guess.
An exact expected identity with a missing association can be rebound as before.

Recovery is repeatable and audited with executor, target and result. To clear a
known stale noncanonical copy after reviewing a crash, an administrator may remove
that block with `/setblock`; its binding cannot found a new city/state. Do not copy
NBT from another settlement or edit registry UUIDs/claims to force recovery.

## Persistence and compatibility

The existing `data/project-reigns-kingdoms.dat` remains **schema 5**. No migration,
new data file or persistent relocation transaction is introduced. Existing NBT
keys, UUIDs, citizens/roles, claims, affiliation, creation times, names, colors,
government, registry IDs, deletion tombstones, key bindings and old packet layouts
remain. Two additive `regnum-nations:obelisk_*` channels serve the new form;
clients and servers should run matching versions. No ticking or world scan is added.

SavedData updates both references for a historical combined anchor in one save.
It cannot make that file and Minecraft chunk files an atomic disk transaction.
After an interrupted save, inspect and recover the **persisted** position; it may
be the old or new location depending on which writes completed. Recovery never
silently adopts the other copy. Back up the whole world/config before upgrading
and restore the backup for rollback if operational state is uncertain.

## Verification

`ObeliskIntegrityTests` holds the two initially failing regressions.
`ObeliskRelocationTests` covers successful city movement; outsider/officer/Creative
rejection; invalid/changed destinations; cross-dimension selection and confirmation;
duplicate bindings/types; citizens, roles and pending citizenship invitations;
claims; affiliation and pending state invitations; native state and legacy shared
anchors; capital chunk restrictions; actual player breaking, explosion handling,
piston policy and drops; SavedData disk reload and recreated chunk entity;
expired/replayed/forged/disconnected/concurrent requests; existing city and kingdom
founding; validated administrator recovery and mismatched save recovery.

Run `./gradlew build --no-daemon --console=plain`, which includes all required
headless server GameTests and kingdom-name checks. Test worlds reset only under
`build/run/gameTest/world`; player worlds under `run/` are untouched.

Manual checks still required: rendered form at supported GUI scales in both
languages, real clients receiving packets, live multiplayer, actual chunk cycling,
clean full process restart and recovery on a disposable copy of an existing world.
No manual multiplayer or full process-restart test is claimed by automated tests.

Validation performed for this milestone: `./gradlew build --no-daemon --console=plain`
passed all **96 required GameTests** (72 existing, two integrity regressions and
22 relocation tests), including the kingdom-name rule checks. The packaged
`regnum-nations-0.8.0.jar` passed `tools/verify_mod_jar.py`; version metadata and
new relocation classes were also checked. Locale key sets, translation references,
format placeholders and `git diff --check` passed. Expected invalid-NBT diagnostics
appear in negative tests. No manual gameplay, rendered UI or live multiplayer
validation was performed.
