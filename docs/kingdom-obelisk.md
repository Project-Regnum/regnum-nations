# Kingdom Obelisk

For the current founding GUI, kingdom menu and interactive map controls, see
[kingdom management v0.5.0](kingdom-management.md). This document retains the
earlier milestone behavior and verification history.

The obelisk now serves as the physical founding capital. Right-click selects an
unbound capital; `/reigns found <name>` explicitly confirms creation. See
[kingdom foundation](kingdom-foundation.md) for workflow, persistence, provisional
policies, verification, and updated singleplayer/dedicated-server checks. The
baseline block mechanics and six tests described below are retained.

The registered block, item, and block entity type all use
`project-reigns:kingdom_obelisk`. The item appears in the Functional Blocks Creative
tab and Creative search. The current appearance is a full cube with an original
16×16 dark-stone placeholder texture. No recipe is provided.

## Compatibility and design

The existing configuration is retained: Minecraft 1.21.1, Fabric Loader 0.20.1,
Fabric API 0.116.17+1.21.1, official Mojang mappings for 1.21.1, Gradle 9.8.2,
and Java 21 compilation. Loom is configured as `1.19-SNAPSHOT` and resolved to
1.19.4 during verification; that existing snapshot is not a reproducible version pin.
The only build addition configures Loom's isolated server GameTest source set,
using the existing Fabric API without adding dependencies.

`ModBlocks` registers the block, its BlockItem, and its BlockEntityType during
common initialization. The block uses stone-like hardness and blast resistance,
deepslate sounds, pickaxe mining, and correct-tool drops. Pistons cannot move it.
No client-only classes, renderer, ticking, or custom packets are needed.

The logical server assigns a UUID when a new block entity joins its world and
marks the entity changed. Minecraft 1.21.1's
`saveAdditional(CompoundTag, HolderLookup.Provider)` and `loadAdditional(...)`
persist `ObeliskId` using `putUUID` / `getUUID`. Normal chunk loading restores NBT
before world attachment, so an existing UUID is reused. Missing or malformed IDs
are initialized once on the server; constructors and detached/client entities
never generate identity.

A non-sneaking right-click consumes the block interaction on both sides. Only the
server sends the interacting player a translated chat component containing the
UUID plus a founding prompt or capital status; the recipient's client selects
English or Ukrainian. The ID is not otherwise
synchronized to clients. Holding the use button can produce repeated interactions,
each with its own single message, as with normal Minecraft input.

The loot table drops one plain obelisk item without block entity data. Breaking and
replacing it, including with Silk Touch, creates a new UUID. Creative breaking
uses vanilla behavior and does not normally drop an item. IDs identify placed
obelisks, not owners or kingdoms. Administrative NBT edits, `/clone`, and privileged
block-data copying are outside the uniqueness guarantee for ordinary placement.

Resources use the Minecraft 1.21.1 layout: `models/item` for the BlockItem,
`loot_table/blocks` for block drops, and `tags/block/mineable/pickaxe` for mining.

## Automated verification

Run `./gradlew check build`. The six baseline server GameTests cover:

- Registration, BlockItem association, Creative tab availability, and distinct IDs
  assigned before interaction.
- Saving UUID data to an NBT file, reading it into a new block entity, and attaching
  it to the server world without changing identity.
- Missing and malformed identity recovery, and stability after recovery.
- Pickaxe tags, loot-table loading, actual item drops, and new identity when the
  dropped BlockItem is placed again.
- A consumed interaction, one translated message, correct UUID, and no broadcast
  to the other simulated player.
- Rejection of identity generation without a logical server world.

The foundation adds fourteen server GameTests and pure name-rule checks, documented
in the [foundation guide](kingdom-foundation.md). These tests run in a server
environment. They do not replace a full world restart
or interactive tests with real connected clients.

## Manual test checklist

1. Run `./gradlew runClient`. Create a Creative test world with cheats enabled.
   Open the Functional Blocks tab and find **Kingdom Obelisk**. Also find it in
   Creative search. Run `/give @s project-reigns:kingdom_obelisk 2` to verify its ID.
2. Place both items on accessible solid ground. Verify the block and held item
   render with the dark-stone/gold texture, with no missing-model checkerboard.
   Use F3's targeted-block coordinates to record each position.
3. With an empty main hand, right-click each block once. Expect exactly one chat
   message per click, with different UUIDs for the two blocks. Repeat a click and
   confirm that block's UUID stays unchanged. Repeat with an item in the main hand
   and with an item in the offhand; ordinary non-sneaking interaction should still
   produce one message. Keep the use button released between clicks.
4. Record one block's UUID from chat. Run `/data get block X Y Z ObeliskId`, replacing
   `X Y Z` with its recorded integer coordinates. Expect a four-integer UUID array.
   Save and Quit to Title, reopen the same world, and interact again. Compare the
   displayed UUID with the recorded value; it must match. Repeat after leaving the
   area far enough for chunks to unload and returning.
5. In Options → Language, select **Українська (Україна)**. Verify the item is named
   **Обеліск королівства** and interaction displays **Обеліск королівства — ID:**
   followed by the same UUID. Switch back to English and verify both translations.
6. Run `/give @s minecraft:iron_pickaxe` and `/gamemode survival`. Mine the obelisk
   using the pickaxe, collect the single dropped item, and place it again. Expect
   a new UUID. Save/reopen and confirm the replacement's UUID stays stable. In
   Creative, verify normal breaking does not drop an item.
7. Run `./gradlew runServer`. On the first normal server run, review Minecraft's
   EULA and accept it in `run/eula.txt` if you agree, then rerun the task. Connect
   using the development client and a second modded Fabric 1.21.1 client with the
   same mod and Fabric API. Obtain/place the obelisk as an operator. Each player's
   click should message only that player, in their chosen language, with the same
   UUID. Check the server console for registration, class-loading, or resource errors.
8. In the server console, run `save-all flush`, then `stop`. Restart
   `./gradlew runServer`, reconnect both clients, and verify the original UUID.
   Confirm breaking/replacing on this server also gives a fresh UUID. Stop the
   server cleanly when finished.

Visual appearance, language selection, full restart persistence, and real-client
multiplayer behavior require the manual checks above and in the foundation guide.
Kingdom founding is implemented; domains, claims, GUI, diplomacy, warfare, and an
external database remain outside this iteration.
