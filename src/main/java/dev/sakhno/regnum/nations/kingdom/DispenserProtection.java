package dev.sakhno.regnum.nations.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

/** Bounded to one synchronous dispense; also checks secondary bonemeal/tree writes at their actual targets. */
public final class DispenserProtection {
    private static final ThreadLocal<BlockSource> SOURCE = new ThreadLocal<>();
    private DispenserProtection() { }

    public static ItemStack dispense(DispenseItemBehavior behavior, BlockSource source, ItemStack stack) {
        if (!TerritoryProtectionConfig.dispensers) return behavior.dispense(source, stack);
        BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        if (!TerritoryProtection.transferAllowed(source.level(), source.pos(), target)) return stack;
        BlockSource previous = SOURCE.get();
        SOURCE.set(source);
        try { return behavior.dispense(source, stack); }
        finally {
            if (previous == null) SOURCE.remove(); else SOURCE.set(previous);
        }
    }

    public static boolean allows(Level level, BlockPos target) {
        BlockSource source = SOURCE.get();
        return source == null || source.level() != level
                || TerritoryProtection.transferAllowed(level, source.pos(), target);
    }
}
