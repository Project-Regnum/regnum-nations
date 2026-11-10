package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Compact snapshot inspector; the political map remains the single territory renderer. */
public final class KingdomManagementScreen extends RoyalScreen {
    private enum Section { CITY, KINGDOM, TERRITORIES }
    private Section section=Section.CITY;
    private State state;
    private UUID viewedCity;
    private int sequence;
    private long requested;
    private Component feedback=Component.empty();
    private boolean failed,pendingRead,refreshOnInit;
    private Button refresh;
    private Readout readout;
    private KingdomAtlasView atlas;
    private CityAtlasView civic;
    private boolean expandedAtlasRecord,expandedCityRecord;

    public KingdomManagementScreen(State initial) {
        super(text("kingdom_title")); state=initial;
        viewedCity=initial==null||initial.city()==null?null:initial.city().id();
        if (initial!=null&&initial.intent()==dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.OPEN_MENU) section=Section.KINGDOM;
        if (initial!=null&&!initial.result().isEmpty()) { feedback=Component.translatable(initial.result()); failed=!initial.success(); }
    }
    public State state() { return state; }
    public void refreshOnOpen() { requested=0; sequence=0; refreshOnInit=true; }
    @Override protected void init() {
        atlas=null; civic=null;
        if (section==Section.KINGDOM) {
            atlas=new KingdomAtlasView(font,width,height,state,expandedAtlasRecord,this::request,this::onClose,this::navigate,
                    () -> { expandedAtlasRecord=!expandedAtlasRecord; rebuildWidgets(); if (atlas.disclosure!=null) setInitialFocus(atlas.disclosure); },requested!=0,failed);
            for (AbstractWidget widget:atlas.widgets()) addRenderableWidget(widget);
            refresh=atlas.refresh; readout=atlas.record;
            setInitialFocus(atlas.initialFocus);
        } else {
            civic=new CityAtlasView(font,width,height,state,section==Section.TERRITORIES,expandedCityRecord,this::request,this::onClose,this::navigate,
                    () -> { expandedCityRecord=!expandedCityRecord; rebuildWidgets(); if (civic.disclosure!=null) setInitialFocus(civic.disclosure); },requested!=0,failed);
            for (AbstractWidget widget:civic.widgets()) addRenderableWidget(widget);
            refresh=civic.refresh; readout=civic.record;
            setInitialFocus(civic.initialFocus);
        }
        if (refreshOnInit || state==null&&requested==0) { refreshOnInit=false; request(); }
    }
    private void navigate(int index) {
        if (index==3) minecraft.setScreen(new PoliticalMapScreen(this,false));
        else { section=Section.values()[index]; rebuildWidgets(); }
    }
    private void request() {
        if (minecraft.player==null||minecraft.level==null) return;
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { failed=true; feedback=text("unavailable"); return; }
        requested=System.currentTimeMillis(); feedback=text("loading"); failed=false;
        if (!canRead()) { pendingRead=true; return; }
        pendingRead=false; readSent(); sequence=nextSequence(); var pos=minecraft.player.chunkPosition();
        ClientPlayNetworking.send(new Request(sequence,Action.VIEW,viewedCity,minecraft.level.dimension().location(),pos.x,pos.z));
    }
    public void invalidate() {
        sequence=nextSequence(); state=null; requested=0; pendingRead=false; refreshOnInit=true;
        if (minecraft!=null&&minecraft.screen==this) { refreshOnInit=false; request(); rebuildWidgets(); }
    }
    public void accept(State reply) { if (reply.sequence()==sequence) { requested=0; update(reply); } }
    public void update(State reply) {
        if (reply.city()!=null||reply.result().isEmpty()) {
            state=reply; viewedCity=reply.city()==null?null:reply.city().id();
        }
        failed=!reply.result().isEmpty()&&!reply.success();
        feedback=reply.result().isEmpty()?Component.empty():Component.translatable(reply.result());
        if (failed&&state!=null&&state.city()!=null) feedback=text("stale_view",feedback);
        if (minecraft!=null&&minecraft.screen==this) {
            Component focused=getFocused() instanceof AbstractWidget widget?widget.getMessage():null;
            boolean inDetails=readout!=null&&getFocused()==readout;
            double scroll=readout==null?0:readout.scrollPosition();
            rebuildWidgets();
            if (readout!=null) { readout.restoreScroll(scroll); if (inDetails) setFocused(readout); }
            if (!inDetails&&focused!=null) for (var child:children())
                if (child instanceof AbstractWidget widget&&widget.getMessage().equals(focused)) { setFocused(widget); break; }
        }
    }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (pendingRead&&canRead()) request();
        if (requested!=0&&System.currentTimeMillis()-requested>10000) { requested=0; pendingRead=false; failed=true; feedback=text("timeout"); }
        refresh.active=requested==0;
    }
    private Component status() {
        if (state!=null&&state.city()!=null&&state.city().repair()) return Component.translatable("message.regnum-nations.founding.domain_repair");
        if (state!=null&&state.overview()!=null&&state.overview().capitalConflict()) return Component.translatable("message.regnum-nations.founding.capital_conflict");
        return feedback;
    }
    @Override public Component getNarrationMessage() {
        return (atlas!=null?atlas.narration():civic!=null?civic.narration():title).copy().append(". ").append(status());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        Component message=status();
        if (atlas!=null) atlas.render(g,message,failed); else civic.render(g,message,failed);
        super.render(g,mx,my,delta);
        if (atlas!=null) atlas.renderHover(g,mx,my,message); else civic.renderHover(g,mx,my,message);
    }
}
