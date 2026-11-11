# Cities, sovereignty and citizenship

For the current v0.6.1 visual standard and clickable dissolution UX, see
[Royal Cartography](ui-design-system.md). v0.7.0 extends the v0.6 city model with
player citizenship. v0.7.1 adds fixed city roles and named permissions below.
The existing affiliation and ownership workflows remain in effect.

## v0.7.1 — City roles and permissions

This milestone extends the existing citizenship system. Each non-owner membership
now contains one assigned role: `CITIZEN` by default or `OFFICER` when the owner
promotes that citizen. `OWNER` is derived from the city's existing `ownerId`, never
stored or assigned. Outsiders have no role or role record. There are no new cities,
secondary membership tables, configurable role permissions or role editors.

`CityPermissions` evaluates the authenticated player's current municipal role
against a named `CityPermission`. Owner permissions are immutable and include all
five actions; the other two roles have fixed defaults:

| Permission | Owner | Officer | Citizen | Outsider |
| --- | --- | --- | --- | --- |
| `BUILD` (place/break, buckets) | Yes | Yes | Yes | No |
| `INTERACT` (protected block use) | Yes | Yes | Yes | No |
| `MANAGE_CLAIMS` | Yes | No | No | No |
| `INVITE` | Yes | Yes | No | No |
| `MANAGE_MEMBERS` (kick/assign roles) | Yes | No | No | No |

The permission vocabulary and policy resolver are the extension point for future
configurable member permissions. There is no configuration surface in v0.7.1 and
ownership is not editable through this policy. Existing claim adjacency, dimension,
position, capital and legacy-owner repair checks still apply. Kingdom leadership
and affiliation never grant municipal permissions. Verified obelisk viewing stays
public; the existing environment/automation protection limits remain.

New commands, also available through `/reigns citizen`:

| Command | Behavior |
| --- | --- |
| `/nations citizen role <player> <role>` | Owner assigns `officer` or `citizen` to an existing citizen of their city. Role names are case-insensitive. Accepts online/local citizen names or an offline player's listed UUID. |
| `/nations citizen roles` | City member reads the three role definitions. |
| `/nations citizen permissions` | City member reads all five effective permissions for their current role. |

`citizen list` now shows a role beside every UUID, including the implicit owner.
Requests to demote/reassign an owner or grant `OWNER` reject without changing saved
state. Officers cannot kick, modify claims or assign roles, including promoting
themselves. An owner's reassignment of a role to its existing value is a no-op.

All v0.7.0 commands and invitation safeguards remain. Owners **and officers** can
invite online players; acceptance always creates `CITIZEN`, never `OFFICER`.
Invitations still expire after 60 elapsed seconds, are single-use and session-bound,
and obey the existing 500 ms sender throttle. Acceptance rechecks the inviter's
current `INVITE` permission. Demotion, leaving or kicking an officer revokes their
issued invitations, so re-promotion does not revive them. Disconnects and server
restarts still discard temporary invitations while preserving membership/roles.

### Schema 5 upgrade and rollback

The retained `data/project-reigns-kingdoms.dat` now writes **schema 5**. Each existing
`Citizens` compound keeps `Player` and `Settlement` UUIDs and gains a `Role` string
(`CITIZEN` or `OFFICER`). In memory this is one membership record containing city
and role, rather than a separate role-assignment table. Leaving, kicking and city
deletion remove that record; rejoining defaults to `CITIZEN` again.

Schema 4 loads missing roles as `CITIZEN`, keeps ownership implicit and is marked
dirty for a schema-5 save. Earlier schemas retain their existing city/citizenship
migrations, then write schema 5. Existing UUIDs, ownership, affiliations, claims,
anchor identities, tombstones, file paths, registry IDs and wire formats remain.
A role field present in an older schema is validated rather than silently discarded.
Schema 5 requires a known non-owner role on every explicit membership. Missing,
malformed, unknown or stored `OWNER` roles preserve the original NBT and disable
mutations/build access, just like conflicting membership records. Schema-5 reload
is idempotent.

Back up the entire world before upgrading. v0.7.0 does not understand schema 5;
rollback requires restoring the pre-upgrade world backup and matching v0.7.0 JARs
on server and clients. Editing `Version` to downgrade can lose role information
and is unsupported. SavedData and chunk NBT remain separate, non-atomic disk writes.

