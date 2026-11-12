package dev.sakhno.regnum.nations.client.heraldry;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.sakhno.regnum.nations.block.*;
import dev.sakhno.regnum.nations.kingdom.*;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One anchor, static supports and a pinned, deforming cloth. Animation is entirely client-side. */
public final class RoyalBannerRenderer implements BlockEntityRenderer<RoyalBannerBlockEntity> {
    private static final ResourceLocation WOOD=ResourceLocation.withDefaultNamespace("textures/block/dark_oak_log.png");
    private static final ResourceLocation METAL=ResourceLocation.withDefaultNamespace("textures/block/gold_block.png");
    private static final ResourceLocation IRON=ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private static final int COLUMNS=8,ROWS=16,STRIDE=6;
    private static final float WIDTH=1.5f,HEIGHT=2.25f;
    private final ModelPart standingWood,standingMetal,wallWood,wallMetal,wallIron;
    // Reused render-thread scratch space: no vertex/vector allocations per frame.
    private final float[] mesh=new float[(COLUMNS+1)*(ROWS+1)*STRIDE];
    private final int[] rowLight=new int[ROWS+1];

    private RoyalBannerRenderer() {
        standingWood=model(CubeListBuilder.create()
                .addBox(7,0,7,2,45,2).addBox(-5,43,5,26,2,2));
        standingMetal=model(CubeListBuilder.create()
                .addBox(6.5f,0,6.5f,3,2,3).addBox(6.5f,39,6.5f,3,2,3)
                .addBox(6,45,6,4,1.5f,4).addBox(7,46.5f,7,2,3.5f,2)
                .addBox(5.5f,47,7.25f,5,1,1.5f)
                .addBox(-6,42.5f,4.5f,2,3,3).addBox(20,42.5f,4.5f,2,3,3));
        // North-facing local model: the supporting wall is at z=16.
        wallWood=model(CubeListBuilder.create()
                .addBox(7,8,10,2,2,6).addBox(-5,13,10,26,2,2));
        wallMetal=model(CubeListBuilder.create()
                .addBox(-6,12.5f,9.5f,2,3,3).addBox(20,12.5f,9.5f,2,3,3)
                .addBox(6.5f,12.5f,9.5f,3,3,3));
        wallIron=model(CubeListBuilder.create()
                .addBox(5,4,15,6,12,1).addBox(6.5f,9,10.5f,3,5,2)
                .addBox(6,5,14.5f,1,1,1).addBox(9,14,14.5f,1,1,1));
    }

    private static ModelPart model(CubeListBuilder cubes) {
        var mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("support",cubes,PartPose.ZERO);
        return LayerDefinition.create(mesh,16,16).bakeRoot();
    }

