# Diplomacy and interstate relations — v0.10.0

Based on PR #20 (`feat/nations-economy-integration`, `483330f`). The existing
Nations API v1, Economy role policies, integration entrypoints and shared Economy
UI contracts are unchanged. Nations has no dependency on Economy.

Open `/nations menu` (or `/reigns menu`) → Kingdom → Diplomacy. The kingdom
directory shows current relations; Incoming and Outgoing show official pending
proposals. Select a kingdom for scrollable treaty/proposal details and contextual
controls. Every mutation has a confirmation and receives server feedback.
English and Ukrainian use the existing folio, RoyalButton, typography, colors,
heraldry identities and paragraph widgets. Small windows retain local row paging.
Proposal dates use the client's local time zone; enforcement uses server time.

## Rules and authority

Each canonical pair of existing kingdom UUIDs has exactly one effective relation:
Neutral, Allied, Non-aggression pact, Hostile, or At war. Renaming never changes
contracts. Hostility retains the existing directional declarations; treaties and
war are symmetric. There is at most one pending proposal per pair, including
crossed proposals. A crossed proposal never accepts the other kingdom's request.

| Current relation | Action | Result |
| --- | --- | --- |
| Neutral | Propose alliance or pact | Pending proposal; relation unchanged |
| Neutral or Hostile | Declare war | At war immediately; clears proposals and hostility |
| Neutral or Hostile | Declare/withdraw own hostility | Effective hostility follows remaining declarations; clears proposals |
| Allied | Propose alliance termination | Alliance remains until acceptance |
| Non-aggression pact | Propose pact termination | Pact remains until acceptance |
| At war or Hostile | Propose peace | Current relation remains until acceptance |
| Any valid proposal | Recipient accepts | Applies proposed treaty/termination/peace and consumes proposal |
| Any valid proposal | Recipient rejects / sender cancels | Consumes proposal; relation unchanged |

War declarations cannot bypass an active alliance or pact. War cannot be ended
by withdrawing hostility, rejecting peace, cancelling peace or allowing a proposal
to expire. An accepted peace treaty returns the pair to Neutral. Treaties have no
automatic lifetime; proposal expiration is separate. No conquest, PvP/protection
changes, territory transfers, treasury transfers, reparations or Economy mechanics
are implemented. Dissolution removes all related diplomatic records and active
wars; existing dissolution behavior keeps city ownership and claims intact.

The existing role model has fixed political capabilities and separately customized
Economy policies. This feature preserves those policies and role assignments:

| Role | Diplomatic capabilities |
| --- | --- |
| Ruler, Chancellor | View, propose alliance, manage treaties/hostility, declare war, negotiate peace |
| Officer, Member | View own kingdom's public relations |
| Unaffiliated | Public kingdom directory |

`VIEW_DIPLOMACY`, `PROPOSE_ALLIANCE`, `MANAGE_TREATIES`, `DECLARE_WAR`, and
`NEGOTIATE_PEACE` are appended to `KingdomPermission`; existing enum ordinals
are retained. `MANAGE_DIPLOMACY` remains for existing consumers. There is no new
custom political-role editor. Delegation uses the existing Chancellor role.
Operator status and municipal authority alone do not grant diplomatic powers.
Only officials see pending proposals. Neither client flags nor UI visibility grant
authority. Diplomacy is global and intentionally has no proximity/dimension rule.

Every action checks the authenticated live player's current kingdom and role,
existing target UUID, expected pair revision, proposal UUID and transition rules.
Acceptance also rechecks sender/recipient, issuer authority, creation/deadline and
the live relation. Demotion/departure revokes the issuer's pending proposals;
re-promotion cannot revive them. Existing agreements survive leadership changes.
Pair revisions and consumed proposal IDs persist, preventing replay and stale
confirmations across restarts. Existing request throttling is reused.

## Configuration and notifications

Retains `config/regnum-nations-diplomacy.properties`:

```properties
invitationLifetimeSeconds=604800
proposalCooldownSeconds=60
```

The legacy lifetime key now applies to every proposal type, clamped to 60–2592000
seconds. Pair-wide proposal cooldown is clamped to 0–86400 seconds, applies after
rejection/cancellation as well, and survives restarts. Missing keys use defaults;
existing files are not overwritten. Restart to apply config changes. Existing
proposal deadlines/cooldowns remain unchanged. Elapsed offline time counts.

