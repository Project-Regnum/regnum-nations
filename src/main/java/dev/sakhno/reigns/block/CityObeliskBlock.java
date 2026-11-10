package dev.sakhno.reigns.block;

import com.mojang.serialization.MapCodec;
import dev.sakhno.reigns.kingdom.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CityObeliskBlock extends KingdomObeliskBlock {
    public static final MapCodec<CityObeliskBlock> CODEC=simpleCodec(CityObeliskBlock::new);
    public CityObeliskBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends CityObeliskBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new CityObeliskBlockEntity(pos,state); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CityObeliskBlockEntity block) {
            var service=KingdomService.get(serverPlayer.serverLevel().getServer()); service.forget(player.getUUID());
            Settlement city=service.data().cityAt(level.dimension(),pos);
            if (city!=null&&city.obeliskId().equals(block.getObeliskId())&&service.cities().resolve(city)==Kingdom.CapitalStatus.PRESENT) {
                KingdomManagementServer.openCityMenu(serverPlayer,city.id());
            } else if (city!=null||block.getSettlementId()!=null) player.displayClientMessage(Component.translatable("message.project-reigns.founding.already_bound"),false);
            else try {
                service.cities().select(serverPlayer,pos); KingdomManagementServer.openCityFounding(serverPlayer,block.getObeliskId());
            } catch (FoundingException e) { player.displayClientMessage(Component.translatable(e.translationKey()),false); }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock())&&level instanceof ServerLevel serverLevel) {
            var data=KingdomService.get(serverLevel.getServer()).data(); Settlement city=data.cityAt(level.dimension(),pos);
            if (city!=null) KingdomService.get(serverLevel.getServer()).cityRemoved(city);
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
