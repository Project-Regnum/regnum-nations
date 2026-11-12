# Regnum Nations v0.8.5 integration audit

## Remote and workspace audit (2026-11-12)

Fetched origin with pruning and fetched refs/pull/{15,14,13}/head before changes.
GitHub REST API confirms all three PRs are OPEN and unmerged. Its synthetic
merge_commit_sha fields are merge previews, not evidence of merged ancestry.

| Source | Audited head | Actual base | Status |
| --- | --- | --- | --- |
| origin/main | f14cd92ae63aeebd6619cf404ec5659966b39945 | — | latest fetched main |
| #15 protection | a87f62f3290212ac8d77719e7a41929f459eec09 | f14cd92 | one independent commit |
| #14 administration | 42929b15779008cc585c446b791ac04d0e979446 | f14cd92 | one independent commit |
| #13 diplomacy | f6cc91fef14b0ae6e7f42583019050af50141463 | f14cd92 | one independent commit |

No feature head is an ancestor of another or of main. Integrate #15, #14,
then #13 as requested; there is no dependency requiring reordering. Apply their
changes without committing intermediate inconsistent states, then record the
source hashes in the validated integration commit. Original PRs stay open.

The supplied working directory is clean on feat/kingdom-administration-v0.8.4.
Existing administration, diplomacy, protection and obelisk worktrees are left
untouched. Integration uses its own worktree at
/home/sanyacat/Documents/regnum-nations-integration-v0.8.5 and branch
integration/nations-v0.8.5 from origin/main. The unrelated tooltip-removal branch
70a26bf is not part of the three requested PRs. A later read-only audit of all
worktrees found uncommitted edits to art/obelisks/city_obelisk.bbmodel and
art/obelisks/kingdom_obelisk.bbmodel in the separate regnum-nations-obelisk-art
worktree. Those edits are untouched and excluded. Other original worktrees remain
clean.

## Overlapping changes

- `docs/rename-migration.md`
- `gradle.properties`
- `src/client/java/dev/sakhno/regnum/nations/client/RegnumNationsClient.java`
- `src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomAtlasView.java`
- `src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java`
- `src/gametest/java/dev/sakhno/regnum/nations/kingdom/CitizenshipTests.java`
- `src/gametest/java/dev/sakhno/regnum/nations/kingdom/CityRoleTests.java`
- `src/gametest/java/dev/sakhno/regnum/nations/kingdom/SovereigntyTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/sakhno/regnum/nations/RegnumNations.java`
- `src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomPermission.java`
- `src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomSavedData.java`
- `src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java`
- `src/main/resources/assets/regnum-nations/lang/en_us.json`
- `src/main/resources/assets/regnum-nations/lang/uk_ua.json`

The common/client boundaries are shared saved data, services, permissions,
network snapshots and registration on the server, and screen registration,
Kingdom navigation and both locales on the client. Protection is entirely
common/server code with no diplomacy dependency. Preserve every GameTest
entrypoint and both UI routes when resolving overlaps.

## Persistence audit and integration plan

Main writes schema 5; protection keeps schema 5. Administration and diplomacy
independently allocate schema 6. Administration adds Kingdom.Ruler, KingdomRoles
and CityInvitations and requires those fields for version 6; diplomacy adds the
Diplomacy list and has no Ruler. Naively merging the version-6 readers rejects
valid diplomacy saves or silently drops the other branch's records.

Implement schema 7 with additive migration from schemas 1–5, both schema-6
variants and combined fields. Founders become rulers only where no administration
state exists. Preserve stable IDs, historical founders, city roles, claims,
obelisk anchors, tombstones and the project-reigns-kingdoms filename. Validate
recognized fields, retain unrecognized extension fields where appropriate, and
retain unreadable NBT verbatim with mutations disabled. Test repeated migration,
branch-specific fixtures, corruption and actual saved-data disk reload.

Diplomacy currently authorizes the historical founder through its own helper;
administration resolves current ruler and government roles. Replace founder
checks with MANAGE_DIPLOMACY in the existing capability model: RULER and
CHANCELLOR may manage diplomacy; OFFICER and MEMBER may not. Recheck current
server authority for every action and invalidate stale invitations from revoked
representatives without granting any municipal/build permission.

## Tests, CI and packaging audit

Main registers 111 GameTests. #15 adds 37, #14 adds 16 and #13 adds 14;
inspect registrations and run the combined suite plus focused integration tests.
The ordinary test task has no sources. build/check runs kingdom-name rules and
Fabric headless GameTests. runGameTest resets only build/run/gameTest/world.
Existing tests cover legacy schemas, roles, disk reload, administration races,
diplomacy lifecycle/wire snapshots and vanilla territory mutation paths.

