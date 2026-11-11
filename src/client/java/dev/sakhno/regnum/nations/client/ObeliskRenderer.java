package dev.sakhno.regnum.nations.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.block.ModBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Static JSON upper geometry and full-bright hearts. No animation, ticking or packets. */
public final class ObeliskRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public static final ResourceLocation CROWN = RegnumNations.id("block/kingdom_obelisk_crown");
    public static final ResourceLocation CITY_GLOW = RegnumNations.id("block/city_obelisk_glow");
    public static final ResourceLocation KINGDOM_GLOW = RegnumNations.id("block/kingdom_obelisk_glow");
    private final boolean kingdom;
    private final RandomSource random = RandomSource.create(0);

    private ObeliskRenderer(boolean kingdom) {
        this.kingdom = kingdom;
    }

    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.addModels(CROWN, CITY_GLOW, KINGDOM_GLOW));
        BlockEntityRenderers.register(ModBlocks.KINGDOM_OBELISK_ENTITY, context -> new ObeliskRenderer<>(true));
        BlockEntityRenderers.register(ModBlocks.CITY_OBELISK_ENTITY, context -> new ObeliskRenderer<>(false));
    }

    @Override
    public void render(T obelisk, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = obelisk.getLevel();
        if (level == null) return;
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.getBlockRenderer().getModelRenderer();
        poses.pushPose();
        if (kingdom) {
            poses.translate(0, 1, 0);
            random.setSeed(0);
            // Look up baked models each frame so resource reloads cannot retain a stale atlas.
            renderer.tesselateBlock(level, minecraft.getModelManager().getModel(CROWN),
                    obelisk.getBlockState(), obelisk.getBlockPos().above(), poses,
                    buffers.getBuffer(RenderType.solid()), false, random, 0, overlay);
        }
        // Bright surfaces only: this does not add world light or change spawn/gameplay rules.
        renderer.renderModel(poses.last(), buffers.getBuffer(RenderType.solid()), obelisk.getBlockState(),
                minecraft.getModelManager().getModel(kingdom ? KINGDOM_GLOW : CITY_GLOW),
                1, 1, 1, LightTexture.FULL_BRIGHT, overlay);
        poses.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(T obelisk) {
        // The upper geometry can be visible while the anchor is outside the frustum.
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
