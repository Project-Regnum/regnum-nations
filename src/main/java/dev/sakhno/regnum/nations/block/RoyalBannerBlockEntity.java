package dev.sakhno.regnum.nations.block;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only stable identity persists. No image, copied arms, tick, or global placed-banner index. */
public final class RoyalBannerBlockEntity extends BlockEntity {
    private UUID kingdom;
    public RoyalBannerBlockEntity(BlockPos pos,BlockState state) { super(ModBlocks.ROYAL_BANNER_ENTITY,pos,state); }
    public UUID kingdom() { return kingdom; }
    public void bind(UUID kingdom) {
        if (level==null||level.isClientSide||!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        this.kingdom=java.util.Objects.requireNonNull(kingdom); setChanged();
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.saveAdditional(tag,registries); if (kingdom!=null) tag.putUUID("KingdomId",kingdom); }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) { super.loadAdditional(tag,registries); kingdom=tag.hasUUID("KingdomId")?tag.getUUID("KingdomId"):null; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var tag=new CompoundTag(); if (kingdom!=null) tag.putUUID("KingdomId",kingdom); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
}