### Manual checklist for v0.7.0 and v0.7.1

These are checks to perform on a disposable dedicated world, not claims of completed
manual multiplayer testing:

1. **Citizenship baseline:** A founds a city and invites B; B accepts. Both can
   place/break and use a chest. A non-citizen remains blocked. Decline, expire and
   replay invitations; check that none grants membership. A can manage claims;
   ordinary citizen B cannot. A cannot leave or kick themselves.
2. **Roles:** A assigns B `officer`. List shows `OWNER`/`OFFICER`; B's permissions
   show build, interact and invite only. B invites C, who accepts as `CITIZEN`.
   B cannot kick C, claim/unclaim or change any role. C cannot promote themselves.
3. **Revocation/cleanup:** B invites another player; A demotes B before acceptance.
   Acceptance rejects, including after re-promotion. Kick/leave and rejoin B;
   their old officer role must not return. Try role assignment by offline UUID.
4. **Persistence/affiliation:** Save, stop, restart and reconnect; check all roles,
   permissions and building rights. Affiliate the city to a kingdom, then dissolve
   that kingdom; municipal roles remain. Verify outsider public obelisk inspection
   still works and does not grant building rights.

The full Gradle build runs both the existing citizenship/kingdom regression suite
and new role GameTests. The new cases cover the permission matrix, owner immutability,
officer invitations and revocation, unauthorized roles, block protection, claims,
actual SavedData disk reload, schema-4 migration, role cleanup, invalid NBT, affiliation
and registered commands/role output. Full process restart, live multi-client play,
and rendered UI are manual checks; automated disk reload is not a process restart.

Validation for v0.7.1: `./gradlew build --no-daemon --console=plain` passed all
**72 required GameTests** (13 new role tests) and kingdom-name checks.
`python3 tools/verify_mod_jar.py build/libs/regnum-nations-0.7.1.jar` also passed.
The initial run failed two new tests due to synthetic-player sound delivery and
an unordered NBT comparison; both test issues were fixed before the passing run.
Invalid-role/corruption tests deliberately log errors while checking NBT preservation.

## v0.7.0 — City citizenship (baseline)

Ownership, citizenship and kingdom affiliation are separate. The owner is an
implicit citizen and retains all existing authority. Other players join a city as
citizens; joining grants no ownership or kingdom leadership. A kingdom leader has
no rights in another owner's city merely because it belongs to that kingdom.
Players can belong to one city, including a city they own. A citizen must leave
before joining or founding another city; an owner cannot leave or kick themselves.

`CitizenshipService` validates authenticated server players and performs all actions
on the server thread. `KingdomSavedData` owns the membership records; there is no
second settlement registry or client-authoritative membership state. `CityPermissions`
provides the initial policy used by citizenship administration and `ClaimService`.
Existing `TerritoryProtection` events, placement and bucket mixins continue through
`ClaimService.canBuild`, so accepted citizens receive the same block access as owners.

| Command | Behavior |
| --- | --- |
| `/nations citizen invite <player>` | Owner invites one online player who does not belong to a city. |
| `/nations citizen accept <cityUUID>` | Recipient consumes their invitation and joins that city. |
| `/nations citizen decline <cityUUID>` | Recipient consumes their invitation without joining. |
| `/nations citizen leave` | Non-owner leaves only their current city. |
| `/nations citizen list` | Shows the current city's citizens, including the owner, with UUIDs and locally known names. |
| `/nations citizen kick <player>` | Owner removes a citizen by online name, locally cached citizen name, or UUID. UUIDs work for offline citizens. |

`/nations citizen` prints command help. `/reigns citizen ...` shares the same handlers
and checks. UUIDs in invitation messages are **city UUIDs**. `/nations invite` and
`/nations join <kingdomUUID>` remain separate 30-second city-to-kingdom affiliation
commands; neither can accept a citizenship invitation.

Citizenship invitations last **60 elapsed seconds**, measured with a monotonic clock
independently of server tick rate. They are temporary and bound to the recipient's
player session. Accept/decline attempts consume that recipient's invitation to the
specified city; another player's attempts cannot consume it. Acceptance rechecks
membership, city existence, city anchor identity and the inviter's current ownership.
A successful acceptance clears the player's other invitations. Duplicate invitations
replace the same pending entry and never duplicate membership. Owners may send an
invitation at most once per 500 ms. Disconnecting either participant revokes their
pending invitations; accepted citizenship survives disconnects and restarts.

