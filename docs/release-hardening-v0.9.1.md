# Nations v0.9.1 integration audit and release candidate

## 1. Executive summary

Independent code audit on 2026-11-12. Baseline: PR #17 head
`e871e0e60f9a9d77c27acb39f303d16ba0bbaff9`, containing PR #16 head
`33532d6c6ddfa95580e19db16895f990724d6821`. Remote `main` was
`f14cd92ae63aeebd6619cf404ec5659966b39945`. Both PRs were open and unmerged.

Three confirmed **medium** findings were fixed: repeated recovery discarded older
heraldry originals; a 129-design working set caused full cache thrashing; and
monotonically increasing texture identifiers escaped the bounded texture cache
through Minecraft's process-lifetime render-type memoization. No critical/high
security or gameplay defect was confirmed. Missing test coverage alone was not
classified as a defect.

The candidate retains schema 8, public wire layouts, legacy identifiers, government,
diplomacy, claim/obelisk behavior, and captured-banner reuse. Dependencies are unchanged.
Version metadata is 0.9.1. This is a release candidate with manual acceptance tasks,
not an unconditional production-readiness claim.

## 2. Audited components and boundaries

| Component | Code reviewed / evidence |
| --- | --- |
| Persistence/migrations | `KingdomSchema`, `KingdomSavedData`, `DiplomacyData`; original branch fixtures, strict malformed-data paths, extension retention, disk I/O |
| Administration/authorization | `KingdomAdministration`, `KingdomAdministrationView`, `KingdomPermissions`, shared request gate; current ruler versus historical founder, municipal versus government roles |
| Diplomacy | `DiplomacyService`, `DiplomacyServer`, persistent pair revisions and issuer revocation; interleaved administration/diplomacy tests |
| Territory | `ClaimService`, protection entrypoints, `TerritoryBlockItemMixin`, banner placement/drop paths; existing territory hardening and integration suites |
| Heraldry requests/sync | `HeraldryServer`, `HeraldryService`, `HeraldryPayloads`, `HeraldryClient`; bounded codecs, current capabilities, one-use tokens, baselines, connection and dimension checks |
| Banners | `RoyalBannerItem`, standing/wall blocks, block entity, native placement and drop flow, malicious item block-entity data, creative and survival behavior |
| Rendering/resources | `HeraldryTextures`, `RoyalBannerRenderer`, native texture manager; verified Minecraft 1.21.1 bytecode for `RenderType` memoization and `DynamicTexture.close()` |
| UI | `HeraldryScreen`, `KingdomAdministrationScreen`, shared native controls, actual English/Ukrainian framebuffer captures and native layout/focus checks |
| Dedicated server | Java 21 GameTests and normal dedicated processes; package validator rejects client imports in common sources |

Fabric networking 4.3.1's installed source attachments confirm that these object
payload callbacks run on the server thread and render thread respectively. Clients
hold presentation state and drafts; server services decide authorization and persistence.
No Regnum World or Agriculture files were opened for modification.

## 3. Confirmed findings

| ID | Severity | Location | Reproduction and consequence | Resolution |
| --- | --- | --- | --- | --- |
| RN-091-1 | Medium, recovery data loss | `src/main/java/dev/sakhno/regnum/nations/kingdom/KingdomSavedData.java:438`, `load()` recovery handler | Load a kingdom containing an older `HeraldryRecovery` and another invalid `Heraldry`, then save. The old code replaced the older original with the newly rejected design. The new GameTest failed on the baseline handler. | Preserve the earlier value under `Previous` and the new rejected value under `Rejected`. Both are opaque NBT and may have different types. First recovery retains the old single-value shape. Normalized saves reload identically. |
| RN-091-2 | Medium, render-thread performance | `src/client/java/dev/sakhno/regnum/nations/client/heraldry/HeraldryTextures.java:23`, `Shape.contains()` and `texture()` | Request 129 distinct cloth representations twice in the same order. A 128-entry LRU evicted every entry before reuse: 129 recreations on the second pass, measured at 1300.77 ms. Per-pixel streams amplified cold composition cost. | Retain 256 representations and use direct polygon loops. The observed 129-design working set has zero second-pass recreations. The compositor and banner geometry remain intact. |
| RN-091-3 | Medium, client heap lifecycle | `src/client/java/dev/sakhno/regnum/nations/client/heraldry/HeraldryTextures.java:62`, identifiers consumed by `RoyalBannerRenderer.cloth()` | Repeatedly evict/recreate designs or reload resources. Each creation used a new serial texture path. Minecraft's `entityCutoutNoCull` and outline factories memoize by texture path, so their references outlive texture release. Verified from the actual mapped 1.21.1 bytecode. | Recycle 256 texture identifiers. Flush standard/outline buffers before rebinding an evicted slot, release the old native/GPU texture, and register the replacement. Native regression checks bound the union of identifiers through repeated eviction/clear cycles. |

