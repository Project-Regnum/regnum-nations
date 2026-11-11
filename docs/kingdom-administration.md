# Kingdom administration — v0.8.4

## Repository audit and integration

Built from `main` at `f14cd92`, without a diplomacy branch dependency. Existing
`Settlement` ownership, citizenship, `CityPermissions`, claim ownership, capital
restrictions, deletion tombstones and saved-data filename remain authoritative.
The old kingdom invitation API in `SettlementService` now delegates to the same
persistent invitation lifecycle used by the UI. `/nations invite <player>` and
`/nations join <kingdom UUID>` remain available, including through `/reigns`.

Common code contains the records, policy, service, persistence and bounded payloads.
`KingdomAdministrationView` is a server session/presentation adapter, following the
citizen management pattern; `KingdomAdministrationScreen` stays in the client source
set. There are no added dependencies, ticks, scans of world blocks or chunk tickets.
No diplomacy, protection, economy, warfare or world-generation behavior is added.

## Membership and capabilities

A city has one nullable kingdom UUID (`Settlement.sovereign`). Its owner and citizens
participate in that kingdom as `MEMBER` unless assigned a government role. City
membership and government roles are separate: municipal officers gain no political
powers, and kingdom officials gain no municipal building, interaction, claim or
citizen-management powers. Changing city roles does not change kingdom roles.

| Capability | Ruler | Chancellor | Officer | Member |
| --- | --- | --- | --- | --- |
| MANAGE_KINGDOM (rename service) | Yes | Yes | No | No |
| MANAGE_KINGDOM_ROLES | Yes | Yes | No | No |
| INVITE_CITY | Yes | Yes | Yes | No |
| REMOVE_CITY | Yes | Yes | No | No |
| MANAGE_KINGDOM_MEMBERS (revoke government role) | Yes | Yes | No | No |

Only the ruler may appoint a chancellor. Chancellors can assign/revoke officer and
member roles; they cannot alter themselves, another chancellor or the ruler. Officers
can invite cities, but cannot assign roles or remove cities. Only an explicitly
confirmed ruler transfer can assign `RULER`. There is no second stored ruler role.
Individual political participation follows city citizenship; revoking a government
role sets `MEMBER`, rather than expelling someone from their city. Removing a city's
political membership is the kingdom's removal operation, with no citizen expulsions.

Government assignments use player UUIDs and are removed when citizenship or city
affiliation ends. Rejoining never restores prior authority. The ruler UUID is a
persistent government seat, retained even for an offline historical ruler lacking a
current citizenship record. Historical multiple-domain owners retain the existing
repair requirement; this update does not discard or merge their cities.

## City lifecycle and leadership safety

Kingdom officers or higher invite an independent city by stable city ID. The same
kingdom cannot issue a second pending offer to that city. Different kingdoms may
make competing offers; accepting one atomically removes all the city's other offers.
Only the current city owner may accept or reject. Invitations survive disconnects
and restarts, and do not expire by player session. An authorized kingdom inviter may
cancel an offer. Revoking its issuer's invitation authority removes the offer.

A city owner may leave, and a chancellor or ruler may remove a member city. These
operations change only affiliation and clear former political roles. The city's
owner, UUID, citizens, municipal roles, claims, obelisk UUID/position and personal
color remain unchanged. Kingdom territory totals continue to aggregate city-owned
claims dynamically, so leaving never reallocates claim records.

Capital cities cannot leave, be removed or be deleted while their kingdom exists.
The current ruler's city cannot leave, be removed or be deleted until leadership is
transferred. A ruler who is a non-owner citizen cannot be kicked or leave citizenship
before transferring. A ruler cannot demote/remove themselves. Thus the last ruler
and at least the capital city always remain; kingdoms never become empty through
normal membership operations. Existing operator-confirmed kingdom dissolution is
still available and leaves surviving cities independent with their claims intact.
Disconnecting or unloading an obelisk never deletes government or the kingdom.

Leadership transfer selects an existing participant, including an offline UUID.
The first request prepares a confirmation; only a subsequent confirmation using the
fresh session token can transfer. Tokens bind actor entity/session, kingdom, current
ruler, target and displayed page, expire after 60 seconds, and are consumed once.
Changed ruler/target roles, reconnects, stale pages and replays are rejected. The old
ruler becomes chancellor if still a city participant. The historical founder and
capital-city owner never change. Existing state-obelisk relocation continues to
require both municipal ownership and current ruler authority; transfer does not let
a new ruler move another player's privately owned shared anchor.

## UI and network

Open **Kingdom → Administration**. The kingdom overview remains in the parent folio;
the administration summary shows city/member counts and your role. Tabs provide the
government roster, member cities, independent cities to invite, your owned city's
inbox, and outgoing offers. Select a roster row, cycle the destination-role button,
then choose **Assign role**. Leadership transfer, role changes, acceptance, leaving
and removal require confirmation. Search includes names and UUIDs; paging and mouse
scrolling expose the full roster. Offline names use the server profile cache with
UUID fallback. English and Ukrainian text is provided; no new custom tooltips.

