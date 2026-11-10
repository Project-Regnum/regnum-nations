# Regnum Nations UI design system

The subsequent [Living Atlas terrain integration](living-atlas-terrain.md) retains
this folio styling and extends the political map with explored terrain and layers.

## v0.6.3 — The Living Atlas standard

The user-approved v0.6.2 **Kingdom Administration** folio is the visual standard
for every Regnum Nations client screen. Its engraved charcoal parchment, brass
binding, pixel ornaments, heraldic identity, compact manuscript tabs and clear
native typography establish the direction. Future screens must extend this
language with a composition suited to their purpose. Avoid modern dashboard
cards, large generic dark rectangles and unnecessary decoration over content.
The Kingdom composition and stable UUID-derived arms remain the reference.

This release changes presentation and client layout only. The server still owns
settlements, affiliation, claims, permissions, validation, founding selections and
request outcomes. Do not infer global statistics from nearby map snapshots, add
placeholder statistics, or imply that an identity preview has already been saved.
There is no heraldic editor, terrain rendering, new persistent field or protocol.

### Shared components

| Component | Responsibility |
| --- | --- |
| `AtlasGraphics.folio` | Chamfered leather binding, tiled parchment, original mirrored corners and repeated/cropped edge strips |
| `AtlasGraphics.divider` | Engraved double rule with the existing central lozenge; separates registers without enclosing every value in a card |
| `AtlasGraphics.banner` | Sovereign hanging standard, dark shield, stable charge and field division; the approved Kingdom renderer |
| `AtlasGraphics.civicSeal` | Smaller municipal tower stamp using the existing shield and tower sprites, with a political-color field and untinted brass rim |
| `KingdomUi.FolioBounds` | Shared inner edges, footer/disclosure bands and six-pixel control gaps in logical pixels |
| `AtlasWidgets.tabs` / `rowWidths` | Native index tabs and measured control rows; bounded widths, shared hitboxes and selected/focused rules |
| `KingdomUi.RoyalButton` | Shared ruled actions matching the Kingdom prototype; quiet secondary, recessed primary and burgundy/legible warning-colored danger actions |
| `KingdomUi.Readout` | Native scrollable engraved registers; cached full components, shortened visible rows, compact hover only for clipped values, keyboard scroll and full narration |
| `KingdomUi.paragraph` | Native wrapping/scrolling context, territory rules, empty states and error explanations |
| `KingdomUi.field` / `ColorSwatch` | Native editable name field, brass surround, ten named political swatches and a geometric selection check |
| `RoyalScreen` | Shared sharp background, non-pausing lifecycle and explicit initial-focus preservation |

These are rendering helpers and ordinary Minecraft widgets, not a UI framework.
Every interactive control must remain registered through `addRenderableWidget`.
The owning screen retains its existing request/response and navigation lifecycle.
`KingdomAtlasView` and `CityAtlasView` are small compositions within the existing
`KingdomManagementScreen`; there is still one political-map screen/renderer.

### Palette and texture usage

Parchment `#252926`, shadow `#171E1B`, leather `#101714`, engraved lines `#595C4A`,
brass `#A78E61`, light brass `#D3BC89`, main text `#E5D9BE`, secondary text
`#A8A9AD` and the approved opaque obsidian screen surround `#171A1F` are the basis.
Small warning text remains readable pink `#EFA9B3`; burgundy `#834751` is an
accent, not body text. Focus and selection also use ivory rules/checks and words.

Reuse these v0.6.2 resources under
`src/client/resources/assets/regnum-nations/textures/gui/atlas/`:

| Texture | Size | Rendering |
| --- | --- | --- |
| `folio_tile.png` | 64×64 | Original five-color dark parchment, tiled and clipped to the folio |
| `ornaments.png` | 256×128 RGBA | Original binding, dividers, banner, shield, four charges, four divisions and land/capital marks |
| `.png.mcmeta` beside each | — | `blur: false`, `clamp: false`; keep pixel edges crisp |

No new textures were required for v0.6.3. Never stretch border pixels to fill a
large frame, use fractional heraldic scales, bake text into textures or dynamically
generate a texture while rendering. Corners and municipal seals render at 1×;
sovereign standards use integer 1×/2×. The existing original artwork source,
regions and generation provenance remain in [atlas-assets.md](../tools/atlas-assets.md)
and `tools/generate_atlas_assets.py`. Resources remain replaceable by resource packs.

### Screen patterns

**Kingdom.** Preserve the approved 620×286 maximum folio, hanging sovereign
standard, large name, ruler/access/capital header and two engraved registers for
actual aggregate claims and observed capital-anchor status. Technical UUID,
coordinates, dimension and detailed status remain an explicit disclosure that
replaces the overview and scrolls. The tab/action implementations are now shared;
the header geometry, deterministic heraldry mixing constants, charge selection,
political colors and existing overview behavior are unchanged.

**City.** Use a smaller municipal tower seal and the prominent city name rather
than another sovereign standard. Show the actual owner, independent city-state or
named kingdom affiliation, city-owned claim count, city obelisk status and named
political swatch. The technical disclosure contains the full owner/access,
affiliation, color, anchor status, dimension/coordinates and city UUID. No treasury,
population, invented municipal metrics or heraldry editing is present.

**Territories.** Use the municipal folio as a personal-domain register. The header
pairs the viewed city's name with its claim count and owner/visitor access. A
scrollable register explains same-city edge expansion, standing in the selected
chunk and the reserved capital chunk without repeating the header. The primary map entry
leads to the existing selected-territory inspector. Claim/release actions continue
to depend on server-supplied reasons, the current player position and request
state. Being a city owner grants no kingdom-wide land controls.

