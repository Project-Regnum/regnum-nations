# Animated obelisk crystals

The manually edited Blockbench sources in `art/obelisks/` are authoritative.
This change imports the artist's files byte-for-byte, exports their geometry,
rotations, UVs and embedded textures, and animates only the crystal geometry.
Stone, metal, inscriptions, the City uprights and the royal levitation socket
remain static. Items include the complete authored models at their rest pose.

## Source preservation and export

The source snapshot was copied from
`/home/sanyacat/Documents/regnum-nations-obelisk-art/art/obelisks/` into this
feature worktree. Neither original file was written or regenerated.

| Source | SHA-256 at import | Geometry |
| --- | --- | --- |
| `city_obelisk.bbmodel` | `a6f38cb84c8a02ecbbb7baaff16e51d2ab337555aa0ec13004ebe23b0b165acf` | 52 cubes, including one amber diamond; manually removed roof/cap/finial stay removed |
| `kingdom_obelisk.bbmodel` | `208edb2b4f2ffd2425dbfc70a52a3c04163af5b986d33010a251d61c3ec8f991` | 88 cubes, including one sapphire; refined bosses, shoulders, buttresses and crown widths retained |

Both use Blockbench 5's Generic Model format, seven named groups, embedded 64×64
PNGs and 16-unit UV coordinates. All ten embedded PNGs match the prior pixels;
the manual geometry and UV rounding changes are exported directly. The City
source has three fewer cubes, while the royal source retains all 88.

Run `python3 tools/obelisk_assets.py` to export updated sources. This is now a
read-only source converter, replacing the old procedural authoring recipe.
It writes seven runtime model JSONs, ten texture files and
`assets/regnum-nations/obelisk_animation.json`. It never writes `.bbmodel` files.
`python3 tools/obelisk_assets.py --check` compares current outputs against the
source snapshot without writing. No Pillow or other Python package is needed.

The converter preserves cube pivots, supported single-axis rotations, face
rotations, mirrored UV order, texture references and export flags. It supports
the current sources' zero-rotation groups. Unsupported nonzero group rotations,
meshes or multi-axis cube rotations fail explicitly rather than silently losing
edits. Keep textures embedded when saving. After future geometry edits, re-export
and rerun clearance validation; animation settings must fit the new opening.
Existing inventory display transforms are retained unless the source supplies
explicit replacements; item geometry and textures always come from the source.

## Animation and pivot alignment

Both sources name their crystal group `07 Crystal`. Their group origins are
`[8,0,8]`, with zero rotation, so these are organizational anchors at the base.
The actual diamond cubes define the centered animation pivots:

| Profile | Authored cube pivot, model units | Rotation | Bob | Brightness |
| --- | --- | --- | --- | --- |
| City amber | `[8,27.25,8]` | 40 seconds per turn | ±0.25 model units, 5-second period | 94–100% |
| Kingdom sapphire | `[8,40.75,8]` | 30 seconds per turn | ±0.4 model units, 6-second period | 92–100% |

These pivots are read from the current source cube origins, not inherited from
old renderer constants. The exporter records them in animation metadata. The
royal glow model already renders one block above its anchor, so its local pivot
is `[8,24.75,8]`; the renderer applies that existing offset exactly once.

The royal `07 Crystal` group also contains a **levitation socket**. Membership
alone must not animate the whole group: the converter selects only the cube
whose faces use the `core` texture. The socket stays in the static crown model.
Static block/crown models exclude the diamonds, while the item models include
them once. The authored 45° diamond tilt remains baked into each glow model.

`ObeliskRenderer` draws static architecture first, then translates to the
exported pivot, adds a small sine bob, applies a Y-axis rotation, translates
back, and draws the glow model. Time is `level.getGameTime()` plus the supplied
partial tick. Integer modulo is applied before converting to floating point,
keeping interpolation precise in long-running worlds. Rotation and bob periods
share seamless 40/30-second loops. No frame counters, wall-clock jumps, new
block-entity fields, server tickers or animation packets are used.

`CrystalAnimation` loads metadata through the client resource reload system.
Every render resolves current baked models, so resource reload replaces both
geometry/textures and pivots. Invalid metadata disables movement and renders the
authored rest pose instead of keeping a stale pivot.

Brightness varies smoothly by multiplying the existing full-bright crystal's
RGB with a small sine envelope. Amber/blue colors come from the artist's actual
textures. This needs no `.png.mcmeta`, shaders, bloom, additional draw pass or
new dependency. It casts no world light and changes no spawn/lighting rules.

## Renders and verification

- [Current Blockbench source pair](images/animated-obelisks/concept-angle-pair.png),
  including the artist's open City frame.
- [Minecraft daylight](images/animated-obelisks/day-front.png),
  [night](images/animated-obelisks/night.png),
  [normal player height](images/animated-obelisks/normal-player.png),
  [inventory](images/animated-obelisks/inventory.png).
- [Recorded crystal motion](images/animated-obelisks/crystal-animation.mp4):
  actual Minecraft framebuffer captures, 240 frames at 20 client ticks/second.
  The excerpt demonstrates motion; mathematical checks cover complete loops.

Verification commands:

```sh
python3 tools/obelisk_assets.py --check
python3 tools/validate_obelisk_assets.py
python3 tools/validate_crystal_animation.py
./gradlew runObeliskTest --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Asset validation compares every exported vertex definition, UV, texture and
profile to the source, checks all model/texture paths and registry adapters,
and confirms embedded textures/group membership. Clearance validation samples
the complete loops at half-tick intervals with oriented-box separating axes,
then subtracts an upper bound on vertex travel between samples. Minimum
continuous separation from the artist's static geometry is at least **0.3064
model units for City** and **0.4808 for Kingdom**. This checks all angles and bob
phases while leaving the source geometry unchanged.

The opt-in Minecraft harness checks seven baked models, missing sprites,
absence of duplicate static crystals, baked center/exported pivot agreement,
partial-tick movement, loop boundaries, long-clock precision and resource
reload. It captures the real animated scene in a disposable world under
`build/run/obeliskTest/`; player worlds under `run/` are untouched. The release
JAR must contain the renderer, animation class, metadata and models, and must
exclude the test harness and editable source files.

Executed on 2026-11-11: export comparison, asset validation and continuous
clearance validation passed; `runObeliskTest` passed all client assertions and
recorded 240 actual frames; the full Gradle build passed the kingdom-name rules
and **all 96 required headless server GameTests**. JAR inspection verified both
animation classes, profile metadata, all seven model exports and ten textures,
with the visual harness and `.bbmodel` sources excluded. The two source copies
still matched the original artist files byte-for-byte after all exports/tests.
Sampled stone regions were pixel-identical between the first and last recorded
frames, while both crystal regions changed. New daylight/night/inventory and
resource-reload captures were inspected along with multiple animation phases.

No common gameplay code, registry ID, SavedData, founding, relocation,
citizenship, role, permission or network code changes. No dependency/version
changes. The original one-block collision/interaction targets and decorative
overhang behavior remain. Manual multiplayer, shader-pack compatibility and a
mass-obelisk performance benchmark remain untested; each visible crystal adds
one pivot transform and one existing six-quad draw, with no extra render pass.

This branch is based on `feat/obelisk-visual-overhaul` / PR #10 at `9fc7de8`,
which contains the existing renderer and geometry integration. Citizen
Management UI / PR #9 remains outside the branch.
