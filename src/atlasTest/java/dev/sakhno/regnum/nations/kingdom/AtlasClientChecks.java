package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.client.terrain.diorama.MapSession;
import dev.sakhno.regnum.nations.client.terrain.diorama.Projection;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.BlockLooks;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.TileBaker;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.TileImage;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.TileKey;
import dev.sakhno.regnum.nations.client.terrain.diorama.data.TileStore;
import dev.sakhno.regnum.nations.client.ui.PoliticalMapScreen;
import dev.sakhno.regnum.nations.networking.AtlasPayloads;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/** Opt-in integration checks against a real hidden client and disposable integrated world. */
public final class AtlasClientChecks implements ClientModInitializer {
    private final long start=System.nanoTime();
    private final Path output=Path.of("checks").toAbsolutePath();
    private int stage, ticks;
    private volatile boolean prepared;
    private volatile int roof;
    private CompletableFuture<TileImage> baked;
    private PoliticalMapScreen screen;
    private TileImage red;
    private int scaleStep;
    private String pendingCapture;
    private int hoverX,hoverY,hoverTicks;
    private dev.sakhno.regnum.nations.client.terrain.diorama.render.MapLevel previousMap;
    private final String worldName = "atlas-check-" + System.currentTimeMillis();
    private CompletableFuture<?> dungeonAction;
    private volatile UUID bastionId;
    private int dungeonSequence;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("regnum.atlasTest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); }
            catch (Throwable error) {
                RegnumNations.LOGGER.error("ATLAS CLIENT CHECKS FAILED at stage {}",stage,error);
                try { Files.createDirectories(output); Files.writeString(output.resolve("failure.txt"),error.toString()); } catch (Exception ignored) { }
                System.exit(1);
            }
        });
    }

    private void tick(Minecraft mc) throws Exception {
        if ((System.nanoTime()-start)/1_000_000_000L>300) throw new AssertionError("Client checks timed out, stage "+stage);
        if (pendingCapture!=null) {
            pointCursor(mc,hoverX,hoverY);
            if (++hoverTicks>=4) {
                screenshot(mc,pendingCapture); pendingCapture=null; pointCursor(mc,2,2);
            }
            return;
        }
        switch (stage) {
            case 0 -> {
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if (mc.getOverlay()!=null) return;
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (!(mc.screen instanceof TitleScreen)) return;
                Files.createDirectories(output);
                Files.deleteIfExists(output.resolve("passed.txt")); Files.deleteIfExists(output.resolve("failure.txt"));
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),2,2);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1100,760);
                mc.options.pauseOnLostFocus=false;
                mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2); mc.options.framerateLimit().set(60);
                mc.options.tutorialStep=TutorialSteps.NONE;
                mc.createWorldOpenFlows().createFreshLevel(worldName,
                        new LevelSettings("Atlas checks",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234567L,false,false),WorldPresets::createNormalWorldDimensions,new TitleScreen());
                stage=1;
            }
            case 1 -> {
                if (mc.player==null || mc.getSingleplayerServer()==null || mc.screen!=null) return;
                stage=2;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var server=mc.getSingleplayerServer(); var level=server.overworld();
                        roof=Math.max(120,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,-8,-8)+8);
                        for (int x=-16;x<0;x++) for (int z=-16;z<0;z++) level.setBlockAndUpdate(new BlockPos(x,roof,z),Blocks.RED_WOOL.defaultBlockState());
                        for (int x=0;x<16;x++) for (int z=-16;z<0;z++) level.setBlockAndUpdate(new BlockPos(x,roof,z),Blocks.BLUE_WOOL.defaultBlockState());
                        var data=KingdomService.get(server).data();
                        UUID owner=server.getPlayerList().getPlayers().getFirst().getUUID();
                        var kingdom=new Kingdom(UUID.randomUUID(),new KingdomName("Atlas Red"),owner,Level.OVERWORLD,
                                new BlockPos(-8,roof+1,-8),UUID.randomUUID(),0,Kingdom.CapitalStatus.PRESENT);
                        data.add(kingdom);
                        data.putClaim(new ClaimPos(Level.OVERWORLD,-2,-1),kingdom.id());
                        data.putClaim(new ClaimPos(Level.OVERWORLD,-2,-2),kingdom.id());
                        var city=new Settlement(UUID.randomUUID(),new KingdomName("Willow Harbor"),UUID.randomUUID(),UUID.randomUUID(),Level.OVERWORLD,
                                new BlockPos(24,roof+1,-24),UUID.randomUUID(),0,Kingdom.CapitalStatus.PRESENT,null,1,false);
                        data.addSettlement(city);
                        server.getPlayerList().getPlayers().getFirst().teleportTo(-8,roof+8,-8);
                        prepared=true;
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            case 2 -> {
                if (!prepared || MapSession.get()==null || BlockLooks.get()==null || mc.player.getY()<roof) return;
                var chunk=MapSession.get().map().chunk(-1,-1);
                if (chunk==null || chunk.maxY<roof) return;
                screen=new PoliticalMapScreen(); mc.setScreen(screen);
                pointCursor(mc,2,2);
                ticks=0; stage=3;
            }
            case 3 -> {
                if (++ticks<80 || !"ready".equals(field("state"))) return;
                check(((java.util.List<?>)field("cityMarkers")).size()==2,"Authoritative city and capital markers received");
                checkLayout(mc);
                checkMarkerPositions(mc);
                check(Projection.cos()==0 && Projection.sin()==1,"Camera is exactly vertical");
                check(Projection.v(-64,-17)==-17 && Projection.v(319,-17)==-17,"Height cannot move terrain relative to chunks");
                bake(); stage=4;
            }
            case 4 -> {
                if (!baked.isDone()) return;
                red=baked.join();
                int pixel=roofPixel(red);
                check((pixel&255)>((pixel>>16)&255)*1.5,"Red world structure must bake red, ABGR="+Integer.toHexString(pixel));
                long distinct=Arrays.stream(red.mips()[0]).filter(p -> (p>>>24)!=0).distinct().limit(20).count();
                check(distinct>5,"Real terrain has varied world-derived colors");
                check(!MapSession.get().map().chunk(-1,-1).equals(MapSession.get().map().chunk(0,-1)),"Distinct loaded chunks");
                screenshot(mc,"combined.png");
                var pin=recordField(((java.util.List<?>)field("mapMarkers")).getFirst(),"point");
                hoverScreenshot(mc,"capital-tooltip.png",(int)recordField(pin,"x"),(int)recordField(pin,"y"));
                stage=19;
            }
            case 19 -> {
                // Exercise the actual scroll/pan handlers at negative coordinates.
                double x=100,y=100;
                double oldZoom=(double)field("zoom");
                screen.mouseScrolled(x,y,0,-3);
                check((double)field("zoom")<oldZoom,"Wheel input must actually change zoom");
                double oldCenter=(double)field("centerX");
                screen.mouseClicked(x,y,0); screen.mouseDragged(x+37,y+19,0,37,19); screen.mouseReleased(x+37,y+19,0);
                check((double)field("centerX")!=oldCenter,"Dragging must actually pan");
                ticks=0; stage=5;
            }
            case 5 -> {
                if (++ticks<30) return;
                screenshot(mc,"pan-zoom.png");
                checkMarkerPositions(mc);
                screen.mouseClicked(100,100,0); screen.mouseReleased(100,100,0);
                check((boolean)field("selected"),"Chunk selection still works after pan/zoom");
                double ox=(8+(int)field("right"))/2.0-(double)field("centerX")*(double)field("zoom");
                int expected=(int)Math.floor((100-ox)/(double)field("zoom"));
                check((int)field("selectedX")==expected,"Inspection must select the chunk under the cursor after pan/zoom");
                set("layer",2); ticks=0;stage=6;
            }
            case 6 -> {
                if (++ticks<20) return;
                screenshot(mc,"terrain.png");
                mc.getSingleplayerServer().execute(() -> {
                    var level=mc.getSingleplayerServer().overworld();
                    for (int x=-16;x<0;x++) for (int z=-16;z<0;z++) level.setBlockAndUpdate(new BlockPos(x,roof,z),Blocks.BLUE_WOOL.defaultBlockState());
                });
                ticks=0; stage=7;
            }
            case 7 -> {
                if (++ticks<80) return;
                bake();stage=8;
            }
            case 8 -> {
                if (!baked.isDone()) return;
                var blue=baked.join(); int pixel=roofPixel(blue);
                check(((pixel>>16)&255)>(pixel&255)*1.3,"Block edits invalidate snapshots and bake blue, ABGR="+Integer.toHexString(pixel));
                // Run actual tile IO off the client thread, with a fresh independent store.
                baked=CompletableFuture.supplyAsync(() -> {
                    var folder=output.resolve("tile-reload");
                    var writer=new TileStore(folder); writer.baked(blue); writer.close();
                    var reader=new TileStore(folder); int[] loaded=reader.read(blue.key()); reader.close();
                    check(Arrays.equals(blue.mips()[0],loaded),"Saved terrain survives close/reload");
                    return blue;
                });
                stage=9;
            }
            case 9 -> {
                if (!baked.isDone()) return;
                baked.join();
                set("layer",0); ticks=0;stage=10;
            }
            case 10 -> {
                if (++ticks<20) return;
                screenshot(mc,"updated.png");
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),640,480);
                ticks=0; stage=11;
            }
            case 11 -> {
                if (++ticks<30) return;
                screenshot(mc,"small.png");
                check(screen.width<=400 && screen.height<=300,"Small-window layout must be exercised");
                checkLayout(mc); checkMarkerPositions(mc);
                hoverScreenshot(mc,"inspector-tooltip.png",25,(int)field("inspectorY")+4);
                stage=20;
            }
            case 20 -> {
                // Check the same physical window at three logical GUI scales.
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,960);
                mc.options.guiScale().set(1); mc.resizeDisplay();
                ticks=0; scaleStep=1; stage=15;
            }
            case 15 -> {
                if (++ticks<25) return;
                checkLayout(mc); checkMarkerPositions(mc);
                screenshot(mc,"scale-"+scaleStep+".png");
                if (++scaleStep<=3) {
                    mc.options.guiScale().set(scaleStep); mc.resizeDisplay(); ticks=0; return;
                }
                // Show fog well beyond explored chunks, without requesting terrain from the server.
                set("centerX",40.0); set("centerZ",40.0); set("zoom",4.0);
                ticks=0; stage=16;
            }
            case 16 -> {
                if (++ticks<30) return;
                checkMarkerPositions(mc);
                screenshot(mc,"fog.png");
                // Dense markers at low zoom must share hover targets instead of shifting coordinates.
                check(((java.util.List<?>)field("mapMarkers")).isEmpty(),"Offscreen pins must be culled");
                set("centerX",0.0); set("centerZ",-1.0); set("zoom",4.0);
                ticks=0; stage=17;
            }
            case 17 -> {
                if (++ticks<20) return;
                check(((java.util.List<?>)field("mapMarkers")).size()==1,"Nearby city/capital pins cluster at minimum zoom");
                checkMarkerPositions(mc);
                screenshot(mc,"cluster.png");
                var pin=recordField(((java.util.List<?>)field("mapMarkers")).getFirst(),"point");
                hoverScreenshot(mc,"cluster-tooltip.png",(int)recordField(pin,"x"),(int)recordField(pin,"y"));
                stage=21;
            }
            case 21 -> {
                set("manageMode",true); screen.init(mc,screen.width,screen.height);
                ticks=0; stage=18;
            }
            case 18 -> {
                if (++ticks<20) return;
                checkLayout(mc); screenshot(mc,"manage.png");
                previousMap=MapSession.get().map();
                mc.getSingleplayerServer().execute(() -> {
                    var server=mc.getSingleplayerServer();
                    server.getPlayerList().getPlayers().getFirst().teleportTo(server.getLevel(Level.NETHER),-8,70,-8,0,0);
                });
                ticks=0; stage=12;
            }
            case 12 -> {
                if (mc.level==null || mc.level.dimension()!=Level.NETHER || MapSession.get()==null) return;
                check(MapSession.get().map()!=previousMap,"Dimension switch must isolate terrain state");
                if (++ticks<80) return;
                screen = new PoliticalMapScreen(); mc.setScreen(screen); ticks=0; stage=13;
            }
            case 13 -> {
                if (++ticks<100) return;
                screenshot(mc,"nether.png");
                if (!FabricLoader.getInstance().isModLoaded("regnum-world")) {
                    finish(mc, "");
                    return;
                }
                check(dungeonMarkers().isEmpty(), "Start with an empty dungeon overlay");
                dungeonAction = onServer(mc, this::registerDungeons);
                stage = 30;
            }
            case 30 -> {
                if (!dungeonAction.isDone()) return;
                dungeonAction.join();
                var markers = dungeonMarkers();
                if (markers.size() != 3) return;
                check(markers.stream().map(AtlasPayloads.DungeonMarker::type).toList()
                                .equals(java.util.List.of("mine", "catacombs", "bastion")),
                        "The open map must receive all three types after registration");
                check(markers.get(0).entrance().equals(new BlockPos(-40, 74, -32))
                                && markers.get(1).entrance().equals(new BlockPos(24, 75, -32))
                                && markers.get(2).entrance().equals(new BlockPos(-8, 76, 32)),
                        "Atlas marker positions must use the true entrance heights and signed coordinates");
                set("zoom", 16.0);
                set("layer", 1);
                pointCursor(mc, 2, 2);
                ticks = 0; stage = 31;
            }
            case 31 -> {
                if (++ticks < 5) return;
                var labels = new java.util.ArrayList<String>();
                for (var label : (java.util.List<?>)field("labels")) labels.add((String)recordField(label, "name"));
                check(labels.containsAll(java.util.List.of("Forgotten Mine", "Ancient Catacombs", "Ruined Bastion")),
                        "Adjacent markers must have room for all three readable labels");
                screenshot(mc, "dungeons-political.png");
                set("layer", 2);
                ticks = 0; stage = 32;
            }
            case 32 -> {
                if (++ticks < 5) return;
                screenshot(mc, "dungeons-terrain.png");
                set("layer", 0);
                dungeonSequence = (int)field("sequence");
                dungeonAction = onServer(mc, server -> dungeonCommand(server, "recover"));
                stage = 33;
            }
            case 33 -> {
                if (!dungeonAction.isDone()) return;
                dungeonAction.join();
                if (dungeonSequence == (int)field("sequence") || dungeonMarkers().size() != 3) return;
                check((double)field("zoom") == 16, "Recovery refresh must preserve the map view");
                dungeonAction = onServer(mc, server -> dungeonCommand(server, "remove"));
                stage = 34;
            }
            case 34 -> {
                if (!dungeonAction.isDone()) return;
                dungeonAction.join();
                if (dungeonMarkers().size() != 2) return;
                check(dungeonMarkers().stream().noneMatch(marker -> marker.type().equals("bastion")),
                        "Removing the dungeon must remove its marker on the open map");
                screenshot(mc, "dungeons-after-removal.png");
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
                stage = 35;
            }
            case 35 -> {
                if (mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
                mc.createWorldOpenFlows().openWorld(worldName, () -> {
                    throw new AssertionError("Could not reopen the Atlas test world");
                });
                stage = 36;
            }
            case 36 -> {
                if (mc.player == null || mc.screen != null || mc.level.dimension() != Level.NETHER) return;
                screen = new PoliticalMapScreen();
                mc.setScreen(screen);
                stage = 37;
            }
            case 37 -> {
                if (dungeonMarkers().isEmpty()) return;
                check(dungeonMarkers().stream().map(AtlasPayloads.DungeonMarker::type).toList()
                                .equals(java.util.List.of("mine", "catacombs")),
                        "World reload must preserve both registrations and omit the removed Bastion");
                finish(mc, " World dungeon registration, exact entrance coordinates, recovery, removal and saved-world reload: PASS");
            }
        }
    }

    private void finish(Minecraft mc, String dungeonChecks) throws Exception {
        Files.writeString(output.resolve("passed.txt"), "Actual terrain and structures; vertical projection; server capital marker; negative-coordinate pan, zoom and exact selection; block-edit refresh; tile persistence; small window; GUI scales 1/2/3; viewport gain; exact marker anchors; clustering; manage layout; fog; dimension isolation: PASS"
                + dungeonChecks + "\n");
        RegnumNations.LOGGER.info("ATLAS CLIENT CHECKS PASSED: {}", output);
        stage = 38;
        mc.stop();
    }

    @SuppressWarnings("unchecked")
    private java.util.List<AtlasPayloads.DungeonMarker> dungeonMarkers() throws Exception {
        return (java.util.List<AtlasPayloads.DungeonMarker>)field("dungeonMarkers");
    }

    private static CompletableFuture<?> onServer(Minecraft mc, ServerAction action) {
        var server = mc.getSingleplayerServer();
        return server.submit(() -> {
            try { action.run(server); }
            catch (Exception error) { throw new IllegalStateException(error); }
        });
    }

    @FunctionalInterface
    private interface ServerAction {
        void run(MinecraftServer server) throws Exception;
    }

    private void registerDungeons(MinecraftServer server) throws ReflectiveOperationException {
        var type = Class.forName("zooller.regnumworld.dungeon.DungeonData");
        var registry = type.getMethod("get", MinecraftServer.class).invoke(null, server);
        var record = Class.forName("zooller.regnumworld.dungeon.DungeonData$Dungeon");
        var create = record.getConstructor(String.class, String.class, int.class, int.class, int.class);
        var register = type.getMethod("register", record);
        int[][] origins = {{-64, -32}, {0, -32}, {-32, 32}};
        String[] types = {"mine", "catacombs", "bastion"};
        for (int i = 0; i < types.length; i++) {
            var dungeon = create.newInstance(types[i], Level.NETHER.location().toString(), origins[i][0], 70, origins[i][1]);
            record.getField("frontY").setInt(dungeon, 74 + i);
            register.invoke(registry, dungeon);
            if (i == 2) bastionId = (UUID)record.getField("id").get(dungeon);
        }
        var changed = Class.forName("zooller.regnumworld.dungeon.Dungeons").getDeclaredMethod("atlasChanged", ServerLevel.class);
        changed.setAccessible(true);
        changed.invoke(null, server.getLevel(Level.NETHER));
    }

    private void dungeonCommand(MinecraftServer server, String action) {
        var output = new java.util.ArrayList<Component>();
        var source = server.createCommandSourceStack().withPermission(4).withSource(new CommandSource() {
            @Override public void sendSystemMessage(Component message) { output.add(message); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        });
        var commands = server.getCommands();
        commands.performPrefixedCommand(source, "regnum dungeon " + action + " " + bastionId);
        var click = output.getLast().getSiblings().stream().map(c -> c.getStyle().getClickEvent())
                .filter(event -> event != null && event.getValue().startsWith("/regnum dungeon confirm ")).findFirst().orElseThrow();
        commands.performPrefixedCommand(source, click.getValue());
        check(output.getLast().getString().equals("Dungeon " + action + " complete."), "Dungeon " + action + " must succeed");
    }

    private void checkLayout(Minecraft mc) throws Exception {
        int right=(int)field("right"),bottom=(int)field("bottom"),toolbar=(int)field("toolbarY");
        check(bottom>48 && right>8,"Map has a usable viewport");
        int oldRight=screen.width-Math.clamp(screen.width/4,80,140)-14;
        double oldArea=(oldRight-8.0)*(screen.height-96-48);
        double area=(right-8.0)*(bottom-48);
        double minimumGain=screen.height<=400?1.20:screen.height<=480?1.15:1.05;
        check(area>=oldArea*minimumGain,"Map area gain at tested resolution: "+screen.width+"x"+screen.height);
        check(bottom+27<toolbar,"Two inspector text rows fit above toolbar");
        var action=(net.minecraft.client.gui.components.Button)field("territoryAction");
        if (action.visible) check(action.getY()>=bottom && action.getBottom()<toolbar,"Manage action fits in compact inspector");
    }
    private void checkMarkerPositions(Minecraft mc) throws Exception {
        double zoom=(double)field("zoom");
        double ox=(8+(int)field("right"))/2.0-(double)field("centerX")*zoom;
        double oz=(48+(int)field("bottom"))/2.0-(double)field("centerZ")*zoom;
        for (Object rendered:(java.util.List<?>)field("mapMarkers")) {
            Object point=recordField(rendered,"point");
            var marker=(dev.sakhno.regnum.nations.networking.AtlasPayloads.CityMarker)recordField(point,"marker");
            int expectedX=(int)Math.floor(ox+(marker.pos().getX()+0.5)/16*zoom);
            int expectedY=(int)Math.floor(oz+(marker.pos().getZ()+0.5)/16*zoom);
            check((int)recordField(point,"x")==expectedX && (int)recordField(point,"y")==expectedY,"Rendered pin must remain at its exact block anchor");
            var cell=(dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.Cell)recordField(point,"cell");
            check(cell.owner().equals(marker.city()),"Rendered marker must match its disclosed claim owner");
        }
    }
    private static Object recordField(Object record,String name) throws Exception {
        var accessor=record.getClass().getDeclaredMethod(name); accessor.setAccessible(true); return accessor.invoke(record);
    }

    private void bake() {
        var map=MapSession.get().map(); var looks=BlockLooks.get();
        baked=CompletableFuture.supplyAsync(() -> TileBaker.bake(new TileKey(TileKey.SURFACE,-1,-1),map,looks,null,false));
    }
    private static int roofPixel(TileImage image) {
        int coordinate=(128-8)*Projection.PIXELS_PER_UNIT+2;
        return image.mips()[0][coordinate*Projection.TILE_PIXELS+coordinate];
    }
    private Object field(String name) throws Exception { var f=PoliticalMapScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen); }
    private void set(String name,Object value) throws Exception { var f=PoliticalMapScreen.class.getDeclaredField(name); f.setAccessible(true); f.set(screen,value); }
    private void hoverScreenshot(Minecraft mc,String name,int x,int y) throws Exception {
        check(!(boolean)field("panning"),"Hover targets are available when not dragging");
        pendingCapture=name; hoverX=x; hoverY=y; hoverTicks=0;
        pointCursor(mc,x,y);
    }
    private void pointCursor(Minecraft mc,int x,int y) throws Exception {
        var horizontal=net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"); horizontal.setAccessible(true);
        var vertical=net.minecraft.client.MouseHandler.class.getDeclaredField("ypos"); vertical.setAccessible(true);
        horizontal.setDouble(mc.mouseHandler,x*(double)mc.getWindow().getScreenWidth()/screen.width);
        vertical.setDouble(mc.mouseHandler,y*(double)mc.getWindow().getScreenHeight()/screen.height);
    }
    private void screenshot(Minecraft mc,String name) throws Exception {
        try (var pixels=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { pixels.writeToFile(output.resolve(name)); }
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
