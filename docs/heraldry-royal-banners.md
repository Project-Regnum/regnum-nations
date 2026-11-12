# Heraldry and royal banners — v0.9.0

## Foundation and dependency

This feature branches from PR #16's **unmerged** integration head
`33532d6c6ddfa95580e19db16895f990724d6821`, not outdated main. PR #16 combines
administration, diplomacy and territory hardening at schema 7. The feature PR
targets `integration/nations-v0.8.5` to keep its diff reviewable. Merge #16 first,
then retarget this PR to main; do not merge this feature independently onto an
older foundation. No existing PR is closed or merged by this work.

## Player guide

Open a kingdom obelisk, choose kingdom administration, then **Heraldry**.
Rulers and chancellors may edit official arms; others may inspect them. Native
buttons support Tab / Shift+Tab, arrow navigation and Enter/Space activation.
All pages fit a 320×240 logical GUI, as well as larger common GUI sizes.

The editor keeps an unsaved local draft. Composition selects a shield, field
division, ordinary and zero/one/two/three charge arrangements. Tinctures sets the
two field colours, ordinary colour and selected charge colour. Charges selects
an individual charge, its emblem, quarter-turn orientation, mirror and size.
Use Default or Randomize, then Save arms. Escape/Cancel discards changes.
The traditional rule of tincture warns about metal on metal or colour on colour;
it does not block saving. A divided field can legitimately trigger a warning.

Craft one **unsealed royal banner** from six wool (any colour) and one stick:

```text
WWW
WWW
 S
```

Hold that one banner in the main hand and choose **Seal banner** in the editor.
Sealing uses the kingdom's **saved** arms; save your draft first. The server checks
current political authority and seals that item. Official banners stack to one.
Place on a solid top face for a standing standard, or on a supported side for a
wall standard. Both variants use the same item. They have selection shapes and
no movement collision, matching decorative cloth behavior. Removing their support
breaks them normally. Piston destruction/explosions follow existing territory
protection; banners receive no obelisk immunity.

A captured/broken banner retains its original kingdom UUID and seal. Anyone can
place an authentic trophy where municipal protection permits construction. It
never claims land, grants access, creates a settlement, or bypasses allied-city
restrictions. Dissolved kingdoms' banners become neutral, uncharged black cloth;
the historic item/entity UUID remains and cannot be reassigned. A new kingdom
with a reused display name has a different UUID. Creative operators remain able
to duplicate authentic items or edit worlds, as with other Minecraft content.

## Canonical data and visual representations

`kingdom/Heraldry` is an immutable version-1 definition with stable textual IDs:

- Shields: heater, kite, round, French and bouche (a lance cutout).
- Tinctures: Or, Argent, Gules, Azure, Sable, Vert and Purpure.
- Fields: plain, per pale, per fess, per bend, per saltire and quarterly.
- Ordinaries: optional pale, fess, bend, chevron, cross and saltire.
- Charges: lion, eagle, wolf, stag, crown, sword, fleur-de-lis, tower, sun, star, rose.
- Up to three individually coloured charges, bounded positions (15–85% X,
  15–80% Y), 20–85% scale, four quarter turns and optional reflection.

The editor exposes curated arrangements instead of arbitrary drag handles. Each
charge can have its own emblem and tincture. All shapes are original polygon
artwork under CC0; editable source is `tools/heraldry_assets.py`. No CKIII artwork,
vanilla banner pattern engine, uploaded images, arbitrary SVGs or external URLs
are used. English and Ukrainian translations cover every new player-facing key.

`HeraldryTextures` composes bundled masks into a 128×160 native client texture,
with separate shield and swallowtail-cloth representations of the **same** arms.
Kingdom overview, administration, diplomacy/alliance/relationship rows and the
kingdom badge in affiliated city/obelisk menus use that compositor. No obelisk
models or claim behavior are changed. Banner items also show current arms.
The cloth is double sided; the reverse naturally mirrors its physical front.

