# Obelisk art sources

Open either `.bbmodel` in Blockbench as a Generic Model. All textures are embedded.
The sources contain the full monuments at 16 units per block, with named cubes
and numbered groups. See [asset integration and export instructions](../../docs/obelisk-models.md).
`tools/obelisk_assets.py` is the canonical repeatable export recipe; rerunning it
overwrites these source files, the PNGs and JSONs.

`concept-reference.png` was generated with the built-in imagegen tool as an art
direction reference only. It is not an in-game render or a texture atlas.
The delivered geometry and pixel textures were authored with the export recipe.
Actual Minecraft and Blockbench renders are under `docs/images/obelisks/`.

Iteration 2 widens the plinths, adds tapered corner buttresses and bent royal
supports, enlarges the suspended crystals, and replaces the texture set with
64×64 pixel-art sheets. Sources embed those updated sheets. The matching-angle
pair and untextured silhouettes are documented in the asset guide.

The imagegen prompt was:

> Use case: stylized-concept. Asset type: art direction reference only for a Minecraft 1.21.1 block model pair, not a screenshot. On a neutral warm-gray backdrop show two isolated Minecraft Vanilla+ royal medieval fantasy monuments at the same scale, city left 2 blocks tall and kingdom right 3 blocks tall, each fits a single block footprint. Entirely cuboidal geometry with occasional 45-degree single-axis cuboid rotations. City: layered gray slate pedestal, tall stepped taper stone pillar, bronze corner clasps, carved amber geometric vertical runes, small amber crystal held in a square bronze lantern cap. Kingdom: heavy layered charcoal blackstone pedestal, tall dark sculpted tapered pillar, antique gold edging, blue inset runes, open four-prong architectural gold crown around a floating blue faceted cubic crystal, substantial negative space between crown prongs. Restrained original Minecraft pixel art textures, coherent texel density, stone variation in broad clusters not static, warm muted bronze/gold, deep blue. Symmetric architectural design, strong and distinct silhouettes, elegant sturdy proportions, not smooth, no spheres, no neon, no text, no photorealism, no particles. Orthographic isometric three-quarter view, enough light to read dark stone.