CI uses pinned actions, JDK 25, the existing Loom 1.19.4 and Minecraft 1.21.1
Mojang mappings; compilation targets Java 21. Do not change dependency versions.
Use an installed Java 21 launcher for rules and server tests while retaining
the required newer Gradle JVM. Record actual runtimes and counts after execution.

The packaging checker asserts that legacy item adapters point to block models.
Main already uses regnum-nations:item/{city,kingdom}_obelisk, a distinct static
inventory model alongside animated world models. Fix the stale expectation and
validate both the inventory and world/crystal model graph; retain compatibility,
license, locale, class isolation and resource checks.

## Validation and delivery

The combined clean build has executed all **193/193 required GameTests** on Java
21, plus **21 kingdom-name checks** (6 comparisons, 2 additional valid names and
13 invalid-name rejections). `test` correctly reports NO-SOURCE. The test breakdown
is 111 base tests + 37 protection + 16 administration + 14 diplomacy + 15 new
integration tests. The build log records `Verification runtime: Java 21` for both
rule and GameTest tasks. Gradle uses JDK 25; no dependency versions changed.

Initial integration test runs failed with three, then one, new-test fixture errors:
live-world directory pagination was unsuitable for locating an arbitrary pair;
one interleaved request selected an action absent from the original snapshot;
and the wilderness positive control landed in the neighboring fixture's claim.
The fixtures/requests were corrected. All original feature suites passed these
runs, and the corrected full suite passes. Corruption fixtures intentionally log
errors; these are expected test inputs, not unexpected runtime failures.

Native client checks initially found implicit tooltips on administration controls
inherited from RoyalButton. These are explicitly disabled on the new controls
and administration navigation button. The unrelated tooltip-removal branch is
not integrated. Final client, packaging and process-restart evidence follows below.

### Final executed checks

| Check | Result |
| --- | --- |
| `./gradlew clean build --no-daemon --console=plain` | PASS: 193/193 required GameTests, zero failed; 21 name-rule checks; common/client compilation and mod/sources packaging |
| `./gradlew runIntegrationLayoutTest --no-daemon --console=plain` | PASS: 312 native widget layouts on Java 21, English/Ukrainian, 320×240 / 640×360 / 1100×760 logical sizes |
| Client layout/snapshot cases | Kingdom/city folios, every administration/diplomacy/citizen tab, privileged/read-only states, confirmations, widget bounds/overlap, no new administration/diplomacy tooltips, invalidation and mismatched reply sequences |
| `python3 tools/verify_mod_jar.py` | PASS: metadata, licenses, dependency set, 469 locale keys, client isolation, compatibility adapters and model/texture references |
| Packaging negative controls | All 3 rejected: missing static item model, missing animated glow part, obsolete adapter parent |
| `python3 tools/validate_obelisk_assets.py` | PASS: 7 models, 10 textures, retained IDs, source exports, UVs/bounds/rotations |
| `python3 tools/validate_crystal_animation.py` | PASS: city 1,600 samples (clearance ≥0.3064), kingdom 1,200 samples (clearance ≥0.4808 model units) |
| Packaged bytecode | All classes have major version 65 (Java 21) |
| Localization | All 469 English/Ukrainian keys and indexed placeholder signatures match |
| Normal dedicated Fabric server | PASS: two Java 21 server processes, migration, save-all/stop, restart and restored data checks |
| `git diff --check` | PASS |

The client harness loads real Minecraft fonts, languages and widgets with synthetic
snapshots in a disposable hidden client. It checks geometry and snapshot behavior;
it does not render/capture every gameplay screen or simulate remote network clients.
It is opt-in and is not a headless CI requirement.

The dedicated smoke test copied the completed GameTest world into
`build/run/integrationServer/world`, replaced only that copy's kingdom registry
with the combined fixture encoded as schema 6 (no legacy diplomacy issuer), and
used a temporary ignored init script to select Java 21 and set **Loom's server run
configuration** to that disposable directory. The first bootstrap attempt only
set JavaExec.workingDir; Loom overrides that value, so it exited at the missing
EULA in the new integration worktree's default run directory without opening a
world. Correcting the Loom run directory allowed both actual test processes to
start, bind localhost only at 127.0.0.1:25589 and exit cleanly.

