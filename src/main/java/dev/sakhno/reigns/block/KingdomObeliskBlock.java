package dev.sakhno.reigns.block;

import com.mojang.serialization.MapCodec;
import dev.sakhno.reigns.kingdom.FoundingException;
import dev.sakhno.reigns.kingdom.Kingdom;
import dev.sakhno.reigns.kingdom.KingdomService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class KingdomObeliskBlock extends BaseEntityBlock {
	public static final MapCodec<KingdomObeliskBlock> CODEC = simpleCodec(KingdomObeliskBlock::new);

	public KingdomObeliskBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KingdomObeliskBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!level.isClientSide) {
			if (!(level.getBlockEntity(pos) instanceof KingdomObeliskBlockEntity obelisk)) {
				return InteractionResult.FAIL;
			}

			boolean guiOpened = false;
			MutableComponent message = Component.translatable(
					"message.project-reigns.kingdom_obelisk.status", obelisk.getObeliskId().toString());
			if (player instanceof ServerPlayer serverPlayer) {
				KingdomService service = KingdomService.get(serverPlayer.serverLevel().getServer());
				service.forget(player.getUUID());
				service.cleanDeletedBinding(serverPlayer.serverLevel(),pos,obelisk);
				dev.sakhno.reigns.kingdom.Settlement legacyCity=service.data().cityAt(level.dimension(),pos);
				Kingdom kingdom = service.data().atCapital(level.dimension(), pos);
				if (kingdom != null) service.resolveCapital(kingdom);
				if (kingdom != null && kingdom.capitalObeliskId().equals(obelisk.getObeliskId())
						&& kingdom.id().equals(obelisk.getKingdomId())) {
					message.append(" ").append(Component.translatable("message.project-reigns.kingdom_obelisk.bound",
							kingdom.name().display()));
					guiOpened = dev.sakhno.reigns.kingdom.KingdomManagementServer.openMenu(serverPlayer, kingdom.id());
				} else if (kingdom==null && legacyCity!=null
						&& legacyCity.legacyAnchor() && legacyCity.obeliskId().equals(obelisk.getObeliskId()) && !player.isShiftKeyDown()) {
					guiOpened = dev.sakhno.reigns.kingdom.KingdomManagementServer.openCityMenu(serverPlayer,legacyCity.id());
					message.append(" ").append(net.minecraft.network.chat.Component.translatable("city.project-reigns.bound",legacyCity.name().display()));
				} else if (obelisk.getKingdomId() != null || kingdom != null) {
					message.append(" ").append(Component.translatable(
							"message.project-reigns.founding.already_bound"));
				} else {
					try {
						service.select(serverPlayer, pos);
						guiOpened = dev.sakhno.reigns.kingdom.KingdomManagementServer.openFounding(serverPlayer, obelisk.getObeliskId());
						message.append(" ").append(Component.translatable("message.project-reigns.founding.selected"));
					} catch (FoundingException exception) {
						message.append(" ").append(Component.translatable(exception.translationKey()));
					}
				}
			}
			if (!guiOpened) player.displayClientMessage(message, false);
		}

		// Consume the interaction on both sides; only the server sends the message.
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
			KingdomService.get(serverLevel.getServer()).capitalRemoved(serverLevel, pos);
		}
		super.onRemove(state, level, pos, newState, moving);
	}
}
