package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.DiplomacyData.Relation;
import dev.sakhno.regnum.nations.api.client.v1.NationsIdentity;
import dev.sakhno.regnum.nations.kingdom.KingdomPermission;
import dev.sakhno.regnum.nations.networking.InterstatePayloads.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.InterstatePayloads.PAGE_SIZE;

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
    private boolean initial=true,pendingRead,failed,detail;
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
        availability.clear(); bounds=folioBounds(width,height,720,460); int w=bounds.contentWidth(),third=(w-2*GAP)/3;
        for (Tab value:Tab.values()) {
            int index=value.ordinal();
            button(bounds.left()+index*(third+GAP),bounds.y()+43,third,message("tab."+key(value)),() -> { tab=value; page=offset=0; selected=null; detail=false; read(); },
                    () -> !busy()&&confirmation==null&&(value==Tab.KINGDOMS||state!=null&&state.manage()),ButtonStyle.NORMAL);
        }
        button(bounds.right()-104,bounds.y()+12,104,WarfareScreen.message("title"),() -> minecraft.setScreen(new WarfareScreen(this)),
                () -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        listTop=bounds.y()+85; actionsY=bounds.footerY()-(1+(actions().size()+2)/3)*(CONTROL+GAP);
        visible=Math.clamp((actionsY-listTop-GAP)/(CONTROL+2),1,PAGE_SIZE);
        if (confirmation!=null) {
            int y=bounds.footerY()-CONTROL-GAP;
            addRenderableWidget(paragraph(font,bounds.left(),listTop,w-GAP,Math.max(CONTROL,y-listTop-GAP),
                    message("confirm."+key(confirmation),confirming.name()).copy().append("\n\n").append(details(confirming))));
            int half=(w-GAP)/2;
            button(bounds.left(),y,half,message("confirm"),() -> send(confirmation,confirming),() -> !busy()&&canMutate(),ButtonStyle.DANGER);
            button(bounds.left()+half+GAP,y,half,text("cancel"),() -> { confirmation=null; confirming=null; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
        } else {
            if (detail&&selected()!=null) addRenderableWidget(paragraph(font,bounds.left(),listTop,w-GAP,Math.max(CONTROL,actionsY-listTop-GAP),details(selected())));
            else if (state==null||state.entries().isEmpty()) addRenderableWidget(paragraph(font,bounds.left(),listTop,w-GAP,Math.max(CONTROL,actionsY-listTop-GAP),message("empty")));
            else {
                offset=Math.clamp(offset,0,Math.max(0,state.entries().size()-1));
                for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
                    Entry entry=state.entries().get(i);
                    Component status=state.kingdom()==null?message("no_relation"):message("relation."+key(entry.relation()));
                    if (entry.invitation()!=null) status=status.copy().append(" · ").append(message(entry.incoming()?"incoming":"outgoing"));
                    button(bounds.left()+22,listTop+(i-offset)*(CONTROL+2),w-22,message("row",entry.name(),status),
                            () -> { selected=entry.kingdom(); detail=true; feedback=message(state.manage()?"detail_ready":"read_only"); rebuildWidgets(); },() -> !busy(),entry.kingdom().equals(selected)?ButtonStyle.PRIMARY:ButtonStyle.NORMAL);
                }
            }
            List<Action> actions=actions();
            for (int i=0;i<actions.size();i++) action(i%3,i/3,actions.get(i));
            int y=bounds.footerY()-CONTROL-GAP;
            if (detail) button(bounds.left(),y,100,message("back"),() -> { detail=false; selected=null; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
            else {
                button(bounds.left(),y,54,message("previous"),() -> turn(-1),() -> !busy()&&(offset>0||page>0),ButtonStyle.NORMAL);
                button(bounds.right()-54,y,54,message("next"),() -> turn(1),() -> !busy()&&state!=null&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches()),ButtonStyle.NORMAL);
            }
        }
        int half=(w-GAP)/2;
        button(bounds.left(),bounds.footerY(),half,text("refresh"),this::read,() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left()+half+GAP,bounds.footerY(),half,text("close"),this::onClose,() -> true,ButtonStyle.NORMAL);
        availability.forEach(Runnable::run);
        if (initial) { initial=false; read(); }
    }
    private Component details(Entry e) {
        var text=message("row",e.name(),message("relation."+key(e.relation()))).copy();
        if (e.relationSince()>0) text.append("\n").append(message("since",date(e.relationSince())));
        if (e.invitation()!=null) {
            Component sender=Component.literal(e.incoming()?e.name():state.name());
            Component recipient=Component.literal(e.incoming()?state.name():e.name());
            text.append("\n\n").append(message("proposal_detail",message("proposal."+key(e.proposalType())),sender,recipient,
                    date(e.createdAt()),date(e.expiresAt()),Math.max(0,(e.expiresAt()-System.currentTimeMillis()+999)/1000)));
        }
        if (e.relation()==Relation.HOSTILE) text.append("\n").append(message("hostile_detail",message(e.ownHostility()?"yes":"no"),message(e.theirHostility()?"yes":"no")));
        if (e.relation()==Relation.AT_WAR) text.append("\n").append(message("war_by",e.kingdom().equals(e.warDeclarer())?e.name():state.name())).append("\n").append(message("war_detail"));
        if (e.relation()==Relation.ALLIED||e.relation()==Relation.NON_AGGRESSION) text.append("\n").append(message("treaty_detail"));
        if (e.invitation()==null&&e.nextProposalAt()>System.currentTimeMillis()) text.append("\n").append(message("next_proposal",date(e.nextProposalAt())));
        if (state!=null&&!state.manage()) text.append("\n").append(message("read_only"));
        return text;
    }
    private static String date(long time) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(time));
    }
    private List<Action> actions() {
        Entry e=selected();
        if (!detail||e==null||state==null||!state.manage()) return List.of();
        if (e.invitation()!=null) return e.incoming()?List.of(Action.ACCEPT,Action.REJECT):List.of(Action.CANCEL);
        return switch (e.relation()) {
            case NEUTRAL -> List.of(Action.INVITE,Action.PROPOSE_PACT,Action.HOSTILE,Action.DECLARE_WAR);
            case HOSTILE -> List.of(Action.PROPOSE_PEACE,Action.DECLARE_WAR,e.ownHostility()?Action.NEUTRAL:Action.HOSTILE);
            case ALLIED -> List.of(Action.TERMINATE);
            case NON_AGGRESSION -> List.of(Action.END_PACT);
            case AT_WAR -> List.of(Action.PROPOSE_PEACE);
        };
    }
    private boolean authorized(Action action) {
        if (state==null) return false;
        KingdomPermission permission=switch (action) {
            case INVITE -> KingdomPermission.PROPOSE_ALLIANCE;
            case PROPOSE_PEACE -> KingdomPermission.NEGOTIATE_PEACE;
            case DECLARE_WAR -> KingdomPermission.DECLARE_WAR;
            case ACCEPT, REJECT, CANCEL -> selected()==null||selected().proposalType()==null?KingdomPermission.MANAGE_TREATIES:selected().proposalType().permission();
            default -> KingdomPermission.MANAGE_TREATIES;
        };
        return state.permissions().contains(permission);
    }
    private void action(int column,int row,Action action) {
        int w=(bounds.contentWidth()-2*GAP)/3;
        button(bounds.left()+column*(w+GAP),actionsY+row*(CONTROL+GAP),w,message("action."+key(action)),() -> {
            confirmation=action; confirming=selected(); rebuildWidgets();
        },() -> mutable()&&authorized(action),action==Action.HOSTILE||action==Action.DECLARE_WAR||action==Action.TERMINATE||action==Action.END_PACT?ButtonStyle.DANGER:ButtonStyle.NORMAL);
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
        if (selected()==null) { selected=null; detail=false; }
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
        invalidated=true; state=null; confirmation=null; selected=null; detail=false;
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
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth()-114);
        Component own=state==null||state.kingdom()==null?message("no_kingdom"):message("kingdom",state.name());
        int identityWidth=state!=null&&state.kingdom()!=null?20:0;
        if (identityWidth>0) NationsIdentity.drawKingdomArms(g,state.kingdom(),bounds.left(),bounds.y()+25,12,15);
        g.drawString(font,ellipsize(font,own,bounds.contentWidth()-identityWidth-114),bounds.left()+identityWidth,bounds.y()+27,INK,false);
        g.drawString(font,ellipsize(font,feedback,bounds.contentWidth()),bounds.left(),bounds.y()+70,failed?ERROR:MUTED,false);
        if (state!=null&&confirmation==null&&!detail) g.drawCenteredString(font,message("page",page+1,Math.max(1,(state.matches()+PAGE_SIZE-1)/PAGE_SIZE)),bounds.left()+bounds.contentWidth()/2,bounds.footerY()-CONTROL-GAP+6,MUTED);
        super.render(g,mx,my,delta);
        if (state!=null&&confirmation==null&&!detail) for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
            var e=state.entries().get(i);
            NationsIdentity.drawKingdomArms(g,e.kingdom(),bounds.left()+1,listTop+(i-offset)*(CONTROL+2),16,20);
        }
    }
}
