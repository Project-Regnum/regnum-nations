# Shared Regnum button styling

This change builds on PR #4 (`2e54929`) on `feat/unified-royal-buttons`.
The source-level review below was performed on 2026-11-10. It is not compilation,
a test result or rendered-client validation.

## One shared implementation

`KingdomUi.RoyalButton` now owns the Political Map button frame: chamfered edges,
dark inset background, muted gold borders, normal/hover/focus/selected/disabled
states and the existing primary/danger accents. Native activation adds 120 ms of
visual pressed feedback with a one-logical-pixel content offset, then delegates
to the existing callback once. No keyboard, mouse-click or permission handler is
replaced. Controls remain 20 logical pixels high, with six-pixel content padding.
Map icons remain 11 logical pixels with a four-pixel gap before their labels.

`RoyalControlButton` only supplies icon content and the three-position map-layer
indicator. Its duplicate frame, selected flag and style flag are removed.
`AtlasWidgets.Tab` inherits the same frame and adds a small polity-color stripe
inside the selected tab. `ColorSwatch` inherits it too, retaining its current color
selection supplier, political colors and selected check mark. Focus marks render
after content so the swatch color cannot cover them. Disabled content stays muted.

Button labels are centered, bounded and ellipsized inside their existing hitboxes.
A nested scissor keeps content within each button even with long localized labels.
Default tooltips retain complete labels; existing specialized map tooltips still
override them. Native narration retains full labels and selected-state hints.

## Screen inventory and source review

| Screen or flow | Source reviewed | Controls using the shared style |
| --- | --- | --- |
| City and Municipal Records | `CityAtlasView`, hosted by `KingdomManagementScreen` | Four navigation tabs, record disclosure, Refresh and Close |
| Territories / personal-land register | `CityAtlasView` territory branch | Navigation, primary Open Map action, Refresh and Close |
| Kingdom and Sovereign Realm / technical record | `KingdomAtlasView`, hosted by `KingdomManagementScreen` | Navigation, record disclosure, Refresh and Close |
| Political Map | `PoliticalMapScreen`, `RoyalControlButton` | Menu, View/Manage, inspector claim/release, Refresh, zoom/Fit, Layers and header Close |
| City and kingdom founding, elevation and legacy combined founding | `KingdomFoundingScreen` | Primary confirmation, Cancel/Close and existing political-color swatches |
| City/state obelisk interactions | `RegnumNationsClient` screen-opening routes | Existing founding or management screens above; no separate obelisk button screen exists |

The repository's three concrete screens are `KingdomFoundingScreen`,
`KingdomManagementScreen` and `PoliticalMapScreen`; Municipal Records and Sovereign
Realm are views within management. A source inventory found no additional concrete
screen class or independent plain/native button builder needing conversion.

Reviewed control construction, native `Button.onPress`/widget API signatures in
the local mapped 1.21.1 JAR, callback delegation, selected/danger presentation,
management focus restoration after widget rebuilds, tab creation order, narrator
hints and the live color-selection supplier. Client routing and screen callbacks
were read without modification. Map gestures, key handlers, Escape behavior,
action availability and server permission checks are unchanged.

For compact layouts, the existing `FolioBounds`, 20-pixel control heights, row-width
allocation and inter-control gaps are retained. At the existing 320×240 logical
layout, the founding form keeps its two 133-pixel footer buttons six pixels apart;
management tabs stay inside their allocated row, and disclosure/footer controls
retain separate vertical bands. The map retains its grouped footer and reserved
header Close space from PR #4. The frame occupies the same hitbox and selected
indicators stay inset; content clipping prevents labels painting over neighbors.
These are source-level observations, not a guarantee of rendered layout.

## Scope and unverified behavior

No Gradle build, compilation, automated tests, GameTests, automated client checks,
package verification or validation suite was run, as explicitly requested. No
client was launched and no new screenshots were produced. Both commits include
`[skip ci]` to suppress the repository's push/pull-request validation workflow.
Previous PR results do not validate these changes.

Compilation and runtime behavior remain unverified, including the new pressed
feedback, tooltip positioning, visible focus/selected/disabled contrast, icons,
full label readability, tab navigation and control activation. Actual overlap or
clipping at different GUI scales, small resolutions, long translations and
resource-pack fonts still needs visual review. Loading, empty, visitor, pending,
error and expanded-record variants were reviewed in source but not displayed.

Only three client UI source files change. Terrain rendering, screen layouts,
networking, claims, saved data, dependencies, world/dimension handling and gameplay
rules are unchanged. The existing political-color picker is restyled; no heraldry
or banner editing is introduced.
