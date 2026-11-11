package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import dev.sakhno.regnum.nations.kingdom.CitizenshipTests.Fixture;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Regressions first run against v0.7.1 before implementing the protection fixes. */
public class ObeliskIntegrityTests implements FabricGameTest {
    private static final java.util.concurrent.atomic.AtomicInteger NEXT=new java.util.concurrent.atomic.AtomicInteger();
    // GameTest cells can share a chunk. Live founding fixtures need independent claim chunks.
    private static BlockPos location() { return new BlockPos(6_000_008+NEXT.getAndIncrement()*48,64,6_000_008); }
    @GameTest(template = EMPTY_STRUCTURE, batch = "obelisk_integrity")
    public void activeCityCannotBeMinedByItsOwner(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var pos = location();
            h.getLevel().setBlockAndUpdate(pos, ModBlocks.CITY_OBELISK.defaultBlockState());
            owner.setPos(Vec3.atCenterOf(pos));
            f.kingdoms.cities().select(owner, pos);
            var block = (CityObeliskBlockEntity) h.getLevel().getBlockEntity(pos);
            f.kingdoms.cities().found(owner, block.getObeliskId(), "Protected City", 1);
            h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(h.getLevel(), owner, pos,
                    h.getLevel().getBlockState(pos), block), "Owner mining must not destroy an active anchor");
            h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(h.getLevel(),owner,pos,
                    h.getLevel().getBlockState(pos),null),"Protection must not depend on a block entity");
            var broken=block.saveWithoutMetadata(h.getLevel().registryAccess()); broken.putUUID("ObeliskId",java.util.UUID.randomUUID());
            block.loadWithComponents(broken,h.getLevel().registryAccess());
            var explosion=new net.minecraft.world.level.Explosion(h.getLevel(),null,pos.getX(),pos.getY(),pos.getZ(),4,false,net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
            h.getLevel().getBlockState(pos).onExplosionHit(h.getLevel(),pos,explosion,(item,dropPos) -> h.fail("Invalid city entity cannot enable drops"));
            h.assertTrue(h.getLevel().getBlockState(pos).is(ModBlocks.CITY_OBELISK),"City explosion protection survives an invalid entity identity");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "obelisk_integrity")
    public void copiedCityIdentityCannotFoundAnotherKingdom(GameTestHelper h) throws Exception {
        try (var f = new Fixture(h, true)) {
            var owner = f.player(); var outsider = f.player(); var level = h.getLevel();
            var pos = location();
            level.setBlockAndUpdate(pos, ModBlocks.CITY_OBELISK.defaultBlockState()); owner.setPos(Vec3.atCenterOf(pos));
            var block = (CityObeliskBlockEntity) level.getBlockEntity(pos);
            f.kingdoms.cities().select(owner, pos); f.kingdoms.cities().found(owner, block.getObeliskId(), "Original City", 1);
            // Simulate privileged NBT copying into a different, unclaimed state obelisk.
            var copyPos = pos.offset(48, 0, 0); level.getChunkAt(copyPos);
            level.setBlockAndUpdate(copyPos, ModBlocks.KINGDOM_OBELISK.defaultBlockState());
            var copy = (dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity) level.getBlockEntity(copyPos);
            var tag = copy.saveWithoutMetadata(level.registryAccess()); tag.putUUID("ObeliskId", block.getObeliskId());
            copy.loadWithComponents(tag, level.registryAccess()); outsider.setPos(Vec3.atCenterOf(copyPos));
            boolean rejected = false;
            try { f.kingdoms.select(outsider, copyPos); f.kingdoms.found(outsider, "Copied State"); }
            catch (FoundingException expected) { rejected = expected.reason() == FoundingException.Reason.ALREADY_BOUND; }
            h.assertTrue(rejected, "A registered city identity cannot be reused to found a kingdom");
            level.setBlockAndUpdate(copyPos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }
}
