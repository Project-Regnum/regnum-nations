# Kingdom management — v0.5.0

Current [v0.8.0 obelisk relocation and recovery](obelisk-relocation.md) supersedes
earlier anchor destruction and recovery behavior described below.


For the current compact menu, single-map modes and administrative commands, see
[v0.5.1 UX and administration](ux-and-administration.md). The implementation and
verification notes below describe the v0.5.0 baseline.

## Architecture and implementation plan

1. Keep `KingdomSavedData` schema v2, kingdom UUID claim ownership, capital NBT,
   commands and protection rules. Share claim validation between preview and mutation.
2. Add bounded founding/management request DTOs and one server view/result DTO.
   Founding consumes the existing server selection and checks its obelisk identity.
   Management reads public kingdom information; mutations authenticate the sender,
   viewed kingdom, dimension and selected current chunk before calling ClaimService.
3. Add client-only founding and overview screens; integrate the existing political
   map as the Territories and Political Map views. Select with click; drag still pans.
   The server supplies selection ownership, adjacency and unavailable-action reasons.
4. Localize both languages, add at most three server regression tests, compile both
   source sets, and run one final build including the existing checks.

No membership, subordinate cities/domains, economy, diplomacy or warfare. A future
kingdom may have unlimited subordinate cities; individual domain ownership is a
separate future policy with a proposed configurable default of two. No limits or
new persistence fields for those concepts are introduced here.

## Delivered behavior

- Right-click an unbound capital to enter its name and explicitly found a kingdom.
  The original 30-second selection, six-block distance and consumed-attempt policy
  still apply. Failed founding requires closing and clicking the obelisk again.
  `/nations found <name>` remains available after closing the dialog.
- `/nations menu` opens your kingdom. Bound capital interaction opens that capital's
  kingdom, including for visitors. Visitors have no management rights. A verified
  capital permits ordinary public interaction; sneaking and building still use
  the original protection policy.
- Overview displays the saved name, cached/online founder name, capital coordinates,
  readable dimension, on-demand capital status, total claims and viewer relationship.
  No kingdom displays a founding hint; failures and timeouts have explicit feedback.
- Territories and Political Map share the existing map renderer and selected-chunk
  controls. M still opens the map. Click selects; drag pans; cursor-centered wheel
  zoom, buttons, Fit, scrolling legend and collision-avoiding labels remain.
- Selection shows exact server ownership and cardinal adjacency to the viewed
  kingdom. Claim/Unclaim require the founder to physically occupy that exact chunk
  in the specified dimension. Capital release, foreign management, overlaps and
  non-adjacent claims are rejected by the shared ClaimService validation.
- Gold land identifies your kingdom; other kingdoms retain distinct muted colors;
  wilderness is gray and unknown areas are dark. A white frame selects a chunk;
  the gold frame/cross identifies the player's chunk/position. Hover the legend's
  heading for the color key and chunks/selection/actions for complete details.
- Action success updates the overview from the server response and requests a fresh
  map snapshot, preserving the current pan/zoom. No ownership is changed optimistically.
  Manual Refresh recenters/fits the map and refreshes selected details and overview.
- All screens paint an opaque charcoal background and override the vanilla
  background pass, preserving sharp text with menu blur enabled. Buttons/EditBox
  retain native focus, Tab navigation, narration and keyboard activation.

## Wire protocol and lifecycle

| Direction / ID | Values and purpose |
| --- | --- |
| C2S `kingdom_found` | Sequence, selected obelisk UUID, name (maximum 128 UTF-16 units). Calls the existing selection/founding service. |
| C2S `kingdom_request` | Sequence, VIEW/INSPECT/CLAIM/UNCLAIM, optional viewed kingdom UUID, dimension, selected chunk X/Z. |
| S2C `kingdom_state` | Correlated response or server-triggered founding/menu open; optional overview, own kingdom UUID, selected-chunk details, localized result key and success flag. |
| Existing C2S `map_request` / S2C `map_snapshot` | Unchanged codecs, server-chosen radius 1–64, maximum 4,096 cells, one request per player per 500 ms. |