Process 1 executed `nations admin list`, inspected the capital settlement, renamed
kingdom 00000000-0000-0000-0000-000000000001 to `Restart Verified`, then ran
`save-all flush` and `stop`. Process 2 loaded that same saved world, listed kingdoms,
inspected the renamed kingdom, then flushed and stopped. After **each** process,
a stdlib NBT probe asserted schema 7, 2 kingdoms, 3 cities, 4 claims, 2 citizens,
1 CHANCELLOR assignment, 1 city invitation, 1 diplomacy invitation, retained
founder/ruler/obelisk IDs and tombstones, and migrated issuer UUID. The rename and
transferred ruler survived the process boundary. Fixture anchors are records,
not placed obelisks in that world; resolving them as missing is expected. Actual
player-led creation, transfer, alliances/hostility and protection are exercised by
GameTests, not claimed as manual actions in these two processes. No player worlds
in the original or other worktrees were opened or modified.

### Focused integration coverage

| Requested coverage | Executed automated evidence |
| --- | --- |
| Legacy migration | Existing schemas 1–3/name-role tests; new schemas 4/5 and both handwritten branch-v6 fixtures |
| Administration + diplomacy persistence | Combined-v6 normalization and actual compressed DimensionDataStorage save/load |
| Leadership followed by diplomacy | Transfer to citizen, negotiate/accept as new ruler, demote historical founder and reject their action |
| Role revocation | Chancellor → officer/member removes capability and issuer requests; promotion cannot revive old IDs |
| Alliance lifecycle | Existing 14 diplomacy tests retain invitation/accept/reject/cancel/expiry/replace/terminate/hostile/neutral cases |
| City membership | Detach/rejoin preserves owner, claim and obelisk IDs and revokes government roles/requests |
| Protection after administration | Transferred leader, demotion/citizenship removal and actual vanilla explosion finalization |
| Cross-claim protection | Foreign allied city access/machine denial, same-city and wilderness positive controls; original hardening suite |
| Restart/restoration | New combined disk restoration accepts saved request after reload; full process evidence below |
| Invalid/corrupt data | Existing malformed government/diplomacy tests and integration sector isolation/verbatim retention |
| Missing optional fields | Empty government/invitation/diplomacy defaults; present malformed lists rejected |
| Concurrent administration | Interleaved server snapshots reject a stale target-role assignment; existing confirmation/race tests |
| Client/server synchronization | Invalidation notifications and fresh permission/private-request wire snapshots; native client sequence checks |
| Unauthorized network intent | Forged codec-round-tripped requests, officer/outsider denial, shared replay gate and existing payload bounds tests |

Tests use synthetic authenticated ServerPlayer objects and actual service/network
codecs, not separate remote clients. Concurrent requests are interleaved on the
single owning server thread, matching Fabric's execution model; this does not
claim an external network load test.


## Final persistence contract

Schema **7** writes the same `data/project-reigns-kingdoms.dat`:
`Kingdoms` (including historical `Founder` and current `Ruler`), `Settlements`,
`Claims`, `DeletedStateAnchors`, `Citizens` (municipal role), `KingdomRoles`,
`CityInvitations` and `Diplomacy`. No IDs, filenames, registry entries, config
names, saved bindings or existing network layouts are renamed.

`KingdomSchema.migrate` copies input before normalization. The reader retains
historical schema-specific city/claim/citizenship migrations. Schemas 1–5 gain a
founder ruler if none was present. Schema-6 administration markers are any Ruler,
KingdomRoles or CityInvitations field. With those markers, valid ruler UUIDs are
required and transferred rulers survive. A diplomacy-only v6 save has no markers
and gains founder rulers. Both sets of fields may coexist. Version 7 requires
valid rulers. Optional absent government/city-invitation/diplomacy lists default
empty; malformed present lists are never interpreted as empty.

Each new diplomacy Invitation includes its player `Inviter`. Legacy requests
without it are attributed deterministically to the sender's persisted ruler;
this is the founder for a diplomacy-only v6 save. Pair revisions, request UUIDs,
absolute deadlines, agreements and hostility survive. Issuer revocation removes
only pending requests, with a deterministic changed revision. Migration/reload
does not consult a clock or generate random IDs. Serialized record collections
use explicit stable ordering, including dimension/chunk order for claims. Expiration remains an on-demand
runtime operation. Repeated migration and v7 save/load reach an identical result.

Unknown fields are copied at root and on surviving identity-matched kingdoms,
cities, claims, citizens, government roles, tombstones, city invitations, diplomacy
pairs, nested requests and hostility records. Known optional fields and deleted
records are never resurrected. Orphan political references retain the existing
conservative cleanup/detachment behavior. Malformed government or an unsupported
schema retains the whole original NBT and disables mutations and terrain access;
malformed diplomacy alone retains that exact section and disables diplomacy.