**Found City / Found Kingdom.** Use a ceremonial charter: municipal stamp,
foundation-document eyebrow, strong localized title, contextual explanation,
name field/rules, ten-color palette, named live preview and Confirm/Cancel.
The preview's tower is a decorative stamp, not proposed sovereign arms or an
editor. The selected political field updates live; the text explicitly identifies
a preview. Scroll the explanation when needed so the domain-slot, adjacency and
30-second selection-lifetime explanations remain accessible. Failed attempts use
readable warning text in that same context region. Preserve the existing one-shot
submission, server validation, selection expiry, response matching, timeout and
reselection behavior; never extend a server selection because a form was resized.

**Political map.** Use a compact full-viewport atlas binding and engraved header,
legend partition and selected-territory register. Preserve the existing canvas
area, inspector and navigation, with shared 20-pixel toolbar hitboxes. This is a political
chunk chart, not a geographical map. Continuous subdued land fills take priority
over the faint surveying grid; below eight pixels per chunk the grid disappears.
Fine rules separate city domains; stronger one/two-pixel contours separate
sovereign kingdoms or independent city-states. Missing snapshot cells remain dark
unknowns and never become confirmed wilderness. The existing gold personal-domain
corners, ivory inset selection, outlined player cross and current-chunk contour
remain separate shapes. A notched legend swatch denotes independence; an unnotched
square denotes affiliation, with explicit text in the selected-chunk inspector
and narration. Chunk, seal and legend explanation paragraphs never appear on hover.
Zoom, pointer-anchored wheel zoom, drag/keyboard pan, Fit, Refresh, Back, Close and
View/Manage retain their existing semantics. The bounded snapshots and protocol
are unchanged; borders are derived only from the current snapshot.

### Political color handling

Only political identity fields, cloth, preview/legend swatches and territory fills
use the existing ten-color palette. Keep the interface chrome charcoal/brass and
informational text neutral even for dark political colors. In particular, full
map affiliation tooltips now use readable neutral text rather than political RGB.
A chosen gold field is still a political color, distinguished from personal-domain
corner marks, selection and player position by context and shape. Never change
saved palette indexes, derive authority from a color or send arbitrary RGB.

Kingdom arms remain deterministic from `Overview.id()` using the exact v0.6.2
UUID mixing and sixteen charge/division combinations. They survive reopening,
renaming and sessions, confer no gameplay behavior and need no additional packet
or SavedData field. A city's municipal stamp uses the tower motif consistently;
it does not pretend that the future heraldic editor exists.

### Responsive layout rules

All layout measurements use logical GUI pixels. Support common 16:9, 16:10 and
4:3 windows and a practical minimum of **320×240 logical pixels**. Reduce decoration
and margins before reducing hitboxes or text. Do not shrink the native font below
1× or use blurred fractional scaling to force content into a small window.

- Management folios cap at 620 pixels wide, with eight-pixel outer margins. Kingdom
  retains its 286/256-pixel wide/compact height limits. City/Territories cap at
  256 pixels high, with a shorter municipal header and 16/24-pixel inner margins.
- Native index tabs use their measured natural widths, compress proportionally
  when necessary and retain full labels through native scrolling and narration.
- City and Kingdom names use integer 2× text with measured shortening and full
  hover/narration; unusually large claim counts fall back to native 1×.
- City and Kingdom information ends before their shared 20-pixel disclosure band;
  the technical register scrolls above it. Controls use a six-pixel vertical gap.
- Founding caps at 480×330. Its title drops from integer 2× to 1× in compact layouts.
  Pin the form, 20-pixel palette hitboxes and footer first; only explanatory prose
  gives up space and scrolls. All ten swatches remain on one row at 320×240.
- The map preserves its 48-pixel header and canvas height, with a narrower legend
  that increases usable map width. City entries precede a pinned compact key.
  Navigation sits above the map; view tools sit below the selected-chunk inspector.
  Ornament sits outside controls. The header rule cannot overlap button hitboxes.
