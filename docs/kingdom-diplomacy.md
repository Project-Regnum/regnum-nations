# Kingdom diplomacy — v0.8.3

## Base and boundaries

Based on `main` at `f14cd92`, which contains kingdom UUIDs, city sovereignty,
municipal permissions, relocation, citizen management and server GameTests.
The separate, unmerged kingdom-administration work is intentionally excluded.
At this base, the kingdom founder is the sole kingdom representative. No kingdom
leadership-transfer or delegated diplomatic role exists.

`KingdomPermission.MANAGE_DIPLOMACY` checks the current server registry's founder.
City owners, officers, citizens, affiliation, operator status and client flags do
not independently grant that capability. No city-role defaults change. Future
leadership work must adapt this capability and its tests explicitly rather than
inferring kingdom rights from municipal roles.

Common data/services/payloads have no client imports. The client screen renders
snapshots and submits requests; every mutation validates the authenticated live
player, current kingdom authority, both kingdom UUIDs, pair revision, invitation
identity and transition rules on the server thread. Diplomacy is global, so these
actions deliberately have no proximity or dimension restriction. There are no
changes to protection, PvP, conquest, economy, world generation or obelisk rules.

## Relations and invitations

- An absent pair is **NEUTRAL**. UUID pairs have one canonical order; self-pairs
  and missing kingdoms are invalid. Names never identify diplomatic contracts.
- An invitation is directional and has its own UUID, sender, recipient, creation
  time and absolute expiration time. Only one invitation can exist for a pair,
  including crossed proposals. A crossed proposal never silently accepts one.
- Only the recipient may accept or reject; only the sender may cancel. Acceptance
  consumes the request and creates a distinct bilateral alliance record.
- Either founder may terminate an **ALLIED** agreement. Both kingdoms become
  neutral. Ending an alliance does not declare hostility.
- **HOSTILE** means at least one kingdom has a unilateral hostility declaration.
  A declaration ends an alliance and clears pending proposals. Both parties see
  the same effective relation, while each declarer remains identifiable.
- Withdrawing hostility removes only the acting kingdom's declaration. Neutrality
  resumes once neither party declares hostility. Old alliances do not revive.
- A pair's persisted random revision changes on every transition and expiration.
  Old acceptance/termination confirmations cannot affect a replacement invitation
  or alliance, including after a return to the same apparent state.
- Kingdom deletion removes every related diplomatic pair in the same saved-data
  operation; unrelated relations, cities and claims survive.

## Configuration and persistence

`config/regnum-nations-diplomacy.properties` uses the project's properties-file
convention. `invitationLifetimeSeconds=604800` defaults to seven days and clamps to
60–2,592,000 seconds. Restart to apply changes. New invitations use the configured
lifetime; existing invitation deadlines remain unchanged. Real elapsed time,
including server downtime, counts toward expiration. Server time is authoritative;
the client displays an approximate remaining duration.

Schema **6** extends the retained `data/project-reigns-kingdoms.dat` with a
`Diplomacy` list. Existing schema 1–5 readers/migrations, fields, identities,
memberships, cities, claims and deletion tombstones remain. Each pair stores
`First`, `Second`, `Revision`, optional `Alliance`/`AcceptedAt`, a `Hostility` list
of declarer UUIDs, and an optional `Invitation` compound (`Id`, `Sender`,
`Recipient`, `CreatedAt`, `ExpiresAt`). Neutral pair revisions remain to prevent
old confirmations becoming valid again.

Missing diplomacy starts empty. Orphan pairs are pruned on loading. Malformed,
duplicate or contradictory diplomacy disables diplomacy alone, logs the problem,
and preserves that original NBT section for administrator repair. Kingdom/city
records remain available. Expired invitations are invalid immediately; cleanup
happens on the next relevant snapshot/action, without ticking or background scans.
Ordinary reads and rejected non-expiring actions do not dirty saved data.

Back up the world and config before upgrading. Older releases reject schema 6;
rollback requires restoring the pre-upgrade world backup and matching old JARs.
The unmerged administration branch must reconcile its schema/capability changes
if integrated later; this PR does not consume its uncommitted format.

## UI and networking

Open `/nations menu` (or `/reigns menu`), then **Kingdom → Diplomacy**. The existing
kingdom control row gains the diplomacy button. English and Ukrainian are included.
There are no tooltips or hover descriptions on diplomacy controls.

The kingdom directory and incoming/outgoing tabs have six server rows per page;
small windows use local row paging. The server supplies permission flags and
relation/request status. Only representatives see pending invitations. Affiliated
members can read their kingdom's relations; unaffiliated players see the public
directory without a kingdom comparison. Buttons submit UUIDs without optimistic
changes. Reject, cancel, terminate, declare hostility and withdraw hostility show
explicit confirmation. Long confirmations use the existing scrollable paragraph.

New `regnum-nations:diplomacy_request` / `diplomacy_state` channels are additive;
all existing channels and codecs remain. The shared server gate limits reads and
mutations and rejects mutation-sequence replay. Replies are bounded to six entries.
Client sequence checks discard stale responses, disable controls while pending,
and time out after ten seconds. No polling or unsolicited broadcast is added;
refresh or reopen to see another player's changes. Matching clients and servers
are required; mixed-version multiplayer is unverified.

## Verification

`./gradlew build --no-daemon --console=plain` passed, including kingdom-name rule
checks and all **125 required server GameTests**, of which **14** are diplomacy
GameTests. They exercise service transitions, duplicate/crossed requests, forged
authority, preserved municipal permissions, hostility, termination, neutrality,
self/missing targets, deletion, real `DimensionDataStorage` disk reload, rename,
expiration across reload, replacement/stale confirmations, malformed data,
migration, pagination, wire round trips and dirty-write behavior.

`python3 tools/verify_mod_jar.py` fails at line 73: it expects the old obelisk item
adapter parent `regnum-nations:block/<obelisk>`. Both `main` and this branch instead
use `regnum-nations:item/<obelisk>` after the merged obelisk models. The checker and
model files are unchanged by diplomacy. This inherited packaging-check failure is
reported separately from the successful Gradle build; unrelated models/checker
are not changed here.

Manual client visual checks and real multi-client/process-restart play have not
been performed. Headless disk-reload tests do not substitute for these checks.

## Manual multiplayer checklist

1. Install matching JARs on a disposable dedicated server and two clients. Found
   kingdoms A/B as non-operators. Open diplomacy in both English and Ukrainian;
   check standard and 320×240 logical windows, GUI scales, Tab/keyboard controls,
   long kingdom names, confirmation scrolling, paging and absence of hover text.
2. Propose A→B; compare incoming/outgoing lists. Accept, reject, cancel, replace,
   terminate and repeat. Cancel each confirmation once to verify no change.
3. Declare hostility from A, then B. Withdraw A: both still show hostile. Withdraw
   B: both show neutral. Confirm PvP and existing claim permissions behave as before.
4. Try as a city officer, ordinary citizen, affiliated city owner and outsider;
   verify read access and disabled actions. Have both founders submit crossed or
   competing actions, then refresh; stale actions must fail with current status.
5. Leave a request pending, run `save-all flush`, stop/restart, and accept it. Repeat
   across the configured deadline (use 60 seconds on this test server). Rename a
   kingdom, and delete one through the existing administrator confirmation flow;
   verify stable relations after rename and removal of every deleted-state link.
6. Disconnect with a pending response, reconnect, switch dimensions, and close/reopen
   during requests. Timed-out mutations must be checked with Refresh, never retried
   automatically. Verify existing city memberships, anchors and claims after restart.
