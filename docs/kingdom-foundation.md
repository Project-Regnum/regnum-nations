# Persistent kingdom foundation

For the current founding GUI, kingdom menu and interactive map controls, see
[kingdom management v0.5.0](kingdom-management.md). This document retains the
earlier milestone behavior and verification history.

This guide describes the foundation baseline. v0.3 adds automatic capital claims,
territory commands, and building protection; see [territory claims](territory-claims.md).
Its territory/protection exclusions below describe the earlier iteration.

Players found kingdoms through a placed **Kingdom Obelisk**, which serves as the
capital obelisk in this iteration. Placement never creates a kingdom. Ordinary
founding commands require no operator permission.

## Player workflow

1. Obtain and place an obelisk. There is still no recipe; use Creative inventory
   or have an administrator supply `project-reigns:kingdom_obelisk`.
2. Right-click it without sneaking. The server sends one private localized message
   with the existing obelisk UUID and a founding prompt.
3. Within 600 server ticks (30 seconds at 20 TPS), stay in that dimension and within
   6 blocks of the block's center, then enter `/reigns found Golden Vale`.
   The entire remaining argument is the name: use spaces **without quotes**.
4. The server validates everything before inserting the kingdom record and binding
   the block entity. Success reports the kingdom name and its new kingdom UUID.
5. `/reigns kingdom` shows your kingdom UUID, capital dimension, coordinates, and
   current capital status. `/reigns` and `/reigns found` show founding help.

Every attempted confirmation consumes the selection, including failures. Click
again to retry. Clicking another capital replaces/clears the previous selection.
Selections are tied to a player UUID **and that player entity**, preventing reuse
after respawn. Disconnect clears them; they are never saved across server sessions.
Expiry is checked on use; no ticking block entity or global polling is needed.

Names contain 3–32 Unicode code points after NFC normalization, trimming, and
collapsing repeated ASCII spaces. Letters, combining marks, digits, ASCII spaces,
hyphens, and straight/curly apostrophes are allowed; a letter or digit is required.
Formatting/control characters and double quotes are rejected. Uniqueness uses normalized
names lowercased with `Locale.ROOT`; names remain reserved when capitals are missing.
Messages have English (`en_us`) and Ukrainian (`uk_ua`) translations.

## Architecture and storage

| File/class | Responsibility |
| --- | --- |
| `kingdom/KingdomName` | Minecraft-independent validation and name uniqueness key |
| `kingdom/Kingdom` | Immutable UUID, name, founder UUID, dimension, position, obelisk UUID, UTC epoch milliseconds, capital status |
| `kingdom/KingdomSavedData` | Authoritative records and uniqueness indexes; versioned Minecraft NBT |
| `kingdom/KingdomService` | Server-thread rules, temporary selections, validated founding, capital resolution |
| `kingdom/FoundingException` | Rule failures mapped to translation keys by adapters |
| `kingdom/KingdomCommands` | Fabric command registration, localized help/feedback and own-kingdom inspection |
| `block/KingdomObeliskBlock` | Selection interaction and capital destruction notification |
| `block/KingdomObeliskBlockEntity` | Existing `ObeliskId` plus distinct optional `KingdomId` NBT |

All gameplay code remains in the common source set with no client-only imports.
The existing resources, block registration, item, loot, and UUID generation behavior
are retained. No versions or dependencies change. The APIs were checked against
the installed Minecraft 1.21.1 Mojang-mapped classes and Fabric source attachments.

There is one registry per server save, stored through the overworld's
`DimensionDataStorage` as `data/project-reigns-kingdoms.dat`, including capitals
in other dimensions. `SavedData.Factory`, `save(CompoundTag, HolderLookup.Provider)`,
and the existing block-entity serialization APIs match 1.21.1. Fabric's existing
object-builder API supports a null vanilla data fixer for custom saved data.

Only the server service performs founding. It checks a living non-spectator player,
selection lifetime/session, actual player dimension and distance, a loaded chunk,
the current block/type and placed-obelisk UUID, binding availability, founder
constraint, and name uniqueness. It checks both the registry and block entity.
Server commands and interactions run serially on the server thread, so competing
confirmations cannot interleave between validation, registry insertion and binding.
Rejected requests leave saved registry/block data unchanged; only temporary
selections are consumed. Clients supply neither capital coordinates nor identity
to the founding command. No custom packets or client screens are involved.

## Consistency and provisional policies

- **One founded/controlled kingdom per player** is an MVP restriction, enforced by
  founder UUID across all dimensions and restarts. It persists after destruction.
  There is no separate membership or control-transfer model yet.
- **Destruction preserves history.** Ordinary block removal marks the registered
  capital `MISSING`; it never deletes a kingdom or frees its name/founder/location.
  A newly placed obelisk receives a new identity and cannot inherit that capital.
  The old capital location remains reserved pending a future recovery/relocation
  design. This is provisional gameplay policy, not territory protection.
- **Resolution is on demand.** `/reigns kingdom` and capital interaction check
  loaded chunks only. An unloaded chunk or unavailable dimension is `UNRESOLVED`;
  an absent/replaced/conflicting block in a loaded chunk is `MISSING`. Saved
  `PRESENT` flags become `UNRESOLVED` on registry reload until checked again.
  There are no world scans, chunk tickets, or chunk-loading side effects.
- **Registry wins when a link is lost.** Resolution repairs a null `KingdomId`
  only at the exact registered dimension/position with the exact obelisk UUID.
  It never overwrites another binding or adopts a replacement UUID. A bound block
  with no matching registry record remains unavailable for founding. No orphan
  block can recreate a kingdom automatically.