| Action | Owner | Citizen | Outsider |
| --- | --- | --- | --- |
| Place/build, break, use protected blocks in city claims | Yes | Yes | No |
| Claim/unclaim city territory | Yes | No | No |
| Invite/kick citizens | Yes | No | No |
| Manage ownership or kingdom leadership | Existing behavior | No | No |

Verified obelisk viewing stays public. Existing territory limitations, capital
reservations and administrative commands remain. Joining, leaving and being kicked
change building rights immediately. Kingdom affiliation changes and kingdom deletion
never remove city citizens. Breaking an obelisk still marks its anchor missing;
it does **not** delete the city, its claims or membership.

### Persistence and migration

Schema **4** adds `Citizens`, a list of `{Player: UUID, Settlement: UUID}` compounds,
to the existing `data/project-reigns-kingdoms.dat`. Only non-owner membership is
stored. The player UUID is the unique key; citizen lists are derived from this table
plus the city's owner. Usernames are presentation/target-resolution hints only.
Existing IDs, keys, kingdoms, cities, claims and deletion tombstones remain intact.
No channels or client payload layouts change.

Versions 1/2 still migrate through the existing settlement conversion. Version 3
loads without explicit citizen records; owners immediately retain implicit citizenship.
All older valid versions are marked dirty and next save as version 4. Reloading
version 4 is idempotent. If a citizenship field is present in an older-version tag,
it is also validated and retained. Duplicate player records (even identical ones),
owner/explicit-member conflicts, missing cities, malformed UUIDs/lists and missing
version-4 membership lists preserve the original NBT and disable mutations/build
access using the existing fail-closed behavior. No conflicting record is selected
or overwritten silently.

Historical multiple-city owners remain the existing explicit repair exception:
all ownership records and owner build privileges are preserved, but ambiguous
citizenship actions and claim management report `DOMAIN_REPAIR`. No new operation
can create another such conflict. The internal city-deletion operation removes all
citizenship and claims for that city in the same server-thread operation and pending
invitations subsequently fail validation. It rejects capital-city deletion until
its state is dissolved. There is no new player/admin city-deletion or ownership
transfer command in this release.

Back up the whole world before upgrading. Chunk NBT and SavedData remain separate
writes rather than a crash-atomic transaction. To roll back to v0.6, restore the
pre-upgrade backup: v0.6 cannot safely interpret schema 4.

### Verification and limitations

The citizenship GameTests cover invitations/consent, exact expiration, replay,
duplicates, recipient/session isolation, unauthorized actions, competing joins,
founding restrictions, leaving/offline kicks, disk persistence, schemas 1–3,
malformed/conflicting NBT, city deletion, affiliation isolation, registered commands
and aliases, and actual block protection/placement hooks. The full build also runs
the pre-existing kingdom, claim, public-obelisk and compatibility tests.

Validation: `./gradlew build --no-daemon --console=plain` passed all **59 GameTests**
(17 citizenship tests) and kingdom-name checks. Packaged JAR verification also passed.
The first full run failed one command test because the headless server lacks a
profile cache; local name/UUID resolution fixed it before the passing run. Corruption
tests intentionally emit invalid-data error logs while asserting preservation.

Manual acceptance on a disposable dedicated world:

1. Owner A founds a city; non-owner B cannot build in its claims. A runs
   `/nations citizen invite B`; B runs the acceptance command from the invitation.
2. Both players place/break blocks and use a chest. A third, non-citizen player
   remains denied. B cannot claim/unclaim, invite, or kick; A can manage claims.
3. Stop and restart the server, reconnect both players, and check citizen lists
   and building rights. This validates an actual process restart in addition to
   the automated SavedData disk reload.
4. A kicks B (also try B offline using their listed UUID). B loses access. Repeat
   joining and leaving; confirm expired/declined invitations cannot be replayed.