A deadline-ordered queue checks its earliest deadline each server tick, visits only
due proposals, and rebuilds once from persisted pending proposals at server startup.
Views/actions also enforce the deadline immediately. No world scans, scheduled
kingdom scans or per-pair tick handlers are introduced. New and expired proposals,
rejections, cancellations and issuer revocations notify relevant online officials.
Accepted treaties/terminations, war declarations, peace, hostility changes and
dissolution notify relevant online members. Messages never use global chat.
Offline notifications are not queued for later delivery.

## Persistence and migration

Writes **schema 9** to the retained `data/project-reigns-kingdoms.dat`. Schemas
1–8 keep their existing migrations. Existing alliances retain UUIDs/timestamps;
legacy invitations default to type ALLIANCE; missing cooldown defaults to zero.
No names are used as persistent references. Added pair fields:

- `TreatyKind` distinguishes ALLIED / NON_AGGRESSION while retaining `Alliance`
  and `AcceptedAt` as the existing agreement identity/time fields.
- `War`, `DeclaredAt`, `WarDeclarer` identify an active war.
- `NextProposalAt` stores the next permitted proposal creation time.
- `Invitation.Type` distinguishes all five supported proposal types. Existing
  ID, sender, recipient, issuer and creation/expiration fields are retained.

The strict reader rejects contradictory relations, invalid proposal types/states,
malformed IDs/times and duplicate contract identities. Invalid diplomacy disables
only diplomacy and preserves its original NBT for administrator repair. Orphan
pairs are pruned. Existing unknown extension fields are retained, and recognized
optional fields cannot be resurrected after treaty/war/proposal removal.

Back up the entire stopped world and configuration before upgrading. Older
binaries cannot interpret schema 9 safely; rollback requires restoring the backup.
A real player-world upgrade and disk restart have not been runtime-verified.

## Additive API and networking

`api.diplomacy.DiplomacyApi.get(server)` exposes `relation(first, second)` and
`relations(kingdom)` as `ApiResult` values containing immutable snapshots with
stable UUIDs, relation, revision, treaty, war/declarer, pending proposal and cooldown.
Reads require the owning server thread. Missing/dissolved kingdoms return MISSING;
invalid/self references return INVALID; unreadable data returns UNAVAILABLE.
Snapshots are trusted server-mod data and include official proposals; integrations
must not export them to unauthorized clients. Reads may expire due proposals.

`DiplomacyEvents.CHANGED` emits synchronous post-mutation before/after snapshots
for proposals, acceptance, rejection, cancellation, expiration, revocation, war,
relation changes and dissolution. Listener failures are isolated. Events are not a
durable log; consumers must reconcile snapshots at startup and must not mutate
political state reentrantly from a listener. Load/save do not emit events.

New bounded `regnum-nations:interstate_request` / `interstate_state` payloads carry
the expanded UI. Existing diplomacy channels/codecs and all unrelated channels
remain intact. Legacy requests use the same server rules: termination creates a
proposal, and old clients cannot consume a non-alliance proposal. Legacy snapshots
project pacts as Neutral and wars as Hostile; use matching v0.10 clients/servers for
the complete interface. Mixed-version gameplay has not been verified.

## Verification and remaining manual work

Validation command (automated tests explicitly excluded):

```sh
./gradlew build --no-daemon --console=plain -x test -x testKingdomNames -x runGameTest
```

Result: **BUILD SUCCESSFUL**. Common/client production code compiled and remapped
artifacts were packaged.
No tests, GameTests or new test infrastructure were created or run. No dependency
versions were changed. Runtime multiplayer interaction, GUI appearance at different
sizes/languages, actual save/restart/reload, dedicated-server startup and live
Regnum Economy integration remain unverified. Existing historical diplomacy test
expectations describe the earlier unilateral termination/hostility rules and have
not been rewritten or executed for this milestone.

Manual acceptance should cover two authorized kingdoms proposing/accepting/rejecting
all five proposal types; attempted self-war/duplicate war/treaty bypass; forged or
stale requests; issuer demotion and membership departure; expiration while online
and offline; rename/dissolution; save/restart with a pending proposal, pact and war;
and unchanged standalone Nations / Economy navigation and custom financial policy.
