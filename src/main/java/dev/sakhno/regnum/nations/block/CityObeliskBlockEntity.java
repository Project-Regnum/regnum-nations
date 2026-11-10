package dev.sakhno.regnum.nations.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Distinct registry type and identity, reusing the proven non-ticking obelisk serialization. */
public final class CityObeliskBlockEntity extends KingdomObeliskBlockEntity {
    public CityObeliskBlockEntity(BlockPos pos,BlockState state) { super(ModBlocks.CITY_OBELISK_ENTITY,pos,state); }
    @Override protected String bindingTag() { return "SettlementId"; }
    public UUID getSettlementId() { return getKingdomId(); }
    public void bindSettlement(UUID id) { bindKingdom(id); }
}
