package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.*;
import dev.sakhno.regnum.nations.networking.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Opt-in native widget geometry checks; synthetic snapshots send no network requests. */
public final class IntegrationLayoutChecks implements ClientModInitializer {
    private static final UUID REALM=new UUID(0,1),TARGET=new UUID(0,2),TOKEN=new UUID(0,3);
    private final long started=System.nanoTime();
    private CompletableFuture<Void> reload;
    private int stage,layouts;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("regnum.integrationLayout")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (System.nanoTime()-started>240_000_000_000L) throw new AssertionError("Layout checks timed out");
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if (mc.getOverlay()!=null) return;
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (!(mc.screen instanceof TitleScreen)) return;
                if (stage==0) {
                    checkLocale(mc); mc.getLanguageManager().setSelected("uk_ua"); mc.options.languageCode="uk_ua";
                    reload=mc.reloadResourcePacks(); stage=1;
                } else if (stage==1&&reload.isDone()) {
                    reload.join(); checkLocale(mc);
                    Files.createDirectories(Path.of("checks"));
                    Files.writeString(Path.of("checks/integration-layout-passed.txt"),"PASS "+layouts+" native widget layouts, English/Ukrainian, 320x240/640x360/1100x760; snapshot invalidation and sequence checks\n");
                    RegnumNations.LOGGER.info("INTEGRATION LAYOUT CHECKS PASSED: {} layouts",layouts);stage=2;mc.stop();
                }
            } catch (Throwable error) {
                RegnumNations.LOGGER.error("INTEGRATION LAYOUT CHECKS FAILED",error);
                try { Files.createDirectories(Path.of("checks")); Files.writeString(Path.of("checks/integration-layout-failure.txt"),error.toString()); } catch (Exception ignored) { }
                System.exit(1);
            }
        });
    }
    private void checkLocale(Minecraft mc) throws Exception {
        for (int[] size:List.of(new int[]{320,240},new int[]{640,360},new int[]{1100,760})) {
            for (boolean manager:List.of(false,true)) {
                var parent=parent(manager); check(mc,parent,size,false);
                var city=new KingdomManagementScreen(snapshot(manager,KingdomManagementPayloads.OPEN_CITY_MENU));check(mc,city,size,false);
                for (var tab:KingdomAdministrationPayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new KingdomAdministrationScreen(parent);
                    set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new KingdomAdministrationPayloads.Entry(TARGET,"Representative","City / invitation",tab==KingdomAdministrationPayloads.Tab.GOVERNMENT?KingdomRole.MEMBER:null,false,List.of(KingdomAdministrationPayloads.Action.values()));
                    var state=new KingdomAdministrationPayloads.State(42,TOKEN,REALM,"Integration Kingdom",manager?KingdomRole.RULER:KingdomRole.MEMBER,2,3,tab,0,1,List.of(entry),null,"",false);
                    set(screen,"state",state);set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",KingdomAdministrationPayloads.Action.TRANSFER);set(screen,"confirmationText",Component.translatable("kingdom_admin.regnum-nations.confirm.transfer","Representative",TARGET.toString())); }
                    check(mc,screen,size,true);
                    set(screen,"sent",1L);set(screen,"sequence",42);screen.invalidate();
                    require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(w -> w.active&&w.getMessage().getString().equals(Component.translatable("kingdom_admin.regnum-nations.confirm").getString())),"Invalidation must clear confirmation controls");
                    screen.accept(new KingdomAdministrationPayloads.State(41,TOKEN,REALM,"Stale",KingdomRole.RULER,2,3,tab,0,1,List.of(entry),null,"",false));
                    require(get(screen,"state")==null,"Wrong-sequence response cannot restore invalidated privileges");screen.accept(state);
                    require(get(screen,"state")==state,"Matching server snapshot restores the view");
                }
                for (var tab:DiplomacyPayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new DiplomacyScreen(parent);set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new DiplomacyPayloads.Entry(TARGET,"Other Kingdom",DiplomacyData.Relation.NEUTRAL,TOKEN,TOKEN,true,10000000000000L,false,false);
                    set(screen,"state",new DiplomacyPayloads.State(42,REALM,"Integration Kingdom",manager,tab,0,1,List.of(entry),"",false));set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",DiplomacyPayloads.Action.HOSTILE);set(screen,"confirming",entry); }
                    check(mc,screen,size,true);
                }
                for (var tab:CitizenManagementPayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new CitizenManagementScreen(parent);set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new CitizenManagementPayloads.Entry(TARGET,TARGET,"Citizen",CityRole.CITIZEN,false,REALM,"Integration City",60);
                    set(screen,"state",new CitizenManagementPayloads.State(42,TOKEN,REALM,"Integration City",2,manager,manager,!manager,tab,0,1,List.of(entry),"",false));set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",CitizenManagementPayloads.Action.KICK);set(screen,"confirmationText",Component.translatable("citizen_ui.regnum-nations.confirm.kick","Citizen",TARGET.toString())); }
                    check(mc,screen,size,false);
                }
            }
        }
    }
    private KingdomManagementScreen parent(boolean manager) { return new KingdomManagementScreen(snapshot(manager,KingdomManagementPayloads.OPEN_MENU)); }
    private KingdomManagementPayloads.State snapshot(boolean manager,int intent) {
        var dimension=ResourceLocation.withDefaultNamespace("overworld");
        var overview=new KingdomManagementPayloads.Overview(REALM,"Integration Kingdom","Current Ruler",dimension,BlockPos.ZERO,"unresolved",4,manager,false,1);
        var city=new KingdomManagementPayloads.CityOverview(TARGET,"Integration City","City Owner",dimension,BlockPos.ZERO,"unresolved",2,manager,REALM,"Integration Kingdom",1,false);
        return new KingdomManagementPayloads.State(0,intent,null,REALM,overview,null,"",false,TARGET,city);
    }
    private void check(Minecraft mc,Screen screen,int[] size,boolean noTooltips) {
        screen.init(mc,size[0],size[1]);
        var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w -> w.visible).toList();
        for (int i=0;i<widgets.size();i++) {
            var a=widgets.get(i);String context=screen.getClass().getSimpleName()+" "+size[0]+"x"+size[1]+" "+a.getMessage().getString();
            require(a.getX()>=0&&a.getY()>=0&&a.getRight()<=size[0]&&a.getBottom()<=size[1],"Out-of-bounds widget: "+context);
            if (noTooltips) require(a.getTooltip()==null,"Unexpected tooltip: "+context);
            for (int j=i+1;j<widgets.size();j++) {
                var b=widgets.get(j);
                require(a.getX()>=b.getRight()||b.getX()>=a.getRight()||a.getY()>=b.getBottom()||b.getY()>=a.getBottom(),"Overlapping widgets: "+context+" / "+b.getMessage().getString());
            }
        }
        layouts++;
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static Object get(Object target,String name) throws Exception { var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target); }
    private static void set(Object target,String name,Object value) throws Exception { var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value); }
}