    public static void initialize() {
        BlockEntityRenderers.register(ModBlocks.ROYAL_BANNER_ENTITY,context -> new RoyalBannerRenderer());
        var itemRenderer=new RoyalBannerRenderer();
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.ROYAL_BANNER_ITEM,(stack,mode,poses,buffers,light,overlay) -> {
            poses.pushPose();
            // Fit the complete standard into the unit-sized item presentation in every hand/view.
            poses.translate(.5,0,.5); poses.scale(.31f,.31f,.31f); poses.translate(-.5,0,-.5);
            itemRenderer.supports(poses,buffers,light,overlay,false);
            itemRenderer.cloth(HeraldryClient.arms(HeraldryService.identity(stack)),poses,buffers,
                    light,overlay,false,0,0,null,1);
            poses.popPose();
        });
    }

    @Override public void render(RoyalBannerBlockEntity banner,float delta,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        var level=banner.getLevel();
        if (level==null) return;
        boolean wall=banner.getBlockState().is(ModBlocks.WALL_ROYAL_BANNER);
        BlockPos pos=banner.getBlockPos();
        Direction facing=banner.getBlockState().getValue(RoyalBannerBlock.FACING);
        // Vanilla's banner principle: bounded world time plus a stable position phase.
        long offset=Math.floorMod(pos.getX()*7L+pos.getY()*9L+pos.getZ()*13L,100L);
        float time=((level.getGameTime()%24000L)+delta)*(Mth.TWO_PI/80f);
        float phase=offset*(Mth.TWO_PI/100f);
        double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(
                pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        poses.pushPose(); poses.translate(.5,0,.5);
        poses.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot())); poses.translate(-.5,0,-.5);
        int supportLight=LevelRenderer.getLightColor(level,wall?pos:pos.above(2));
        supports(poses,buffers,supportLight,overlay,wall);
        cloth(HeraldryClient.arms(banner.kingdom()),poses,buffers,light,overlay,wall,time,phase,banner,distance>32*32?2:1);
        poses.popPose();
    }

    @Override public boolean shouldRenderOffScreen(RoyalBannerBlockEntity banner) {
        // Cloth extends above/below the single anchor's frustum bounds.
        return true;
    }

    private void supports(PoseStack poses,MultiBufferSource buffers,int light,int overlay,boolean wall) {
        (wall?wallWood:standingWood).render(poses,buffers.getBuffer(RenderType.entitySolid(WOOD)),light,overlay);
        (wall?wallMetal:standingMetal).render(poses,buffers.getBuffer(RenderType.entitySolid(METAL)),light,overlay);
        if (wall) wallIron.render(poses,buffers.getBuffer(RenderType.entitySolid(IRON)),light,overlay);
    }

    private void cloth(Heraldry arms,PoseStack poses,MultiBufferSource buffers,int light,int overlay,
                       boolean wall,float time,float phase,RoyalBannerBlockEntity banner,int step) {
        float top=wall?.8125f:2.6875f,baseZ=wall?.59375f:.28125f;
        // Travel down the cloth with an increasing amplitude. The entire top edge stays pinned.
        for (int row=0;row<=ROWS;row+=step) {
            float t=(float)row/ROWS;
            rowLight[row]=banner==null?light:LevelRenderer.getLightColor(banner.getLevel(),
                    banner.getBlockPos().offset(0,Mth.floor(top-HEIGHT*t),0));
            for (int column=0;column<=COLUMNS;column+=step) {
                float u=(float)column/COLUMNS;
                float a=time+phase-t*4.2f;
                float b=time*1.7f+phase+u*Mth.TWO_PI-t*6.5f;
                float c=time*.63f+phase+u*3.1f-t*2.4f;
                float amplitude=wall?.055f:.11f;
                float wave=amplitude*Mth.sin(a)+.025f*Mth.sin(b)+.018f*Mth.sin(c);
                float dzdu=t*(.025f*Mth.TWO_PI*Mth.cos(b)+.018f*3.1f*Mth.cos(c));
                float dzdt=wave+t*(-4.2f*amplitude*Mth.cos(a)-.025f*6.5f*Mth.cos(b)-.018f*2.4f*Mth.cos(c));
                // Normal of the deformed surface, rather than a fixed normal on a moving quad.
                float nx=dzdu/WIDTH,ny=-dzdt/HEIGHT;
                float inverse=Mth.invSqrt(nx*nx+ny*ny+1);
                int i=index(column,row);
                mesh[i]=.5f+(u-.5f)*WIDTH; mesh[i+1]=top-t*HEIGHT; mesh[i+2]=baseZ+t*wave;
                mesh[i+3]=nx*inverse; mesh[i+4]=ny*inverse; mesh[i+5]=-inverse;
            }
        }
        var consumer=buffers.getBuffer(RenderType.entityCutout(HeraldryTextures.INSTANCE.texture(arms,true)));
        var pose=poses.last();
        for (int row=0;row<ROWS;row+=step) for (int column=0;column<COLUMNS;column+=step) {
            // Opposite winding and normals provide correctly lit fabric on both sides.
            vertex(consumer,pose,column,row,overlay,1);
            vertex(consumer,pose,column+step,row,overlay,1);
            vertex(consumer,pose,column+step,row+step,overlay,1);
            vertex(consumer,pose,column,row+step,overlay,1);
            vertex(consumer,pose,column,row+step,overlay,-1);
            vertex(consumer,pose,column+step,row+step,overlay,-1);
            vertex(consumer,pose,column+step,row,overlay,-1);
            vertex(consumer,pose,column,row,overlay,-1);
        }
    }

    private static int index(int column,int row) { return (row*(COLUMNS+1)+column)*STRIDE; }
    private void vertex(VertexConsumer v,PoseStack.Pose pose,int column,int row,int overlay,int side) {
        int i=index(column,row);
        // UVs are attached to the deforming mesh; heraldry never slides over the cloth.
        v.addVertex(pose,mesh[i],mesh[i+1],mesh[i+2]).setColor(255,255,255,255)
                .setUv((float)column/COLUMNS,(float)row/ROWS).setOverlay(overlay).setLight(rowLight[row])
                .setNormal(pose,mesh[i+3]*side,mesh[i+4]*side,mesh[i+5]*side);
    }
}
