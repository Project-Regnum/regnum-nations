# Living Atlas terrain integration

## Technical assessment and approved approach

The user approved extracting MIT Diorama terrain code, keeping Living Atlas
top-down, and requiring no external mapping mod. Conflux code is not included.

Both repositories were cloned outside this repository under a temporary sibling
directory. Inspection covered licenses, version configurations, chunk capture,
tile composition, persistence, rendering, and GUI coupling.

| Project | Inspected revision | Finding |
| --- | --- | --- |
| Conflux Map | `a966dcf124fbfb26234f82570f710e728e946d93`, also release `v0.1.16` (`88c51a694c409076b9cee3b95a8cd0ce9fa1366c`) | GPL-3.0; explicit Fabric 1.21.1 build. |
| Diorama | `8e8a21bbf285c247298f5eb2258a1ce3928be682`, then release `v0.2.1` (`d9f4a28`, full revision recorded with attribution) | MIT; explicit Fabric 1.21.1 build using Mojang mappings, Java 21 and Fabric API `0.116.17+1.21.1`. |

**Selected source: Diorama v0.2.1.** This is an early stable release with the
1.21.1 port and the necessary terrain pipeline, before later GPU baking,
moving contraptions and additional integrations expanded its dependency graph.
The source subset will be specialized to Fabric 1.21.1 and relocated under
the Regnum Nations client package. Existing dependency versions stay fixed.

Conflux's documented `ConfluxMapApi.mapData().snapshotTile(...)` returns
asynchronous 256-pixel ARGB tiles without opening its GUI. Its pipeline uses
`ChunkCaptureService`, `TileService`, `RegionCacheService`, `RegionDiskCache`
and `TileTextureManager`; session guards isolate world/dimension data.
It supports loaded-chunk capture without a server companion; sharing and
server-wide mapping are separate server features. Its API is experimental,
published via JitPack, and supplied by the installed mod at runtime. No
separately permissive API license or linking exception was found. CC0 code can
participate in a GPL distribution, but approving that distribution is a separate
decision; it was not authorized here. This approach was stopped before copying.
See the pinned [license](https://github.com/Conflux-Union/conflux-map/blob/88c51a694c409076b9cee3b95a8cd0ce9fa1366c/LICENSE)
and [API guide](https://github.com/Conflux-Union/conflux-map/blob/88c51a694c409076b9cee3b95a8cd0ce9fa1366c/docs/api.md).

Diorama's terrain code is separable from its original Screen, although it does
not publish a standalone terrain-library API. The minimum useful source family
is `ChunkSnapshot`, `Sections`, `BlockLooks`, `BiomeTints`, `VoxelReader`,
`TileBaker`, `TileImage`, `TileKey`, `Mipmaps`, `MapLevel`, `MapTile`, and
`TileStore`, plus projection/style, coverage and texture support. These retain
snapshot sampling, terrain colors, vegetation/water blending, shading, worker
baking, partial invalidation, bounded tile caching and saved overview tiles.
No minimap, waypoint UI, radar, teleportation or seed prediction is needed.

The upstream camera is oblique: map rows depend on both elevation and Z.
Directly drawing its tiles beneath the existing square claims would misalign
terrain. The extraction will use an isolated vertical camera, including safe
zero-direction ray stepping, rather than changing an installed Diorama's global
style. The original terrain baker is reused; a new sampling engine is not needed.

The [MIT license](https://github.com/SunkenInTime/diorama/blob/v0.2.1/LICENSE)
requires retaining Dara Adedeji's copyright and permission notice. A complete
license will accompany the adapted source and be packaged in binary/source JARs;
Fabric metadata and attribution will identify the MIT subset alongside the
project's CC0 code. JSpecify nullability annotations can be removed during
specialization, avoiding an additional dependency. No Conflux, Diorama,
JourneyMap, native library, or new Gradle plugin is required at runtime.

## Integration plan

1. Extract the approved terrain subset, retaining its provenance and license;
   specialize Minecraft calls to verified local 1.21.1 mappings. Keep every
   Minecraft client reference in `src/client` and client-only mixins.
2. Capture bounded loaded chunks on the client thread and bake their copied
   snapshots on workers. Dirty hooks trigger updates; unloaded chunks are never
   force-loaded. Cache terrain separately by world and dimension, with session
   invalidation and explicit resource cleanup.
3. Draw only visible tiles inside `PoliticalMapScreen`'s scissor. Reuse its
   origin and zoom: a block spans `zoom / 16` GUI pixels. Terrain precedes
   translucent political fills, existing exterior city/kingdom borders, labels
   and selection. All layers use the same coordinate transform and rounding.
4. Preserve medieval controls, pan, zoom, ownership inspection and mutation
   permissions. Add a compact layer control. Unknown terrain stays unknown;
   political snapshot coverage is not evidence of explored terrain.
5. Add practical coordinate/cache/persistence coverage, run the complete build
   (including headless server GameTests), verify packaged metadata/notices, and
   record client checks and limitations before opening a PR against `main`.

The existing map snapshot contains claims, names and sovereignty, but no city
obelisk positions or capital flags. Exact city/capital markers need separately
bounded server-supplied data; their positions must not be guessed from arbitrary
claimed chunks or client world scanning. Existing wire identifiers/codecs and
kingdom persistence contracts must remain intact.

## Initial verification

Before implementation, `./gradlew build --no-daemon --console=plain` passed,
including all 40 required server GameTests and the kingdom-name rule checks.
This is baseline evidence only, not validation of the forthcoming terrain code.
No interactive terrain or gameplay test has been performed at this checkpoint.