Citizenship is command-driven; existing atlas/menu ownership markers still show
ownership. No citizenship GUI, role editor, ownership transfer, diplomacy, taxes or
kingdom-wide citizenship is added in the v0.7.0 baseline. The fixed roles and
named permissions introduced by v0.7.1 are described above; configurable role
policies remain future work. Live two-client play,
full Minecraft process restart of an upgraded player world and rendered UI checks
remain manual validation tasks; automated disk reload is not a full process restart.

## Existing city and sovereignty architecture (v0.6)

Implementation architecture:

1. SavedData schema 3 contains settlements, kingdoms, one settlement-owned claim
   table and deleted-state-anchor tombstones. No secondary authoritative claim table.
2. Legacy capitals become combined city/state anchors. Their capital settlement
   uses the legacy kingdom UUID in the separate settlement identity namespace,
   retaining all claim identifiers. Native city/kingdom identities are independent.
3. City obelisks found independent city-states. A directly adjacent state obelisk
   in the same chunk elevates an existing city without another domain slot.
   The old /nations found path remains a combined city/kingdom compatibility flow.
4. Claim management follows personal settlement ownership; v0.7 also grants citizens
   block access. Sovereignty supplies political color/identity only. Invitations and explicit acceptance are
   the minimal mechanism for different owners to affiliate their cities.
5. Confirmed administrative deletion preserves cities/claims, detaches sovereignty
   and tombstones only the exact former state binding. Physical blocks survive.
6. UI/network changes carry city and kingdom separately, selected palette indexes,
   settlement inspection and political colors. Existing bounded map behavior remains.

Conflicting legacy ownership is preserved. Historical multiple domains are retained
but claim management is blocked for those owners pending administrative repair;
no additional domain can be founded. Invalid schemas remain preserved and read-only.

## Player workflow and obelisks

The existing `project-reigns:kingdom_obelisk` is displayed as **State Obelisk**;
its block/item/entity registration IDs, existing `ObeliskId` and `KingdomId` NBT
remain compatible. **City Obelisk** is a separate block/item/entity registration
`project-reigns:city_obelisk`, with its own placed `ObeliskId` and `SettlementId`.
Both are available in the Functional Blocks Creative tab; no recipe is added.
The city uses a distinct dark-prismarine appearance. Drops do not retain bindings.

1. Place and right-click a City Obelisk. Enter a city name, select one of ten named
   palette colors, inspect its live map preview and explicitly confirm. The new
   independent **city-state** owns its capital chunk. Cancel does nothing.
2. One player may create/own one city/domain. That slot remains occupied if the city
   joins a kingdom or its physical obelisk is destroyed. Founding a kingdom does
   not consume another slot.
3. To establish a kingdom, place a State Obelisk **face-adjacent** to your existing
   city obelisk, in the **same chunk and dimension**. A vertical stack or a horizontal
   neighbor works; two blocks never occupy one BlockPos. Right-click the state
   anchor, choose a kingdom name and political color, and confirm. The original
   city UUID/claims/owner remain; it becomes the capital settlement.
4. `/nations found <name>` remains the original state-founding confirmation command,
   using a deterministic default palette choice. For compatibility, selecting a
   State Obelisk without already owning a city allows the original combined
   city-and-kingdom founding flow (including its GUI); it creates exactly one
   capital city/domain. New players are encouraged to start with a City Obelisk.
5. A kingdom leader can `/nations invite <player>` to invite an independent city
   owner. The recipient explicitly accepts with `/nations join <kingdomUUID>`
   within 30 seconds. No ownership/claim UUID changes; the city adopts the kingdom
   color while preserving its original independent color. Invitations are temporary,
   recipient-session-bound and consumed on acceptance attempts. No join without
   consent, automatic land transfer, role editor or diplomacy system is provided.

Founding selections last 30 seconds, are tied to the player entity and selected
placed identity, and require the same dimension and a six-block distance. Failed
confirmation consumes the selection; close and click the obelisk again. No client
flags decide ownership or permissions. Colors are indexes in a fixed ten-color
palette; arbitrary client RGB values reject server-side.

Missing/removed/replaced obelisks preserve records, names, domain slots, claims and
capital reservation. A replacement UUID never inherits a previous binding. Native
city and state statuses are independent: losing the state does not delete its city.
A missing city anchor requires future repair/relocation; no implicit adoption is
performed. Legacy combined anchors retain both historical functions. If a kingdom
is deleted, ordinary right-click opens the surviving city; sneak-right-click the
legacy state anchor **with empty hands** to select it for establishing a new state.