No native/GPU leak was claimed merely from cache churn. The native test separately
checks that evicted `DynamicTexture` pixel ownership is closed, texture-manager
entries disappear on clear, and resources can be recreated. RN-091-3 concerns the
otherwise unbounded memoized **heap** references; no heap-profiler size is claimed.

## 4. Implementation details and compatibility

Production changes are confined to the recovery handler, texture cache/compositor,
and a small `HeraldryServer.batches()` extraction used by both login and deltas.
Batching behavior is unchanged; it now uses the codec's shared `BATCH_SIZE` constant
and can be exercised directly through the actual codecs/client receiver.

Login sends one empty reset packet for zero kingdoms, or reset on only the first
32-entry batch. Later batches append. Public edits/creation/deletion use deltas;
unchanged state emits no delta. PR #17's existing first-post-login deletion fix
was already present at the audited head and is preserved.

No public-sync sequence was added: server mutations and sends are serialized,
play packets travel over an ordered connection, and Fabric applies these payloads
on the render thread. Arbitrary reordering of fabricated `Sync` objects is not a
supported transport guarantee. Editor replies already require the outstanding
request sequence; mutation tokens also revalidate current permission, entity,
dimension, time and displayed heraldry baseline. Two writers cannot silently
commit against different current designs. Former rulers intentionally retain
chancellor rights while still members; a subsequent demotion revokes heraldry rights.

`HeraldryRecovery` was already opaque and retained as a generic tag. Its additive
history wrapper does not require a new kingdom schema or change ordinary arms.
All registry IDs, filenames, channels, NBT identity keys and key bindings remain.

## 5. Banner guarantees and recovery

The HMAC authenticates a kingdom UUID **within one world key**. It does not sign all
custom item fields, provide serial numbers, prevent copying by privileged creative
users, or prove item uniqueness. Tests explicitly accept unrelated custom-data
changes and authentic copied/captured identities, while rejecting forged UUIDs,
missing/modified seals and cross-world signatures. The signing key is never synced.

Sealing still requires current heraldry authority and one main-hand blank. Native
survival placement consumes that item; drops return one authenticated reusable item.
Creative placement deliberately does not consume it. Creative mode alone does not
bypass municipal protection. Alliance, ruler status, or an enemy banner confers no
foreign BUILD permission. Item-provided block-entity data cannot replace the signed
identity. Banner placement never creates a claim.

Dissolved kingdoms lose live public arms; authentic trophies retain their historical
UUID and can be placed again in permitted locations. Standing/wall support removal,
block-entity serialization, drops and replacement use native paths.

A malformed present signing key fails closed and the exact original saved data is
retained. Restoring the matching stopped-world backup restores authentication. A
missing key cannot be reconstructed from signatures; it can also mean that no key
was issued yet, so the existing lazy generation policy remains. Never delete or
rotate `BannerSecret` as a repair technique: it invalidates previously issued items.
Recovery requires the matching key/backup, not a new random key.

## 6. Tests added or strengthened

Seven additional GameTests bring the suite from 213 to **220**: competing editors
across leadership transfer/demotion; reconnection with a different player entity;
corrupt-key fail-closed/backup restoration and HMAC scope; survival/creative captured
trophy replacement; repeated recovery retention; all supported schema disk saves;
and malformed/unknown schema preservation. Existing vocabulary round trips remain
one test rather than inflating counts for individual combinations.

Native checks now exercise the production batching helper, wire codec and actual
client cache at 0, 1, 32, 33, 64, 129 and 300 kingdoms. They cover edit/create/delete
deltas and reset replacement. Texture tests cover 1,000 identical lookups, 129
representations, bounded eviction, native resource closure, slot reuse and repeated
clear/recreation. The visual fixture now issues a real authenticated inventory item
so normal-server restarts can check its persisted key and HMAC.

