# Territory and border management (v0.11)

This milestone builds on PR #21 (interstate diplomacy). It adds peaceful land
administration; political claims never transfer land, grant build rights, alter
relations, start wars, or call the Economy API.

## Player workflows

Open a city or kingdom folio and choose **Territories**. The atlas claim/release
actions also open this folio. The Nearby page shows a seven-by-seven-chunk preview
around the player. Select a tile to see its city, kingdom, foreign edges, adjacency,
political claims, expansion rejection reason and abandonment rejection reason.
`@` marks the player, `◆` the viewed city, `◇` other cities of the player's kingdom,
`×` foreign cities, `+` adjacent wilderness, and `!` disputed land. An adjacent tile
is a candidate; its selected detail applies the complete server validation.

Expansion and abandonment require standing in the selected chunk. Expansion must
connect to the city's anchor-connected territory and stay within its claim limit.
The city and kingdom anchor chunks cannot be abandoned. Removing a bridge that
would disconnect land is rejected. Existing disconnected historical claims remain
owned: players can reconnect them, reduce detached components without splitting
them further, or request administrative repair. A lower configured limit preserves
existing claims and blocks additional expansion until capacity is available.

Abandonment first opens a warning and requires a second action. The server issues
a token bound to the player, city, exact chunk and city territory revision; it
expires after 30 seconds. Confirmation rechecks ownership, permissions, location,
anchors and connectivity. `/nations unclaim` prepares the same warning;
`/nations unclaim confirm` confirms it. `/reigns` shares these handlers. Old
unconfirmed release packets are rejected. Explicit administrator force-unclaim
retains its permission check and also checks anchors and connectivity.

City land and Kingdom pages are paged. Kingdom rows show city claim counts,
remaining capacity, chunks touching foreign land and disputed chunks. Selecting a
city opens its land list. **My location** restores the player's city and local
selection. **Open atlas** reuses the existing map and returns to the folio.
`/nations territory` reports the current ownership, four neighboring chunks,
foreign edges, political claim count and municipal capacity.

The Disputes page lists demands involving the player's kingdom **and demands on
the selected chunk**, so nearby third-party disputes can also be inspected. Each
detail identifies claimant, current owner, owning city, dimension/chunk, creation
time and expiry. Only the claimant's authorized government can withdraw a demand.
The expiry selector offers 7, 30 or 90 days, or no expiration. The server accepts
only 0–365 days. A duplicate claimant/chunk pair is rejected. There is no military
resolution. A changed owning city, changed sovereign, missing kingdom, abandoned
chunk, or expiry invalidates the affected demand without changing ownership.

Border crossings show a server-authored action-bar message identifying wilderness,
city/kingdom territory or foreign territory. Nearby changes and entering disputed
land also give informational feedback. Foreign borders include independent cities;
political demands require two distinct existing kingdoms.

## Permissions and server authority

Capabilities extend the existing municipal and government role resolvers:

| Capability | Default roles |
| --- | --- |
| `CityPermission.VIEW_TERRITORY` | Owner, officer, citizen |
| `CityPermission.EXPAND_TERRITORY` | Owner, officer |
| `CityPermission.ABANDON_TERRITORY` | Owner |
| `KingdomPermission.VIEW_TERRITORY` | All kingdom roles |
| `KingdomPermission.MANAGE_TERRITORIAL_CLAIMS` | Ruler, chancellor |

Public local ownership and political-demand information are readable by visitors;
city and kingdom administration summaries require the corresponding role. Kingdom
authority grants no municipal expansion or abandonment rights. Every mutation
resolves current server roles and state. Packet requests use the existing replay
and rate gates, and reject dead, removed or spectator players. Political demand
creation is limited to the current dimension and within 32 chunks of the player;
land mutations require the exact current chunk. Withdrawals check the claimant,
not the territory's proximity. Request and snapshot lists have wire bounds.

## Configuration and performance

`config/project-reigns-territory.properties` is created with defaults:

```properties
settlementClaimLimit=256
politicalClaimsPerKingdom=256
```

The settlement limit includes its anchor chunk. Valid ranges are 1–100000 city
chunks and 1–4096 outgoing political claims per kingdom. Restart to apply changes.
Invalid configuration logs an error and uses defaults; it never deletes land.

The authoritative chunk-owner map remains intact. A derived per-city chunk index
is rebuilt on load and updated by all live claim writes. City counts are constant
time; connectivity checks visit only the affected city's chunks on requests.
Kingdom totals sum indexed city counts. Local preview and border queries use direct
chunk lookups. Border feedback runs once per second per player and checks only the
current chunk and four neighbors. A spatial political-claim index serves dispute
lookups; a priority queue expires only due demands. Political lifecycle changes
prune demands; there is no full territory-map scan per tick and no world/chunk
loading for these queries. Kingdom and city lists are calculated on demand and
sent in pages of six; local previews contain at most 49 cells.

## Save compatibility and delivery validation

Schema **10** adds `PoliticalClaims` to the retained
`data/project-reigns-kingdoms.dat`. Each record stores UUID `Id`, `Claimant`, `Owner`
and `City`, plus `Dimension`, `X`, `Z`, `CreatedAt` and optional `ExpiresAt`.
Schemas 1–9 migrate additively to an empty political-claim list. Existing city and
kingdom UUIDs, territorial ownership, diplomacy and Economy policies are retained.
Malformed recognized data fails closed with the original NBT retained. Unknown
extension fields on surviving political records are preserved. Derived indexes,
notification state and confirmation tokens are never persisted.

Back up the stopped world and config before upgrading. Install matching v0.11
clients and server. Existing channel layouts and enum ordinals remain; the two
territory channels are additive. Older binaries cannot safely write schema 10;
rollback requires restoring the pre-upgrade world backup.

The delivery build excludes `test`, `testKingdomNames` and `runGameTest`, as requested.
No tests or GameTests were added. Interactive client layout, multiplayer behavior,
dedicated-server startup and a real player-world save/reload need manual validation;
compilation alone does not establish those results.