## Persistence and migration

`data/project-reigns-kingdoms.dat` remains the one authoritative SavedData file.
Schema 3 introduced the following records, retained in schema 4 alongside `Citizens`:

- **Kingdoms:** existing stable UUID, name, founder, state location/dimension/obelisk
  UUID, creation time/status; plus capital settlement UUID, validated palette index
  and an `unspecified` government placeholder.
- **Settlements:** stable UUID/name, separate founder/owner UUIDs, city anchor
  identity/location/dimension/time/status, optional sovereign kingdom UUID, original
  city palette index and legacy-combined-anchor flag. Claimed chunks are derived
  from the single claim table, not copied into another authoritative registry.
- **Claims:** dimension/chunk → settlement UUID.
- **DeletedStateAnchors:** deleted kingdom/state identities for exact lazy binding
  cleanup after unloaded chunks or separately saved chunk NBT return.

Version 1 and 2 loads migrate explicitly. Each legacy kingdom receives exactly one
capital settlement; that settlement uses the historical kingdom UUID in the distinct
settlement namespace, preserving every existing claim identifier. Kingdom identity,
name, founder, state identity/location and claims remain. Native kingdoms created
from an existing city get a fresh independent political UUID and reference that
city. UUID sharing for migrated combined entities is a compatibility convention,
not circular ownership: settlements reference sovereign kingdoms; kingdoms reference
one capital city, and only settlements own claims.

Missing political colors use a deterministic UUID-to-palette fallback. Missing
capital history is retained. Capital claims are reconciled with put-if-absent, in
creation-time/UUID order, without loading chunks or overwriting an existing owner.
Conflicting capitals remain recorded and protected; their owners receive a repair
notice and cannot mutate claims. Existing non-capital claims are never discarded.

Historical multiple kingdoms/domains for one founder are all retained, with stable
IDs and affiliations. All extra historical holdings remain protected for that
owner, but new founding and claim management are blocked for the ambiguous owner.
This is an explicit grandfathered repair case, not permission to create a second
new domain. `/nations admin settlements` and `settlement <UUID>` identify the records.
Conflicting capitals and historical duplicate owners require deliberate expert
administrative repair of a **whole-world backup**, or a future transfer/relocation
feature. This milestone does not guess a new owner, delete the extra city, move an
anchor, expose an unsafe repair-all command or silently pick which land to lose.

Schema-3 reload is idempotent: no extra cities or claim copies are generated.
Unrecognized versions, malformed identities/affiliations/colors or invalid lists
retain the original NBT verbatim and disable mutations rather than overwriting it.
As before, Minecraft chunk data and SavedData are separate disk writes: this is not
a crash-atomic transaction journal. Back up the whole world before upgrading and
do not downgrade an active schema-3 world to an older gameplay implementation.

## Ownership, claims and map

City owners administer their own claims, regardless of political affiliation.
Citizens also have build/container access as described above. Kingdom leadership
does not grant build, container or claim rights in another
owner's city. Claims require cardinal adjacency to **that settlement's** land,
not merely another city in the same kingdom. The player must stand in the selected
chunk/dimension. Overlap and every city/state capital release are forbidden.
Unclaiming bridge chunks may disconnect territory, as in previous versions.

The compact menu provides City, Kingdom, Territories and a functional Map entry.
City displays personal ownership/claims/status and separate sovereignty; independent
cities have a valid city-state view. Kingdom displays the distinct ruler, state
anchor and aggregate city claims. Territories summarizes the selected city and
opens the single full-size View/Manage map. `/nations menu`, `/nations city`, obelisk
interaction and M remain available, including visitor inspection.

Map cells/borders identify settlement UUIDs and names. Their fill derives from the
sovereign kingdom's persisted color or their independent city's own color. The
legend and colored tooltips show city-state/kingdom identity. Small gold corner
marks identify the viewer's personal domain without overriding political color;
selected/current chunk highlights, pan, zoom, Fit and sharp rendering remain.

