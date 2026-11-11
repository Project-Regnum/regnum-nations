package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Invokes vanilla ticks/operations in a live dedicated server. Reflection only exposes private vanilla entry points. */
public class TerritoryHardeningTests implements FabricGameTest {
    private static final AtomicInteger NEXT = new AtomicInteger();

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void tntInsideClaimStillDestroysWilderness(GameTestHelper h) throws Exception {
        tnt(h, true); h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void tntOutsideClaimCannotDestroyClaimedBlocks(GameTestHelper h) throws Exception {
        tnt(h, false); h.succeed();
    }

    private static void tnt(GameTestHelper h, boolean inside) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.west, Blocks.GLASS); f.set(f.east, Blocks.GLASS);
            BlockPos origin = inside ? f.east : f.west;
            var tnt = new PrimedTnt(f.level, origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5, null);
            tnt.setFuse(1); tnt.tick();
            h.assertTrue(tnt.isRemoved(), "Actual TNT fuse must detonate");
            f.is(f.east, Blocks.GLASS, "Claim must survive TNT from either side");
            f.is(f.west, Blocks.AIR, "Wilderness must retain vanilla blast destruction");
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void creeperCrossingBoundaryLeavesClaimIntact(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.west, Blocks.GLASS); f.set(f.east, Blocks.GLASS);
            Creeper creeper = EntityType.CREEPER.create(f.level);
            var tag = new CompoundTag(); creeper.saveWithoutId(tag); tag.putShort("Fuse", (short)1);
            creeper.load(tag); creeper.setPos(Vec3.atCenterOf(f.west.above())); creeper.ignite(); creeper.tick();
            h.assertTrue(creeper.isRemoved(), "Actual creeper fuse must detonate");
            f.is(f.east, Blocks.GLASS, "Creeper cannot destroy claimed terrain");
            f.is(f.west, Blocks.AIR, "Creeper still destroys wilderness");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void explosiveFireballsAndWitherSkullsRespectBoundary(GameTestHelper h) throws Exception {
        for (boolean skull : List.of(false, true)) try (var f = new Fixture(h, true, false)) {
            f.set(f.west, Blocks.GLASS); f.set(f.east, Blocks.GLASS);
            Projectile projectile = skull ? new WitherSkull(EntityType.WITHER_SKULL, f.level)
                    : new LargeFireball(EntityType.FIREBALL, f.level);
            projectile.setPos(Vec3.atCenterOf(f.west));
            invoke(projectile.getClass(), "onHit", projectile, new Class<?>[] {net.minecraft.world.phys.HitResult.class}, f.hit(f.west, Direction.UP));
            f.is(f.east, Blocks.GLASS, "Explosive projectile cannot cross claim boundary");
            f.is(f.west, Blocks.AIR, "Explosive projectile still affects wilderness");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void explosionCannotPrimeClaimedTntOrCreateFire(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.east, Blocks.TNT); f.set(f.east.above(), Blocks.AIR);
            f.set(f.west, Blocks.GLASS);
            var explosion = new Explosion(f.level, null, f.west.getX(), f.west.getY(), f.west.getZ(), 4, true, Explosion.BlockInteraction.DESTROY);
            // Explicit air entries exercise vanilla's separate post-destruction fire loop deterministically.
            explosion.getToBlow().addAll(List.of(f.west, f.east, f.east.above()));
            explosion.finalizeExplosion(false);
            f.is(f.east, Blocks.TNT, "Protected TNT must not be primed as a secondary effect");
            f.is(f.east.above(), Blocks.AIR, "Explosion must not create fire in claimed air");
            f.is(f.west, Blocks.AIR, "Unclaimed affected list entries remain effective");
            h.assertFalse(explosion.getToBlow().contains(f.east.above()), "Protected air must also leave the affected list sent to clients");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void pistonPushIntoClaimIsAtomic(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            BlockPos piston = f.west.west();
            f.piston(piston, Direction.EAST, false, false); f.set(f.west, Blocks.STONE);
            h.assertFalse(f.extend(piston), "Piston must reject whole operation before writes");
            f.is(f.west, Blocks.STONE, "Pushed source stays in place"); f.is(f.east, Blocks.AIR, "Protected destination stays empty");
            h.assertFalse(f.level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "Rejected piston must stay retracted");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void pistonHeadCannotExtendIntoClaim(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.piston(f.west, Direction.EAST, false, false);
            h.assertFalse(f.extend(f.west), "Empty head placement also crosses a boundary");
            f.is(f.east, Blocks.AIR, "No piston head in protected air");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void stickyPistonCannotPullFromClaimBeforeRetractionWrites(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            BlockPos piston = f.west.west();
            f.piston(piston, Direction.EAST, true, true); f.set(f.east, Blocks.STONE);
            h.assertFalse(f.retract(piston), "Sticky pull must be denied before base/head writes");
            f.is(f.east, Blocks.STONE, "Protected source cannot be stolen");
            f.is(f.west, Blocks.PISTON_HEAD, "Denied pull must not partially remove the head");
            f.is(piston, Blocks.STICKY_PISTON, "Denied pull must not replace the base with moving piston");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void slimeAndHoneyBranchesAreValidatedTogether(GameTestHelper h) throws Exception {
        for (Block sticky : List.of(Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK)) try (var f = new Fixture(h, true, false)) {
            BlockPos piston = f.west.west().north();
            f.piston(piston, Direction.SOUTH, false, false);
            f.set(piston.south(), sticky); f.set(f.west, sticky); f.set(f.east, Blocks.STONE);
            h.assertFalse(f.extend(piston), "Foreign block attached sideways must cancel entire sticky graph");
            f.is(piston.south(), sticky, "Primary sticky block cannot partially move");
            f.is(f.west, sticky, "Branched sticky block cannot partially move");
            f.is(f.east, Blocks.STONE, "Claimed attached block must stay in place");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void pistonCannotDestroyFragileBlocksInAnotherCity(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, true)) {
            BlockPos piston = f.west.west();
            f.piston(piston, Direction.EAST, false, false); f.set(f.west, Blocks.STONE);
            f.set(f.east.below(), Blocks.DIRT); f.set(f.east, Blocks.SHORT_GRASS);
            h.assertFalse(f.extend(piston), "Different city claims must not exchange or destroy blocks");
            f.is(f.east, Blocks.SHORT_GRASS, "Resolver destruction list must also be protected");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void pistonsWorkInWildernessAndAcrossSameCityChunks(GameTestHelper h) throws Exception {
        for (boolean claimed : List.of(false, true)) try (var f = new Fixture(h, claimed, false)) {
            if (claimed) f.data.putClaim(ClaimPos.at(f.level.dimension(), f.west), f.eastCity.id());
            BlockPos piston = f.west.west();
            f.piston(piston, Direction.EAST, true, false); f.set(f.west, Blocks.STONE);
            h.assertTrue(f.extend(piston), "Permitted piston extension must execute vanilla movement");
            f.settle(piston, f.east);
            f.is(f.east, Blocks.STONE, "Authorized piston must finish placing its moved block");
            h.assertTrue(f.retract(piston), "Permitted sticky retraction must execute");
            f.settle(piston, f.east);
            f.is(f.west, Blocks.STONE, "Authorized sticky pull must finish");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void waterAndLavaCannotFlowIntoClaims(GameTestHelper h) throws Exception {
        for (Block liquid : List.of(Blocks.WATER, Blocks.LAVA)) try (var f = new Fixture(h, true, false)) {
            f.channel(f.west, liquid); f.tickFluid(f.west);
            f.is(f.east, Blocks.AIR, "Wilderness fluid must stop at protected boundary");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fluidCannotFlowBetweenDifferentCityClaims(GameTestHelper h) throws Exception {
        for (Block liquid : List.of(Blocks.WATER, Blocks.LAVA)) try (var f = new Fixture(h, true, true)) {
            f.channel(f.west, liquid); f.tickFluid(f.west);
            f.is(f.east, Blocks.AIR, "A city's fluid cannot modify another city's claim");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fluidsStillFlowAcrossWildernessAndSameCityChunks(GameTestHelper h) throws Exception {
        for (boolean claimed : List.of(false, true)) for (Block liquid : List.of(Blocks.WATER, Blocks.LAVA)) {
            try (var f = new Fixture(h, claimed, false)) {
                if (claimed) f.data.putClaim(ClaimPos.at(f.level.dimension(), f.west), f.eastCity.id());
                f.channel(f.west, liquid); f.tickFluid(f.west);
                f.is(f.east, liquid, "Permitted fluid tick must spread normally");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fluidRecalculationCannotImportOutsideSourceStrength(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.channel(f.west, Blocks.WATER);
            f.set(f.east, Fluids.FLOWING_WATER.getFlowing(1, false).createLegacyBlock());
            f.tickFluid(f.east);
            f.is(f.east, Blocks.AIR, "Claimed flow must not renew its strength using a foreign source");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void outsideWaterCannotSolidifyClaimedLava(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.east, Blocks.LAVA); f.level.setBlockAndUpdate(f.west, Blocks.WATER.defaultBlockState());
            f.is(f.east, Blocks.LAVA, "Neighbor updates must not cool protected lava across boundary");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void dispensersCannotPlaceOrRemoveForeignFluids(GameTestHelper h) throws Exception {
        for (Item item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BUCKET)) try (var f = new Fixture(h, true, false)) {
            if (item == Items.BUCKET) f.set(f.east, Blocks.WATER);
            f.dispense(f.west, Direction.EAST, item);
            f.is(f.east, item == Items.BUCKET ? Blocks.WATER : Blocks.AIR, "Denied dispenser must preserve target");
            var inventory = (DispenserBlockEntity)f.level.getBlockEntity(f.west);
            h.assertTrue(inventory.getItem(0).is(item) && inventory.getItem(0).getCount() == 1, "Denied dispenser must preserve its item");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void dispenserFluidsWorkInOwnClaimsAndWilderness(GameTestHelper h) throws Exception {
        for (boolean claimed : List.of(false, true)) for (Item bucket : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
            try (var f = new Fixture(h, claimed, false)) {
                if (claimed) f.data.putClaim(ClaimPos.at(f.level.dimension(), f.west), f.eastCity.id());
                f.dispense(f.west, Direction.EAST, bucket);
                f.is(f.east, bucket == Items.WATER_BUCKET ? Blocks.WATER : Blocks.LAVA, "Permitted dispenser must place fluid");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void dispenserIgnitionTntAndBonemealCannotCrossBoundary(GameTestHelper h) throws Exception {
        for (Item item : List.of(Items.FLINT_AND_STEEL, Items.TNT, Items.BONE_MEAL)) try (var f = new Fixture(h, true, false)) {
            f.set(f.east.below(), Blocks.DIRT); f.set(f.east, item == Items.BONE_MEAL ? Blocks.OAK_SAPLING : Blocks.AIR);
            f.dispense(f.west, Direction.EAST, item);
            f.is(f.east, item == Items.BONE_MEAL ? Blocks.OAK_SAPLING : Blocks.AIR, "Indirect modification must be denied");
            h.assertTrue(((DispenserBlockEntity)f.level.getBlockEntity(f.west)).getItem(0).is(item), "Denied action must not consume ignition/TNT/bonemeal");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fireCannotBurnOrSpreadIntoClaims(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.enableFireTicks();
            f.set(f.west.below(), Blocks.NETHERRACK); f.set(f.west, Blocks.FIRE); f.set(f.east, Blocks.OAK_PLANKS);
            for (int i = 0; i < 150; i++) f.level.getBlockState(f.west).tick(f.level, f.west, f.level.random);
            f.is(f.east, Blocks.OAK_PLANKS, "Actual fire ticks must never consume claimed fuel");
            f.set(f.east, Blocks.AIR); f.set(f.east.above(), Blocks.OAK_PLANKS);
            Object odds = invoke(FireBlock.class, "getIgniteOdds", Blocks.FIRE,
                    new Class<?>[] {net.minecraft.world.level.LevelReader.class, BlockPos.class}, f.level, f.east);
            h.assertTrue(odds.equals(0), "Fire spread candidate in claimed air must be rejected");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fireChargesAndFlamingArrowsRequireCurrentPermission(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.east.below(), Blocks.STONE); f.set(f.east, Blocks.TNT);
            var arrow = new Arrow(EntityType.ARROW, f.level); arrow.igniteForSeconds(20);
            invoke(Projectile.class, "onHitBlock", arrow, new Class<?>[] {BlockHitResult.class}, f.hit(f.east, Direction.UP));
            f.is(f.east, Blocks.TNT, "Ownerless flaming arrow cannot prime claimed TNT");
            f.set(f.east, Blocks.AIR);
            var ball = new SmallFireball(EntityType.SMALL_FIREBALL, f.level);
            invoke(SmallFireball.class, "onHitBlock", ball, new Class<?>[] {BlockHitResult.class}, f.hit(f.east.below(), Direction.UP));
            f.is(f.east, Blocks.AIR, "Ownerless fire charge cannot ignite claimed air");
            ball.setOwner(f.owner);
            invoke(SmallFireball.class, "onHitBlock", ball, new Class<?>[] {BlockHitResult.class}, f.hit(f.east.below(), Direction.UP));
            f.is(f.east, Blocks.FIRE, "Authorized projectile must retain ignition behavior");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void endermenCannotTakeOrPlaceClaimedBlocks(GameTestHelper h) throws Exception {
        for (boolean claimed : List.of(false, true)) try (var f = new Fixture(h, claimed, false)) {
            EnderMan enderman = EntityType.ENDERMAN.create(f.level);
            enderman.setPos(Vec3.atCenterOf(f.east));
            f.set(f.east, Blocks.DIRT);
            Goal take = goal("net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal", enderman);
            for (int i = 0; i < 2000 && enderman.getCarriedBlock() == null; i++) take.tick();
            h.assertTrue((enderman.getCarriedBlock() == null) == claimed, "Pickup must execute only in wilderness");
            if (claimed) f.is(f.east, Blocks.DIRT, "Denied pickup cannot duplicate or remove the held block");
            f.set(f.east.below(), Blocks.STONE); f.set(f.east, Blocks.AIR);
            enderman.setCarriedBlock(Blocks.DIRT.defaultBlockState());
            Goal leave = goal("net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal", enderman);
            for (int i = 0; i < 2000 && enderman.getCarriedBlock() != null; i++) leave.tick();
            if (claimed) {
                f.is(f.east, Blocks.AIR, "Denied placement cannot change terrain");
                h.assertTrue(enderman.getCarriedBlock() != null, "Denied placement cannot discard a carried block");
            } else h.assertTrue(enderman.getCarriedBlock() == null, "Enderman must still place blocks in wilderness");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void witherAndRavagerTerrainEditsRespectActualTarget(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            var wither = EntityType.WITHER.create(f.level);
            var ravager = new Ravager(EntityType.RAVAGER, f.level) {
                @Override public void travel(Vec3 movement) { horizontalCollision = true; }
            };
            f.set(f.east, Blocks.STONE); f.set(f.west, Blocks.STONE);
            wither.setPos(Vec3.atCenterOf(f.west));
            var counter = WitherBoss.class.getDeclaredField("destroyBlocksTick"); counter.setAccessible(true); counter.setInt(wither, 1);
            invoke(WitherBoss.class, "customServerAiStep", wither, new Class<?>[0]);
            f.is(f.east, Blocks.STONE, "Wither terrain scan from outside must preserve claimed targets");
            f.is(f.west, Blocks.AIR, "Wither must still destroy wilderness");
            f.set(f.east, Blocks.OAK_LEAVES); f.set(f.west, Blocks.OAK_LEAVES);
            ravager.setPos(Vec3.atCenterOf(f.west)); ravager.horizontalCollision = true; ravager.aiStep();
            f.is(f.east, Blocks.OAK_LEAVES, "Ravager collision must preserve claimed leaves");
            f.is(f.west, Blocks.AIR, "Ravager collision must still break wilderness leaves");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void tramplingCannotBypassPlayerRoles(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            ServerPlayer outsider = f.player(); var ravager = EntityType.RAVAGER.create(f.level);
            for (Entity entity : List.of(outsider, ravager, f.owner)) {
                f.set(f.east, Blocks.FARMLAND);
                Blocks.FARMLAND.fallOn(f.level, f.level.getBlockState(f.east), f.east, entity, 100);
                f.is(f.east, entity == f.owner ? Blocks.DIRT : Blocks.FARMLAND, "Trampling must respect municipal BUILD permission");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void ownersOfficersCitizensKeepBuildAndInteractPermissions(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            var officer = f.player(); var citizen = f.player(); var outsider = f.player();
            f.data.addCitizen(f.eastCity.id(), officer.getUUID()); f.data.assignRole(f.eastCity.id(), officer.getUUID(), CityRole.OFFICER);
            f.data.addCitizen(f.eastCity.id(), citizen.getUUID()); f.set(f.east.below(), Blocks.STONE);
            for (ServerPlayer member : List.of(f.owner, officer, citizen, outsider)) {
                f.set(f.east, Blocks.AIR);
                BlockHitResult hit = f.hit(f.east.below(), Direction.UP);
                boolean allowed = member != outsider;
                h.assertTrue((UseBlockCallback.EVENT.invoker().interact(member, f.level, InteractionHand.OFF_HAND, hit) != InteractionResult.FAIL) == allowed,
                        "Every existing role must retain INTERACT; outsider remains denied");
                var stone = (BlockItem)Items.STONE;
                boolean placed = stone.place(new BlockPlaceContext(f.level, member, InteractionHand.MAIN_HAND, new ItemStack(stone), hit)).consumesAction();
                h.assertTrue(placed == allowed, "Actual BlockItem placement must retain role BUILD semantics");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void legacyConfigurationUsesSecureDefaultsAndIndependentCategories(GameTestHelper h) {
        try {
            Properties properties = new Properties(); properties.setProperty("radiusChunks", "12");
            TerritoryProtectionConfig.load(properties);
            h.assertTrue(TerritoryProtectionConfig.explosions && TerritoryProtectionConfig.pistons && TerritoryProtectionConfig.fluids
                    && TerritoryProtectionConfig.dispensers && TerritoryProtectionConfig.fire && TerritoryProtectionConfig.entities, "Old properties must enable all protections");
            properties.setProperty("protection.fluids", "false"); properties.setProperty("protection.explosions", "invalid");
            TerritoryProtectionConfig.load(properties);
            h.assertFalse(TerritoryProtectionConfig.fluids, "Explicit server override must load");
            h.assertTrue(TerritoryProtectionConfig.explosions && TerritoryProtectionConfig.pistons, "Invalid values stay secure; categories load independently");
        } finally { TerritoryProtectionConfig.load(new Properties()); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    @SuppressWarnings("unchecked")
    public void unavailableRegistryFailsClosedForRealOperations(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, false, false)) {
            var field = KingdomService.class.getDeclaredField("SERVICES"); field.setAccessible(true);
            var services = (Map<net.minecraft.server.MinecraftServer, KingdomService>)field.get(null);
            KingdomService previous = services.get(f.level.getServer());
            CompoundTag tag = new CompoundTag(); tag.putInt("Version", Integer.MAX_VALUE);
            var unavailable = KingdomSavedData.load(tag, f.level.registryAccess());
            services.put(f.level.getServer(), new KingdomService(f.level.getServer(), unavailable));
            try {
                f.set(f.east, Blocks.GLASS);
                var explosion = new Explosion(f.level, null, f.east.getX(), f.east.getY(), f.east.getZ(), 4, true, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(f.east); explosion.finalizeExplosion(false);
                f.is(f.east, Blocks.GLASS, "Unavailable registry must deny explosion writes even in apparent wilderness");
                f.set(f.east, Blocks.AIR); f.channel(f.west, Blocks.WATER); f.tickFluid(f.west);
                f.is(f.east, Blocks.AIR, "Unavailable registry must deny fluid boundary writes");
                f.piston(f.west.west(), Direction.EAST, false, false); f.set(f.west, Blocks.STONE);
                h.assertFalse(f.extend(f.west.west()), "Unavailable registry must deny piston operation");
                h.assertFalse(ClaimService.canBuild(f.level, f.owner, f.east), "Unavailable registry must preserve existing player fail-closed behavior");
            } finally { services.put(f.level.getServer(), previous); }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void dispenserBonemealSecondaryGrowthCannotModifyNeighborClaim(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            for (BlockPos pos : BlockPos.betweenClosed(f.west.offset(-2, 0, -3), f.east.offset(3, 0, 3))) {
                f.set(pos.below(), Blocks.STONE); f.set(pos, Blocks.WATER);
            }
            f.dispense(f.west.west(), Direction.EAST, Items.BONE_MEAL);
            boolean grewOutside = false;
            for (BlockPos pos : BlockPos.betweenClosed(f.west.offset(-2, 0, -3), f.east.offset(3, 0, 3))) {
                if (pos.getX() >= f.east.getX()) f.is(pos, Blocks.WATER, "Secondary underwater bonemeal write cannot cross boundary");
                else if (f.level.getBlockState(pos).is(Blocks.SEAGRASS) || f.level.getBlockState(pos).is(Blocks.TALL_SEAGRASS)) grewOutside = true;
            }
            h.assertTrue(grewOutside, "Vanilla bonemeal must actually grow plants in wilderness");
            f.level.setBlockAndUpdate(f.east, Blocks.STONE.defaultBlockState());
            f.is(f.east, Blocks.STONE, "Dispense context must end before subsequent world writes");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void lavaAndLightningCannotIgniteClaimedTerrain(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.enableFireTicks();
            f.set(f.east.below(), Blocks.STONE); f.set(f.east, Blocks.AIR);
            var lightning = EntityType.LIGHTNING_BOLT.create(f.level); lightning.setPos(Vec3.atBottomCenterOf(f.east));
            invoke(net.minecraft.world.entity.LightningBolt.class, "spawnFire", lightning, new Class<?>[] {int.class}, 0);
            f.is(f.east, Blocks.AIR, "Lightning fire must not appear in claims");
            f.set(f.west.below(), Blocks.STONE); lightning.setPos(Vec3.atBottomCenterOf(f.west));
            invoke(net.minecraft.world.entity.LightningBolt.class, "spawnFire", lightning, new Class<?>[] {int.class}, 0);
            f.is(f.west, Blocks.FIRE, "Lightning ignition must still work in wilderness");
            f.set(f.west, Blocks.LAVA); f.set(f.east.above(), Blocks.OAK_PLANKS);
            for (int i = 0; i < 200; i++) f.level.getFluidState(f.west).randomTick(f.level, f.west, f.level.random);
            f.is(f.east, Blocks.AIR, "Lava random ticks cannot ignite protected air across boundary");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void sheepAndFoxesCannotConsumeClaimedTerrain(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            var sheep = EntityType.SHEEP.create(f.level);
            for (BlockPos pos : List.of(f.east, f.west)) {
                f.set(pos, Blocks.SHORT_GRASS); sheep.setPos(Vec3.atBottomCenterOf(pos));
                var eat = new EatBlockGoal(sheep); eat.start();
                for (int i = 0; i < 40; i++) eat.tick();
                f.is(pos, pos.equals(f.east) ? Blocks.SHORT_GRASS : Blocks.AIR, "Sheep consumption must respect actual target claim");
            }
            Fox fox = EntityType.FOX.create(f.level);
            Constructor<?> constructor = Class.forName("net.minecraft.world.entity.animal.Fox$FoxEatBerriesGoal")
                    .getDeclaredConstructor(Fox.class, double.class, int.class, int.class); constructor.setAccessible(true);
            Object eat = constructor.newInstance(fox, 1.0, 8, 2);
            var target = MoveToBlockGoal.class.getDeclaredField("blockPos"); target.setAccessible(true);
            for (BlockPos pos : List.of(f.east, f.west)) {
                f.set(pos.below(), Blocks.DIRT); f.set(pos, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3)); target.set(eat, pos);
                invoke(eat.getClass(), "onReachedTarget", eat, new Class<?>[0]);
                h.assertTrue(f.level.getBlockState(pos).getValue(SweetBerryBushBlock.AGE) == (pos.equals(f.east) ? 3 : 1), "Fox harvesting must respect actual target");
                h.assertTrue(fox.getMainHandItem().isEmpty() == pos.equals(f.east), "Denied harvesting must not duplicate berries");
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void silverfishCannotMergeIntoClaimedStone(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            Silverfish silverfish = EntityType.SILVERFISH.create(f.level); silverfish.setPos(Vec3.atBottomCenterOf(f.west));
            Class<?> type = Class.forName("net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal");
            var constructor = type.getDeclaredConstructor(Silverfish.class); constructor.setAccessible(true);
            Goal merge = (Goal)constructor.newInstance(silverfish);
            var merging = type.getDeclaredField("doMerge"); merging.setAccessible(true); merging.setBoolean(merge, true);
            var direction = type.getDeclaredField("selectedDirection"); direction.setAccessible(true); direction.set(merge, Direction.EAST);
            f.set(f.east, Blocks.STONE); merge.start();
            f.is(f.east, Blocks.STONE, "Silverfish outside claim cannot infest protected stone");
            h.assertFalse(silverfish.isRemoved(), "Denied merge must preserve the mob");
            f.data.removeClaim(ClaimPos.at(f.level.dimension(), f.east)); merge.start();
            f.is(f.east, Blocks.INFESTED_STONE, "Wilderness infestation must retain vanilla behavior");
            h.assertTrue(silverfish.isRemoved(), "Authorized vanilla merge must finish");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void fallingBlocksRetainOriginAcrossEntityReload(GameTestHelper h) throws Exception {
        for (boolean sameCity : List.of(false, true)) try (var f = new Fixture(h, true, false)) {
            if (sameCity) f.data.putClaim(ClaimPos.at(f.level.dimension(), f.west), f.eastCity.id());
            f.set(f.west.above(3), Blocks.SAND); f.set(f.east.below(), Blocks.STONE);
            FallingBlockEntity falling = FallingBlockEntity.fall(f.level, f.west.above(3), Blocks.SAND.defaultBlockState());
            falling.setPos(Vec3.atBottomCenterOf(f.east));
            CompoundTag saved = new CompoundTag(); falling.saveWithoutId(saved); falling.discard();
            var loaded = new FallingBlockEntity(EntityType.FALLING_BLOCK, f.level); loaded.load(saved); loaded.setDeltaMovement(0, -.1, 0); loaded.setNoGravity(true);
            loaded.tick();
            f.is(f.east, sameCity ? Blocks.SAND : Blocks.AIR, "Reload must not erase falling-block boundary attribution");
            h.assertTrue(loaded.isRemoved(), "Falling block must finish or drop its item after a denied landing");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void bucketAirUseChecksInteractForPlacementAndPickup(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            ServerPlayer member = h.makeMockServerPlayerInLevel();
            try {
                f.data.addCitizen(f.eastCity.id(), member.getUUID());
                ServerPlayer outsider = f.player();
                f.set(f.east.below(), Blocks.STONE);
                for (ServerPlayer actor : List.of(outsider, member)) {
                    actor.setPos(f.east.getX() + .5, f.east.getY() + 2, f.east.getZ() + .5);
                    actor.setXRot(90); actor.setYRot(0);
                    for (Item bucket : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
                        f.set(f.east, Blocks.AIR); actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bucket));
                        boolean poured = bucket.use(f.level, actor, InteractionHand.MAIN_HAND).getResult().consumesAction();
                        h.assertTrue(poured == (actor == member), "Actual air-use bucket placement must require INTERACT");
                        f.is(f.east, actor == member ? (bucket == Items.WATER_BUCKET ? Blocks.WATER : Blocks.LAVA) : Blocks.AIR, "Bucket placement target must follow authorization");
                    }
                    f.set(f.east, Blocks.WATER); actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    boolean pickedUp = Items.BUCKET.use(f.level, actor, InteractionHand.MAIN_HAND).getResult().consumesAction();
                    h.assertTrue(pickedUp == (actor == member), "Actual air-use bucket removal must require INTERACT");
                    f.is(f.east, actor == member ? Blocks.AIR : Blocks.WATER, "Bucket pickup must preserve an unauthorized source");
                }
            } finally { f.level.getServer().getPlayerList().remove(member); }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void spongesCannotDrainWaterAcrossClaimBoundaries(GameTestHelper h) throws Exception {
        for (boolean sameCity : List.of(false, true)) try (var f = new Fixture(h, true, false)) {
            if (sameCity) f.data.putClaim(ClaimPos.at(f.level.dimension(), f.west), f.eastCity.id());
            f.set(f.east, Blocks.WATER); f.set(f.west, Blocks.SPONGE);
            f.is(f.east, sameCity ? Blocks.AIR : Blocks.WATER, "Sponge absorption must respect ownership boundaries");
            f.is(f.west, sameCity ? Blocks.WET_SPONGE : Blocks.SPONGE, "Denied sponge cannot count protected water as absorbed");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void frostWalkerDiskRequiresBuildPermissionAtEveryTarget(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            var effect = new net.minecraft.world.item.enchantment.effects.ReplaceDisk(
                    net.minecraft.world.item.enchantment.LevelBasedValue.constant(2), net.minecraft.world.item.enchantment.LevelBasedValue.constant(1),
                    net.minecraft.core.Vec3i.ZERO, java.util.Optional.empty(),
                    net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.FROSTED_ICE), java.util.Optional.empty());
            f.set(f.west, Blocks.WATER); f.set(f.east, Blocks.WATER);
            effect.apply(f.level, 1, null, f.player(), Vec3.atCenterOf(f.west));
            f.is(f.west, Blocks.FROSTED_ICE, "Outsider enchantment must retain wilderness behavior");
            f.is(f.east, Blocks.WATER, "Outsider enchantment must not freeze protected water");
            effect.apply(f.level, 1, null, f.owner, Vec3.atCenterOf(f.west));
            f.is(f.east, Blocks.FROSTED_ICE, "Authorized owner enchantment can freeze own water");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void lingeringArrowsCannotActivateProtectedWoodenButtons(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true, false)) {
            f.set(f.east.below(), Blocks.STONE);
            f.set(f.east, Blocks.OAK_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACE,
                    net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            Arrow arrow = new Arrow(EntityType.ARROW, f.level); arrow.setPos(Vec3.atBottomCenterOf(f.east).add(0, .05, 0)); f.level.addFreshEntity(arrow);
            try {
                f.level.getBlockState(f.east).entityInside(f.level, f.east, arrow);
                h.assertFalse(f.level.getBlockState(f.east).getValue(net.minecraft.world.level.block.ButtonBlock.POWERED), "Ownerless lingering arrow must not power protected redstone");
                arrow.setOwner(f.owner); f.level.getBlockState(f.east).entityInside(f.level, f.east, arrow);
                h.assertTrue(f.level.getBlockState(f.east).getValue(net.minecraft.world.level.block.ButtonBlock.POWERED), "Authorized arrow must retain wooden button behavior");
            } finally { arrow.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "territory_hardening")
    public void explosionsCannotIndirectlyDropProtectedBoundaryAttachments(GameTestHelper h) throws Exception {
        for (boolean claimed : List.of(false, true)) try (var f = new Fixture(h, claimed, false)) {
            f.set(f.west, Blocks.GLASS);
            f.set(f.east, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.EAST));
            var explosion = new Explosion(f.level, null, f.west.getX(), f.west.getY(), f.west.getZ(), 4, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(f.west); explosion.finalizeExplosion(false);
            f.is(f.west, Blocks.AIR, "Wilderness support must still be destroyed normally");
            f.is(f.east, claimed ? Blocks.WALL_TORCH : Blocks.AIR, "Losing foreign support must not indirectly destroy claimed attachments");
        }
        h.succeed();
    }

    private static Goal goal(String name, EnderMan enderman) throws Exception {
        Constructor<?> constructor = Class.forName(name).getDeclaredConstructor(EnderMan.class);
        constructor.setAccessible(true); return (Goal)constructor.newInstance(enderman);
    }

    private static Object invoke(Class<?> type, String name, Object instance, Class<?>[] signature, Object... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, signature); method.setAccessible(true);
        return method.invoke(instance, arguments);
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final KingdomSavedData data;
        final BlockPos east, west;
        final ServerPlayer owner;
        final Settlement eastCity;
        final boolean previousFireTicks;
        final List<Kingdom> kingdoms = new ArrayList<>();

        Fixture(GameTestHelper h, boolean claimEast, boolean claimWest) throws Exception {
            this.h = h; level = h.getLevel(); data = KingdomService.get(level.getServer()).data();
            previousFireTicks = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
            int id = NEXT.incrementAndGet(); east = new BlockPos(64_000 + id * 64 + 16, 100, 64_008); west = east.west();
            level.getChunkAt(east); level.getChunkAt(west);
            owner = player(); eastCity = claimEast ? city(owner, east.offset(8, 0, 0), id + " East") : null;
            if (claimWest) city(player(), west.offset(-8, 0, 0), id + " West");
        }

        Settlement city(ServerPlayer founder, BlockPos capital, String name) throws Exception {
            Kingdom kingdom = new Kingdom(UUID.randomUUID(), new KingdomName("Hardening " + name), founder.getUUID(), level.dimension(), capital,
                    UUID.randomUUID(), System.currentTimeMillis(), Kingdom.CapitalStatus.PRESENT);
            data.add(kingdom); kingdoms.add(kingdom); return data.settlement(kingdom.id());
        }

        ServerPlayer player() {
            var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Hardening"), ClientInformation.createDefault()) {
                @Override public void displayClientMessage(Component message, boolean overlay) { }
            };
            player.setPos(0, 100, 0); return player;
        }

        void set(BlockPos pos, Block block) { set(pos, block.defaultBlockState()); }
        void set(BlockPos pos, BlockState state) { level.setBlock(pos, state, 18); }
        void is(BlockPos pos, Block block, String message) { h.assertTrue(level.getBlockState(pos).is(block), message + " at " + pos + ": " + level.getBlockState(pos)); }
        BlockHitResult hit(BlockPos pos, Direction direction) { return new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false); }

        void piston(BlockPos pos, Direction facing, boolean sticky, boolean extended) {
            set(pos, (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState().setValue(PistonBaseBlock.FACING, facing).setValue(PistonBaseBlock.EXTENDED, extended));
            if (extended) set(pos.relative(facing), Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, facing)
                    .setValue(PistonHeadBlock.TYPE, sticky ? PistonType.STICKY : PistonType.DEFAULT));
        }
        boolean extend(BlockPos pos) {
            set(pos.above(), Blocks.REDSTONE_BLOCK);
            return level.getBlockState(pos).triggerEvent(level, pos, 0, level.getBlockState(pos).getValue(PistonBaseBlock.FACING).get3DDataValue());
        }
        boolean retract(BlockPos pos) {
            set(pos.above(), Blocks.AIR);
            return level.getBlockState(pos).triggerEvent(level, pos, 1, level.getBlockState(pos).getValue(PistonBaseBlock.FACING).get3DDataValue());
        }
        void settle(BlockPos start, BlockPos end) {
            for (int i = 0; i < 4; i++) for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
                if (level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving) PistonMovingBlockEntity.tick(level, pos.immutable(), level.getBlockState(pos), moving);
            }
        }
        void channel(BlockPos source, Block fluid) {
            for (int dx = -1; dx <= 2; dx++) {
                set(source.offset(dx, -1, 0), Blocks.STONE);
                set(source.offset(dx, 0, -1), Blocks.STONE); set(source.offset(dx, 0, 1), Blocks.STONE);
            }
            set(source.west(), Blocks.STONE); set(source, fluid);
        }
        void enableFireTicks() { level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK).set(true, level.getServer()); }
        void tickFluid(BlockPos pos) { level.getFluidState(pos).tick(level, pos); }
        void dispense(BlockPos pos, Direction direction, Item item) {
            set(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, direction));
            var inventory = (DispenserBlockEntity)level.getBlockEntity(pos); inventory.clearContent(); inventory.setItem(0, new ItemStack(item));
            level.getBlockState(pos).tick(level, pos, level.random);
        }
        @Override public void close() throws Exception {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK).set(previousFireTicks, level.getServer());
            for (Kingdom kingdom : kingdoms) { data.deleteKingdom(kingdom.id()); data.deleteSettlement(kingdom.id()); }
        }
    }
}
