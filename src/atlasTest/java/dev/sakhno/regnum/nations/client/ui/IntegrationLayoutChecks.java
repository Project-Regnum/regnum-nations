package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.api.v1.*;
import dev.sakhno.regnum.nations.api.client.v1.*;
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
    private int stage,layouts,captureTicks;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("regnum.integrationLayout")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (System.nanoTime()-started>240_000_000_000L) throw new AssertionError("Layout checks timed out");
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                if (mc.getOverlay()!=null) return;
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (stage>=2) {
                    if (++captureTicks<10) return;
                    Files.createDirectories(Path.of("checks/phase-b"));
                    try (var pixels=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        pixels.writeToFile(Path.of("checks/phase-b/"+(stage==2?"sections":"confirmation")+".png"));
                    }
                    if (stage==2) { stage=3; captureTicks=0; mc.setScreen(new NationsConfirmationScreen(new TitleScreen(),Component.literal("Confirmation"),Component.literal("Long consequence and target. ".repeat(100)),true,() -> {})); }
                    else mc.stop();
                    return;
                }
                if (!(mc.screen instanceof TitleScreen)) return;
                if (stage==0) {
                    if (!mc.getLanguageManager().getSelected().equals("en_us")) {
                        mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode="en_us"; mc.reloadResourcePacks(); return;
                    }
                    checkLocale(mc); mc.getLanguageManager().setSelected("uk_ua"); mc.options.languageCode="uk_ua";
                    reload=mc.reloadResourcePacks(); stage=1;
                } else if (stage==1&&reload.isDone()) {
                    reload.join(); checkLocale(mc);
                    Files.createDirectories(Path.of("checks"));
                    Files.writeString(Path.of("checks/integration-layout-passed.txt"),"PASS "+layouts+" native widget layouts, English/Ukrainian, 320x240/640x360/1100x760; snapshot invalidation and sequence checks\n");
                    RegnumNations.LOGGER.info("INTEGRATION LAYOUT CHECKS PASSED: {} layouts",layouts);stage=2; mc.setScreen(new SectionSelectionScreen(new TitleScreen(),Arrays.stream(WarfarePayloads.Tab.values()).map(t -> WarfareScreen.message("tab."+t.name().toLowerCase(Locale.ROOT))).toList(),i -> {}));
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
            checkExtras(mc,size);
            for (boolean manager:List.of(false,true)) {
                var parent=parent(manager); check(mc,parent,size,false);
                var city=new KingdomManagementScreen(snapshot(manager,KingdomManagementPayloads.OPEN_CITY_MENU));check(mc,city,size,false);
                for (int page=0;page<3;page++) for (int count=0;count<=3;count++) {
                    var screen=new HeraldryScreen(parent,REALM); var base=Heraldry.defaults(REALM);
                    var arms=new Heraldry(1,base.shield(),base.field(),base.division(),base.secondary(),base.ordinary(),base.ordinaryTincture(),
                            java.util.Collections.nCopies(count,base.charges().getFirst()));
                    set(screen,"initial",false); set(screen,"draft",arms); set(screen,"page",page);
                    set(screen,"state",new HeraldryPayloads.State(42,REALM,count==3?"Long Kingdom ".repeat(4).strip():"Integration Kingdom",arms,manager,manager?TOKEN:null,"",false));
                    check(mc,screen,size,true);
                    var focusable=screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                            .map(net.minecraft.client.gui.components.Button.class::cast).filter(w -> w.active&&w.visible).toList();
                    var reached=new HashSet<Object>();
                    for (int i=0;i<focusable.size()+2;i++) { screen.keyPressed(GLFW.GLFW_KEY_TAB,0,0); reached.add(screen.getFocused()); }
                    require(reached.containsAll(focusable),"Tab navigation reaches every enabled heraldry control");
                    require(!screen.getNarrationMessage().getString().isBlank(),"Heraldry supplies screen narration text");
                    set(screen,"sent",1L);set(screen,"sequence",42);
                    screen.accept(new HeraldryPayloads.State(41,REALM,"Stale",arms,true,TOKEN,"",false));
                    require((long)get(screen,"sent")==1L,"Heraldry rejects stale reply sequences");
                }
                verifyHeraldrySync();
                for (var tab:KingdomAdministrationPayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new KingdomAdministrationScreen(parent);
                    set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new KingdomAdministrationPayloads.Entry(TARGET,"Representative","City / invitation",tab==KingdomAdministrationPayloads.Tab.GOVERNMENT?KingdomRole.MEMBER:null,false,List.of(KingdomAdministrationPayloads.Action.values()));
                    var state=new KingdomAdministrationPayloads.State(42,TOKEN,REALM,"Integration Kingdom",manager?KingdomRole.RULER:KingdomRole.MEMBER,2,3,tab,0,1,List.of(entry),null,"",false);
                    set(screen,"state",state);set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",KingdomAdministrationPayloads.Action.TRANSFER);set(screen,"confirmationText",Component.translatable("kingdom_admin.regnum-nations.confirm.transfer","Representative",TARGET.toString())); }
                    check(mc,screen,size,false);
                    set(screen,"sent",1L);set(screen,"sequence",42);screen.invalidate();
                    require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(w -> w.active&&w.getMessage().getString().equals(Component.translatable("kingdom_admin.regnum-nations.confirm").getString())),"Invalidation must clear confirmation controls");
                    screen.accept(new KingdomAdministrationPayloads.State(41,TOKEN,REALM,"Stale",KingdomRole.RULER,2,3,tab,0,1,List.of(entry),null,"",false));
                    require(get(screen,"state")==null,"Wrong-sequence response cannot restore invalidated privileges");screen.accept(state);
                    require(get(screen,"state")==state,"Matching server snapshot restores the view");
                }
                for (var tab:InterstatePayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new DiplomacyScreen(parent);set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new InterstatePayloads.Entry(TARGET,"Other Kingdom",DiplomacyData.Relation.NEUTRAL,TOKEN,TOKEN,true,10000000000000L,false,false,DiplomacyData.ProposalType.ALLIANCE,1L,1L,0L,null);
                    set(screen,"state",new InterstatePayloads.State(42,REALM,"Integration Kingdom",manager,tab,0,1,List.of(entry),"",false,manager?EnumSet.allOf(KingdomPermission.class):Set.of()));set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",InterstatePayloads.Action.HOSTILE);set(screen,"confirming",entry); }
                    check(mc,screen,size,true);
                }
                for (var tab:CitizenManagementPayloads.Tab.values()) for (boolean confirmation:List.of(false,true)) {
                    var screen=new CitizenManagementScreen(parent);set(screen,"initial",false);set(screen,"tab",tab);
                    var entry=new CitizenManagementPayloads.Entry(TARGET,TARGET,"Citizen",CityRole.CITIZEN,false,REALM,"Integration City",60);
                    set(screen,"state",new CitizenManagementPayloads.State(42,TOKEN,REALM,"Integration City",2,manager,manager,!manager,tab,0,1,List.of(entry),"",false));set(screen,"selected",TARGET);
                    if (confirmation) { set(screen,"confirmation",CitizenManagementPayloads.Action.KICK);set(screen,"confirmationText",Component.translatable("citizen_ui.regnum-nations.confirm.kick","Citizen",TARGET.toString())); }
                    check(mc,screen,size,false);
                    if (confirmation) { screen.onClose(); require(get(screen,"confirmation")==null,"Escape cancels citizen confirmation"); }
                    else {
                        var owner=new CitizenManagementPayloads.Entry(TARGET,TARGET,"Owner",CityRole.OWNER,false,REALM,"Integration City",60);
                        set(screen,"state",new CitizenManagementPayloads.State(42,TOKEN,REALM,"Integration City",2,true,true,false,CitizenManagementPayloads.Tab.MEMBERS,0,1,List.of(owner),"",false));
                        set(screen,"tab",CitizenManagementPayloads.Tab.MEMBERS); check(mc,screen,size,false);
                        require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(w -> w.getMessage().equals(Component.translatable("citizen_ui.regnum-nations.kick"))||w.getMessage().equals(Component.translatable("citizen_ui.regnum-nations.demote"))),"Owner has no kick or demotion control");
                    }
                }
            }
        }
    }
    private void checkExtras(Minecraft mc,int[] size) throws Exception {
        var parent=parent(true);
        var territory=new TerritoryScreen(parent); set(territory,"initial",false); check(mc,territory,size,false);
        var confirm=new NationsConfirmationScreen(parent,Component.literal("Confirm"),Component.literal("Long consequence. ".repeat(150)),true,() -> {});
        check(mc,confirm,size,false);
        require(((AbstractWidget)confirm.getFocused()).getMessage().equals(KingdomUi.text("cancel")),"Confirmation starts on Cancel");
        check(mc,new SectionSelectionScreen(parent,Arrays.stream(WarfarePayloads.Tab.values()).map(t -> WarfareScreen.message("tab."+t.name().toLowerCase(Locale.ROOT))).toList(),i -> {}),size,false);
        for (var tab:WarfarePayloads.Tab.values()) for (boolean empty:List.of(true,false)) for (boolean selected:List.of(true,false)) {
            var screen=new WarfareScreen(new DiplomacyScreen(parent)); set(screen,"initial",false); set(screen,"tab",tab);
            var entry=new WarfarePayloads.Entry(null,null,REALM,TARGET,REALM,TARGET,"City ".repeat(12),"Kingdom ".repeat(8),"Rival",null,null,0,0,0,0,0,0,0,0,"",0,0,"",false);
            set(screen,"state",new WarfarePayloads.Snapshot(42,REALM,tab,0,empty?0:1,empty?List.of():List.of(entry),Set.of(),"",false));
            if (!empty&&selected) set(screen,"selected",entry);
            check(mc,screen,size,false);
        }
        var entity=new EntitySnapshot(new EntityRef(EntityRef.Scope.CITY,TARGET),"Long City ".repeat(6),REALM,REALM,Optional.empty(),Optional.empty(),0xffaaaaaa,Optional.empty(),ResourceLocation.withDefaultNamespace("overworld"),BlockPos.ZERO);
        var panelsField=EconomyUi.class.getDeclaredField("PANELS"); panelsField.setAccessible(true);
        var registry=(Map<Object,Object>)panelsField.get(null);
        var oldPanels=new HashMap<>(registry); registry.clear();
        var providersField=EconomyUi.class.getDeclaredField("serverProviders"); providersField.setAccessible(true);
        var oldProviders=providersField.get(null);
        var provider=ResourceLocation.fromNamespaceAndPath("regnum-nations","layout_fixture");
        try {
            providersField.set(null,Set.of(provider));
            for (int count:new int[]{0,1,12}) {
                for (int i=registry.size();i<count;i++) EconomyUi.register(provider,new EconomyPanel(ResourceLocation.fromNamespaceAndPath("regnum-nations","fixture_"+i),EconomyPanel.Category.TREASURY,Component.literal("Treasury "+i+" long provider name"),Set.of(EntityRef.Scope.CITY),Set.of(),(c,p) -> p));
                for (boolean details:List.of(false,true)) {
                    var screen=new EconomyScreen(parent,entity.ref());
                    set(screen,"context",new EconomyContext(entity,"owner",Set.of())); set(screen,"received",System.currentTimeMillis()); set(screen,"details",details);
                    check(mc,screen,size,false);
                    if (!details) require(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).noneMatch(w -> w.getMessage().getString().contains(TARGET.toString())),"Overview hides UUID");
                }
            }
        } finally { registry.clear(); registry.putAll(oldPanels); providersField.set(null,oldProviders); }
    }
    private void verifyHeraldrySync() {
        var arms=Heraldry.defaults(REALM);
        dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.accept(new HeraldryPayloads.Sync(true,List.of(new HeraldryPayloads.Entry(REALM,arms))));
        require(dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.arms(REALM).equals(arms),"Login insignia sync");
        var changed=Heraldry.defaults(TARGET);
        dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.accept(new HeraldryPayloads.Sync(false,List.of(new HeraldryPayloads.Entry(REALM,changed))));
        require(dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.arms(REALM).equals(changed),"Dynamic insignia delta");
        dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.accept(new HeraldryPayloads.Sync(false,List.of(new HeraldryPayloads.Entry(REALM,null))));
        require(!dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.arms(REALM).equals(changed),"Dissolved kingdoms render neutral");
        dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.accept(new HeraldryPayloads.Sync(true,List.of()));
        require(!dev.sakhno.regnum.nations.client.heraldry.HeraldryClient.arms(REALM).equals(arms),"Reconnect clears stale public state");
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
