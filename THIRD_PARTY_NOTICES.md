# Third-party notices

Living Atlas includes a terrain subset adapted from
[Diorama v0.2.1](https://github.com/SunkenInTime/diorama/tree/v0.2.1),
Copyright (c) 2026 Dara Adedeji, under the MIT license.

The adapted files live in `src/client/java/dev/sakhno/regnum/nations/client/terrain/diorama/`
and the three `Atlas*` hooks in `src/client/java/dev/sakhno/regnum/nations/client/mixin/`.
The complete upstream license is in `third_party/diorama/LICENSE`, and the exact
revision and source file inventory are in `third_party/diorama/provenance.json`.
Binary JARs include the license at `META-INF/licenses/diorama/LICENSE` and this
notice; source JARs include the license and provenance under `third_party/diorama/`.
These files retain their MIT license; original Regnum Nations code remains CC0-1.0.

Adaptations specialize the upstream multi-version source for Fabric 1.21.1,
relocate packages, remove optional nullability annotations, isolate a vertical
camera, adapt texture drawing to Living Atlas, and bound snapshot/upload work.
The original sampling, biome tinting, terrain shading, tile baking, mipmaps,
invalidation, and tile storage are reused. See `docs/living-atlas-terrain.md`.

Conflux Map was inspected but no Conflux source, assets or libraries are included.
Neither mapping mod is a required dependency.
