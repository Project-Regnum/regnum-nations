# Citizen management UI — v0.8.1

Open **City → Citizens** from an obelisk or `/nations city`. Existing city
management now resolves the viewer's current membership, including non-owner
citizens, while retaining owner-only controls. The Citizens action is also
available without city membership so newcomers can accept received invitations.
Opening a public city folio never grants access to that city's private roster;
the citizen screen always resolves the authenticated viewer's own city.

## Screens and actions

The form reuses the existing dark medieval/gold folio, native fields, buttons,
scrollable text and tooltips. Living Atlas and terrain rendering are unchanged.

| Tab | Content and actions |
| --- | --- |
| Members | City name, total citizens including owner, usernames, UUIDs, owner/officer/citizen roles and online/offline status. Select a member to manage them; non-owners can leave. |
| Invite | Eligible online players who have no city membership. Owners and officers can select a player and send the existing citizenship invitation. |
| Received | Only invitations addressed to the current player session. Select one to accept or decline. Acceptance is disabled while already a city member. |
| Sent | Active invitations issued by the viewer's city, visible to owners/officers. Recipients, city names and remaining lifetime are shown. This is a read-only view. |

Search by username, UUID substring or city name, then press Search or Enter.
Previous/Next and mouse-wheel paging make every matching record reachable. The
server returns at most six rows per packet; compact screens show fewer at once
and page through all six before fetching the next server page. The displayed
range identifies the visible records, not just the server page number.

Select a row before acting. Promote/demote, remove and leave open an explicit
confirmation panel. Long confirmation text can scroll. Cancel changes no gameplay
state. The owner cannot leave, be removed or have their role reassigned. Disabled
controls reflect the last server snapshot; they do not provide authorization.
Successful actions show localized feedback and a fresh server snapshot. Empty,
search-empty, unavailable, timeout, expired and stale states are handled.

Player UUIDs are the only member identity. Online names come from the actual
server player; offline names use the local profile cache. No external name lookup
is performed. A missing cached name displays the UUID instead. Row tooltips retain
the complete UUID/label; long city summaries and notifications have tooltips, and
confirmation context is included in narration. Online status is accurate at the
snapshot time, not a continuous presence feed.

## Permissions and authoritative operations

| Viewer | Members | Invite / sent invitations | Roles / removal | Leave |
| --- | --- | --- | --- | --- |
| Owner | Own city | Yes | Yes, non-owner targets | No |
| Officer | Own city | Yes | No | Yes |
| Citizen | Own city | No | No | Yes |
| Outsider | No private roster | No | No | No |

Anyone can view and consume their own valid received invitations. All mutations
call the existing `CitizenshipService`, `CityPermissions` and SavedData operations.
The UI never creates membership records or trusts submitted role/permission flags.
Kingdom leadership alone still grants no municipal management rights.

`CitizenManagement` adapts those services into bounded presentation pages.
`CitizenManagementServer` uses the existing management request throttle/replay gate.
New additive `regnum-nations:citizen_request` and `regnum-nations:citizen_state`
payloads keep all existing channel IDs and codec layouts intact. Request strings,
response counts and strings have codec bounds; target UUIDs are checked against
rows actually issued to the authenticated player in that session/tab.

Every mutation consumes a server snapshot token. Tokens expire after **60 elapsed
seconds**, are tied to the player entity and current city, and cannot be reused.
The transient membership revision advances on join, leave, removal, role change
and settlement deletion, including mutations issued through commands. Changes to
any membership in that city invalidate old confirmations. Even removal and
rejoining with the same role cannot revive a stale request. The current service
permissions are checked again at mutation time.

Invitation views expose the existing ephemeral invitation map. Each invitation
now has an ephemeral UUID so an old dialog cannot consume a replacement invitation.
The original 60-second expiry, 500 ms invite throttle, session binding, permission
revocation and disconnect/restart cleanup remain. There is no second invitation
registry and no persistent invitation storage.

On stale/error responses, the adapter rebuilds only an authorized current view.
Removed members receive no fresh private roster. A disconnected invite target,
unknown UUID, forged tab/target, missing permission, expired invitation or token
rejects without modifying SavedData. Refresh obtains a new token. Lists refresh
on demand and after this screen's mutations; no recurring roster broadcast,
world scan or server tick task is added. A concurrent external change is caught
when the viewer next refreshes or submits an action.

## Compatibility and integration branch

At implementation start PRs #6 (citizenship), #7 (roles) and #8 (relocation) were
all open. This branch starts from #8's `feat/obelisk-relocation-v0.8` at `fd910bb`,
which contains the preceding branches, and targets that integration branch.

Version 0.8.1 retains **schema 5**, existing SavedData filenames/NBT keys, registry
identifiers, UUIDs, claims, city/state anchors, roles, key bindings and older
packet layouts. The membership revision, invitation identities and UI tokens
exist only in memory and require no migration. No dependency changes are made.
Use matching client/server mod versions and back up the world before upgrading.
Earlier schema-5 readers remain compatible with the saved data; the new UI needs
the new channels. The existing relocation and recovery restrictions remain in force.

## Automated and manual validation

`CitizenManagementTests` covers member/role/count/name/presence synchronization,
bounded paging and UUID search, owner role changes and owner immutability,
officer invitations and both invitation lists, outsider/citizen rejection,
offline member management, leave/removal refresh, concurrent role changes,
remove/rejoin races, invalid/out-of-page targets, expiration/replay/disconnect/
wrong session, replaced/declined/expired invitations, permission revocation,
disconnected invite targets, read-only snapshots, actual payload codec round-trips,
shared packet replay gating and citizen access to the existing city folio.
The full build also runs all existing citizenship, role, obelisk relocation,
protection, persistence and kingdom regressions.

Manual scenarios still to execute on a disposable dedicated world:

1. Open City → Citizens as owner, officer, citizen and outsider on real clients;
   verify tab/action availability and that public folios expose no foreign roster.
2. Invite a second online player; view Sent and Received, accept and decline,
   expire an invitation, disconnect/reconnect and confirm cleanup.
3. Promote/demote/remove offline members with both cached and unknown usernames;
   cancel each confirmation and verify there is no membership change.
4. Open a confirmation while another client changes that member or the viewer's
   permissions; confirm that it rejects and displays current records.
5. Check search, paging, wheel navigation, Tab/Enter/Escape, narration, tooltips
   and scrollable confirmations at 320×240 logical pixels and larger GUI sizes,
   in English/Ukrainian and with long names/resource-pack fonts.
6. Confirm success/error notifications, timeout/reconnect behavior, city summaries
   after joining/leaving and continued obelisk relocation/recovery access.

Headless tests do not exercise rendered layouts, real client packet delivery or
live multiplayer interaction. No manual UI or multiplayer testing is claimed.

Validation performed: `./gradlew build --no-daemon --console=plain` passed all
**111 required GameTests** (96 existing and 15 citizen UI tests), plus the
kingdom-name rule checks. The new tests use the actual server adapter and payload
codecs. The prior obelisk integrity fixtures now use separate claim chunks to
avoid collisions when the GameTest layout changes; their assertions are retained.
`tools/verify_mod_jar.py` passed for `regnum-nations-0.8.1.jar`, including common/client
isolation, metadata and legacy resources. Locale key sets, placeholder counts and
citizen UI translation references matched. Expected invalid-NBT diagnostics remain
in negative tests. Player worlds under `run/` were not modified.