The layout harness explicitly starts in English, reloads Ukrainian, tests every
heraldry page with zero to three charges, read-only/editable controls, long names,
320×240 / 640×360 / 1100×760 logical sizes, stale reply rejection, Tab reachability
of enabled controls and nonempty narration text. Geometry/focus/narration-string
checks do not establish human accessibility or audible narration quality.

Two opt-in client profiles and `tools/verify_multiplayer.py` coordinate independent
Java 21 client JVMs against a disposable localhost dedicated server. File barriers
coordinate observations; all editing and synchronization still cross the real
Fabric connection. They are offline test identities, not authenticated accounts.

Actual candidate framebuffer captures (automated, visually inspected; not human
accessibility acceptance): [English editor](images/release-v0.9.1/editor-en.png),
[Ukrainian editor](images/release-v0.9.1/editor-uk.png), and
[standing/wall banners](images/release-v0.9.1/banners-front.png).

## 7. Exact verification commands and results

Verification is local unless explicitly identified as CI.

| Command | Result |
| --- | --- |
| `./gradlew clean build --no-daemon --console=plain` on baseline | PASS, 213 GameTests and 21 kingdom-name checks |
| `./gradlew runGameTest --no-daemon --console=plain` with added recovery regression, before fix | Expected FAIL: exactly the repeated-recovery regression; 219/220 passed |
| `./gradlew clean build runIntegrationLayoutTest runHeraldryVisualTest --no-daemon --console=plain` | PASS, 220 GameTests, 21 name checks, 456 native layouts and visual checks |
| `./gradlew build runHeraldryVisualTest --no-daemon --console=plain` after render-type self-review fix | PASS, 220 GameTests, native visual/resource/eviction checks |
| `./gradlew runIntegrationLayoutTest runHeraldryVisualTest --no-daemon --console=plain` (final rerun) | PASS, 456 layouts including keyboard/narration/long-name checks; native batching, texture object reuse, eviction, reload and visual checks |
| `python3 tools/verify_multiplayer.py` | PASS, two independent Java 21 offline loopback clients; 129 kingdoms, concurrent edit rejection/convergence, dimension change, disconnect cleanup and reconnect |
| `python3 tools/verify_heraldry_restart.py` | PASS, two normal Java 21 dedicated processes; renamed kingdom UUID/arms, both banner entities, world signing key and saved item HMAC preserved |
| `python3 tools/verify_release_restart.py` | PASS, ten supported fixture variants through 20 normal Java 21 server processes; canonical data values unchanged after upgrade/save/shutdown/restart |
| `python3 tools/verify_mod_jar.py` | PASS, 0.9.1 metadata, Java 21 classfile majors, entrypoints, common/client isolation, compatibility resources, locales and banners |
| `python3 tools/validate_obelisk_assets.py` | PASS, 7 models / 10 textures and retained identifiers |
| `python3 tools/validate_crystal_animation.py` | PASS, continuous city/kingdom crystal clearances |
| `python3 -m py_compile tools/verify_heraldry_restart.py tools/verify_release_restart.py tools/verify_multiplayer.py` | PASS |
| `git diff --check` | PASS |
| Final required `./gradlew build --no-daemon --console=plain` | PASS, 220/220 required GameTests and 21 name checks; normal server shutdown and Gradle exit 0 |

Gradle/Loom runs on the existing JDK 25; production classes target Java 21, and
verification/gameplay processes use installed Temurin 21.0.12.1. No dependency was
updated. Expected malformed-fixture ERROR logs are assertions of fail-closed
behavior. Flite is absent, so audible narration was unavailable. Initial harness
iterations exposed missing imports and Loom launch-command preparation assumptions;
the disconnect harness also initially omitted `ClientLevel.disconnect()`, which the
native pause-menu calls before `Minecraft.disconnect()`. Matching that verified
1.21.1 path passed the real cleanup/reconnect check. These are test implementation
corrections, not mod defects; failed attempts are not counted as passing evidence. A shadowed variable in the
restart probe was also corrected before its passing rerun; no signing-key mutation
was observed.

## 8. Multiplayer matrix

