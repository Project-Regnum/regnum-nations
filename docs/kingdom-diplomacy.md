# Kingdom diplomacy — v0.8.5 integration

## Base and boundaries

Integrated from PR #13 (`f6cc91f`) with administration #14 and protection #15.
`KingdomPermissions` resolves `MANAGE_DIPLOMACY` from the current server registry:
**RULER and CHANCELLOR** are authorized; OFFICER, MEMBER and outsiders are not.
Municipal owners/officers and operator status alone grant no diplomatic capability.
The historical founder has no special diplomatic privilege. Leadership transfer
changes the ruler immediately; the previous ruler remains a chancellor while a
participant and loses authority on demotion or departure.

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
- Either authorized representative may terminate an **ALLIED** agreement. Both kingdoms become
  neutral. Ending an alliance does not declare hostility.
- **HOSTILE** means at least one kingdom has a unilateral hostility declaration.
  A declaration ends an alliance and clears pending proposals. Both parties see
  the same effective relation, while each declarer remains identifiable.
- Withdrawing hostility removes only the acting kingdom's declaration. Neutrality
  resumes once neither party declares hostility. Old alliances do not revive.
- A pair's persisted revision changes on every transition, expiration and issuer revocation.
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

Schema **7** extends the retained `data/project-reigns-kingdoms.dat` with a
`Diplomacy` list. Schemas 1–5, both independently developed schema-6 formats, fields, identities,
memberships, cities, claims and deletion tombstones remain. Each pair stores
`First`, `Second`, `Revision`, optional `Alliance`/`AcceptedAt`, a `Hostility` list
of declarer UUIDs, and an optional `Invitation` compound (`Id`, `Sender`,
`Recipient`, `CreatedAt`, `ExpiresAt`, `Inviter`). New invitations persist the
issuing player UUID. Demotion below chancellor or membership departure revokes
that issuer's pending requests and changes pair revisions; re-promotion cannot
revive them. Existing alliances/hostility are kingdom contracts and survive role
changes. Legacy v6 requests had no issuer: migration attributes them to the sender's
persisted ruler (founder on the diplomacy-only branch). Earlier issuers cannot be
reconstructed from that format. Neutral pair revisions remain to prevent
old confirmations becoming valid again.

Missing diplomacy starts empty. Orphan pairs are pruned on loading. Malformed,
duplicate or contradictory diplomacy disables diplomacy alone, logs the problem,
and preserves that original NBT section for administrator repair. Kingdom/city
records remain available. Expired invitations are invalid immediately; cleanup
happens on the next relevant snapshot/action, without ticking or background scans.
Ordinary reads and rejected non-expiring actions do not dirty saved data.

Back up the world and config before upgrading. Older releases reject schema 7;
rollback requires restoring the pre-upgrade world backup and matching old JARs.
The [integration audit](integration-audit-v0.8.5.md#final-persistence-contract)
documents variant detection, extension retention and unavoidable rollback limits.

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
and time out after ten seconds. Political/membership mutations emit an existing
invalidation signal; open views clear stale controls/confirmations and request
fresh bounded snapshots through the existing read gate. Closed screens do not
poll. Matching clients/servers are required; mixed-version play is unverified.

## Verification

The combined clean build passes **193 required GameTests on Java 21**, including
all 14 diplomacy tests, 16 administration tests, 37 protection-hardening tests and
15 integration tests. Existing diplomacy lifecycle, duplicate/crossed requests,
expiration, cancellation, rejection, replacement, hostile/neutral transitions,
wire bounds, dirty-write and disk-reload cases remain registered. New integration
tests exercise delegated authority, transferred leadership, revocation and mixed
persistence. The packaging checker now validates the actual static inventory
adapters and animated model resources; its obsolete block-model expectation is
fixed. See the [executed results and runtime/UI limits](integration-audit-v0.8.5.md#validation-and-delivery).

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
   verify read access and disabled actions. Have rulers/chancellors submit crossed or
   competing actions, then refresh; stale actions must fail with current status.
5. Leave a request pending, run `save-all flush`, stop/restart, and accept it. Repeat
   across the configured deadline (use 60 seconds on this test server). Rename a
   kingdom, and delete one through the existing administrator confirmation flow;
   verify stable relations after rename and removal of every deleted-state link.
6. Disconnect with a pending response, reconnect, switch dimensions, and close/reopen
   during requests. Timed-out mutations must be checked with Refresh, never retried
   automatically. Verify existing city memberships, anchors and claims after restart.

7. Transfer A's leadership to a citizen, then negotiate as the new ruler. Demote
   the former ruler from chancellor and verify their actions are denied. Issue a
   proposal as a chancellor, demote/kick them or detach their city, and confirm the
   old request disappears at both ends and cannot be accepted after re-promotion.
   Verify open views invalidate without granting any allied building rights.
