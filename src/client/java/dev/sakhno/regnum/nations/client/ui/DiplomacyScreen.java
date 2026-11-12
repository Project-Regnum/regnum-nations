package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.DiplomacyData.Relation;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.DiplomacyPayloads.PAGE_SIZE;

/** On-demand server snapshots. No optimistic relations, client authority or hover descriptions. */
public final class DiplomacyScreen extends RoyalScreen {
    private final KingdomManagementScreen parent;
    private State state;
    private Tab tab=Tab.KINGDOMS;
    private int page,offset,visible,sequence,listTop,actionsY;
    private UUID selected;
    private Entry confirming;
    private Action confirmation;
    private Component feedback=message("loading");
    private boolean initial=true,pendingRead,failed;
    private long sent;
    private FolioBounds bounds;
    private final List<Runnable> availability=new ArrayList<>();
    public DiplomacyScreen(KingdomManagementScreen parent) { super(message("title")); this.parent=parent; }
    static Component message(String key,Object... args) { return Component.translatable("diplomacy.regnum-nations."+key,args); }
    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private boolean busy() { return sent!=0||pendingRead; }
    private Entry selected() { return state==null?null:state.entries().stream().filter(e -> e.kingdom().equals(selected)).findFirst().orElse(null); }
    private boolean mutable() { return !busy()&&state!=null&&state.manage()&&confirmation==null&&canMutate(); }
    private void button(int x,int y,int w,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        Button b=addRenderableWidget(new RoyalButton(x,y,w,label,ignored -> action.run(),style));
        b.setTooltip(null);
        availability.add(() -> b.active=enabled.getAsBoolean());
    }
    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,680,420); int w=bounds.contentWidth(),third=(w-2*GAP)/3;
        for (Tab value:Tab.values()) {
            int index=value.ordinal();
            button(bounds.left()+index*(third+GAP),bounds.y()+43,third,message("tab."+key(value)),() -> { tab=value; page=offset=0; selected=null; read(); },
                    () -> !busy()&&confirmation==null&&(value==Tab.KINGDOMS||state!=null&&state.manage()),ButtonStyle.NORMAL);
        }
        listTop=bounds.y()+85; actionsY=bounds.footerY()-3*(CONTROL+GAP);
        visible=Math.clamp((actionsY-listTop-GAP)/(CONTROL+2),1,PAGE_SIZE);
        if (confirmation!=null) {
            int y=bounds.footerY()-CONTROL-GAP;
            addRenderableWidget(paragraph(font,bounds.left(),listTop,w-GAP,Math.max(CONTROL,y-listTop-GAP),
                    message("confirm."+key(confirmation),confirming.name())));
            int half=(w-GAP)/2;
            button(bounds.left(),y,half,message("confirm"),() -> send(confirmation,confirming),() -> !busy()&&canMutate(),ButtonStyle.DANGER);
            button(bounds.left()+half+GAP,y,half,text("cancel"),() -> { confirmation=null; confirming=null; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
        } else {
            if (state==null||state.entries().isEmpty()) addRenderableWidget(paragraph(font,bounds.left(),listTop,w-GAP,Math.max(CONTROL,actionsY-listTop-GAP),message("empty")));
            else {
                offset=Math.clamp(offset,0,Math.max(0,state.entries().size()-1));
                for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
                    Entry entry=state.entries().get(i);
                    Component status=state.kingdom()==null?message("no_relation"):message("relation."+key(entry.relation()));
                    if (entry.invitation()!=null) status=status.copy().append(" · ").append(message(entry.incoming()?"incoming":"outgoing"));
                    button(bounds.left(),listTop+(i-offset)*(CONTROL+2),w,message("row",entry.name(),status),
                            () -> { selected=entry.kingdom(); feedback=details(entry); rebuildWidgets(); },() -> !busy(),entry.kingdom().equals(selected)?ButtonStyle.PRIMARY:ButtonStyle.NORMAL);
                }
            }
            Entry e=selected();
            action(0,0,Action.INVITE,() -> e!=null&&e.relation()==Relation.NEUTRAL&&e.invitation()==null);
            action(1,0,Action.TERMINATE,() -> e!=null&&e.relation()==Relation.ALLIED);
            action(2,0,Action.HOSTILE,() -> e!=null&&!e.ownHostility());
            action(0,1,Action.ACCEPT,() -> e!=null&&e.invitation()!=null&&e.incoming());
            Action refusal=e!=null&&e.invitation()!=null&&!e.incoming()?Action.CANCEL:Action.REJECT;
            action(1,1,refusal,() -> e!=null&&e.invitation()!=null);
            action(2,1,Action.NEUTRAL,() -> e!=null&&e.ownHostility());
            int y=bounds.footerY()-CONTROL-GAP;
            button(bounds.left(),y,54,message("previous"),() -> turn(-1),() -> !busy()&&(offset>0||page>0),ButtonStyle.NORMAL);
            button(bounds.right()-54,y,54,message("next"),() -> turn(1),() -> !busy()&&state!=null&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches()),ButtonStyle.NORMAL);
        }
        int half=(w-GAP)/2;
        button(bounds.left(),bounds.footerY(),half,text("refresh"),this::read,() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left()+half+GAP,bounds.footerY(),half,text("close"),this::onClose,() -> true,ButtonStyle.NORMAL);
        availability.forEach(Runnable::run);
        if (initial) { initial=false; read(); }
    }
    private Component details(Entry e) {
        if (e.invitation()!=null) return message(e.incoming()?"incoming_detail":"outgoing_detail",e.name(),
                Math.max(0,(e.expiresAt()-System.currentTimeMillis()+999)/1000));
        if (e.relation()==Relation.HOSTILE) return message("hostile_detail",message(e.ownHostility()?"yes":"no"),message(e.theirHostility()?"yes":"no"));
        return message("row",e.name(),message("relation."+key(e.relation())));
    }
    private void action(int column,int row,Action action,BooleanSupplier enabled) {
        int w=(bounds.contentWidth()-2*GAP)/3;
        button(bounds.left()+column*(w+GAP),actionsY+row*(CONTROL+GAP),w,message("action."+key(action)),() -> {
            if (action==Action.INVITE||action==Action.ACCEPT) send(action,selected());
            else { confirmation=action; confirming=selected(); rebuildWidgets(); }
        },() -> mutable()&&enabled.getAsBoolean(),action==Action.HOSTILE||action==Action.TERMINATE?ButtonStyle.DANGER:ButtonStyle.NORMAL);
    }
    private void read() {
        if (!canRead()) { pendingRead=true; return; }
        pendingRead=false; readSent(); send(Action.VIEW,null);
    }
    private void send(Action action,Entry target) {
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { feedback=text("unavailable"); failed=true; return; }
        if (action!=Action.VIEW) mutationSent();
        sequence=nextSequence(); sent=System.currentTimeMillis(); failed=false;
        ClientPlayNetworking.send(new Request(sequence,action,tab,page,state==null?null:state.kingdom(),target==null?null:target.kingdom(),target==null?null:target.revision(),target==null?null:target.invitation()));
        availability.forEach(Runnable::run);
    }
    public void accept(State reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        invalidated=false; sent=0; pendingRead=false; state=reply; tab=reply.tab(); page=reply.page(); confirmation=null; confirming=null;
        if (selected()==null) selected=null;
        failed=!reply.result().isEmpty()&&!reply.success();
        feedback=reply.result().isEmpty()?message(reply.manage()?"ready":"read_only"):Component.translatable(reply.result());
        rebuildWidgets();
    }
    private void turn(int direction) {
        if (state==null) return;
        if (direction<0&&offset>0) { offset=Math.max(0,offset-visible); rebuildWidgets(); }
        else if (direction>0&&offset+visible<state.entries().size()) { offset+=visible; rebuildWidgets(); }
        else { page=Math.max(0,page+direction); offset=0; selected=null; read(); }
    }
    private boolean invalidated;
    public void invalidate() {
        invalidated=true; state=null; confirmation=null; selected=null;
        parent.invalidate();
        rebuildWidgets();
    }
    @Override public void tick() {
        if (invalidated&&sent==0) { invalidated=false; read(); }
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (pendingRead&&canRead()) read();
        if (sent!=0&&System.currentTimeMillis()-sent>10000) {
            sent=0; pendingRead=false; sequence=nextSequence(); state=null; confirmation=null; confirming=null;
            failed=true; feedback=text("timeout"); rebuildWidgets();
        }
        availability.forEach(Runnable::run);
    }
    @Override public void onClose() { parent.refreshOnOpen(); minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(feedback); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth());
        Component own=state==null||state.kingdom()==null?message("no_kingdom"):message("kingdom",state.name());
        g.drawString(font,ellipsize(font,own,bounds.contentWidth()),bounds.left(),bounds.y()+27,INK,false);
        g.drawString(font,ellipsize(font,feedback,bounds.contentWidth()),bounds.left(),bounds.y()+70,failed?ERROR:MUTED,false);
        if (state!=null&&confirmation==null) g.drawCenteredString(font,message("page",page+1,Math.max(1,(state.matches()+PAGE_SIZE-1)/PAGE_SIZE)),bounds.left()+bounds.contentWidth()/2,bounds.footerY()-CONTROL-GAP+6,MUTED);
        super.render(g,mx,my,delta);
    }
}
