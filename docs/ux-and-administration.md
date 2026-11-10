# v0.5.1 — Compact kingdom UI and server administration

For the current v0.6.1 shared components, responsive layouts and clickable
dissolution controls, see [Royal Cartography](ui-design-system.md).

For current city ownership, sovereignty and safe dissolution, see
[v0.6.0 cities and sovereignty](cities-and-sovereignty.md). This document retains
the v0.5.1 implementation and verification history.

## UI architecture

`KingdomManagementScreen` is now a centered window at approximately 72% of logical
screen width, capped at 640×270. A 300-pixel minimum width keeps a narrow navigation
sidebar and content usable; on very small windows it uses the available width with
an eight-pixel margin. Only two functional sections appear:

- **Overview:** concise rows for name, ruler (the existing founder), capital status,
  capital dimension/position, claim count and viewer access.
- **Territories:** kingdom/claim summary, access hint and **Open Map**. No map is
  rendered inside the menu, and no future placeholder navigation is shown.

The single full-screen `PoliticalMapScreen` provides **View** and **Manage** modes.
M opens View directly; Open Map also opens View. Its mode button toggles management
without reopening or fitting the map. View retains claims, borders, legend, selection,
player location and hover information. Manage adds one contextual action: Unclaim
for selected land belonging to the viewed kingdom, otherwise Claim. The server
still determines whether that action is allowed.

A single selected-chunk message gives the result or most relevant unavailable
reason. Separate claim/unclaim reason lists, duplicate standing instructions and
the old Territories/Political Map title variants were removed. Both screens now
use `KingdomUi` for button and panel painting. Standard Minecraft buttons retain
focus, Tab/keyboard activation and narration. The opaque map and custom background
overrides preserve sharp rendering without vanilla's background blur pass.

Refreshing, loading/empty/error states, visitors, founding and capital interactions
remain. Visitors may inspect another kingdom but cannot manage it. Closing a map
opened from the kingdom menu returns to the same menu section and refreshes its
state; closing an M-opened map returns to the game. The map's Kingdom button opens
its compact menu. Pan, anchored zoom and Fit remain; successful mutations refresh
authoritative map/overview data while preserving the viewport.

## Administration

All commands start with `/reigns admin`. They require Minecraft command permission
level **3 or higher**, including console/execution contexts. Command-tree gating
hides administration from lower-permission players. Every handler checks permission,
and mutation services independently check it again on the server thread. There are
no client admin flags, custom admin packets, protection bypasses or new dependencies.

| Command | Behavior |
| --- | --- |
| `list [page]` | Eight kingdoms per page, sorted by normalized name and UUID; shows kingdom names/UUIDs, founder names/UUIDs and claim counts. Pages begin at 1. |
| `info <kingdom>` | Name/UUID, owner/founder, dimension, capital coordinates/status, obelisk UUID and total claims. |
| `tp <kingdom>` | Teleports the executing player to safe ground near its capital. Requires the target world to exist; console must execute as a player. |
| `unclaim <kingdom> <chunkX> <chunkZ> [dimension]` | Removes exactly one claim belonging to that kingdom. The dimension must exist. If omitted, it uses the command source's dimension, including `execute in` contexts. |
| `rename <kingdom> <newName>` | Uses existing Unicode/name uniqueness rules and updates the name index, retaining every UUID-based association. |
| `help` | Lists commands and identifier/dimension conventions. |

`<kingdom>` accepts a complete canonical UUID or the **full normalized name**.
Names with spaces must be quoted. No prefix, substring or UUID-shortening lookup
is performed; ambiguous names explicitly reject. Rename's new name may be unquoted
with spaces or enclosed in double quotes.

Examples:

```text
/reigns admin list 1
/reigns admin info "Golden Vale"
/reigns admin rename "Golden Vale" "Золота Долина"
/reigns admin tp "Золота Долина"
/reigns admin unclaim "Золота Долина" 12 -4 minecraft:overworld
```

For precise identification, replace the quoted name with its UUID from `list`.

## Persistence and mutation safety