- Minecraft saves chunk NBT and world saved data separately. This implementation
  handles a missing block-side link conservatively, but provides no transaction
  journal or guarantee against process crashes, disk failure, privileged NBT edits,
  `/clone`, or restoring inconsistent backups. Back up the entire world together.
  Parsed records with invalid/unknown schemas disable founding and retain their
  original NBT rather than overwriting history. Vanilla handles file-level I/O
  failures; this is not a replacement for backups.
- No domains, domain ownership/limits, invitations, roles, claims, protection,
  diplomacy, economy, recipes, new obelisks, custom GUI/maps, or warfare are added.
  A future kingdom can contain many domains owned by individual players. The
  proposed personal default of 2 domains is not enforced or configured here;
  there is no kingdom-wide city limit. Future subordinate cities need a different
  obelisk type.

## Automated verification

```sh
./gradlew compileJava compileClientJava testKingdomNames
./gradlew runGameTest
./gradlew check build
```

`check` includes the headless server GameTests and `testKingdomNames`. The latter
runs a dependency-free Java test entrypoint in `src/rulesTest`, checking name
normalization, English/Ukrainian case keys, Unicode canonical equivalence, length
boundaries, punctuation, invalid formatting/control characters, and empty names.
It uses a dedicated source set because it does not use a JUnit engine; the normal
Gradle `test` task has no sources.

The server suite includes the **six unchanged obelisk tests** and **fourteen
foundation tests** for successful founding, invalid/duplicate names, competing and
repeated confirmations, one-founder enforcement after destruction, selection
expiry/clearing/session replacement, dimension/distance checks, removed/replaced
obelisks, spectator/dead players, distinct identities, disk serialization/reload,
association repair/conflicts, missing/unloaded capitals, invalid-record/unknown-schema
preservation, and actual commands/interaction at permission level 0 with private
localized feedback, disconnect invalidation, and forged command-source positions.
Rejection checks compare registry and block NBT and verify that the registry is not
marked dirty. Expiry advances the test world's game clock temporarily; it does not
wait 30 seconds. Competing players are simulated serial requests, matching server
thread execution, not real connected clients.

The persistence test writes through `DimensionDataStorage`, opens a fresh storage
instance, restores block NBT from disk, and resolves the association. This verifies
serialization and reload, not a full server process restart or an actual chunk
unload/reload cycle. The invalid-data test intentionally logs data-error messages.
The suite runs with Fabric's `SERVER` environment; it does not launch a normal
dedicated server or test rendered client behavior.

## Manual singleplayer checks

1. Run `./gradlew runClient`, create a disposable Creative world, and obtain three
   Kingdom Obelisks from Functional Blocks. Place them and record their coordinates.
   Verify placement alone creates no binding (`KingdomId` is absent from `/data get
   block X Y Z`, if cheats are enabled). Rendering/mining checks remain in
   [the obelisk guide](kingdom-obelisk.md).
2. Run `/reigns`, then `/reigns found Golden Vale` before clicking: expect selection
   instructions. Click a block, then confirm with the command. Expect success,
   different obelisk/kingdom UUIDs, and matching `KingdomId` and `/reigns kingdom`.
3. Click the bound block: expect capital status and no new founding selection.
   Try founding again or selecting the second block: expect the one-founder error.
4. Save and quit, reopen, and compare both UUIDs and `/reigns kingdom` against your
   recorded values. Leave far enough for chunks to unload, inspect status while
   away (`UNRESOLVED`), return, and inspect again (`PRESENT`).
5. Break the capital. Expect `/reigns kingdom` to retain its UUID/name/coordinates
   and report `MISSING`. Replace the block: expect a fresh obelisk UUID, no inherited
   kingdom binding, and the original record still missing. The founder cannot found
   again. Check this behavior again after saving/reopening.
6. Use a fresh disposable world for retry scenarios: click then wait over 30 seconds,
   move over 6 blocks away, enter another dimension, die/respawn, or break/replace
   the selected block before confirming. Each must reject without binding or a
   kingdom record. Click again before each attempt. Try `ab`, `§aName`, and then
   `Золота Долина` to check validation and names with spaces. Switch between English
   and Ukrainian to verify prompt, success, failure, and capital-status translations.

## Manual dedicated-server checks

1. Run `./gradlew runServer` in a disposable development setup. Review Minecraft's
   EULA and accept it in `run/eula.txt` if you agree. Restart as needed. Connect two
   Fabric 1.21.1 clients with the same mod and Fabric API. Have an administrator
   supply/place obelisks; **de-op both players before testing founding**.
2. Both players click the same unbound obelisk, then issue different founding names.
   Exactly one may succeed. The loser gets an already-bound error; each response
   stays private. Use different client languages to check localization.
3. The loser selects a different obelisk. Confirm the winner's name with different
   case/repeated spaces: expect a duplicate-name error and no binding. Select again
   and use a unique name: expect success. Each player can inspect only their own
   kingdom through `/reigns kingdom` and cannot found a second.
4. Test selection followed by disconnect/reconnect: confirmation must fail until
   the player selects again. For a player who has not founded, also select before
   stopping the server and verify that selection does not survive restart.
5. Record both kingdom IDs and block UUIDs. In the console, run `save-all flush`,
   then `stop`. Restart, reconnect, and verify unchanged IDs, associations, names,
   and founder restrictions. Repeat after chunk unloading and after capital
   destruction. Compare the saved registry record count before/after if inspecting
   NBT; retries/restarts must not add duplicate records.

These interactive, normal dedicated-server, real multiplayer, actual chunk-cycle,
and full process-restart checks remain manual unless explicitly reported otherwise.
