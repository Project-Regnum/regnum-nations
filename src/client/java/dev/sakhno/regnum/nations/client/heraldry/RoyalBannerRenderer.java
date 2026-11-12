package dev.sakhno.regnum.nations.client.heraldry;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.sakhno.regnum.nations.block.*;
import dev.sakhno.regnum.nations.kingdom.*;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;

/** Batched, double-sided static cloth: one quad per banner, no tick and no per-frame composition. */
public final class RoyalBannerRenderer implements BlockEntityRenderer<RoyalBannerBlockEntity> {
    public static void initialize() {
        BlockEntityRenderers.register(ModBlocks.ROYAL_BANNER_ENTITY,context -> new RoyalBannerRenderer());
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.ROYAL_BANNER_ITEM,(stack,mode,poses,buffers,light,overlay) -> {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.ROYAL_BANNER.defaultBlockState(),poses,buffers,light,overlay);
            cloth(HeraldryClient.arms(HeraldryService.identity(stack)),poses,buffers,light,overlay,false);
        });
    }
    @Override public void render(RoyalBannerBlockEntity banner,float delta,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        poses.pushPose(); poses.translate(.5,0,.5);
        Direction direction=banner.getBlockState().getValue(RoyalBannerBlock.FACING);
        poses.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()+180)); poses.translate(-.5,0,-.5);
        cloth(HeraldryClient.arms(banner.kingdom()),poses,buffers,light,overlay,banner.getBlockState().is(ModBlocks.WALL_ROYAL_BANNER));
        poses.popPose();
    }
    private static void cloth(Heraldry arms,PoseStack poses,MultiBufferSource buffers,int light,int overlay,boolean wall) {
        var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(HeraldryTextures.INSTANCE.texture(arms,true)));
        var pose=poses.last(); float z=wall?.73f:.42f;
        vertex(consumer,pose,.075f,.875f,z,0,0,light,overlay);
        vertex(consumer,pose,.075f,.075f,z,0,1,light,overlay);
        vertex(consumer,pose,.925f,.075f,z,1,1,light,overlay);
        vertex(consumer,pose,.925f,.875f,z,1,0,light,overlay);
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,float x,float y,float z,float u,float t,int light,int overlay) {
        v.addVertex(pose,x,y,z).setColor(255,255,255,255).setUv(u,t).setOverlay(overlay).setLight(light).setNormal(pose,0,0,-1);
    }
}
