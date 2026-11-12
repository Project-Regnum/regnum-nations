package dev.sakhno.regnum.nations.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

public final class WallRoyalBannerBlock extends RoyalBannerBlock {
    public static final MapCodec<WallRoyalBannerBlock> CODEC=simpleCodec(WallRoyalBannerBlock::new);
    public WallRoyalBannerBlock(Properties properties) { super(properties,true); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
}
