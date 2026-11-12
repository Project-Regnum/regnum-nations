# Kingdom administration — v0.8.5 integration

## Repository audit and integration

Integrated from PR #14 (`42929b1`) with protection #15 and diplomacy #13. Existing
`Settlement` ownership, citizenship, `CityPermissions`, claim ownership, capital
restrictions, deletion tombstones and saved-data filename remain authoritative.
The old kingdom invitation API in `SettlementService` now delegates to the same
persistent invitation lifecycle used by the UI. `/nations invite <player>` and
`/nations join <kingdom UUID>` remain available, including through `/reigns`.

Common code contains the records, policy, service, persistence and bounded payloads.
`KingdomAdministrationView` is a server session/presentation adapter, following the
citizen management pattern; `KingdomAdministrationScreen` stays in the client source
set. There are no added dependencies, ticks, scans of world blocks or chunk tickets.
Diplomacy uses these same capabilities; economy, warfare and world generation remain unchanged.

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
| MANAGE_DIPLOMACY | Yes | Yes | No | No |

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

Schema **7** stores `Ruler`, `KingdomRoles`, `CityInvitations` and `Diplomacy`
in the retained `project-reigns-kingdoms.dat`. Schemas 1–5 migrate additively;
legacy founders become rulers without inventing government assignments. Both
schema-6 feature formats and a combined format are supported. A diplomacy-only
v6 save lacks all administration markers and gains founder rulers; an
administration save retains its actual transferred rulers. Missing rulers in
administration/current saves fail closed rather than restoring founder authority.
Missing optional government/invitation/diplomacy collections default to empty;
present malformed values are rejected. See the [schema and compatibility contract](integration-audit-v0.8.5.md#final-persistence-contract).

Stable identities, founders, cities, citizenship, municipal roles, claims, obelisk
anchors and tombstones remain intact. Unknown extension fields are retained at
root and surviving records, including nested diplomacy requests. Deleted records
and removed recognized optional fields are never resurrected. Invalid government
or unknown schema data is retained verbatim with mutations/protection fail-closed;
corrupt diplomacy alone disables that subsystem and retains its original section.
Migrations are deterministic and reloads idempotent. Back up world/config first;
rollback requires restoring that backup and matching client/server JARs.

Political/membership changes invalidate open management, administration, diplomacy
and citizen views. Ordered server snapshots and client sequence checks prevent
older replies from restoring stale views. Invalidations clear confirmations and
selection; only an open view refreshes, with the existing read throttle. No polling
is introduced. A former ruler retains chancellor powers while a participant;
explicit demotion/departure immediately revokes diplomacy and pending invitations
issued by that representative. Municipal permissions remain independent.

## Verification

The combined Java 21 build runs **193 required GameTests**, including the existing
16 administration tests, 14 diplomacy tests, 37 protection-hardening tests and 15
new integration tests, plus 21 pure kingdom-name checks. The integration suite
covers both v6 formats, combined data, disk restoration, leadership/diplomacy,
revocation, city departure, concurrent requests, wire authorization, invalidation
and protection. Detailed executed evidence and client/runtime limits are in the
[integration audit](integration-audit-v0.8.5.md#validation-and-delivery).

```sh
./gradlew clean build --no-daemon --console=plain
./gradlew runIntegrationLayoutTest --no-daemon --console=plain
python3 tools/verify_mod_jar.py
```

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