- Remove label-repeat tooltips. Keep short zoom, mode, legend-scroll and release
  hints. Hover expansion is reserved for clipped names, feedback or explicit record
  values, at most three lines and 200 logical pixels (half a small window's width).
- Ellipsize complete display components to actual available widths. Preserve full
  values in accessible tooltips/narration or the scrollable register. Do not allow
  long translated labels or names to draw into another column or control.

### Accessibility and performance

Keep native Tab/Shift+Tab, Enter/Space, name editing/clipboard, Esc, Back and
scrolling. Disclosure rebuilds restore focus to the new disclosure widget; state
refreshes retain details focus/scroll and matching native control focus. The map
remains a registered focusable widget: arrows select, Ctrl+arrows pan, Home fits,
and Tab reaches its actions. A complete inset contour makes map focus visible.
Swatches have named narration and geometric checks; selected tabs have an ivory
focus rule and underline. Status/access/affiliation have explicit wording, not
only hue. Full errors remain in narration and context/hover. English and Ukrainian
must have matching translation keys and format argument positions/types.

Cache layout, display labels, name/count widths, UUID-derived heraldry, map border
flags, label placement and legend order at initialization or changed snapshot/
viewport inputs. No per-frame sorting, UUID hashing, texture generation, network
polling or global ownership scans. Existing map/tooltip rendering may build its
small contextual text components; do not add expensive per-frame allocation.
Only visible map cells are drawn. Grid cost scales with visible axes, with two
linear cell passes for fills and boundaries. Avoid dependencies and unrelated
refactoring. Purely visual work does not require new GameTests.

### v0.6.3 verification and remaining manual review

One development client was launched against the existing disposable
`build/run/atlas-client/saves/AtlasReview` world. A new city, Alderhaven, was founded
through the existing form/server validation with the gold political palette entry.
No normal player save was used. Actual window captures inspected by the agent:

| Screen/state | Evidence |
| --- | --- |
| Found City, 1280×800, GUI scale 2 | [Charter](images/living-atlas-v063-found-city-wide-en.png) |
| Found City, 640×480, GUI scale 2 (320×240 logical) | [Compact charter](images/living-atlas-v063-found-city-small-en.png) |
| City, independent gold city, 1280×800 | [Municipal folio](images/living-atlas-v063-city-wide-en.png) |
| City, 320×240 logical | [Compact municipal folio](images/living-atlas-v063-city-small-en.png) |
| City technical disclosure, 320×240 logical | [Native scrolling record](images/living-atlas-v063-city-record-small-en.png) |
| Kingdom, independent/no-kingdom, 320×240 logical | [Independent state](images/living-atlas-v063-kingdom-independent-small-en.png) |

The captures show crisp shared ornaments, readable text and form controls inside
the minimum-size folio. Some native focus/hover tooltips are visible. During the
pass the user began using the client and explicitly asked that it be left for
manual review; further input/capture automation stopped. **Territories, Found
Kingdom and the updated political map were not visually inspected by the agent.**
The populated Kingdom reference was not re-captured in this release. Its approved
v0.6.2 images below remain historical reference, not new verification. Actual
Ukrainian rendering, full aspect-ratio matrix, all colors, visitor states, map
claim/release interactions and spoken narration remain manual review items.

After captures, the final source review reserved the City claim heading's icon
width, cached its count scale, added the full header claim tooltip and used warning
color for founding rejection text. Those small refinements have static/build
verification only. The open client retains its initially loaded classes until a
normal restart; it was left running at the user's request.

Static validation checked matching English/Ukrainian keys/format arguments and
pinned-control arithmetic at seven logical sizes including 320×240, 640×360,
640×400 and 640×480. Session-start SHA-256 comparisons cover unchanged networking,
kingdom/settlement/persistence/permission services and all existing test sources.
There are no new tests or test-source edits.

### v0.6.3 build record

```sh
./gradlew build -x runGameTest -x test
```

**BUILD SUCCESSFUL in 6 seconds.** `testKingdomNames` passed (kingdom display-name
and uniqueness rules). Client and common compilation completed; JAR remapping
produced the signed release artifact. `gradle.properties` was advanced to 0.6.3
immediately before this build.

- Release: `build/libs/project-reigns-0.6.3.jar` (229,032 bytes).
- SHA-256: `db84f17e363eedaefee41e6f5e97343a46b11496d2df2f144bd4e67a5b3f312b`.
- Sources: `build/libs/project-reigns-0.6.3-sources.jar` (104,261 bytes).
- Metadata: version 0.6.3, Minecraft `~1.21.1`, Java `>=21`.
- 257 English and 257 Ukrainian keys matched exactly (zero missing, zero extra).
- No dependency, packet schema, SavedData, sovereignty, claim ownership, migration,
  protection, administrative deletion or test-source changes in this milestone.
- No commit or push. Existing workspace changes remain uncommitted.


### Manual screenshot instructions

1. Continue the disposable review world, or launch `./gradlew runClient` and use a
   disposable Creative world with commands enabled. Use GUI scale 2. Capture actual
   client areas of 1280×800 (16:10), 1280×720 (16:9), 1280×960 (4:3), and 640×480
   (320×240 logical). Move the pointer to an outer margin and press **F2**; keep the
   original Minecraft image in `<game directory>/screenshots/`.
2. Use `/nations city` for City, `/nations menu` for Kingdom, the Territories index
   tab for the personal-domain register, and **M** or Map for cartography. Capture
   City and Kingdom with the technical record both collapsed and expanded. Scroll
   the rows and territory rules, checking their full hover text and keyboard focus.
3. For Found City, place/use a City Obelisk as a player with no personal domain.
   Capture the ten swatches, a valid and invalid name, the live preview and scrolled
   domain-limit explanation before confirming. Reopen after a cancelled/expired
   selection; the server's 30-second lifetime is unchanged.
4. For Found Kingdom, place a State Obelisk directly beside your owned city obelisk
   in the same capital chunk and dimension. Use it and capture the charter before
   confirming. The preserved legacy combined-anchor path may also be checked.
5. In Territories/Map compare owner and visitor views, an independent city and
   kingdom-affiliated cities. Pan, zoom, Fit, Refresh, switch View/Manage and return
   with Back/Esc. Inspect selected/current chunks, unknown snapshot edges, legend
   scrolling and borders. Standing in a permitted adjacent chunk, verify that
   Claim/Unclaim still follows the server's reasons; a capital cannot be released.
6. Repeat in **Українська (Україна)** with long city/kingdom names. Tab/Shift+Tab
   through fields, every swatch, tabs and actions; activate with Enter/Space. Check
   focus, narration and full text, especially compact headers, errors, independent
   versus affiliated status and the light gold political color. Screenshot checks
   are still required; successful compilation is not visual approval.


<details>
<summary>Historical v0.6.2 and v0.6.1 implementation and verification records</summary>

The following records describe earlier releases. The v0.6.3 standard above
supersedes their current/future-direction wording. The Kingdom design was
subsequently approved by the user as the reference for v0.6.3.

## v0.6.2 — Kingdom administration implementation history

The current visual reference is **one Kingdom administration folio**. City,
Territories, founding, the political map and administrative deletion keep their
v0.6.1 behavior and design. This prototype is a candidate for user review, not a
claim that all interfaces now follow the Living Atlas style.

### Art direction and hierarchy

An atlas folio bound in charcoal leather, with dark weathered parchment, brass
engraving and a hanging heraldic standard. The sheet has chamfered corners,
mirrored binding ornaments, continuous tiled rules and a lozenge divider. Its
surface is a small original texture, rather than a large painted background.
An opaque obsidian surround keeps world HUD/chat from competing with the realm.

The composition is deliberately unequal:

1. Compact manuscript index tabs sit along the top binding.
2. A political-color standard hangs from a brass suspension rod in the header.
   Its heater shield and ivory charge are integral to the header composition.
3. The realm name is the strongest text, using the native Minecraft font at an
   integer 2× scale. The sovereign/access label, ruler and capital dimension sit
   below/around it at native size. Unknown ruler names are omitted.
4. Two engraved registers show **actual aggregate claimed chunks** and the
   **capital anchor's observed status**, with a named political-color sample.
   There is no treasury, population, city count, government or diplomacy field.
5. UUID, raw anchor coordinates, raw dimension ID and detailed anchor status live
   in an explicit technical-record disclosure. Its native scroll widget replaces
   the overview area while expanded; the header and footer remain in place.
6. Refresh is a small ruled primary action; Close is a quiet secondary action.
   There is no destructive realm action here. Existing shared danger styling
   remains available; administrative dissolution semantics are unchanged.

### Implementation and native interaction

- `KingdomManagementScreen` routes its existing **Kingdom** section to
  `KingdomAtlasView`. It still receives the same state replies, issues the same
  on-demand requests, retains navigation, handles stale/failed replies and returns
  from the political map to the selected section. No new screen-opening packet,
  polling, client authority or server-side mutation was introduced.
- `KingdomAtlasView` owns the folio layout, cached display text, native index/action
  buttons, disclosure, narration and hover regions. It reuses `KingdomUi.Readout`
  and the existing native paragraph for technical/empty states. The only new state
  is an ephemeral client disclosure flag. This is composition, not a layout engine.
- `AtlasGraphics` owns the atlas-specific palette, sprite regions, tiled folio,
  ornamented divider and banner renderer. It does not change the shared theme of
  other screens. Rendering restores tint after the cloth pass.
- Buttons remain registered Minecraft `Button` widgets. Tab/Shift+Tab, activation,
  selection narration, focus rules and disabled Refresh behavior use the native
  lifecycle. Disclosure rebuilds retain focus on its new widget. Long names retain
  the full snapshot value in hover and narration; technical rows scroll/narrate.
- Fixed display strings and count scaling are prepared on initialization/state
  changes. Rendering does not hash UUIDs, construct heraldry, allocate a layout,
  sort data or repeatedly measure the realm name each frame.

### Heraldic construction

The existing `Overview.id()` supplies the stable kingdom UUID. There is **no
protocol change** and no inference from the bounded political map.

`AtlasGraphics.Heraldry.of(UUID)` XORs the UUID's most-significant bits with a
23-bit rotation of its least-significant bits, then applies fixed 64-bit avalanche
mixing. Two low bits select the charge; the next two select the field division.
The constants and selection order are part of the visual construction and should
remain stable if existing kingdoms are to retain their arms.

Four original 16px charges: **tower, north star, oak, crossed keys**. Four divisions:
**pale, chevron, bend, quartering**. These produce sixteen combinations, further
identified by the kingdom's existing palette color. Arms are stable across sessions
and renames, but are not guaranteed globally unique and confer no gameplay powers.
There is no heraldry editor or new saved field.

Only the grayscale cloth silhouette is multiplied by the selected political RGB.
The division is a restrained translucent overlay. Rod, brass trim, dark heater
shield and ivory charge keep their own colors. The dark shield separates the
charge from light political colors, including the existing gold palette index.
Interface chrome is not recolored to match the kingdom.

### Texture usage and source

Resources are under
`src/client/resources/assets/regnum-nations/textures/gui/atlas/`:

| Texture | Native size | Use |
| --- | --- | --- |
| `ornaments.png` | 256×128 RGBA | Original corners, mirrored edge segments, divider, standard, trim, four charges, four divisions, shield and territory/capital marks |
| `folio_tile.png` | 64×64 RGB | Original generated weathered parchment reduced to a five-color pixel tile |
| Both `.png.mcmeta` files | — | Explicit crisp filtering (`blur: false`) and repeat-compatible sampling |

The renderer tiles the surface, repeats/crops edge segments, and places corners at
1:1, giving a nine-slice-style frame without stretching ornament pixels. The
standard uses integer 1× or 2× rendering. Text is always rendered by Minecraft,
never baked into an image. Both textures are replaceable through resource packs.

The standard-library-only generator `tools/generate_atlas_assets.py` contains the
editable original pixel geometry for the sprite sheet. `tools/atlas-assets.md`
lists every sprite region, the original imagegen prompt and surface conversion.
The built-in imagegen tool generated the surface only; the small heraldic and
structural artwork was constructed programmatically. Large intermediates are not
packaged. No new dependency or external/proprietary artwork is used.

### Layout proportions and scaling

All dimensions are logical GUI pixels. The wide folio caps at **620×286**. Below
500 logical pixels of width it caps at **256 pixels of height**, within the
available viewport. An eight-pixel outer margin remains. Wide composition requires
width ≥500 and height ≥280; otherwise decorative space and standard size reduce.

- Inner horizontal padding: 24. Natural-width index tabs: 19 high, four-pixel gaps.
- Header divider: y+158 wide, y+110 compact. The body begins 16/12 pixels below it.
- Hanging standard: 32×56 at integer 2× wide / 1× compact.
- Name: native font at integer 2×, ellipsized to its actual available width.
- Overview: two columns separated by one engraved rule, not a card grid.
- Technical toggle: 52 pixels from the bottom; Refresh/Close: 34 from the bottom.
  The drawer scrolls instead of extending behind these controls.
- At **320×240 logical pixels**, the frame is 304×224. Tabs, header, overview,
  technical toggle and actions remain composed inside that space. Longer names
  truncate with full hover/narration. A large count can use native 1× text to fit.

No custom font, fractional resampling, blur pass or texture-pack dependency is
required. Ukrainian labels use the same native font and localized components.
The latest Ukrainian rendered layout has not been reviewed; see verification below.

### Captured evidence and limits

Before the user's instruction to stop testing, a real development client was
launched against an isolated copy of the generated GameTest world under
`build/run/atlas-client/`. A realm was founded through the existing GUI/service,
and the Kingdom screen rendered from its actual server snapshot. No original
player save or production kingdom data was edited.

[Wide English capture](images/living-atlas-wide-en.png) shows the final folio
composition at a 1280×800 client viewport, GUI scale 2, with an ordinary claim
hover tooltip visible. [Minimum English capture](images/living-atlas-small-en.png)
shows a 640×480 client viewport at GUI scale 2: **320×240 logical pixels**.
Additional English captures at 1280×720 and 1280×960 are local build artifacts in
`build/reports/living-atlas/`. These are actual window captures, not mockups.

The earlier rendered pass exposed excess vertical space and distracting underlying
HUD; the folio height and backdrop were corrected. An earlier candidate's technical
record was also opened using native keyboard activation. The final minimum-size
drawer interaction and Ukrainian rendering were not completed before testing was
stopped. Linux narrator initialization reported an unavailable native speech
library, so spoken narration was not verified.

**At the user's later request, further UI testing and automated test execution were
stopped.** Existing GameTests have not been rerun for v0.6.2; no test files were
changed or new tests added. Compilation/package results are recorded below after
release assembly. Multiplayer, visitor/empty states, all ten political colors,
long-name/UUID edge cases and actual spoken narration are not fully verified in
this milestone. The prototype still requires user visual approval.

### v0.6.2 package and file record

After further testing was stopped, release assembly used exactly:

```sh
./gradlew build -Pversion=0.6.2 -x check -x runGameTest -x test -x testKingdomNames
```

**BUILD SUCCESSFUL in 14 seconds.** Common compilation was up-to-date, client
compilation and JAR remapping completed. No test tasks ran in this package build.
This is not a fresh all-GameTests-passing release verification claim.

JAR inspection confirmed metadata version 0.6.2, Minecraft `~1.21.1`, Java 21 class
versions, the atlas renderer/view, both crisp textures and their metadata, 252
language keys in each language, one existing political-map renderer and no test
classes. Only afterward was `gradle.properties` advanced to 0.6.2.

- Release: `build/libs/project-reigns-0.6.2.jar` (224,791 bytes).
- SHA-256: `c12cae48b8fab1d98c414be43ab42e0d6c1d291bc1b1891cdc364fe8b7fe5535`.
- Sources: `build/libs/project-reigns-0.6.2-sources.jar` (101,266 bytes).
- No dependency, packet schema, SavedData, sovereignty, claim ownership, migration,
  protection, administrative deletion or test-source changes in this milestone.
- No commit or push. Existing workspace changes remain in place.

Exact implementation/documentation files added or modified for v0.6.2:

```text
README.md
gradle.properties
docs/ui-design-system.md
docs/images/living-atlas-wide-en.png
docs/images/living-atlas-small-en.png
src/client/java/dev/sakhno/regnum/nations/client/ui/AtlasGraphics.java
src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomAtlasView.java
src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomManagementScreen.java
src/client/resources/assets/regnum-nations/textures/gui/atlas/ornaments.png
src/client/resources/assets/regnum-nations/textures/gui/atlas/ornaments.png.mcmeta
src/client/resources/assets/regnum-nations/textures/gui/atlas/folio_tile.png
src/client/resources/assets/regnum-nations/textures/gui/atlas/folio_tile.png.mcmeta
src/main/resources/assets/regnum-nations/lang/en_us.json
src/main/resources/assets/regnum-nations/lang/uk_ua.json
tools/generate_atlas_assets.py
tools/atlas-assets.md
```

### Manual screenshot steps for a later review

These instructions are provided for the user; they were not executed after the
request to stop testing.

1. Run `./gradlew runClient` and use a disposable Creative world with commands
   enabled. Found a city and elevate it with a neighboring State Obelisk, or open
   an existing realm. Use `/nations menu` and select **Kingdom**.
2. In Video Settings choose GUI scale **2**. Use actual client areas of 1280×800
   (16:10), 1280×720 (16:9), 1280×960 (4:3), and 640×480 (minimum logical 320×240).
   Window borders/title bars do not count toward client-area dimensions.
3. Move the pointer into the outer margin so no informational hover obscures the
   composition. Press **F2** to save Minecraft's unmodified screenshot to
   `<game directory>/screenshots/`. Capture both the collapsed overview and the
   expanded technical record. Scroll the record to reveal every row.
4. Use Tab/Shift+Tab from the active Kingdom tab through Territories, Map, technical
   toggle, Refresh and Close; activate with Enter/Space. Check Refresh and Map/Back
   preserve the section and disclosure. Esc still closes the management screen.
5. Change Language to **Українська (Україна)** and repeat. Include a long Ukrainian
   kingdom name and verify full hover text, placeholders and glyphs. Also capture
   a visitor, independent city/no-kingdom, loading/error and missing/unresolved
   capital-anchor state where practical.
6. Compare the same kingdom across reopening/restart/rename: charge and division
   should be identical. Check light/dark political colors, especially gold, without
   expecting new heraldry editing or a kingdom-color editing action in this screen.

### Future adaptation

Keep this a single-screen reference until reviewed. A future City variant may
adapt the folio and native tabs with a civic seal and smaller standard, retaining
personal-domain ownership wording. The political map may later reuse the engraving
and restrained key symbols around its existing canvas; do not paste this entire
folio over the map or reduce usable territory area. Founding flows need a separate
composition review. None of those adaptations is implemented in v0.6.2.

---

The following sections retain the v0.6.1 foundation and verification history.
The Kingdom layout above supersedes only the older Kingdom presentation.

## Royal Cartography — Regnum Nations v0.6.1 (foundation and history)

## Philosophy and boundaries

A restrained political atlas: obsidian surroundings, slate frames, parchment type,
muted gold hierarchy and burgundy danger. Minecraft's own font, logical GUI pixels,
focus paths, buttons, fields, tooltips and narration remain the interaction model.
There are no external assets, web surfaces, dependencies or independent layout engine.

Client widgets describe snapshots and submit requests. Their selection, previews,
scroll positions, cached geometry and focus are ephemeral. A preview is never a
confirmed political identity. Ownership, palette validation, claims, protection,
selection expiry, migration and administration remain server responsibilities.

## Tokens and implementation

`src/client/java/dev/sakhno/regnum/nations/client/ui/KingdomUi.java` owns tokens and small
rendering/widget primitives. `RoyalScreen` owns the shared non-pausing, sharp
background and initial-focus policy; screens compose ordinary native widgets.

| Token | Color | Use |
| --- | --- | --- |
| `BACKGROUND` | `#171A1F` | Obsidian backdrop |
| `PANEL` | `#272C34` | Slate frames |
| `RECESS` | `#20242B` | Fields, buttons, information rows |
| `GOLD` | `#C5A46A` | Headings, focus, primary action borders, personal marks |
| `INK` | `#E5D9BE` | Main text |
| `MUTED` | `#A8A9AD` | Secondary and disabled text |
| `BURGUNDY` | `#834751` | Destructive action borders |
| `ERROR` | `#EFA9B3` | Readable warning text; burgundy itself is not body text |
| `BORDER` | `#64656A` | Structural separation |
| `HOVER` | `#353B45` | Hover/focus surface |
| `SELECTED` | `#F5EEDC` | Selected map chunk and picker check |
| `WILDERNESS` / `UNKNOWN` | `#303740` / `#14171C` | Confirmed unclaimed / missing snapshot cells |

Spacing is in logical pixels: outer margin 8, panel padding 12, control gap 6,
control height 20, text line step 10, information row step 18, header 34 and footer
32. Screen-specific form/inspector geometry stays next to its screen, rather than
being repeated as unrelated widget coordinates. Borders and map lines use integer
pixels; zoom transforms are floored consistently at cell boundaries.

Use the native 9-pixel font at GUI scale. Hierarchy comes from color, position,
spacing, inset surfaces and restrained markers, not fractional font scaling.
Foreground text is sharp. `RoyalScreen.renderBackground` intentionally avoids
vanilla blur: in 1.21.1 `Screen.render` calls it after our foreground drawing.

## Components and states

- `panel`, `frame`, `heading`: one border and surface vocabulary, small title rule
  and a narrow gold title marker. Decorative chrome yields to content first.
- `RoyalButton`: native `Button` behavior and narration. Normal uses a slate surface;
  primary adds a gold border; danger uses burgundy and warning text. Hover lightens
  the surface. Focus adds a parchment top rule independently of hover. Disabled
  keeps readable secondary text and a dark surface. Selected tabs add a side bar
  and narrated selection; they remain focusable and can be revisited.
- `field`: native `EditBox`, labeled above, with native cursor, selection, clipboard
  and horizontal scrolling. Focus has a gold outer border. Validation feedback is
  visible below the field, available in its tooltip, and included in screen narration.
- `ColorSwatch`: exactly the ten `PoliticalColors` indexes. A geometric check and
  border show selection independently of hue; each swatch has a localized name,
  tooltip and selected-state narration. The native button handles Tab/Enter/Space.
- `Readout`: native `AbstractScrollWidget` with cached, ellipsized rows. Hover gives
  full text, including UUIDs. Focus narrates the complete data; Up/Down scroll and
  Tab leaves. Data refresh retains scroll/focus when the same kind of content exists.
- `paragraph`: native `FittingMultiLineTextWidget` for prose, warnings and empty
  states. Long content scrolls and narrates completely instead of extending behind
  fixed controls. Native scrollbars deliberately retain Minecraft behavior.
- `tooltip`: wrap to available logical width, clamp both axes, limit excessive
  tooltip height and render after leaving any scissor. A tooltip supplements data;
  required founding consequences also live in a scrollable/narrated paragraph.
- Confirmation pattern: named target, consequences, explicit primary/danger Confirm
  and separate Cancel. Keep the actions pinned, with descriptive prose scrollable.
  Founding composes this pattern with native fields; administrative dissolution uses
  native chat components through `KingdomDeletionPrompt`, without a client dialog or
  permission flag.

## Screen patterns

### Found City / Found Kingdom

One centered frame, at most 420×286 logical pixels. Title, contextual description,
domain-slot rule, labeled name, local name-rule feedback, ten swatches, live map-color
sample and explicit Confirm/Cancel. The sample says it is a preview. Initial focus
is the name field; Tab reaches every swatch and both actions. At 320×240 the prose
scrolls while the entire form and action row remain in view.

City founding creates an independent city and claims its obelisk chunk, consuming
one personal domain slot. Elevating a city consumes no additional slot. The kingdom
copy also explicitly explains the legacy combined city/kingdom path when no city
exists. It makes no promises about future government or citizenship systems.

Local validation reuses `KingdomName`; uniqueness and all gameplay validation stay
on the server. The first submitted request locks the form. Rejection or timeout
shows feedback and instructions to close/reselect, since failed server founding
attempts consume the selection. The server's 30-second lifetime is unchanged; a
client timer never grants or extends it. Esc and Cancel return to the game. After
submission the secondary action reads Close: leaving the screen cannot undo an
already submitted server request.

### City, Kingdom and Territories

One compact frame, capped at 560×290. At widths below 440 use a four-entry tab row;
otherwise use a small sidebar. A political-color chip and parchment identity sit
above the scrollable facts. No miniature political map or speculative system cards.

City shows owner/access, independent or affiliated identity, political color, claim
count, city anchor status/location and full UUID. Kingdom shows ruler, sovereign
color, aggregate kingdom claims, capital state-anchor status/location, viewer access
and UUID. Visitors are explicitly read-only. The absent-kingdom state explains city
independence and the actual State Obelisk elevation path. No-city, loading, unavailable
and stale/error states have meaningful copy and Refresh where appropriate.

Territories describes personal ownership, the viewed city's claims, cardinal
adjacency/standing requirements and capital reservations. Its primary **Open
Political Map** action always opens View. Closing the map restores the same section
and requests current state. Footer feedback truncates with full hover/narration.

**Existing payload constraint:** `Overview` contains aggregate kingdom claims and
the state anchor, but no aggregate city count or capital-settlement name/UUID. The
UI therefore reports actual claim totals and capital anchor coordinates, without
inventing a city count, mistaking an inspected subordinate city for the capital,
or inferring global totals from a bounded map. These fields would require a separate
protocol decision; v0.6.1 leaves packet schemas unchanged. City color in that snapshot
is the effective political color; an affiliated city's private stored fallback color
is not separately exposed.

### Political map

The single map keeps View/Manage, bounded snapshots, selection/inspection, claim and
unclaim, pan, anchored zoom, Fit and Refresh. A narrower legend and shorter inspector
chrome increase the usable map at 320×240 compared with v0.6.0. Native footer controls
stay in bounds. View/Manage switching and mutation refresh retain pan/zoom.

- Subdued political fill; one-pixel cell grid and neutral city boundaries.
- Gold L corner marks for the player's personal settlement, without recoloring it.
- White inset selection with dark separation; gold current-chunk outline and cross.
- Slate confirmed wilderness; darkest cells outside/omitted from a snapshot. A partial
  snapshot never implies that omitted cells are wilderness.
- Legend samples for personal domain, selection and unknown; scrollable visible-city
  entries. A notched swatch denotes an independent city-state; a square indicates
  kingdom affiliation. Hover spells out the city and sovereign names.
- Selected inspector shows coordinates, city owner and independent/sovereign identity;
  hover includes exact adjacency. One action/message owns management feedback. Claim
  and danger-styled Unclaim enable only from current authoritative reasons plus the
  existing presence/request guards. Disabled-action tooltips explain the reason.
- The canvas is a native focusable/narratable widget: arrows select, Ctrl+arrows pan,
  Home fits, Tab reaches controls. Mouse drag/scroll still pan/zoom. Zoom glyphs have
  complete spoken labels. Map geometry, border flags, name widths, legend order and
  labels cache across unchanged frames. Grid rendering scales with axes rather than
  repainting every unknown cell. Cache rebuilds never request gameplay state.

### Administrative dissolution

`/nations admin delete <exact UUID or full quoted name>` prints the server-resolved
name, consequence summary and red, bold, underlined **[CONFIRM DISSOLUTION]** alongside
**[CANCEL]**. `ClickEvent.Action.RUN_COMMAND` executes the exact server-issued UUID/token
command; it never accepts a display name as authority. Chat uses Minecraft's native
red for readable danger text rather than importing client theme code into the server.

Secondary gray lines retain full textual confirm and cancel commands for keyboard,
console and unsupported chat interaction; `SUGGEST_COMMAND` can populate the chat input.
The token is not the primary control. Old textual confirmation remains supported:

```text
/nations admin delete <kingdomUUID> confirm <token>
/nations admin delete <kingdomUUID> cancel <token>
```

The 30-second server-tick deadline, level-3 permission rechecks, executor/session and
target binding, exact resolution, one-use confirmation, audit entries and retained
cities/claims remain. Repeating the same request reprints the same token with an
explicit unchanged-deadline message. Another target is rejected until cancel/expiry.
Cancel validates that exact pending request and invalidates it server-side; stale
Cancel links cannot invalidate a newer request. Old chat messages can remain visible,
but their stale actions cannot dissolve anything. A request from a changed player entity invalidates the old session prompt with
explicit retry feedback. Disconnect/server-stop cleanup and confirmation consumption
remain in the service. There is no delete-all or city
removal action.

## Political colors and contrast

Political color belongs to territory fill, identity chips, swatches and explicit
political tooltips. Keep ordinary information in parchment/secondary text, even for
a dark political color. A user-selected gold territory must remain distinguishable
from UI gold through its subdued fill, chip context and personal-domain marker shape.
Never change saved palette values or add arbitrary RGB input for presentation.

Main/secondary/gold/warning text has at least 4.5:1 contrast against slate. Burgundy
is a border/accent, not small text. Do not communicate selection, warning or access
only through hue: use checks, borders, wording and narration. Keep native focus
and narration entries registered through `addRenderableWidget`; never paint an
unregistered interactive hitbox. Tooltips do not grant permissions.

## Contributor examples

| Do | Don't |
| --- | --- |
| Use `RoyalButton(..., ButtonStyle.DANGER)` for a destructive action | Style dissolution as an ordinary gold primary action |
| Use `PoliticalColors` index and an explicit preview label | Send client RGB or imply a preview is already saved |
| Wrap/scroll long prose; ellipsize a row with a full tooltip | Draw long names, UUIDs or warnings into another control |
| Keep native Tab/Enter/Space, field clipboard and narration | Handle mouse rectangles while forgetting keyboard widgets |
| Show authoritative action reasons and meaningful empty states | Enable a fake management action for a visitor |
| Cache map text/geometry when its inputs change | Sort and measure every city's name every rendered frame |
| Add every key to both languages with matching argument positions | Concatenate English fragments or omit Ukrainian controls |
| Preserve packet/persistence/gameplay boundaries | Infer global city totals from a truncated nearby snapshot |

## Manual UI acceptance checklist

Use a disposable Creative world and matching clients. These are manual checks,
not claims that rendered or multiplayer verification has been performed.

1. Test 16:9, 16:10 and 4:3 windows, Auto/common GUI scales and logical 320×240.
   Open each screen, resize while focused, and check chrome, borders, scrollers,
   control bounds and sharp foreground with Minecraft menu blur enabled.
2. In English and Ukrainian, found/cancel a city, elevate it and use the legacy
   combined path. Enter invalid/long Unicode names; Tab through all ten colors and
   Confirm/Cancel, use Enter/Space, and inspect check marks/live preview. Read/scroll
   consequences. Expire the selection or replace the obelisk; verify actionable
   rejection, no client success fiction and unchanged server rules.
3. Inspect City/Kingdom/Territories as owner and visitor; inspect an independent
   city and a no-city player. Check long names/UUID tooltips, keyboard-scrolled facts,
   read-only wording, meaningful empty states and map Back/Esc returning to the same
   refreshed section. No government, citizenship, diplomacy or economy placeholders.
4. On M, compare allied cities, independent cities, golden political fill and gold
   personal marks. Select a chunk with arrows/mouse, pan/zoom/Fit, scroll the legend,
   inspect all four edges, Refresh and switch modes. Test wilderness, omitted cells,
   capital/foreign/remote refusal, successful claim/unclaim and stale dimension data.
5. As level 3+, request dissolution by quoted name. Click red Confirm; cities, owners,
   blocks and claims survive independently. Repeat a pending request, choose another
   target, Cancel, try the old links, expire one, de-op and reconnect. No stale or
   unauthorized link may mutate state. Try both textual commands and inspect audits.
6. With narration enabled, check initial name focus, tab selection, named swatches,
   selected-state announcements, full detail/empty/error messages, map keyboard
   instructions and zoom labels. Inspect Ukrainian letters Ґ/ґ, Є/є, І/і, Ї/ї,
   apostrophes and inserted values visually; native-font coverage alone is not a
   rendered typography test.


## v0.6.1 verification record

- Incremental common/client/GameTest compilation and `testKingdomNames` passed.
- The single final `./gradlew build -Pversion=0.6.1` completed successfully in
  36 seconds. All **35 required server GameTests passed**: the unchanged previous
  32 and exactly three new `RoyalCartographyTests`. The separate `test` task has
  no JUnit source, as before; GameTests and the name-rule runner provide coverage.
- New regressions inspect emitted localized chat components and execute their exact
  issued commands through Brigadier; verify danger styling, textual fallback,
  surviving city/claim identity, cancellation, old-link replay, stale Cancel versus
  a fresh token, pending target refusal, unchanged original expiry, session change
  and permission rejection. They do not simulate a rendered mouse click.
- Resource JSON parses; English and Ukrainian have **229 matching keys**, with
  matching formatting argument positions/types (including reordered arguments).
  The native bundled Unifont contains all Cyrillic characters used by the Ukrainian
  translation. Actual glyph appearance remains a rendered-client manual check.
- Arithmetic checks kept founding/map controls in bounds at nine logical sizes,
  including 320×240, 640×360, 640×400 and 640×480. Text contrast on slate is 10.03:1
  for parchment, 5.98:1 for secondary text, 5.94:1 for gold and 7.35:1 for warnings.
  These are static checks, not visual verification.
- Compared with the session-start v0.6.0 files, all existing test Java files,
  network payload classes, dependency/build configuration, SavedData, settlement,
  claim/protection and map/management server implementations remain unchanged.
  Common code has no client imports. Administrative changes are limited to the
  command prompt and ephemeral deletion confirmation preparation/cancellation.
- Packaged JAR verified for ZIP integrity, version 0.6.1, Minecraft 1.21.1, Java 21
  class versions, required UI/admin classes, one political map renderer, both
  translations and exclusion of test classes. Only after that successful build and
  artifact verification was `gradle.properties` set to 0.6.1.
- Release: `build/libs/project-reigns-0.6.1.jar` (205,081 bytes).
  SHA-256: `08f87c11878b0c8e8d0b846b7c71a02d972eb34186b354ec001cf1a9f7b1b695`.
  Sources: `build/libs/project-reigns-0.6.1-sources.jar`.
- Previous generated GameTest worlds were retained at
  `build/run/gameTest/world-backup-v061-20261109-102851` and
  `build/run/gameTest/world-backup-v061-interim-20261109-103959`; the final build
  used a fresh generated test world. No gameplay save was changed.
- **Not performed:** rendered UI/GUI-scale/keyboard/narrator verification, actual
  singleplayer or live two-player testing, chat interaction on a connected client,
  normal dedicated-server startup, chunk unload/reload cycles or full process
  restarts of upgraded gameplay worlds. Follow the acceptance checklist above.
  Aggregate city count, capital-settlement name and private affiliated-city fallback
  color remain unavailable in the preserved payload, as explained above.
- No new dependencies/assets, SavedData migration, commit or push.

</details>
