# Living Atlas artwork source

The original assets live under
`src/client/resources/assets/regnum-nations/textures/gui/atlas/`.
No proprietary artwork, external texture pack or new runtime dependency is used.

## Pixel ornaments

Run `python3 tools/generate_atlas_assets.py` (Python standard library only).
It writes the original 256×128 transparent `ornaments.png` sprite sheet and
nearest-filter texture metadata. Drawings in that script are the editable source.
The geometric heraldic charges deliberately describe identity, not gameplay powers.

| Region `(u, v, width, height)` | Artwork |
| --- | --- |
| `(0, 0, 16, 16)`; then u=16,32,48 | Four binding corners, mirrored in the source |
| `(0, 16, 16, 8)` | Horizontal engraved border tile |
| `(0, 24, 16, 8)` | Mirrored bottom border tile |
| `(16, 16, 8, 16)`; `(24, 16, 8, 16)` | Left and mirrored right border tiles |
| `(32, 16, 32, 8)` | Lozenge section divider |
| `(72, 16, 41, 8)` | Standard suspension rod and finials |
| `(0, 32, 32, 56)` | Grayscale cloth silhouette, multiplied by political color |
| `(32, 32, 32, 56)` | Untinted brass cloth trim |
| `(64, 32, 16, 16)`; then u=80,96,112 | Tower, north star, oak, crossed keys |
| `(208, 32, 24, 28)` | Dark heater shield; contrast behind all ten colors |
| `(64, 48, 12, 12)` | Personal-land register grid mark |
| `(80, 48, 12, 12)` | Capital gateway mark |
| `(64, 64, 32, 56)`; then u=96,128,160 | Pale, chevron, bend, quartering masks |

## Generated surface

`folio_tile.png` is a project-local 64×64, five-color surface derived from an
original image generated with the built-in imagegen tool. The generated source
was reduced with nearest-neighbor sampling and quantized to this RGB ramp:
`#222624`, `#252926`, `#282C28`, `#2C2F29`, `#32342B`.
Pillow already installed in the development environment was used for that one-time
conversion; neither the mod nor the ornament generator depends on it.
The large intermediate image is not loaded or packaged by Minecraft.

Final generation prompt:

> Create ONE original seamless tile texture for a Minecraft-native medieval political atlas interface, no UI mockup. Material: very dark weathered charcoal parchment with faint engraved cartographic hatching and tiny age marks, restrained handmade pixel art. The actual design should look like a 64 by 64 pixel tile enlarged with nearest-neighbor sampling: crisp square pixels, no antialiasing, no photographic grain, no blur. Entire square filled edge to edge; seamless repeats; exceptionally low contrast so ivory text remains easy to read. Palette strictly very dark warm charcoal slate approximately #242725, #272a28, #2a2d29, with occasional desaturated brass #36382e. Keep the middle and all edges equally subdued. NO text, NO letters, NO symbols, NO heraldry, NO map labels, NO border, NO framing, NO large features, NO gradients, NO logos or proprietary references. This is a tiny repeatable surface under an original heraldic atlas UI, not a large background illustration.

Both PNGs have `.png.mcmeta` with `blur: false` and `clamp: false`.
AtlasGraphics renders the corners at 1:1, repeats edge strips with cropped end tiles,
and draws the standard at integer 1× or 2×. Textures never supply text or hitboxes;
native Minecraft widgets and localized fonts own interaction and language.