Wire IDs remain `kingdom_found`, `kingdom_request`, `kingdom_state`, `map_request`
and `map_snapshot`; v0.6 clients/servers use expanded city/politics fields together.
Found requests add city/state intent and palette index. State includes separate city
and kingdom overviews, own settlement UUID, exact selected settlement/sovereign data
and authoritative action reasons. Map cells add sovereign identity/name. Existing
limits remain: radius 1–64, maximum 4,096 cells and a 500 ms map-request limit; a
conservative **900,000-byte cell budget** additionally truncates large-name snapshots
below the S2C payload cap. Partial cells are unknown, with exact selected inspection
still available. Management reads retain 150 ms and mutations 500 ms rate limits.
No periodic server polling, chunk scans or per-tick gameplay tasks are added.

## Safe administration

All previous `/nations admin list [page]`, `info <kingdom>`, `tp <kingdom>`,
`unclaim <kingdom> <chunkX> <chunkZ> [dimension]`, `rename <kingdom> <newName>` and
`help` remain permission-level-3+ operations. Unclaim now validates the actual
settlement owner is affiliated with the specified kingdom, while preserving **all**
capital reservations. Rename changes only the political name, not city names/IDs.

New read-only troubleshooting:

```text
/nations admin settlements [page]
/nations admin settlement <exact-settlementUUID>
```

New deliberate kingdom deletion:

```text
/nations admin delete <kingdomUUID-or-full-quoted-name>
/nations admin delete <same-kingdomUUID> confirm <server-issued-token>
```

The first command prints an explicit consequence summary, clickable Confirm/Cancel
actions and secondary textual commands. The token lasts 30 server seconds, is
executor/session/kingdom-bound, is consumed on service confirmation attempts, and
is not saved. In v0.6.1 a repeated request for the same target retains the token and
original deadline with clear feedback; another target requires cancel/expiry first.
`/nations admin delete <kingdomUUID> cancel <token>` invalidates that exact pending
request on the server. Stale Cancel links cannot invalidate newer requests. Confirmation checks permission level 3 again, current identity, token,
actor and expiry on the server thread. Disconnect/server stop invalidates pending
player confirmations. Wrong, expired, reused or revoked-permission confirmations
cannot mutate saved state. Only complete normalized names or exact canonical UUIDs
resolve kingdoms; ambiguous names/prefixes are never guessed.

Deletion removes only the political kingdom record/indexes and detaches its cities.
**Every capital and subordinate settlement survives**, with the same personal owner,
UUID, name, anchor and claims, becoming an independent city-state with its original
city color. All claims remain settlement-owned, so none are indiscriminately erased.
Unrelated cities/kingdoms are untouched. No physical block is removed. The loaded
state binding clears only when dimension, position, placed UUID and deleted kingdom
match; unloaded or stale block NBT is cleared using the persisted exact tombstone
on a later load/main-thread interaction. Unrelated bindings never clear.

Confirmed dissolution also invalidates connected clients' open management and map
views. They discard cached affiliation, ownership previews and older replies, then
request fresh snapshots through the existing read limits, retaining the viewed city.
Direct founding checks apply the same exact tombstone cleanup as block interactions.

On loading schema 3, an otherwise valid settlement referencing an absent kingdom is
made independent, logged and marked for saving. Owners, city identities and claims
are retained. A historical combined anchor whose city UUID equals the absent state
UUID can also reconstruct that state's exact tombstone from its saved city anchor.
A former member's anchor cannot identify a later sovereign's capital. Separate
native state anchors without a tombstone cannot be inferred. Other malformed data
still preserves its original NBT and disables mutations. No schema migration or
settlement deletion is added.
The surviving city still occupies its owner's personal domain slot; it may be
elevated again under the existing founding rules.

Deletion requests, confirmations and failures reaching the handler/service are
audited with executor, target and outcome. There is no delete-all command and no
settlement deletion/transfer command. Independent-city administrative land repair
is deliberately not exposed through a broad kingdom-delete or ownership bypass.

## Manual gameplay verification — not automated

Singleplayer, disposable Creative world (`./gradlew runClient`):

1. Obtain City and State Obelisks. Confirm distinct appearances/UUIDs. Place a city,
   choose a name and each palette color in turn, inspect its live preview, cancel,
   then explicitly found a city-state. Check City, empty Kingdom, Territories and M.
