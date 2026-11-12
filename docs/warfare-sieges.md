# Warfare, sieges and obelisk defense (v0.12)

This milestone builds directly on PR #21 (`feat/diplomacy-interstate-relations`).
It does not depend on Territory & Border Management and does not add political claims.
Minecraft 1.21.1, Fabric, Java 21 and existing dependency versions are retained.

## Playing a siege

Open the Nations folio, choose Diplomacy, then Warfare. The five pages show active
wars, eligible cities, objectives, sieges and completed history. Choose an enemy
city and plan its capture, then select the objective and declare its siege.
A defending government can instead plan defense of its own city against a current
war opponent; that opponent can declare a siege using the same objective. There
can be only one unfinished objective per city, across all wars and objective types.
A defense plan does not grant immunity from siege declaration.

Sieges require a current diplomatic war and a loaded, correctly bound canonical
city obelisk. The current owner, sovereign, city UUID, war UUID, dimension,
anchor position, obelisk UUID and center claim owner are revalidated. An existing
unfinished objective blocks ordinary obelisk relocation, including confirmations
of a relocation preview made before the objective existed.

Both kingdoms receive declaration, activation and outcome messages. During
preparation neither side can change capture progress. During an active siege,
survival/adventure players sneak within six blocks of the city obelisk, with line
of sight, to channel capture or repair. Current server membership determines the
side; no client packet supplies participation or progress. Creative players and
spectators cannot channel. Player count does not multiply the capture rate.

- Attackers alone add one capture second per twenty server ticks.
- Channeling players from both sides contest the objective and pause capture.
- Defenders alone repair one capture second per twenty server ticks.
- After any capture progress has occurred, fully repairing and continuously
  defending for the configured hold duration wins for the defenders. Leaving,
  disconnecting, contesting, unloading or restarting interrupts the defense hold.
- Reaching the capture goal wins for the attackers. Withdrawing an active or
  preparing siege awards defender victory. Reaching the absolute deadline records
  a distinct timeout result.

Players from either kingdom within 24 blocks see a server boss bar. The folio
shows capture and defense progress, preparation/deadline countdowns, anchor status,
current channeler counts and up to sixteen channeler names. Live siege views
refresh every two seconds. Other views refresh on demand. All ordinary block,
item, explosion and territorial protections from PR #21 remain in force; declaring
war or siege grants no building or block-breaking exception.

Preparation and deadline times use wall-clock timestamps and continue while the
server is stopped. Capture uses server steps, pauses while unloaded/offline, and
never catches up missed seconds. Reconnecting and entering the area requires no
registration; current player entities are sampled once per second using a spatial
query around each siege. No repeating full-server player scan is added. At most
64 unfinished objectives can exist, and completed history is excluded from the
periodic objective loop.

## Objectives, outcomes and conquest safety

Capture-city, defend-city and negotiated-peace objectives have stable UUIDs and
retain their war, attacking/defending kingdom and target-city references. Every
record stores its state, start and completion timestamps. Negotiated peace is
fulfilled only when the existing diplomacy service accepts a peace proposal for
that exact war; it does not compel the other government to accept. Peace also
terminates that war's unfinished sieges immediately. Other war termination,
dissolution, deletion, owner/sovereign/anchor changes and center-claim conflicts
end objectives safely, with no conquest award.

Missing or replaced loaded obelisks invalidate a siege. Unloaded chunks pause
capture without forcing a chunk load. Neither a copied block nor an obelisk at a
new location can replace the recorded objective. Existing administrator obelisk
recovery restores the same canonical identity and never initializes siege progress.

Attacker victories create a **pending conquest result** in the completed objective.
They do not change the city owner, sovereignty, memberships, claims or Economy
accounts. Existing city ownership transfer is a municipal succession operation;
it is not a verified atomic conquest workflow. Applying conquest is deliberately
left for a later milestone. There is no automatic treasury transfer or seizure.

Terminal records are immutable and use the same objective UUID as the siege.
Completion and its pending conquest flag are one SavedData update, not independent
writes or externally replayed rewards. A saved terminal objective is never
completed again on restart. As with existing Nations data, an abrupt process or
storage failure can lose changes after the last Minecraft world save; this does
not introduce a separate transactional journal.

## Permissions and administration

| Capability | Ruler | Chancellor | Officer | Member |
| --- | --- | --- | --- | --- |
| View warfare | Yes | Yes | Yes | Yes |
| Declare or withdraw sieges | Yes | Yes | Yes | No |
| Manage objective plans | Yes | Yes | No | No |
| Negotiate peace | Yes | Yes | No | No |

Creating a negotiated-peace objective requires both objective-management and
peace-negotiation authority. Existing diplomatic peace proposals and acceptance
retain their current permissions. Every mutation rechecks the current server
role, kingdom, war and displayed revision. Duplicate or replayed requests cannot
restart an active/finished objective or reuse an old city snapshot.

An operator with permission level 3 can run:

```text
/nations warfare cancel <objective UUID>
```

The `/reigns` alias uses the same command and service checks. Governments can
cancel their own unstarted plans; only operators can administratively cancel a
live siege. The attacker can withdraw instead. The objective UUID appears in the
folio detail view. All warfare presentation is localized in English and Ukrainian.

## Configuration and persistence

`config/regnum-nations-warfare.properties` is created with these defaults:

| Key | Default | Accepted range (seconds) |
| --- | --- | --- |
| `preparationSeconds` | 300 | 0–86400 |
| `siegeDurationSeconds` | 1800 | 30–86400 |
| `captureSeconds` | 120 | 1–86400 |
| `defenseHoldSeconds` | 30 | 1–86400 |

Configuration loads at startup. Capture and defense goals are snapshotted when an
objective is created; preparation and end deadlines are fixed at siege declaration.
Configuration changes cannot reset an existing objective or extend its deadline.

The existing `project-reigns-kingdoms.dat` remains at **schema 9**. A new optional
`Warfare` compound has its own format version 1 and objective collection. Old saves
load with no objectives. Existing diplomacy, Economy policy, ownership and claim
fields retain their meanings. Unknown warfare root/record fields are retained;
malformed or unknown warfare formats disable warfare and preserve the exact
original warfare NBT without disabling unrelated Nations data. Existing unreadable
whole-registry handling also remains intact. New warfare channels are additive;
matching client/server versions are required.

Back up the stopped world before upgrading. Older schema-9 binaries retain the
unknown extension but do not advance or reconcile sieges. Restore a pre-upgrade
backup for a gameplay rollback; do not expect an older binary to manage active
warfare state.

## Validation and limitations

Implementation-first verification uses the production build with checks/tests
excluded. No automated tests, GameTests, temporary scripts or audit artifacts are
added. Runtime multiplayer participation, server save/restart behavior, chunk
unload/reload, client layouts, notifications and live Economy coexistence still
need manual gameplay verification. Dedicated-server startup was not exercised.

No NPC armies, siege machines, artillery, reparations, sanctions, automatic
ownership transfer, treasury seizure, territory-management UI or matchmaking are
implemented. Siege balancing and the fixed capture/visibility radii are initial
milestone choices, not an expanded territorial warfare model.
