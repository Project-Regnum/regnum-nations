# Territory protection hardening — v0.8.2

## Repository audit

The implementation starts from `origin/main` at `f14cd92`, fetched before work.
That commit includes v0.7 citizenship, v0.7.1 roles, v0.8 obelisk relocation,
v0.8.1 citizen management, and the obelisk visual updates. No proposed or unmerged
PR behavior was assumed. The separate Regnum World and Terra repositories are untouched.

`KingdomService` holds the live server-thread registry. `KingdomSavedData.claimOwner`
is the authoritative in-memory lookup, keyed by dimension and chunk X/Z, at every
height. Its UUID identifies a **city**, not the city's sovereign kingdom.
`ClaimService.canAccess` and `CityPermissions` implement BUILD and INTERACT.
OWNER, OFFICER and CITIZEN retain both permissions; outsiders retain neither.
Political sovereignty, OP and Creative do not grant municipal rights.

Before this change, protection used Fabric player callbacks plus BlockItem and
bucket Mixins. Obelisks already resisted explosions and pistons independently.
There were no general indirect-terrain guards. Existing persistence, roles,
citizenship, relocation and anchor invariants remain in place.

## Decisions and behavior

| Mechanism | Server decision |
| --- | --- |
| Direct actions and buckets | Existing BUILD/INTERACT checks. Buckets explicitly use INTERACT for pickup and pouring, including air-use raycasts. |
| Explosions | Remove every claimed position, including air, from the final affected list before block callbacks and fire. TNT, creepers, ghast fireballs, wither skulls/spawn blasts, beds, anchors, TNT minecarts and wind-charge block effects share vanilla Explosion. Entity damage and knockback remain unchanged. |
| Pistons | Before the first event write, resolve the entire movement graph and check the piston head, every moved source/destination, and every destroyed block against the base's current city domain. Cancel the whole event on a violation. Sticky preflight models the soon-to-be-removed head as air without changing the world; rapid retraction also checks pending moving blocks. |
| Water/lava | Treat ownership boundaries as walls for flow, slope search and source/strength recalculation. Same-city chunks and wilderness remain connected. Cross-boundary lava cooling/basalt conversion is also blocked. |
| Dispensers | Require the source and effect target to share a city domain or both be wilderness before consuming anything. Check synchronous secondary writes, including bonemeal growth, against the dispenser's source. A `finally` block restores the previous context even if a behavior throws. |
| Sponges | Stop absorption traversal before any foreign-domain pickup or plant drops. |
| Fire | Claimed fuel cannot burn and claimed air cannot receive random fire spread, lava ignition or lightning fire. Members may still directly ignite their own fires and use campfires; propagating fire has no player identity. Wilderness fire remains vanilla. |
| Projectiles | Use the current player's existing permission at the actual impact/effect position. Ownerless and mob projectiles have no city permission. Guard fire charges, flaming-arrow TNT ignition, potion extinguishing and lingering-arrow wooden-button scans. Explosion terrain filtering applies regardless of projectile ownership. |
| Mobs and entities | Guard entity-attributed block destruction (including ravagers, crops and withers), Enderman pickup/placement, silverfish infestation/wakeup, sheep grazing, rabbit/fox consumption, villager harvesting/planting, snow-golem trails, dragon terrain removal and player/mob trampling. Pickup/harvest guards precede drops or carried-item changes. |
| Enchantments | Frost Walker's vanilla ReplaceDisk effect checks BUILD at every actual target. |
| Falling blocks | Cross-boundary landings cannot replace claimed terrain. Same-city gravity machinery continues working. Persist the original position and dimension in an additive entity NBT tag; missing attribution permits wilderness landing only. |
| Boundary support loss | A shape update cannot turn a claimed attachment into air because support in another domain disappeared. This also closes indirect player, explosion, fire and piston support-removal bypasses. Other connection/property updates remain vanilla. |

Unattributed explosions, fire propagation and mob griefing have no municipal
permission, even if a citizen originally caused them. Autonomous machines use
current **city identity** at source and target, rather than a remembered player or
sovereign identity. Different cities do not share automation rights merely because
they share an owner or kingdom. Crossing between a claim and wilderness is blocked
for block/fluid transfer in both directions. Redstone signals are not treated as
proof of player authorization and are not globally disabled.

