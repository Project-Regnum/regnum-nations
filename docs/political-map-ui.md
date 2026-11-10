# Political map UI — 0.4.1

For current terrain and city/capital markers, see
[Living Atlas terrain](living-atlas-terrain.md). The details below are historical.

For the current founding GUI, kingdom menu and interactive map controls, see
[kingdom management v0.5.0](kingdom-management.md). This document retains the
earlier milestone behavior and verification history.

Client-only update: charcoal panels, muted gold trim, desaturated kingdom colors,
compact swatches, gold player cross, localized vanilla dimensions, and custom button
painting retaining Minecraft Button focus, keyboard activation and narration.
The existing opaque background and no-op renderBackground override remain: no
vanilla background or blur pass runs after map content.

Opening/refresh fits snapshot claims around the player with two chunks of padding
and 4–64 GUI pixels per chunk bounds. Fit repeats this without a network request.
Wheel zoom remains anchored to the cursor; dragging starts only on the viewport
and continues until release, including beyond viewport edges. Controls use the
existing GUI font at native scale. Layout derives the sidebar width from GUI size.

Kingdom names require enough contiguous horizontal owned space at readable zoom.
Label rectangles avoid each other, the compass and the player marker; full names
remain available through map and legend hover tooltips. The sorted legend scrolls
with the wheel independently of the map and reports overflow.

No networking, server validation or ownership behavior changed. Capital data is
absent from the existing snapshot, so no capital markers are shown. Far-spread
claims may exceed fit bounds at minimum zoom. Short windows show fewer legend
rows, and narrow sidebars truncate names (hover for full text). Extremely small
GUI sizes below Minecraft's usual 320×240 logical minimum are not targeted.

## Manual visual checks

- Open M beside a single claim: verify a prominent cell, sharp text, gold marker
  and no label on top of it. Check a larger kingdom and several adjacent owners.
- Zoom in/out with the cursor over a border: the same map point should stay below
  the cursor. Drag across the viewport edge, release and move back: no stuck pan.
- Hover claims and truncated legend names. Scroll a long legend; confirm the map
  zoom does not change. Inspect borders and ensure map elements remain clipped.
- Tab through controls and activate with Enter/Space; inspect focus/hover trim.
  Try Fit, Refresh, Close and Escape. Refresh must still request fresh ownership.
- Check English/Ukrainian, each vanilla dimension, common GUI scales, resizing and
  a 320×240 logical window. Keep menu blur enabled: the map must remain sharp.
- Confirm loading, empty, unavailable and partial-result states remain readable.

Verification commands: ./gradlew compileClientJava, then
./gradlew build -Pversion=0.4.1. Interactive visual verification was not performed.

Result: both commands passed. The final build included the existing kingdom-name
checks and all 23 dedicated-server GameTests. No tests were added. Version was
updated to 0.4.1 after that build succeeded.
