package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.block.*;
import dev.sakhno.regnum.nations.client.heraldry.*;
import dev.sakhno.regnum.nations.client.ui.HeraldryScreen;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Real client rendering and local client/server packets in a disposable integrated world. */
public final class HeraldryVisualChecks implements ClientModInitializer {
    private final long start=System.nanoTime();
    private final Path output=Path.of("checks").toAbsolutePath();
    private int stage,ticks;
    private volatile UUID realm;
    private volatile Throwable serverError;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private HeraldryScreen editor;
    private static Heraldry design(Emblem emblem,int index) {
        return new Heraldry(1,Shield.values()[index%5],Tincture.AZURE,Division.values()[index%6],Tincture.GULES,
                Ordinary.NONE,Tincture.ARGENT,List.of(new Charge(emblem,Tincture.OR,50,46,70,0,false)));
    }
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("regnum.heraldryVisual")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); }
            catch (Throwable error) {
                RegnumNations.LOGGER.error("HERALDRY VISUAL CHECKS FAILED stage {}",stage,error);
                try { Files.createDirectories(output); Files.writeString(output.resolve("failure.txt"),error.toString()); } catch(Exception ignored) { }
                System.exit(1);
            }
        });
    }
    private void tick(Minecraft mc) throws Exception {
        if (System.nanoTime()-start>360_000_000_000L) throw new AssertionError("Visual timeout stage "+stage);
        if (serverError!=null) throw new AssertionError(serverError);
        switch(stage) {
            case 0 -> {
                GLFW.glfwHideWindow(mc.getWindow().getWindow()); if (mc.getOverlay()!=null) return;
                if (mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (!(mc.screen instanceof TitleScreen)) return;
                if (!mc.getLanguageManager().getSelected().equals("en_us")) {
                    mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode="en_us"; mc.reloadResourcePacks(); return;
                }
                Files.createDirectories(output); mc.options.pauseOnLostFocus=false; mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(5); mc.options.guiScale().set(2); mc.options.framerateLimit().set(40); mc.options.tutorialStep=TutorialSteps.NONE;
                mc.createWorldOpenFlows().createFreshLevel("heraldry-review-"+System.currentTimeMillis(),
                        new LevelSettings("Heraldry review",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234567L,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen()); stage=1;
            }
            case 1 -> {
                if (mc.player==null||mc.getSingleplayerServer()==null||mc.screen!=null) return;
                stage=2; mc.getSingleplayerServer().execute(() -> {
                    try {
                        var server=mc.getSingleplayerServer(); var level=server.overworld(); var player=server.getPlayerList().getPlayers().getFirst(); var data=KingdomService.get(server).data();
                        for (int x=-12;x<=12;x++) for (int z=-8;z<=8;z++) level.setBlockAndUpdate(new BlockPos(x,160,z),Blocks.STONE_BRICKS.defaultBlockState());
                        UUID id=UUID.randomUUID(); var k=new Kingdom(id,new KingdomName("The Azure Crown"),player.getUUID(),Level.OVERWORLD,new BlockPos(8,161,8),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED); data.add(k);
                        new HeraldryService(data).save(id,player.getUUID(),design(Emblem.LION,0));
                        // Persist a real signing key and an authentic item for restart verification.
                        player.getInventory().add(new HeraldryService(data).banner(id));
                        for(int i=0;i<5;i++) {
                            var pos=new BlockPos(-4+i*2,161,0); level.setBlockAndUpdate(pos,ModBlocks.ROYAL_BANNER.defaultBlockState()); ((RoyalBannerBlockEntity)level.getBlockEntity(pos)).bind(id);
                        }
                        for(int i=0;i<3;i++) {
                            var pos=new BlockPos(-2+i*2,162,-3); level.setBlockAndUpdate(pos.south(),Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                            level.setBlockAndUpdate(pos,ModBlocks.WALL_ROYAL_BANNER.defaultBlockState().setValue(RoyalBannerBlock.FACING,Direction.NORTH)); ((RoyalBannerBlockEntity)level.getBlockEntity(pos)).bind(id);
                        }
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server); level.setDayTime(6000); level.setWeatherParameters(100000,0,false,false);
                        HeraldryServer.reconcile(server,data); realm=id;
                    } catch(Throwable error) { serverError=error; }
                });
            }
            case 2 -> {
                if(realm==null) return;
                HeraldryHardeningChecks.synchronization(mc);
                // Restore the actual integrated server's public definition after isolated packet checks.
                mc.getSingleplayerServer().execute(() -> {
                    try { HeraldryServer.login(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),KingdomService.get(mc.getSingleplayerServer()).data()); }
                    catch (Throwable error) { serverError=error; }
                });
                HeraldryHardeningChecks.textures(mc,output);
                mc.setScreen(new Gallery()); stage=3; ticks=0;
            }
            case 3 -> {
                if(++ticks<30) return; capture(mc,"vocabulary.png");
                editor=new HeraldryScreen(new TitleScreen(),realm); mc.setScreen(editor); stage=4; ticks=0;
            }
            case 4 -> {
                if(++ticks<40) return;
                require(HeraldryClient.arms(realm).charges().getFirst().emblem()==Emblem.LION,"Real login/update packets delivered arms");
                capture(mc,"editor-en.png"); mc.getLanguageManager().setSelected("uk_ua"); mc.options.languageCode="uk_ua"; reload=mc.reloadResourcePacks(); stage=5; ticks=0;
            }
            case 5 -> {
                if(!reload.isDone()||mc.getOverlay()!=null) return; reload.join();
                if(++ticks<20) return; capture(mc,"editor-uk.png");
                // Resource reload must reconstruct deterministic masks and invalidate old GPU resources.
                try(var first=HeraldryTextures.INSTANCE.raster(design(Emblem.LION,0),false);var second=HeraldryTextures.INSTANCE.raster(design(Emblem.LION,0),false)) {
                    for(int x=0;x<128;x++) for(int y=0;y<160;y++) require(first.getPixelRGBA(x,y)==second.getPixelRGBA(x,y),"Deterministic raster");
                }
                mc.setScreen(null); mc.options.hideGui=true; mc.options.fov().set(45); camera(mc,5,163,-8,32,19); stage=6; ticks=0;
            }
            case 6 -> {
                if(++ticks<90) return; capture(mc,"banners-front.png"); camera(mc,-5,163,8,-148,19); stage=7; ticks=0;
            }
            case 7 -> {
                if(++ticks<60) return; capture(mc,"banners-rear.png");
                mc.getSingleplayerServer().execute(() -> {
                    try { var server=mc.getSingleplayerServer(); var data=KingdomService.get(server).data(); new HeraldryService(data).save(realm,data.byId(realm).rulerId(),design(Emblem.EAGLE,1)); HeraldryServer.reconcile(server,data); }
                    catch(Throwable error) { serverError=error; }
                }); stage=8; ticks=0;
            }
            case 8 -> {
                if(++ticks<40) return;
                require(HeraldryClient.arms(realm).charges().getFirst().emblem()==Emblem.EAGLE,"Dynamic edit updated real client cache"); capture(mc,"banners-updated.png");
                Files.writeString(output.resolve("passed.txt"),"PASS: real client/server insignia packets, deterministic compositing, resource reload, editor and two-sided banner captures. Integrated local client, not external multiplayer.\n");
                RegnumNations.LOGGER.info("HERALDRY VISUAL CHECKS PASSED"); stage=9; mc.stop();
            }
        }
    }
    private static void camera(Minecraft mc,double x,double y,double z,float yaw,float pitch) { mc.player.setPos(x,y,z); mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO=yaw; mc.player.xRotO=pitch; }
    private void capture(Minecraft mc,String name) { Screenshot.grab(output.toFile(),name,mc.getMainRenderTarget(),message -> RegnumNations.LOGGER.info("Capture {}",name)); }
    private static void require(boolean condition,String reason) { if(!condition) throw new AssertionError(reason); }
    private static final class Gallery extends Screen {
        Gallery() { super(Component.translatable("heraldry.regnum-nations.title")); }
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff20252a); int w=Math.min(64,(width-40)/6),h=w*5/4;
            for(int i=0;i<Emblem.values().length;i++) {
                int x=20+(i%6)*(width-40)/6,y=28+(i/6)*(h+30); var e=Emblem.values()[i]; HeraldryTextures.draw(g,design(e,i),x,y,w,h);
                g.drawString(font,Component.translatable("heraldry.regnum-nations.charge."+e.id()),x,y+h+5,0xffe4b642,false);
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
