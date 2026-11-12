package dev.sakhno.regnum.nations.block;

import dev.sakhno.regnum.nations.kingdom.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class RoyalBannerItem extends StandingAndWallBlockItem {
    public RoyalBannerItem(Properties properties) { super(ModBlocks.ROYAL_BANNER,ModBlocks.WALL_ROYAL_BANNER,properties,Direction.DOWN); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel server) {
            try {
                if (new HeraldryService(KingdomService.get(server.getServer()).data()).verifiedIdentity(context.getItemInHand())==null) {
                    if (context.getPlayer()!=null) context.getPlayer().displayClientMessage(Component.translatable("heraldry.regnum-nations.unsealed"),true);
                    return InteractionResult.FAIL;
                }
            } catch (FoundingException e) { return InteractionResult.FAIL; }
        }
        return super.place(context);
    }
    /** Item-supplied BlockEntityTag must never override the authenticated kingdom. */
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos,Level level,Player player,ItemStack stack,BlockState state) { return false; }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,java.util.List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable(HeraldryService.identity(stack)==null?"heraldry.regnum-nations.unsealed":"heraldry.regnum-nations.trophy"));
    }
}
