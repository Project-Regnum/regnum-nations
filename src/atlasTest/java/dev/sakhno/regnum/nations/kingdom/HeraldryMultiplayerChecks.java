package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.client.heraldry.*;
import dev.sakhno.regnum.nations.client.ui.HeraldryScreen;
import dev.sakhno.regnum.nations.networking.HeraldryPayloads.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

/** Two independent offline loopback clients; file barriers coordinate observations, never gameplay state. */
public final class HeraldryMultiplayerChecks implements ClientModInitializer {
    private static final UUID REALM=new UUID(10,1);
    private final long started=System.nanoTime();
    private final int actor=Integer.getInteger("regnum.multiplayerActor",0);
    private final Path output=Path.of("../multiplayerAudit").toAbsolutePath().normalize();
    private HeraldryScreen screen;
    private int stage;
    private boolean reconnecting;
    private long next;
    @Override public void onInitializeClient() {
        if (actor==0) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); }
            catch (Throwable error) {
                RegnumNations.LOGGER.error("MULTIPLAYER AUDIT FAILED actor {} stage {}",actor,stage,error);
                try { Files.writeString(output.resolve("failure-"+actor+".txt"),error.toString()); } catch (Exception ignored) { }
                System.exit(1);
            }
        });
    }
    private void tick(Minecraft mc) throws Exception {
        if (System.nanoTime()-started>240_000_000_000L) throw new AssertionError("Client timed out at stage "+stage);
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (mc.getOverlay()!=null||System.currentTimeMillis()<next) return;
        switch (stage) {
            case 0 -> {
                if (mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (!(mc.screen instanceof TitleScreen)) return;
                Files.createDirectories(output); mc.options.renderDistance().set(2); mc.options.framerateLimit().set(20); mc.options.pauseOnLostFocus=false;
                connect(mc); stage=1;
            }
            case 1 -> {
                if (mc.player==null||mc.level==null||arms().size()!=129) return;
                for (int i=0;i<129;i++) { UUID id=new UUID(10,i+1); require(HeraldryClient.arms(id).equals(Heraldry.defaults(id)),"Every batch in a 129-kingdom real login reaches actor "+actor); }
                screen=new HeraldryScreen(new TitleScreen(),REALM); mc.setScreen(screen); stage=2;
            }
            case 2 -> {
                State state=state(); if (state==null||!state.editable()) return;
                mark("ready"); if (!exists("ready",1)||!exists("ready",2)) return;
                if (actor==1) {
                    set(screen,"draft",HeraldryHardeningChecks.design(500)); send(Action.SAVE);
                }
                stage=3;
            }
            case 3 -> {
                if (!HeraldryClient.arms(REALM).equals(HeraldryHardeningChecks.design(500))) return;
                if (actor==1) {
                    State state=state(); if (state==null||!state.success()) return;
                    mark("updated"); stage=4;
                } else {
                    require(state()!=null&&!state().arms().equals(HeraldryClient.arms(REALM)),"Second editor still holds its older baseline");
                    set(screen,"draft",HeraldryHardeningChecks.design(501)); send(Action.SAVE); stage=4;
                }
            }
            case 4 -> {
                if (actor==1) { if (!exists("stale",2)) return; }
                else {
                    State state=state(); // Rejection clears state and preserves feedback before requesting a fresh VIEW.
                    Object feedback=get(screen,"feedback");
                    if (!((net.minecraft.network.chat.Component)feedback).getString().equals(HeraldryScreen.message("stale").getString())) return;
                    require(HeraldryClient.arms(REALM).equals(HeraldryHardeningChecks.design(500)),"Rejected stale edit did not overwrite server state");
                    mark("stale");
                }
                mc.setScreen(null); mark("converged"); stage=5;
            }
            case 5 -> {
                if (!exists("converged",1)||!exists("converged",2)) return;
                if (actor==1) { stage=8; return; }
                // Exercise a genuine dimension transition triggered by the disposable server console.
                mark("dimension-ready"); stage=6;
            }
            case 6 -> {
                if (mc.level==null||!mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)) return;
                require(arms().size()==129&&HeraldryClient.arms(REALM).equals(HeraldryHardeningChecks.design(500)),"Dimension change retains current public insignia");
                mark("dimension");
                HeraldryTextures.INSTANCE.texture(HeraldryClient.arms(REALM),true);
                mc.level.disconnect(); // Match the native pause-menu disconnect path.
                mc.disconnect(new TitleScreen());
                // Fabric dispatches disconnect cleanup on the render executor; let this tick unwind.
                stage=10; next=System.currentTimeMillis()+1000;
            }
            case 10 -> {
                require(arms().isEmpty(),"Real disconnect clears all prior server insignia");
                var field=HeraldryTextures.class.getDeclaredField("cache"); field.setAccessible(true);
                require(((Map<?,?>)field.get(HeraldryTextures.INSTANCE)).isEmpty(),"Real disconnect releases heraldry textures");
                mark("disconnected"); next=System.currentTimeMillis()+1000; stage=7;
            }
            case 7 -> {
                if (mc.player==null) { if (!reconnecting) { reconnecting=true; connect(mc); } return; }
                if (arms().size()!=129) return;
                require(HeraldryClient.arms(REALM).equals(HeraldryHardeningChecks.design(500)),"Fresh login restores the current authoritative design");
                mark("reconnected"); stage=8;
            }
            case 8 -> {
                if (!exists("reconnected",2)) return;
                require(HeraldryClient.arms(REALM).equals(HeraldryHardeningChecks.design(500)),"Both independent clients converge after reconnect");
                mark("passed"); RegnumNations.LOGGER.info("MULTIPLAYER AUDIT PASSED actor {}",actor); stage=9; mc.stop();
            }
        }
    }
    private void connect(Minecraft mc) {
        String address="127.0.0.1:25590";
        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Disposable Nations audit",address,ServerData.Type.OTHER),false,null);
    }
    private State state() throws Exception { return (State)get(screen,"state"); }
    @SuppressWarnings("unchecked") private static Map<UUID,Heraldry> arms() throws Exception {
        var field=HeraldryClient.class.getDeclaredField("ARMS"); field.setAccessible(true); return (Map<UUID,Heraldry>)field.get(null);
    }
    private void send(Action action) throws Exception { var method=HeraldryScreen.class.getDeclaredMethod("send",Action.class); method.setAccessible(true); method.invoke(screen,action); }
    private void mark(String name) throws Exception { Files.writeString(output.resolve(name+"-"+actor+".txt"),"PASS\n"); }
    private boolean exists(String name,int who) { return Files.exists(output.resolve(name+"-"+who+".txt")); }
    private static Object get(Object target,String name) throws Exception { var field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Object target,String name,Object value) throws Exception { var field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value); }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
