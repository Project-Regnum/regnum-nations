# Project Reigns v0.3 — Basic territory claims

For the current founding GUI, kingdom menu and interactive map controls, see
[kingdom management v0.5.0](kingdom-management.md). This document retains the
earlier milestone behavior and verification history.

Founding a kingdom automatically claims the capital's entire chunk, at all heights.
Ownership is a kingdom UUID indexed by dimension and chunk X/Z, independent of the
founder. Each chunk has one owner. Negative coordinates use floor-based chunk conversion.

## Commands

- `/reigns claim`: claim your current chunk as your founded kingdom. It must be
  unclaimed and share a cardinal edge with an existing claim in the same dimension.
- `/reigns unclaim`: release your current owned non-capital chunk.
- `/reigns territory`: inspect dimension, chunk coordinates, owner name/UUID, and
  that owner's total claim count. Anyone can inspect; only founders manage land.
- `/reigns`: discover founding and territory commands.

All operations use the actual server player's position, dimension, identity, and
current saved data. Management rejects dead, removed, and spectator players. OP
and Creative mode provide no protection bypass. Feedback supports English/Ukrainian.

## Persistence and migration

`ClaimPos` identifies a chunk. `ClaimService` implements permissions, adjacency,
and release rules. `KingdomSavedData` stores kingdoms and claims together in the
existing overworld `data/project-reigns-kingdoms.dat` file, schema version 2. Founding
validates all kingdom and overlap rules before inserting both kingdom and capital
claim, then binds the block entity on the same server thread. Failed claim/release
validation does not dirty or change persisted data. Chunk NBT remains separately
saved by Minecraft, with the foundation's existing conservative association recovery.

Version 1 saves migrate on load without loading capital chunks. Missing capital
claims are reconciled once per registry load and marked dirty for persistence.
Existing claim owners are never overwritten. If multiple legacy capitals occupy an
unclaimed chunk, oldest creation timestamp wins, with UUID as a stable tie breaker.
Both kingdom histories survive. Conflicts are logged; the losing kingdom's founder
receives a conflict message from territory inspection/management. That kingdom's
claim management stays disabled pending a future explicit administrative recovery
workflow. No transfer or automatic capital relocation is provided.

Capital claims remain after capital destruction and cannot be released. Unclaiming
bridge chunks may disconnect territory; each new claim still needs an owned cardinal
neighbor. No growth limits, upkeep, domains, or claims across a new dimension without
an existing foothold are implemented. No per-tick tasks or world scans are added.

## Protection and limits

`TerritoryProtection` rejects outsider block breaking (including early block attack),
block use, container access, doors/buttons, tool-on-block use, and adjacent use targets
on the logical server. Checks apply to offhand and sneaking use. The clicked block
and face-adjacent position are both checked; interactions right on foreign borders
can therefore be conservatively rejected even when the clicked block is unclaimed.

Two common mixins protect resolved `BlockItem` placement targets (including extended
scaffolding and both bed halves) and bucket air-use ray casts for pouring/pickup.
Founders may place/break normally in their own land. A client may briefly predict an
interaction that the server denies. Protection messages use the action bar.

This is basic player block protection, not a complete anti-grief system. Explosions,
fire/fluid spread, pistons, dispensers, hoppers/automation, mobs, projectiles, entities
such as item frames, and privileged commands/world edits are not protected. Other
mods can introduce placement/use paths outside these hooks. There is no admin bypass
or recovery command yet. Back up the whole world before upgrades; world saved data
and block-entity NBT are not an atomic disk transaction. Parsed invalid registries
preserve their NBT and fail closed for claim management/build permission checks.

## Manual checks

Singleplayer (`./gradlew runClient`, disposable Creative world):

1. Obtain an obelisk, place and select it, then `/reigns found Golden Vale`.
   `/reigns territory` must show its kingdom as owner of the capital chunk.
2. Use F3+G to see chunk boundaries. Claim a cardinal neighbor. Try a remote or
   diagonal-only chunk (reject), repeat the existing claim (reject), and try to
   unclaim the capital (reject). Release the neighbor and inspect wilderness.
3. Place/break stone, use doors/chests, pour/pick up water, and place a bed as founder.
   Save/quit/reopen and confirm ownership remains; break the obelisk and verify its
   chunk still cannot be released.
4. Switch English/Ukrainian and verify help, success, failure, and owner messages.

Dedicated multiplayer (`./gradlew runServer`, disposable world, EULA accepted by
owner, two modded Fabric clients with matching versions):

1. Supply obelisks and materials, then de-op both players. Player A founds a kingdom.
   Player B must fail to break/place/use blocks in A's claims, including Creative,
   offhand, sneak placement, buckets aimed from wilderness, beds across borders,
   and scaffolding extended into protected land. A must build normally.
2. B cannot manage A's territory. Have B found elsewhere, then try expanding into
   A's land and founding a third kingdom in an already claimed capital chunk using
   a fresh player; reject overlaps without creating/binding a new kingdom.
3. Stop with `save-all flush` then `stop`, restart, and verify ownership, protection,
   and capital unclaim refusal. Also leave/return after chunk unloading.
4. On a copy of a pre-v0.3 save, verify automatic capital claims. With legacy capitals
   sharing a chunk, inspect conflict logs and command feedback; existing ownership
   and both histories must survive without silent takeover.

Automated coverage adds only three focused server tests: founding/claim mutation
invariants; disk reload and migration conflicts; outsider rejection/founder placement.
Existing tests remain unchanged. Full process restart, live multiplayer, buckets,
and scaffolding boundary behavior still require the manual checks above.