Overview and selection reads are limited to one per player per 150 ms. Mutations
share a 500 ms limit and reject duplicate or older mutation sequences, including
failed requests. Client reads are coalesced with a small cooldown across screens;
there is no periodic state polling. Inspections use constant registry lookups plus
existing overview/claim-count reads, without loading remote chunks. A single
selected-chunk inspection can resolve ownership omitted from a partial snapshot.

Clients reject mismatched sequences/dimensions, disable pending mutations, invalidate
pre-action map replies and time out after ten seconds. Late replies never reopen a
closed screen. Disconnect clears server rate/replay state and existing founding
selections; server shutdown clears transient maps. Ordinary validation failures
leave persistent state untouched. There is no change to SavedData schema v2,
capital NBT or claim UUID ownership, and no new dependency.

## Manual gameplay verification (not performed automatically)

Singleplayer, disposable Creative world, using `./gradlew runClient`:

1. Obtain `project-reigns:kingdom_obelisk` from Creative inventory. Place and
   right-click it. Enter `Golden Vale` (also try `Золота Долина`) and explicitly
   activate Found kingdom. Verify overview, founder, capital status and one claim.
2. Before founding in a fresh world, try Cancel, Escape, an invalid/duplicate name,
   waiting over 30 seconds, moving over six blocks, replacing the obelisk, and
   double-clicking confirmation. Rejections must leave no extra kingdom/claim.
   Close the dialog and verify `/nations found <name>` still works after selection.
3. Run `/nations menu`; visit all three tabs and M. Click remote chunks: inspect
   owner/adjacency, with Claim/Unclaim disabled and the standing restriction shown.
   Close, walk into a cardinal neighbor, reopen M, select that chunk and Claim.
   Verify confirmation, gold land, unchanged map transform, and overview count two.
   Release it through Unclaim; verify map/count return to wilderness/one.
4. Verify capital release is unavailable, diagonal-only expansion fails, and
   `/nations claim`, `/nations unclaim`, `/nations territory`, `/nations kingdom` work.
   Check Fit, zoom anchoring, dragging beyond viewport and release, legend scroll,
   long-name tooltips, negative coordinates and dimension changes.
5. Switch English/Ukrainian, resize (including logical 320×240), change GUI scale,
   leave menu blur enabled, and Tab through/activate controls. Check no overlapping
   labels, readable errors, loading/empty/partial states and sharp map/text.
6. Save/quit/reopen and compare kingdom/capital identity, claim count and protection.
   Unload/reload the capital chunk, then break/replace the capital: history and
   its protected, unreleasable capital claim must remain.

Two players on a disposable Fabric dedicated server with matching mod versions:

1. Supply materials/obelisks, then de-op both players. Select the same obelisk and
   confirm concurrently: exactly one succeeds. The other gets a localized error.
   Found a second kingdom elsewhere; try duplicate names and overlapping claims.
2. Player B right-clicks A's bound capital without sneaking: A's kingdom opens as
   Visitor, and both mutations remain unavailable. A still manages their own land.
   B must still fail block break/place, container/tool/bucket and sneaking use in
   A's land. Verify both players' own `/nations menu` and M views.
3. Have A inspect a chunk while B changes ownership nearby. Refresh must show the
   new owner; stale attempts must be rejected rather than changing foreign land.
   Rapidly click actions, reopen screens, teleport/change dimensions, disconnect
   with a pending request and reconnect: no duplicate records or remote mutations.
4. Run `save-all flush`, stop and restart. Verify identities, names, claims, capital
   status and protection again. These are real process-restart and multiplayer
   checks; headless tests do not substitute for them.

## Limits

This remains an on-demand snapshot: another player's changes require Refresh or
reopening. Panning does not fetch new regions. Partial snapshots deliberately show
omitted cells as unknown; selected details are exact. Large regions may exceed Fit
at minimum zoom. Kingdom colors may collide; names/tooltips identify owners.
Very short windows have fewer legend rows; long text is clipped with full hover
text. GUI sizes below Minecraft's usual logical 320×240 minimum are not targeted.
Offline founder names come from Minecraft's cache; missing entries display a
localized placeholder. A timed-out action must be checked by refresh/reopening,
not automatically retried. Crash-atomic world/chunk saves and the pre-existing
protection exclusions remain unchanged; see the foundation/territory guides.

