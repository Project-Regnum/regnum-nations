# Political map (v0.4.0)

For the current founding GUI, kingdom menu and interactive map controls, see
[kingdom management v0.5.0](kingdom-management.md). This document retains the
earlier milestone behavior and verification history.

Press **M** (rebindable in Controls) in a world to open the political map. North is up;
white marks your position. Drag with the left mouse button to pan, use the wheel or
−/+ buttons to zoom, and hover over a claim for its kingdom and chunk coordinates.
Escape or Close returns to the game. Refresh fetches a new snapshot and recenters.

The map reads `KingdomSavedData` on the server thread. No second authoritative
registry or persistence format is introduced. The client sends only a correlation
sequence; the server chooses the dimension, center and radius. Requests are limited
to one per player per 500 ms. Opening and manual refresh are the only sync triggers.
Disconnected players are removed from the rate limiter.

`config/project-reigns-map.properties` is generated on startup. Set `radiusChunks`
(default 32, clamped to 1–64) and restart the server. Each response includes dimension,
center, radius, status, and at most 4,096 cells with chunk coordinates, owner UUID,
name (bounded to 64 UTF-16 units), and a deterministic UUID-derived opaque color.
This bounds even worst-case UTF-8 data below the Fabric S2C payload limit. Registry
read failures return an error, and oversized regions return a partial-result notice.
The codec rejects invalid list lengths. No ownership data is accepted from clients.

All screen state, rendering, key handling and client receivers live in the client
source set. Replies only reach the active map screen with a matching request and
current dimension. A ten-second timeout supplies an error state for lost replies.
English and Ukrainian UI translations are included.

Limitations: this is a snapshot, so ownership changes require Refresh. Panning does
not fetch more territory. Dark cells outside the returned area are unknown. If the
cell cap is hit, omitted claims are unknown too; the displayed warning indicates
this. Kingdom colors can collide. Small views can truncate names; hover shows the
full name. The legend lists kingdoms intersecting the viewport, with overflow
indicated by an ellipsis. No terrain or other players are displayed.

## Manual verification

1. Start a dedicated Fabric server and join with this client mod. In a fresh world,
   press M: verify an empty map, dimension/chunk display and working close controls.
2. Use the existing kingdom founding and claim commands with two players. Open M:
   verify colors, white borders at owner transitions, names, legend and hover text.
   Check adjacent claims of the same owner have no white internal boundary.
3. Check negative chunk coordinates and north/south movement against F3. Pan and
   zoom; ensure cells, labels and player marker stay inside the viewport. Try common
   GUI scales and a smaller window.
4. Change claims while the map is open: Refresh should update and recenter it.
   Close and reopen to confirm fresh data. Verify server ownership is unchanged.
5. Travel to another dimension: only its local claims should appear. Pan beyond the
   fetched radius and confirm the darker unknown background. Change radius in the
   config, restart, and confirm the new bounds. A region over 4,096 claims should
   display the partial-result notice.
6. Join a server without map protocol support: opening M should show an error.
   Restart the normal server and verify existing kingdoms and claims still load.

Automated verification: `./gradlew compileJava compileClientJava`, followed by
`./gradlew build -Pversion=0.4.0`. No new dependencies or tests were added; existing
rule tests and build checks remain intact. Interactive gameplay is not verified by
these commands.

Verification result: compilation passed; final 0.4.0 build passed, including the
kingdom name checks and all 23 dedicated-server GameTests. The first build hit
ALREADY_BOUND in the protection test due to a previously persisted fixed capital.
The generated test world was preserved as a sibling world-backup directory under
build/run/gameTest, and the build passed with a fresh test world. No gameplay UI
testing was performed.