Two additive channels, `regnum-nations:kingdom_admin_request` and
`regnum-nations:kingdom_admin_state`, preserve existing packet layouts and IDs.
Pages contain at most six entries; strings and action counts have codec bounds.
Receivers reuse the existing 150 ms read / 500 ms mutation/replay gate. Session tokens
restrict mutations to displayed targets, and the service rechecks current permissions,
affiliation, ownership and existence. Political management is intentionally available
remotely; no block interaction or chunk loading is required. Foreign private rosters
cannot be requested. The client waits for server results and never assigns roles or
affiliations optimistically.

## Persistence, migration and rollback

Schema 6 adds `Ruler` to kingdom records and top-level `KingdomRoles` and
`CityInvitations` lists. All existing fields, kingdom/city/player IDs, municipal roles,
claims, tombstones and `project-reigns-kingdoms.dat` stay intact. Schema 1–5 records
migrate with the historical founder as ruler, no extra officers, and no invented
invitations. Existing city affiliations remain. Once migrated, save/reload is
idempotent. Inviter permission revocation and absent cities/kingdoms prune obsolete
offers and government references; missing sovereigns retain the existing conservative
detachment recovery. Malformed roles, rulers, duplicate offers or unknown schemas
fail closed, preserve the original NBT verbatim, and disable mutation without crashing
the server. They never silently reset authority to the founder.

Stop and back up the complete world before upgrading; use matching client/server
JARs. Downgrade by restoring that pre-upgrade backup: older binaries do not understand
schema 6. This is an additive change, not a destructive conversion, but compatibility
with another feature that independently allocates schema 6 must be reconciled before
integration. This branch includes no diplomacy persistence fields.

## Verification

The registered `KingdomAdministrationTests` adds 16 server GameTests covering roles,
capabilities, privilege escalation, invitations, ownership validation, competing and
duplicate offers, cancellation/rejection, joining, leaving/removal, reference cleanup,
capital/last-ruler safety, transfer confirmations/replay/expiry, offline leadership,
real `DimensionDataStorage` disk reload, schema migration, malformed-data retention,
municipal compatibility, claims/obelisk preservation, pagination and wire codecs.
Existing tests remain, with schema-write assertions updated from 5 to 6.

Verified on 2026-11-12: the clean build passed all 127 required server GameTests
(including the 16 new administration tests) and the kingdom-name rule checks. A
separate `runGameTest` execution also passed all 127 tests. Expected error logs
from malformed-data fixtures are intentional. Gradle used the available Java 25
runtime; packaged classes were checked to target Java 21 (class version 65).
English/Ukrainian administration keys and placeholder counts match. No interactive
client or Java 21 runtime execution was performed.

Commands:

```sh
./gradlew clean build --no-daemon --console=plain
./gradlew runGameTest --no-daemon --console=plain
```

`build` includes headless GameTests and kingdom-name rule checks. `test` has no source
tests. Only `build/run/gameTest/world` is reset. No player world under `run/` is used.

## Manual multiplayer scenarios (not yet performed)

1. On a disposable dedicated server with matching clients, de-op three players.
   Found a kingdom and a second independent city. Invite the offline city through
   Administration; reconnect its owner, reject once, then invite and accept. Restart
   the server with an offer pending and verify the same offer is still answerable.
2. Invite the same city from two kingdoms. Accept one, then try accepting the other
   from a stale client. Verify only one affiliation and unchanged city UUID, owner,
   citizens, roles, claims and obelisk identity.
3. Appoint a chancellor and officer. Try peer/self promotions, forged/stale requests,
   outsider administration and city building as a kingdom official. Only mapped
   political capabilities should succeed; municipal permissions remain unchanged.
4. Transfer leadership to an offline member. Cancel once, let another confirmation
   expire, then confirm a fresh request. Disconnect/reconnect both rulers and restart.
   Verify ruler history, old-ruler chancellor status and unchanged city ownership.
5. Try ruler citizenship departure, city removal and capital deletion. Transfer first
   where required, then leave/remove a noncapital city. Compare claims, citizens,
   colors, obelisk identity and aggregate kingdom territory before/after. Dissolve a
   kingdom through the existing operator confirmation and verify independent cities.
6. Use English/Ukrainian, keyboard navigation, GUI scaling and a 320×240 logical
   window. Verify role selection, confirmation focus, scrolling, paging, offline UUID
   fallback, timeout messages, refresh after another player changes state, and that
   closing a screen never reopens it upon a late response.

Interactive rendering, real network multiplayer, complete process restarts and
real-player-world upgrades remain manual; headless disk tests do not claim those.