The v0.9.1 bounded LRU holds at most 256 textures (~20 MiB native pixel storage plus approximately 20 MiB GPU texture
storage, excluding bookkeeping). No per-kingdom files are generated, on the server or client.
Resource reload and disconnect release textures. A cache miss composes once;
steady rendering submits four cloth vertices through Minecraft's batch buffers.
Banners have no tickers. There is no global placed-banner registry, scan, geometry
rebuild or update packet per banner when arms change. Texture identifiers use 256
recyclable slots because Minecraft memoizes render types for the process lifetime.
Eviction submits pending standard/outline buffers before reusing a slot and closes
the old native/GPU resource. A measured 129-design second pass now performs zero
rasterizations; more than 256 simultaneously used representations can still churn.
See [v0.9.1 audit](release-hardening-v0.9.1.md) for measurements and limitations.
The supports are static baked block models.

## Persistence and upgrade

Schema **8** adds `Heraldry` to each kingdom record and a server-only 32-byte
`BannerSecret` to the existing `project-reigns-kingdoms.dat`. Existing schema 1–7
normalization, both independent schema-6 branches, kingdoms, cities, roles,
invitations, diplomacy, claims, obelisk anchors and tombstones remain intact.
No dependency versions or old registry/channel IDs change. New banner registry
IDs use `regnum-nations:royal_banner` / `wall_royal_banner`.

Legacy kingdoms receive deterministic defaults from their stable UUID, independent
of names, iteration order or sessions. Unknown/malformed heraldry alone falls back
to those defaults and is retained verbatim in `HeraldryRecovery`. An explicit save
can replace the fallback without deleting the recovery field. Unknown extension
keys in supported heraldry records and surviving charge slots are retained.
An unknown whole-world schema or malformed signing key follows the existing
fail-closed saved-data policy and preserves original NBT with mutations disabled.

A banner block entity saves only `KingdomId` and sends that UUID through the normal
chunk/block-entity update lifecycle. Kingdom edits send public definition deltas,
so unloaded banners need no processing. When a chunk loads, it resolves the same
current definition. The secret is generated lazily on first seal/verification and
persists with the registry; it is never sent to clients. Back up the whole world
with the server stopped before upgrading. Downgrade requires the pre-upgrade
backup and matching old client/server JARs. Do not remove or regenerate the secret
while retaining official items.

## Authority and networking

`MANAGE_HERALDRY` follows v0.8.5 political roles: ruler and chancellor only.
It conveys no municipal BUILD or INTERACT permission. Every save/seal rechecks
current server membership, live player status, kingdom existence, a single-use
server-issued session token, dimension, player entity identity, five-minute session
age, and the definition originally read. Concurrent edits cannot silently overwrite
one another. The existing shared gate rate-limits mutations (500 ms), reads
(150 ms) and rejects replayed sequence numbers. Disconnect removes sessions.

Three additive channels carry bounded structured data. Registry options are
validated against the curated vocabulary; charge count, identifiers and all
numbers are bounded before gameplay logic. Requests contain no arbitrary NBT.
The public cache sends login state in batches of at most 32 entries and later
changed/deleted definitions only, with no per-tick polling. It stays valid across
dimensions and resets on login/disconnect. Only public heraldry is synchronized;
roles, secrets and private diplomacy are not part of that cache.

Official item `CUSTOM_DATA` contains `KingdomId` and a world-specific HMAC-SHA256
seal. Changing the UUID invalidates the seal. The only player-facing issuance path
checks current authority and one crafted blank in the main hand. Native item
placement verifies the seal on the server and ignores item-supplied block-entity
NBT. Claims/protection still run through the existing BlockItem mixin and break
hooks. Drops are signed from the authoritative placed block entity. No client
request can supply an owner, texture path or seal key.

## Verification and remaining review

Executed evidence and screenshots are recorded in `heraldry-validation-v0.9.0.md`.
Tests cover vocabulary serialization, schema-7 upgrade, deterministic defaults,
corruption recovery, permission revocation, forged components and seals, native
standing/wall placement and removal, persistent identity, actual registry disk
reload, dynamic definition changes, dissolution, allied construction restrictions,
payload limits, shared rate limits and server edit sessions.

The opt-in native client checks extend existing widget layout coverage and exercise
public login/update/deletion/reset synchronization. `runHeraldryVisualTest` creates
a disposable integrated world, renders the vocabulary/editor/banners, reloads
resources, and checks real local client/server insignia updates. It is not a
remote multiplayer latency/disconnect acceptance test or human accessibility review.
Future work can refine silhouette artwork, add curated arrangements, and profile
larger banner gatherings without changing the canonical identity model. City arms,
dynasties, animation, image uploads, conversion/export and territory mechanics are
outside this release.