2. Try a second city, invalid name, repeated confirmation, distance/dimension change,
   expired selection and replaced anchor. Rejections must not add records or bind
   blocks. Check English/Ukrainian feedback, resize/GUI scales, native keyboard focus
   and menu blur enabled (text/map must remain sharp).
3. Stand in a cardinal neighboring chunk, select it in Manage and claim/unclaim.
   Inspect remote/diagonal chunks, foreign land and the capital. Physical-presence,
   adjacency, owner and capital restrictions must hold. Check counts/map refresh,
   chosen fill color, gold personal marks, tooltip names, zoom/pan/Fit and View mode.
4. Place a State Obelisk face-adjacent to the city within the same chunk. Select a
   different kingdom color and found. Verify the city UUID/owner/domain count is
   unchanged; Kingdom and the map use the new persisted political color.
5. Use the original `/nations found <name>` on a selected state anchor in a fresh
   world to verify combined-founding compatibility. Test `/nations kingdom`,
   `/nations territory`, `/nations claim`, `/nations unclaim`, menu and obelisk views.
6. Copy a v0.5.1 world, record kingdoms/anchors/claims, upgrade, save/restart twice
   and compare all IDs/names/claims. Check missing capitals, ordinary chunk unload
   and reload, capital destruction/replacement, and migration conflict diagnostics.
7. As a level-3 administrator, request deletion, wait over 30 seconds, try wrong and
   reused tokens and revoke permission before confirmation. Then delete deliberately:
   every city/claim/block must survive as independent. Reload previously unloaded
   state chunks and verify binding cleanup; reestablish a state without another slot.

Two-player dedicated server, matching v0.6 clients, disposable world:

1. Each player founds one independent city with different colors. Compete for an
   anchor/chunk/name and try duplicate domains. No duplicate records or overlaps.
2. A establishes a kingdom and invites B. B must explicitly accept the matching
   invitation. Both cities share the kingdom color; B retains its private city color
   in storage and all private rights. A must fail to break/place/use containers or
   unclaim B's land. Expansion needs adjacency to each owner's own city, not just
   a neighboring allied city. Visitors can inspect both obelisks without management.
3. Open menus/maps while another player changes claims; Refresh must show the
   server's state. Disconnect/respawn/change dimension during pending requests or
   invitations and verify stale replies cannot authorize remote mutations.
4. Administrator deletes A's kingdom with confirmation. A and B retain their city
   identities/owners/claims and regain their individual city-state colors. Unrelated
   cities remain unchanged. Check audit logs, old-state binding cleanup and capital
   protection; level-2 players must never access admin mutations.
5. `save-all flush`, stop and restart. Confirm affiliation or independence, chosen
   colors, personal limits, anchor UUIDs, claims and protection persist. No pending
   invitation/deletion confirmation may survive restart.

Limitations: no full diplomacy, role editing, economy, elections, warfare, ownership
transfer, recovery/relocation or city deletion. Leaving an established affiliation
is not a separate diplomacy mechanic; administrative dissolution safely detaches
cities. Historical duplicate owners/capital overlaps need deliberate repair. City
names may repeat; administrative city lookup uses exact UUIDs. Views are on-demand
snapshots and require Refresh/reopening for another player's changes, except that
confirmed kingdom dissolution triggers a refresh of open views. Recipe and
pre-existing protection exclusions remain unchanged. Rendered/live gameplay and
full process restarts require the manual checks above; headless tests do not prove
those behaviors visually or across real client connections.

## Actual verification

- `./gradlew compileJava compileClientJava` passed during implementation.
- `./gradlew compileJava compileClientJava compileGametestJava testKingdomNames`
  passed after integration; the existing pure Java name checks passed. An interim
  client compile caught protected Button narration access in the new color picker;
  it was corrected before the successful compile/build.
- The **single final** `./gradlew build -Pversion=0.6.0` completed with
  **BUILD SUCCESSFUL in 34 seconds**. All **32 server GameTests passed**: the
  unchanged previous 29 tests and exactly three new sovereignty regressions.
- New coverage exercises v2→v3 migration and idempotent v3 reload, stable identities,
  preserved claims/missing capitals/conflicts/historical duplicate owners; actual
  city founding/elevation services and invalid-color/domain-limit refusal; explicit
  affiliation and private build/claim rights; saved city/state colors; deletion
  wrong-token/expiry/revoked-permission/replay refusal; retained cities/owners/claims,
  and persisted exact binding-tombstone cleanup without clearing unrelated bindings.