Rename goes through `KingdomService` into a validated `KingdomSavedData` name-index
replacement. Validation precedes all writes. The kingdom UUID, founder, creation
time, capital identity/status/location, claims and block-entity binding are retained.
The previous name becomes available; the new name is reserved using the existing
case/Unicode normalization. SavedData schema **v2** and its serialized fields remain
unchanged, including support for existing v1 migration and invalid-schema retention.

Forced unclaim goes through `ClaimService`. It checks live permission, registry
availability, kingdom identity, actual target-world availability and ownership,
then uses the same capital-reservation check as ordinary release. **Any** registered
capital chunk is protected, including missing capitals and legacy overlaps with a
different kingdom's capital. There is no delete/overwrite operation or transfer.
Ordinary founders still must stand in the selected chunk; administrative unclaim
is the explicit remote operation and does not grant general building permission.

Teleport conservatively searches within four horizontal blocks and six vertical
blocks of the position above the capital. It requires a sturdy floor, clear body
space, collision-free placement inside the world border and build height, and no
nearby lava/fire, cactus, magma, campfire, powder snow, wither rose or berry hazards.
It dismounts only after finding a safe position and clears fall distance on arrival.
It never builds a platform or removes blocks. No safe position means a rejection.
This explicit operation may load/generate the bounded target chunks.

Administrative rename, forced unclaim and teleport attempts reaching the handler
are logged with executor name/entity UUID, action, resolved target UUID (or rejected
identifier), relevant details and outcome. Control characters in log values are
sanitized. Routing rejects lower-permission commands before they become mutations;
cached/direct handler calls also repeat authorization and log their rejection.
Read-only list/info/help do not create mutation audit entries.

Networking and its bounded snapshots/rate limits are unchanged. Other players'
changes, including administrative renames/unclaims, appear after Refresh/reopening.
No new broadcasts, periodic polling, roles or gameplay systems were added.

## Manual verification — not automated gameplay

Singleplayer (`./gradlew runClient`, disposable Creative world):

1. Found through an unbound obelisk. Open `/reigns menu` and verify the centered
   window, sidebar, concise rows and functional Territories/Open Map action.
2. In M, inspect chunks in View, switch to Manage, stand in a cardinal neighbor,
   Claim and Unclaim. Check one contextual button/reason, capital refusal, unchanged
   pan/zoom on mode switches and action refresh, Fit, zoom and legend scrolling.
3. Open the map from Territories and close it: the menu should return to Territories
   with updated claim count. Test empty/error/loading states and capital interaction.
4. Switch English/Ukrainian; resize and change GUI scale (including logical
   320×240). Check text/tooltips, Tab/Enter/Space navigation and sharp rendering
   with menu blur enabled.
5. With permission ≥3, run every admin command, both names and UUIDs. Verify quoted
   names, page errors, duplicate/invalid rename, unknown IDs/worlds, wrong-owner
   unclaim and capital refusal. Teleport to a safe capital, obstruct it and check
   a safe nearby location or clear rejection. Inspect server mutation audit logs.
6. Rename, record UUID/capital/claim count, save and restart. Confirm they persist
   and `/reigns found`, `/reigns kingdom`, `/reigns claim`, `/reigns unclaim` and
   `/reigns territory` still work with the existing restrictions.

Two-player dedicated server (matching clients, disposable world):

1. A founds a kingdom. B right-clicks A's capital and inspects as Visitor. B must
   retain read-only menu/map access and fail to build or manage A's claims.
2. Set a test administrator to permission level 2: admin commands must be hidden
   and rejected. At level 3, verify list/info and deliberate rename/unclaim/tp.
   De-op and verify immediate rejection again. Ordinary kingdom commands remain.
3. With B's map open, have A/admin rename or remove a non-capital claim. Refresh B's
   view: names/ownership must update; stale mutations must reject. Attempt forced
   unclaim of a capital and another kingdom's land; neither may change.
4. Save, stop and restart the server. Confirm UUID associations, renamed name
   reservation, claims, capital protection and ordinary block protection remain.

Limitations: the teleport search is deliberately local and conservative, and may
reject a capital with safe terrain farther away. It does not guarantee safety from
future world changes or mobs. Offline ruler names depend on Minecraft's profile
cache. Text can truncate in small windows with full hover details. Views remain
snapshots; no live multiplayer or rendered-client testing is implied by headless
checks. Existing protection exclusions and crash consistency limits still apply.