## Verification result

- `./gradlew compileJava compileClientJava`: passed during the server and UI phases.
- `./gradlew compileJava compileClientJava compileGametestJava testKingdomNames`:
  passed, including the existing pure name checks.
- `./gradlew build -Pversion=0.5.0`: **BUILD SUCCESSFUL**, 25 seconds; all **26**
  required server GameTests passed (23 unchanged existing tests plus 3 new tests).
  This was the only full build/GameTest run for this milestone.
- Language JSON parses, all 111 English/Ukrainian keys match, and formatting
  placeholder counts match. Existing SavedData implementation, map payload codecs,
  dependency/build configuration and existing test Java files match the starting
  workspace byte-for-byte. `git diff --check` passed.
- Release JAR metadata is version 0.5.0, includes both new screens and both
  translations, and excludes test classes. `gradle.properties` was set to 0.5.0
  only after the candidate build and artifact checks succeeded.
- The old generated GameTest world was preserved as
  `build/run/gameTest/world-backup-v050-20261109-084145`; the build used a fresh
  generated test world. No gameplay saves were modified.
- Intentional corrupt-data and legacy-conflict diagnostics appeared in existing
  negative tests; the complete suite passed. Java 25 emitted upstream native/Unsafe
  notices. No dependency or JVM configuration was changed.
- **Not performed:** rendered/client gameplay, real singleplayer/two-player tests,
  normal dedicated-server startup, live packet round-trips, actual chunk cycling
  or full process-restart checks. Follow the manual steps above.
- No commit or push was made. Pre-existing workspace changes were preserved.

## Changed files in this milestone

- [README.md](../README.md)
- [docs/kingdom-foundation.md](kingdom-foundation.md)
- [docs/kingdom-management.md](kingdom-management.md)
- [docs/kingdom-obelisk.md](kingdom-obelisk.md)
- [docs/political-map-ui.md](political-map-ui.md)
- [docs/political-map.md](political-map.md)
- [docs/territory-claims.md](territory-claims.md)
- [gradle.properties](../gradle.properties)
- [src/client/java/dev/sakhno/regnum/nations/client/RegnumNationsClient.java](../src/client/java/dev/sakhno/regnum/nations/client/RegnumNationsClient.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomFoundingScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomFoundingScreen.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomUi.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomUi.java)
- [src/client/java/dev/sakhno/regnum/nations/client/ui/PoliticalMapScreen.java](../src/client/java/dev/sakhno/regnum/nations/client/ui/PoliticalMapScreen.java)
- [src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementTests.java](../src/gametest/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementTests.java)
- [src/gametest/resources/fabric.mod.json](../src/gametest/resources/fabric.mod.json)
- [src/main/java/dev/sakhno/regnum/nations/RegnumNations.java](../src/main/java/dev/sakhno/regnum/nations/RegnumNations.java)
- [src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java](../src/main/java/dev/sakhno/regnum/nations/block/KingdomObeliskBlock.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/ClaimService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/ClaimService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/FoundingException.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/FoundingException.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomCommands.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementServer.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomManagementServer.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomService.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalMapServer.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/PoliticalMapServer.java)
- [src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java](../src/main/java/dev/sakhno/regnum/nations/kingdom/TerritoryProtection.java)
- [src/main/java/dev/sakhno/regnum/nations/networking/KingdomManagementPayloads.java](../src/main/java/dev/sakhno/regnum/nations/networking/KingdomManagementPayloads.java)
- [src/main/resources/assets/regnum-nations/lang/en_us.json](../src/main/resources/assets/regnum-nations/lang/en_us.json)
- [src/main/resources/assets/regnum-nations/lang/uk_ua.json](../src/main/resources/assets/regnum-nations/lang/uk_ua.json)
