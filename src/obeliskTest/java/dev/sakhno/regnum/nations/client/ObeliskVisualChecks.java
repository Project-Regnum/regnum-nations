package dev.sakhno.regnum.nations.client;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Real model baking, resource reload and framebuffer captures, isolated from player worlds. */
public final class ObeliskVisualChecks implements ClientModInitializer {
    private final Path output = Path.of("checks").toAbsolutePath();
    private final long started = System.nanoTime();
    private int stage, ticks;
    private volatile boolean prepared;
    private volatile int floor;
    private java.util.concurrent.CompletableFuture<Void> reload;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("regnum.obeliskTest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); }
            catch (Throwable error) {
                RegnumNations.LOGGER.error("OBELISK VISUAL CHECKS FAILED at stage {}",stage,error);
                try { Files.createDirectories(output); Files.writeString(output.resolve("failure.txt"),error.toString()); }
                catch (Exception ignored) { }
                System.exit(1);
            }
        });
    }

    private void tick(Minecraft mc) throws Exception {
        if ((System.nanoTime()-started)/1_000_000_000L>360) throw new AssertionError("Visual checks timed out at stage "+stage);
        switch (stage) {
            case 0 -> {
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if (mc.getOverlay()!=null) return;
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (!(mc.screen instanceof TitleScreen)) return;
                Files.createDirectories(output);
                Files.deleteIfExists(output.resolve("passed.txt")); Files.deleteIfExists(output.resolve("failure.txt"));
                mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2); mc.options.framerateLimit().set(60);
                mc.options.bobView().set(false); mc.options.fov().set(45);
                mc.options.tutorialStep=TutorialSteps.NONE; mc.options.hideGui=true;
                verifyModels(mc);
                mc.createWorldOpenFlows().createFreshLevel("obelisk-review-"+System.currentTimeMillis(),
                        new LevelSettings("Obelisk review",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234567L,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
                stage=1;
            }
            case 1 -> {
                if (mc.player==null || mc.getSingleplayerServer()==null || mc.screen!=null) return;
                stage=2;
                mc.getSingleplayerServer().execute(() -> {
                    var level=mc.getSingleplayerServer().overworld();
                    floor=160;
                    for (int x=-24;x<=24;x++) for (int z=-24;z<=24;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,floor,z),
                                (x%4==0 || z%4==0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE).defaultBlockState());
                    }
                    level.setBlockAndUpdate(new BlockPos(-2,floor+1,0),ModBlocks.CITY_OBELISK.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(2,floor+1,0),ModBlocks.KINGDOM_OBELISK.defaultBlockState());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
                    level.setDayTime(6000);
                    level.setWeatherParameters(100000,0,false,false);
                    prepared=true;
                });
            }
            case 2 -> {
                if (!prepared) return;
                camera(mc,5,floor+3.5,8,148,18);
                stage=3; ticks=0;
            }
            case 3 -> {
                if (++ticks<120) return;
                capture(mc,"day-front.png"); camera(mc,-5,floor+3.5,-8,-32,18);
                stage=4; ticks=0;
            }
            case 4 -> {
                if (++ticks<70) return;
                capture(mc,"day-rear.png");
                camera(mc,4,floor+2.5,13,163,1);
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(18000));
                stage=5; ticks=0;
            }
            case 5 -> {
                if (++ticks<90) return;
                capture(mc,"night.png");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(6000));
                mc.options.fov().set(70);
                camera(mc,7,floor+4,48,172,2);
                stage=6; ticks=0;
            }
            case 6 -> {
                if (++ticks<90) return;
                capture(mc,"distance-48.png");
                mc.setScreen(new InventoryReview());
                stage=7; ticks=0;
            }
            case 7 -> {
                if (++ticks<25) return;
                capture(mc,"inventory.png"); mc.setScreen(null);
                reload=mc.reloadResourcePacks();
                stage=8;
            }
            case 8 -> {
                if (!reload.isDone() || mc.getOverlay()!=null) return;
                reload.join(); verifyModels(mc);
                mc.options.fov().set(45);
                camera(mc,5,floor+3.5,8,148,18);
                stage=9; ticks=0;
            }
            case 9 -> {
                if (++ticks<90) return;
                capture(mc,"after-reload.png");
                mc.options.fov().set(70);
                camera(mc,4,floor+1,8,153,0);
                stage=10; ticks=0;
            }
            case 10 -> {
                if (++ticks<90) return;
                capture(mc,"normal-player.png");
                camera(mc,4,floor+1,16,166,0);
                stage=11; ticks=0;
            }
            case 11 -> {
                if (++ticks<90) return;
                capture(mc,"distance-16.png");
                Files.writeString(output.resolve("passed.txt"),"PASS: real 1.21.1 client; block/item/extra model baking; missing sprite checks; day/rear/night/normal-player/16-block/48-block/inventory captures; resource reload and rebaking.\n");
                RegnumNations.LOGGER.info("OBELISK VISUAL CHECKS PASSED: {}",output);
                stage=12; mc.stop();
            }
        }
    }

    private static void camera(Minecraft mc,double x,double y,double z,float yaw,float pitch) {
        mc.getSingleplayerServer().execute(() -> {
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.teleportTo(mc.getSingleplayerServer().overworld(),x,y,z,yaw,pitch);
            player.getAbilities().flying=true; player.getAbilities().mayfly=true; player.onUpdateAbilities();
        });
    }

    private static void verifyModels(Minecraft mc) {
        var manager=mc.getModelManager();
        var models=List.of(mc.getBlockRenderer().getBlockModel(ModBlocks.CITY_OBELISK.defaultBlockState()),
                mc.getBlockRenderer().getBlockModel(ModBlocks.KINGDOM_OBELISK.defaultBlockState()),
                manager.getModel(ObeliskRenderer.CROWN), manager.getModel(ObeliskRenderer.CITY_GLOW),
                manager.getModel(ObeliskRenderer.KINGDOM_GLOW),
                manager.getModel(ModelResourceLocation.inventory(RegnumNations.legacyId("city_obelisk"))),
                manager.getModel(ModelResourceLocation.inventory(RegnumNations.legacyId("kingdom_obelisk"))));
        for (var model:models) {
            if (model==manager.getMissingModel()) throw new AssertionError("Missing obelisk model");
            var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(0));
            if (quads.isEmpty()) throw new AssertionError("Empty obelisk model");
            for (var quad:quads) if (quad.getSprite().contents().name().getPath().equals("missingno"))
                throw new AssertionError("Missing obelisk sprite");
        }
    }

    private void capture(Minecraft mc,String name) throws Exception {
        try (var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { pixels.writeToFile(output.resolve(name)); }
    }

    private static final class InventoryReview extends Screen {
        InventoryReview() { super(Component.literal("Obelisk item review")); }
        @Override public void renderBackground(GuiGraphics g,int x,int y,float delta) { }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
            g.fill(0,0,width,height,0xff202b35);
            g.drawCenteredString(font,"CITY / KINGDOM  |  actual item models",width/2,24,0xffd7c69a);
            g.pose().pushPose(); g.pose().translate(width/2-160,height/2-80,0); g.pose().scale(8,8,8);
            g.renderItem(new ItemStack(ModBlocks.CITY_OBELISK_ITEM),0,0);
            g.renderItem(new ItemStack(ModBlocks.KINGDOM_OBELISK_ITEM),24,0);
            g.pose().popPose();
            g.drawCenteredString(font,"Native 16px inventory icons",width/2,height-80,0xffb0bac2);
            g.renderItem(new ItemStack(ModBlocks.CITY_OBELISK_ITEM),width/2-24,height-60);
            g.renderItem(new ItemStack(ModBlocks.KINGDOM_OBELISK_ITEM),width/2+8,height-60);
        }
    }
}