| Scenario | Evidence / limits |
| --- | --- |
| Login: zero, 1, 32, 33, 64, 129, 300 kingdoms | PASS, production batch construction → bounded wire codec → actual client cache; reset on first batch only |
| Two independent clients, 129 kingdoms / five login batches | PASS, real dedicated server and two client JVMs |
| Active creation/deletion | PASS, server change/tombstone tests and native client delta application; integrated real create/edit packets in visual fixture |
| Simultaneous heraldry edits | PASS, server interleavings; PASS real two-client stale-save/convergence run |
| Stale/out-of-order editor replies | PASS, unmatched sequence cannot restore the current view; public sync relies on the real ordered connection |
| Revoked permissions / leadership transfer | PASS, request/service interleavings, previous ruler's intentional chancellor retention and explicit demotion |
| Disconnect/reconnect | PASS, different-player-entity token rejection; PASS real client cache/texture cleanup and new login |
| Dimension changes | PASS, server session rejection; PASS real client public-cache retention |
| Restart | PASS GameTest disk boundary and two normal dedicated processes; full migration process matrix below |
| Authenticated internet clients, added latency, packet-loss simulation | NOT RUN; manual procedure below |

## 9. Migration compatibility matrix

The ten supported cases export both raw input and expected canonical NBT. Every
case passes input-immutability, identity/obelisk checks and complete typed canonical disk
save/reload equality. Process probes compare decoded NBT values; the GameTests also
check tag types. All ten real-process cases passed.

| Input | Expected compatibility contract | Disk reload | Dedicated upgrade + restart |
| --- | --- | --- | --- |
| Schema 1 | Retain kingdom/founder/obelisk IDs; derive legacy capital settlement, ruler and default arms; no historical claims existed | PASS | PASS |
| Schema 2 | Same, with kingdom-owned claims mapped to legacy capital-city identity | PASS | PASS |
| Schema 3 | Retain independent/affiliated cities, owners, claims, colors, anchors and deletion tombstones | PASS | PASS |
| Schema 4 | Retain citizenship; missing municipal roles become CITIZEN | PASS | PASS |
| Schema 5 | Retain municipal roles; founders become rulers | PASS | PASS |
| Administration-only 6 | Retain transferred ruler, government roles and city invitations | PASS | PASS |
| Diplomacy-only 6 | Retain agreements/requests/pair revisions; founder ruler and issuer attribution migration | PASS | PASS |
| Combined 6 | Preserve both branches by fields, not release ordering; current ruler issuer attribution | PASS | PASS |
| Integrated 7 | Preserve both political systems; add deterministic UUID-based arms | PASS | PASS |
| Heraldry 8 | Preserve canonical arms and 32-byte signing key with all political state | PASS | PASS |
| Malformed/unknown schema | Disable mutations and retain exact original NBT; malformed diplomacy alone disables diplomacy only | PASS | Not launched as a normal repairable world |
| Invalid/future heraldry | Keep kingdom playable with deterministic fallback and retain opaque originals/history | PASS | Covered by disk/idempotence tests, not a separate process fixture |

Schemas 3 and both branch-6 inputs are handwritten repository fixtures. Schemas
1/2 and 4/5 are explicit transformations of those fixtures; 7 combines branch fields;
8 is a canonical writer fixture with custom arms/key. These are disposable synthetic
fixtures, not upgrades of a production player's world. Existing integration tests
independently verify cities, citizenship, government, diplomacy issuer/revision,
claim ownership, tombstones, and root/record/nested unknown extension retention.
New matrix cases additionally retain root/kingdom extensions across complete disk equality.