Unavoidable limits: the original diplomacy-v6 format did not record invitation
issuers, so an earlier issuer cannot be reconstructed. A field already discarded
by switching older feature binaries cannot be recovered from the surviving save.
Divergent copies of an independently edited world are not automatically merged.
Do not run old feature/main binaries against a v7 world. Back up the whole world
and config with the server stopped; downgrade by restoring that backup.

## Final permission and protection contract

All political decisions use `KingdomPermissions` against current server data.
RULER/CHANCELLOR have MANAGE_DIPLOMACY; OFFICER/MEMBER/outsiders have none. Only
the current ruler may transfer leadership, using the existing fresh one-use
confirmation. The old ruler becomes chancellor while still a participant; explicit
demotion/departure removes authority immediately. A retained historical ruler seat
takes priority over foreign municipal citizenship for political management; that
choice does not change municipal membership or permissions. City departure/citizen removal
prunes government assignments and their pending requests. Existing alliances and
hostility are kingdom contracts and persist independently of a representative.
City owner/officer/citizen permissions remain separate.

Protection source and all 30 common Mixin registrations match PR #15 exactly.
The original 37 tests exercise explosions, complete piston/slime/honey graphs,
fluids, dispenser secondary writes, fire/ignition, projectiles, mob terrain writes,
falling entities, boundary support loss and different-city/unclaimed boundaries.
Political role changes and alliance status cannot bypass ClaimService or grant
foreign BUILD/INTERACT/machine rights. No new terrain ticking, chunk loads or
protection packets were introduced. Political/membership changes use the existing
invalidation payload; only currently open views request a new throttled snapshot.

## Manual multiplayer checklist and known limitations

These scenarios remain **unverified with actual remote players**:

1. Upgrade a copy of a real pre-release player world and compare all city/kingdom
   UUIDs, roles, citizens, obelisks, claims, government and diplomacy after restart.
2. On a dedicated server with matching JARs and two or more non-operator clients,
   create kingdoms/cities, invite/accept/leave/remove cities and check retained
   municipal ownership/claims after every transition.
3. Assign all government ranks and municipal roles independently. Transfer to an
   offline and online participant. Demote the old ruler; attempt replayed/stale
   government and diplomacy actions before and after reconnect.
4. Have representatives send crossed proposals and concurrent accept/cancel/role
   changes. Revoke an issuer while the recipient confirmation is open; ensure
   rejection, no revival on promotion and refreshed private-request visibility.
5. Accept an alliance, declare hostility from each side, withdraw each declaration,
   terminate/reform an alliance, and verify no allied building/interaction rights.
6. Exercise explosions, slime/honey/sticky pistons, fluids, dispensers, fire,
   projectiles, mobs and boundary attachments across two allied cities and
   wilderness. Recheck after leadership/city/citizenship changes and restart.
7. Inspect rendered English/Ukrainian screens at multiple GUI scales: administration,
   citizen/city management, diplomacy, inbox/outbox, permission denial, long names,
   leadership confirmations, keyboard navigation, close/back and hover behavior.
8. Disconnect during mutations, change dimensions, reconnect, and stop/restart with
   pending requests. Verify current server state rather than automatically replaying
   timed-out actions. Test the configured invitation deadline across downtime.

Native widget checks do not establish visual quality, real network ordering under
latency, or human navigation usability. Mixed client/server versions are unsupported
by this evidence. Protection retains #15's documented limitations: privileged or
third-party writes are not universally intercepted; hopper/item-frame inventory
security and PvP are out of scope; protected boundary attachments may remain
unsupported; dispenser growth may truncate; in-flight piston claim changes are
not transactional. No new gameplay features or dependencies were introduced.


## Delivery scope

The integration branch is `integration/nations-v0.8.5`, targeting `main`, with PR
title `chore: integrate and stabilize Nations v0.8.5`. The validated integration
commit applies all three source commits in the requested order and records their
full hashes; their original commits/branches and PR status are not rewritten.
The integration PR functionally supersedes PRs #15, #14 and #13 for the release,
while leaving those PRs open for maintainer disposition. No PR is automatically
merged or closed. A final fetch confirmed main is still f14cd92. No known critical
integration regression remains in the executed checks. Remote-player gameplay,
rendered-screen usability and production-world upgrade scenarios remain explicitly
unverified above.
