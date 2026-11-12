package dev.sakhno.regnum.nations.block;

import com.mojang.serialization.MapCodec;
import dev.sakhno.regnum.nations.kingdom.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;

/** A decorative cloth standard, never a claim anchor or source of permission. */
public class RoyalBannerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<RoyalBannerBlock> CODEC=simpleCodec(RoyalBannerBlock::new);
    protected final boolean wall;
    public RoyalBannerBlock(Properties properties) { this(properties,false); }
    protected RoyalBannerBlock(Properties properties,boolean wall) {
        super(properties); this.wall=wall; registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new RoyalBannerBlockEntity(pos,state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!wall) return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
        Direction face=context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        var state=defaultBlockState().setValue(FACING,face);
        return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;
    }

    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction support=wall?state.getValue(FACING).getOpposite():Direction.DOWN;
        return level.getBlockState(pos.relative(support)).isFaceSturdy(level,pos.relative(support),support.getOpposite());
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        return !state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if (!wall) return Shapes.or(box(6,0,6,10,16,10),state.getValue(FACING).getAxis()==Direction.Axis.X?box(5,4,0,11,16,16):box(0,4,5,16,16,11));
        return switch (state.getValue(FACING)) {
            case NORTH -> box(0,0,9,16,16,16); case SOUTH -> box(0,0,0,16,16,7);
            case WEST -> box(9,0,0,16,16,16); default -> box(0,0,0,7,16,16);
        };
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            try {
                var id=new HeraldryService(KingdomService.get(server.getServer()).data()).verifiedIdentity(stack);
                if (id!=null&&level.getBlockEntity(pos) instanceof RoyalBannerBlockEntity banner) banner.bind(id);
            } catch (FoundingException ignored) { }
        }
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RoyalBannerBlockEntity banner&&banner.kingdom()!=null) {
            try { return List.of(new HeraldryService(KingdomService.get(builder.getLevel().getServer()).data()).banner(banner.kingdom())); }
            catch (FoundingException ignored) { return List.of(); }
        }
        return List.of(new ItemStack(ModBlocks.ROYAL_BANNER_ITEM));
    }
}