## Actual verification result

- `./gradlew compileJava compileClientJava` passed during implementation.
- `./gradlew compileJava compileClientJava compileGametestJava testKingdomNames`
  passed, including the existing pure name-rule checks.
- One final `./gradlew build -Pversion=0.5.1`: **BUILD SUCCESSFUL in 27 seconds**.
  All **29 server GameTests passed**: the unchanged existing 26 plus three focused
  admin tests. The suite exercised successful quoted-name rename and
  dimension-qualified unclaim through actual command routing; lower-permission
  and cached-handler authorization refusal; rename serialization/reload and name
  indexes; forced-unclaim ownership/world/capital/legacy-overlap safeguards; and
  conservative landing-position acceptance/rejection.
- Server logs include the cached-handler permission rejection and successful
  rename/unclaim audit entries. Expected corrupt-schema/legacy-conflict diagnostics
  came from negative tests.
- Both language JSON files parse and have 142 matching keys with matching format
  placeholder counts. The packaged release JAR reports 0.5.1, contains the single
  political map and admin command classes, and excludes test classes.
- Existing test Java files, payload classes, management networking, founding screen,
  protection hooks and dependency/build configuration match the starting workspace.
  Common code has no client imports. `git diff --check` passed.
- Only after the successful build and artifact inspection was `gradle.properties`
  changed from 0.5.0 to 0.5.1. Release artifact: `build/libs/project-reigns-0.5.1.jar`.
- Previous generated GameTest world preserved at
  `build/run/gameTest/world-backup-v051-20261109-090650`; the final build used a
  fresh generated test world. No gameplay save was modified.
- **Not performed:** rendered UI/GUI-scale/keyboard gameplay, actual singleplayer
  or two-player sessions, normal dedicated-server startup, actual player teleport
  packet round-trip/cross-dimension arrival, and full process-restart verification.
  These remain the manual steps above.
- No commit or push was made. Pre-existing workspace changes were preserved.

## Changed files

- [README.md](../README.md)
- [docs/kingdom-management.md](kingdom-management.md)
- [docs/ux-and-administration.md](ux-and-administration.md)
- [gradle.properties](../gradle.properties)
- [src/client/java/dev/sakhno/reigns/client/ui/KingdomManagementScreen.java](../src/client/java/dev/sakhno/reigns/client/ui/KingdomManagementScreen.java)
- [src/client/java/dev/sakhno/reigns/client/ui/PoliticalMapScreen.java](../src/client/java/dev/sakhno/reigns/client/ui/PoliticalMapScreen.java)
- [src/gametest/java/dev/sakhno/reigns/kingdom/KingdomAdminTests.java](../src/gametest/java/dev/sakhno/reigns/kingdom/KingdomAdminTests.java)
- [src/gametest/resources/fabric.mod.json](../src/gametest/resources/fabric.mod.json)
- [src/main/java/dev/sakhno/reigns/kingdom/ClaimService.java](../src/main/java/dev/sakhno/reigns/kingdom/ClaimService.java)
- [src/main/java/dev/sakhno/reigns/kingdom/Kingdom.java](../src/main/java/dev/sakhno/reigns/kingdom/Kingdom.java)
- [src/main/java/dev/sakhno/reigns/kingdom/KingdomAdminCommands.java](../src/main/java/dev/sakhno/reigns/kingdom/KingdomAdminCommands.java)
- [src/main/java/dev/sakhno/reigns/kingdom/KingdomCommands.java](../src/main/java/dev/sakhno/reigns/kingdom/KingdomCommands.java)
- [src/main/java/dev/sakhno/reigns/kingdom/KingdomSavedData.java](../src/main/java/dev/sakhno/reigns/kingdom/KingdomSavedData.java)
- [src/main/java/dev/sakhno/reigns/kingdom/KingdomService.java](../src/main/java/dev/sakhno/reigns/kingdom/KingdomService.java)
- [src/main/resources/assets/project-reigns/lang/en_us.json](../src/main/resources/assets/project-reigns/lang/en_us.json)
- [src/main/resources/assets/project-reigns/lang/uk_ua.json](../src/main/resources/assets/project-reigns/lang/uk_ua.json)