- Expected malformed-schema, capital-conflict and historical-domain diagnostics
  appeared in negative tests. Deletion request, rejected confirmations and successful
  dissolution audit entries appeared in server logs.
- All resource JSON parses. Both translations have 192 matching keys and matching
  formatting placeholders. Existing test Java files and dependency/build configuration
  match the starting workspace. Common code has no client imports.
- Release JAR metadata is 0.6.0, includes city entities/assets and all ten palette
  translations, contains one political map class and excludes regression test classes.
  `git diff --check` passed.
- `gradle.properties` changed to **0.6.0 only after the successful build and packaged
  artifact verification**. Artifact: `build/libs/project-reigns-0.6.0.jar`.
- The previous generated GameTest world was preserved at
  `build/run/gameTest/world-backup-v060-20261109-100453`; a fresh generated test world
  was used. No user's gameplay save was modified.
- **Not performed:** rendered UI/color-picker/GUI-scale/keyboard gameplay, live
  singleplayer/two-player sessions or packet round-trips, normal dedicated-server
  startup, actual chunk unload/load cycles and full process restart of an upgraded
  world. The headless reload tests exercise registry/block NBT, not those operations.
- No commits, pushes or new dependencies. Pre-existing workspace changes preserved.

## Changed files in this milestone

- [README.md](../README.md)
- [docs/cities-and-sovereignty.md](cities-and-sovereignty.md)
- [docs/ux-and-administration.md](ux-and-administration.md)
- [gradle.properties](../gradle.properties)
- [src/client/java/dev/sakhno/regnum/nations/client/RegnumNationsClient.java](../src/client/java/dev/sakhno/regnum/nations/client/RegnumNationsClient.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomFoundingScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomFoundingScreen.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/PoliticalMapScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/PoliticalMapScreen.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/SovereigntyTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/SovereigntyTests.java)
- [src/gametest/resources/fabric.mod.json](../src/gametest/resources/fabric.mod.json)
- [src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlock.java](../src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlock.java)
- [src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlockEntity.java](../src/main/java/dev/sakhno/regnum/nations/block/CityObeliskBlockEntity.java)
- [src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java](../src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java)
- [src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlockEntity.java](../src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlockEntity.java)
- [src/main/java/dev/sakhno/regnum/nations/block/ModBlocks.java](../src/main/java/dev/sakhno/regnum/nations/block/ModBlocks.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/ClaimService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/ClaimService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/FoundingException.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/FoundingException.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/Kingdom.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/Kingdom.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomAdminCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementServer.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementServer.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomSavedData.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomSavedData.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalColors.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalColors.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalMapServer.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalMapServer.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/Settlement.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/Settlement.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/SettlementService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/SettlementService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java)
- [src/main/java/dev/sakhno/regnum/nations/networking/KingdomManagementPayloads.java](../src/main/java/dev/sakhno/regnum/nations/networking/KingdomManagementPayloads.java)
- [src/main/java/dev/sakhno/regnum/nations/networking/PoliticalMapPayloads.java](../src/main/java/dev/sakhno/regnum/nations/networking/PoliticalMapPayloads.java)
- [src/main/resources/assets/project-reigns/blockstates/city_obelisk.json](../src/main/resources/assets/project-reigns/blockstates/city_obelisk.json)
- [src/main/resources/assets/regnum-nations/lang/en_us.json](../src/main/resources/assets/regnum-nations/lang/en_us.json)
- [src/main/resources/assets/regnum-nations/lang/uk_ua.json](../src/main/resources/assets/regnum-nations/lang/uk_ua.json)
- [src/main/resources/assets/regnum-nations/models/block/city_obelisk.json](../src/main/resources/assets/regnum-nations/models/block/city_obelisk.json)
- [src/main/resources/assets/project-reigns/models/item/city_obelisk.json](../src/main/resources/assets/project-reigns/models/item/city_obelisk.json)
- [src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json](../src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [src/main/resources/data/project-reigns/loot_table/blocks/city_obelisk.json](../src/main/resources/data/project-reigns/loot_table/blocks/city_obelisk.json)