All new gameplay hooks are common/server-safe. There are no packets, client
classes, tick listeners, global world scans, new libraries, or dependency changes.
Decisions use in-memory map lookups; the explosion list uses one registry snapshot.
Fluid authorization runs before vanilla's state-only shape cache, so ownership is
never accidentally cached by block shape. Piston validation adds a bounded resolver
pass (vanilla's 12-block movement limit). Unreadable registry data denies changes
rather than being interpreted as wilderness. Unknown city records cannot grant
machine access. Existing server-thread enforcement remains mandatory.

## Configuration and compatibility

The existing `config/project-reigns-map.properties` is retained. New files include
these settings; existing files may add them manually. Missing or invalid values
are `true`, independently of other properties. Restart the server after editing.

```properties
radiusChunks=32
protection.explosions=true
protection.pistons=true
protection.fluids=true
protection.dispensers=true
protection.fire=true
protection.entities=true
```

Each boolean controls the corresponding indirect category. Fluid protection also
covers sponge drainage and cooling; entity protection includes gravity and Frost
Walker. Direct player/projectile permission checks and the boundary support-loss
invariant remain enabled alongside existing BUILD/INTERACT protection. Disabling
categories deliberately restores those vanilla modification paths; protection
categories can overlap (for example, dispenser placement versus later fluid flow).

The claim/kingdom/citizenship schema stays **5**. No claims, UUIDs, roles, ownership,
registry IDs, saved filenames or network contracts change. The only additional
persistent field is `regnum-nations:fall_origin` on falling-block entities, containing
`Position` and `Dimension`; old falling entities without it cannot land in claims.
Older versions ignore this field. Keep the normal world/config backup for rollback.

## Verification and limits

The new `TerritoryHardeningTests` entrypoint exercises real vanilla operations in a
headless Fabric server: actual TNT/creeper fuses, fireball/skull impact, explosion
finalization, piston events and moving-block ticks, fluid ticks, dispenser ticks,
fire ticks, bucket air use, Enderman goals, mob terrain scans, harvest goals,
enchantment application and falling-entity NBT reload. Private vanilla entrypoints
are exposed only with reflection in test code. Tests include wilderness and
same-city positive controls, different-city boundaries, outsider denial, all
existing city roles, secondary effects and unavailable registry data.

The repository's task is **`runGameTest`**, with that capitalization. `build`
already includes the headless GameTests and the kingdom-name rule checks. `test`
alone has no test sources. The existing Loom 1.19.4 requires Java 25 for Gradle;
the mod still targets Java 21 bytecode. Runtime verification also launches the
GameTest server on an installed Java 21 using an external Gradle init script,
without changing project dependencies or build configuration. Only the disposable
`build/run/gameTest/world` is reset; player worlds under `run/` are untouched.

Validation recorded on 2026-11-12:

| Check | Result |
| --- | --- |
| `./gradlew clean build --no-daemon --console=plain` | Passed, including kingdom-name rules and all 148 server GameTests (37 new hardening tests). Gradle/game launcher used Java 25. |
| `./gradlew runGameTest --no-daemon --console=plain -I /tmp/regnum-protection-java21.init.gradle` | Passed, all 148 server GameTests on Java 21. The temporary init script only sets `runGameTest.javaLauncher` to a Java 21 toolchain. |
| Packaged common Mixins | All 30 configured common Mixins present in the v0.8.2 JAR, class-file major version 65 (Java 21). |
| `git diff --check` | Passed. |
| `python3 tools/verify_mod_jar.py` | Failed at line 73 on a pre-existing expectation: the checker requires `regnum-nations:block/{obelisk}` for item adapters, whereas both adapters on audited `main` already use `regnum-nations:item/{obelisk}`. The checker and those resources were not changed here. |

Limitations:

- Entity damage, PvP, inventory theft through hoppers, item-frame/armor-stand access,
  privileged commands and third-party world-edit/mod mechanisms are outside this
  terrain-hardening pass. These hooks are not a universal veto on arbitrary world writes.
- Lightning copper weathering and ordinary redstone signals remain vanilla;
  natural growth is not assigned a player identity.
- A protected attachment may remain unsupported at a foreign boundary after its
  wilderness support is destroyed. Its owner can remove or rebuild it normally.
- Dispenser secondary growth can stop at a boundary; trees/plants may be truncated
  rather than making multi-position vegetation generation transactional.
- In-flight pistons are checked when their operation starts. Claim changes during
  an already-started animation are not a transaction with that animation.
- Interactive multiplayer and full process-restart testing require the manual
  scenarios below. Automated entity and registry reloads do not substitute for them.

## Manual multiplayer scenarios (not performed by this change)

Use a disposable dedicated-server world and matching clients, with de-opped city
owners A/B, an officer, a citizen and an outsider. Display chunk borders.

1. Detonate TNT and lure creepers on both sides of A's boundary; launch ghast
   fireballs/wither skulls across it. Claimed blocks, TNT and air must survive;
   wilderness still craters. Confirm normal entity damage and knockback.
2. Push/pull across wilderness/A/B boundaries, including vertical machines near
   the edge, slime/honey branches, fragile blocks and rapid sticky pulses. A denied
   operation must leave base/head and the entire assembly intact. Repeat inside A
   and across two of A's chunks; normal machinery must still work.
3. Flow water/lava from wilderness and B toward A, create adjacent water/lava,
   absorb water with a sponge, and dispense buckets in both directions. Border
   changes must stop; same-city irrigation, waterlogging and bucket pickup/pouring
   by owner/officer/citizen must work. Outsider bucket air-use must fail.
4. Dispense TNT, flint and steel, fire charges, bonemeal and arrows at the border.
   Try flaming-arrow TNT ignition and lingering arrows on wooden buttons. Burn
   wilderness fuel next to claimed fuel and destroy wilderness torch supports.
   Claimed terrain must remain intact; normal wilderness effects must continue.
5. Lure Endermen, ravagers, withers and silverfish along A's edge; test fox/rabbit
   harvesting and sheep grazing. Jump on farmland and turtle eggs as an outsider,
   then as a citizen. Walk in with Frost Walker and redirect falling sand into A.
6. Change a citizen's membership/role while testing direct actions and projectiles;
   revocation must take effect immediately. Confirm sovereign leaders receive no
   private-city rights.
7. Save/restart, unload/reload border chunks and falling entities, then repeat.
   Confirm existing obelisks, relocation, city ownership, membership and claims
   survive unchanged. Test each category override on a separate disposable copy.