Before upgrading, stop the server and back up the **entire world and config/**,
including player inventories, chunks, `project-reigns-kingdoms.dat` and its signing
key. Test a copy with matching client/server JARs. Do not rename saved files. Older
schema-7 and earlier writers cannot safely downgrade schema 8. Restore the complete
pre-upgrade backup and old matching JARs for rollback; copying only kingdom data
can desynchronize identities, inventories and chunk entities. No real player world
under `run/` was opened or rewritten by these verification tasks.

## 10. Performance and rendering evidence

A native render-thread loop called `texture(arms, true)` for 129 distinct immutable
designs in order, then repeated the same order. This measures cache/composition
cost, **not full-frame FPS** or an entire crowd scene.

| Sample | Cold pass | Second pass | Second-pass recreations |
| --- | --- | --- | --- |
| Baseline 128-entry cache / streams | 2248.34 ms | 1300.77 ms | 129 |
| 256 entries / polygon loops | 890.49 ms | 0.55 ms | 0 |
| After bounded render-type slots, final run (texture object identity checked) | 781.12 ms | 0.34 ms | 0 |

Cold timings vary with concurrent verification and this workstation's memory load;
these are individual observations, not statistical throughput claims. The stable
result is zero warm recreations for the measured working set. Direct loops remove
per-pixel stream creation while preserving polygon tests and raster output.

The cache caps native pixels at 20 MiB and GPU pixel storage at approximately
20 MiB, excluding object/driver bookkeeping. Reused identifiers cap this mod's
contribution to vanilla's memoized entity/outline type keys. Eviction/clear tests
verify closure and recreation; no allocation-profiler or driver VRAM measurement
was performed. More than 256 simultaneously used representations can still churn;
large, diverse crowd FPS and low-end GPU acceptance remain manual. No rendering
architecture replacement or speculative atlas implementation was introduced.

## 11. Remaining acceptance tasks and precise manual procedure

Use a stopped-world copy, a dedicated Java 21 server and two authenticated clients
with the same 0.9.1 JAR. Record server/client versions, language, logical GUI size,
network conditions and logs. Retain the backup until acceptance is complete.

1. Create a ruler, a chancellor and an outsider across two allied kingdoms. Open
   heraldry on both editors; save different drafts close together. Expect the first
   valid save to win, both clients/banners to show that design, and the stale writer
   to receive a visible error before a fresh authorized session is usable.
2. While the editor is open, demote its user, transfer leadership, remove a city,
   create/dissolve another kingdom and reconnect one client under added latency.
   Expect refreshed roles, no unauthorized save, no old arms after dissolution,
   retained municipal ownership/claims, and convergence after reconnect/dimension change.
3. Craft/seal a banner, capture it as the outsider, break/re-place it in wilderness,
   and try placing it inside the allied city's claim in survival and creative.
   Expect authentic reuse, one-item survival accounting, no extra claims and denied
   foreign placement. Tampered UUID/seal and blanks must fail. Dissolve its kingdom:
   the trophy keeps identity but renders neutral. Save/stop/restart and repeat.
4. Test every editor tab/charge slot and administration tab with keyboard only,
   forward/reverse Tab, Enter/Space and Escape. Test cancellation of unsaved drafts,
   request timeout/stale errors, readable feedback and long names at small GUI scales
   in English and Ukrainian. A human must accept focus order, narration wording,
   contrast, text truncation and audible screen-reader behavior. Native geometry and
   automated captures do not substitute for that acceptance.
5. Gather many identical banners, then 129 and more than 256 different
   representations. Repeatedly edit, reload resources, switch dimensions and
   reconnect while watching frame times and memory. Expect shared textures for
   identical arms, released/recreated resources, no stale art or increasing texture
   identifiers. Characterize remaining churn above the bound on supported hardware.
6. Upgrade a **copy of an actual existing player world**. Inspect all obelisks,
   affiliations, ruler/municipal roles, city invitations, diplomacy, claims and old
   banner inventories before/after save, shutdown and restart. Compare backed-up NBT.
   This production-world migration and crash/power-loss recovery were not executed.

Remaining risks are external-network timing, human accessibility, real-world
migration complexity, crash durability, and crowds beyond the measured working set.
No unresolved critical/high defect is known from this audit; absence of such a
finding is not proof that untested situations are safe.

## 12. Self-review and recommended merge order

Self-review checked the final production changes against the baseline: no removed
features, dependencies, wire changes, new permissions/gameplay, debug-only production
code, hardcoded UI strings, unrelated modules, or generated build/IDE/agent artifacts.
Signing claims remain identity authentication, not uniqueness. Payload bounds stay
32 entries and three bounded charges. Recovery retains original NBT; texture eviction
and disconnect/reload release resources. The render-type lifetime issue was found
and fixed during this self-review, then affected checks were rerun.

Recommended approval/merge chain without retargeting existing PRs: this hardening PR
→ PR #17's feature branch → PR #16's integration branch → `main`. Maintainers must
review each merge and the remaining manual acceptance evidence. No PR was merged,
closed or retargeted automatically.

## 13. Delivery identity and CI

Branch: `fix/nations-v0.9.1-release-hardening`.
Target: `feat/heraldry-royal-banners-v0.9.0`.
Title: `fix: Nations v0.9.1 integration and release hardening`.
Commit SHA / PR URL / latest CI status: recorded in the PR description and delivery
message after commit/push, so this report does not contain a self-referential SHA.
Only executed results above are release evidence. CI status will be queried for the
actual pushed commit; local results are not substituted for remote CI.
