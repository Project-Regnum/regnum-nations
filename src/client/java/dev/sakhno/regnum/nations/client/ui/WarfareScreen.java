package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.KingdomPermission;
import dev.sakhno.regnum.nations.kingdom.WarfareData.*;
import dev.sakhno.regnum.nations.networking.WarfarePayloads.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.WarfarePayloads.PAGE_SIZE;

/** Royal folio with paginated, authoritative snapshots. Mutations use the displayed revision. */
public final class WarfareScreen extends RoyalScreen {
    private final DiplomacyScreen parent;
    private Snapshot state;
    private Tab tab=Tab.WARS;
    private int page,offset,visible,sequence;
    private Entry selected;
    private boolean initial=true,pendingRead,failed;
    private long sent,received;
    private Component feedback=message("loading");
    private FolioBounds bounds;
    private final List<Runnable> availability=new ArrayList<>();
    public WarfareScreen(DiplomacyScreen parent) { super(message("title")); this.parent=parent; }
    static Component message(String key,Object... args) { return Component.translatable("warfare.regnum-nations."+key,args); }
    private static String key(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }
    private boolean busy() { return sent!=0||pendingRead; }
    private void button(int x,int y,int w,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        Button button=addRenderableWidget(new RoyalButton(x,y,w,label,b -> action.run(),style));
        availability.add(() -> button.active=enabled.getAsBoolean());
    }
    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,760,490); int w=bounds.contentWidth(),third=(w-2*GAP)/3;
        for (Tab value:Tab.values()) {
            int index=value.ordinal();
            button(bounds.left()+index%3*(third+GAP),bounds.y()+36+index/3*(CONTROL+GAP),third,message("tab."+key(value)),() -> {
                tab=value; page=offset=0; selected=null; read();
            },() -> !busy(),value==tab?ButtonStyle.PRIMARY:ButtonStyle.NORMAL);
        }
        button(bounds.left()+2*(third+GAP),bounds.y()+36+CONTROL+GAP,third,message("peace_talks"),this::onClose,
                () -> !busy()&&state!=null&&state.permissions().contains(KingdomPermission.NEGOTIATE_PEACE),ButtonStyle.NORMAL);
        int top=bounds.y()+112,actionsY=bounds.footerY()-2*(CONTROL+GAP);
        if (selected!=null) {
            addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,Math.max(CONTROL,actionsY-top-GAP),details(selected)));
            var actions=actions(selected);
            for (int i=0;i<actions.size();i++) {
                Action action=actions.get(i); Entry target=selected;
                button(bounds.left()+i*(third+GAP),actionsY,third,message("action."+key(action)),() -> confirm(action,target),
                        () -> !busy()&&canMutate()&&authorized(action),action==Action.WITHDRAW||action==Action.CANCEL_OBJECTIVE?ButtonStyle.DANGER:ButtonStyle.PRIMARY);
            }
            button(bounds.left(),actionsY+CONTROL+GAP,third,message("back"),() -> { selected=null; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
        } else {
            visible=Math.clamp((actionsY-top-GAP)/(CONTROL+GAP),1,PAGE_SIZE);
            if (state==null||state.entries().isEmpty()) addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,Math.max(CONTROL,actionsY-top-GAP),message("empty")));
            else for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
                var entry=state.entries().get(i);
                Component row=tab==Tab.WARS?message("war_row",entry.attackerName(),entry.defenderName()):message("row",entry.cityName(),
                        entry.state()==null?message("eligible"):message("state."+key(entry.state())));
                button(bounds.left(),top+(i-offset)*(CONTROL+GAP),w,row,() -> { selected=entry; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
            }
            button(bounds.left(),actionsY+CONTROL+GAP,54,message("previous"),() -> turn(-1),() -> !busy()&&(offset>0||page>0),ButtonStyle.NORMAL);
            button(bounds.right()-54,actionsY+CONTROL+GAP,54,message("next"),() -> turn(1),
                    () -> !busy()&&state!=null&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches()),ButtonStyle.NORMAL);
        }
        int half=(w-GAP)/2;
        button(bounds.left(),bounds.footerY(),half,text("refresh"),this::read,() -> !busy(),ButtonStyle.NORMAL);
        button(bounds.left()+half+GAP,bounds.footerY(),half,text("close"),this::onClose,() -> true,ButtonStyle.NORMAL);
        availability.forEach(Runnable::run); if (initial) { initial=false; read(); }
    }
    private Component details(Entry e) {
        var result=message("war_row",e.attackerName(),e.defenderName()).copy();
        if (e.city()!=null) result.append("\n").append(message("target_city",e.cityName()));
        if (e.objective()!=null) result.append("\n").append(message("objective."+key(e.objective())));
        if (e.id()!=null) result.append("\n").append(message("reference",e.id().toString()));
        if (e.startedAt()>0) result.append("\n").append(message("started",date(e.startedAt())));
        if (e.state()!=null) result.append("\n").append(message("state."+key(e.state())));
        if (e.state()==State.PREPARING||e.state()==State.ACTIVE) {
            long age=Math.max(0,(System.currentTimeMillis()-received)/1000);
            result.append("\n\n").append(message("timing",Math.max(0,e.readySeconds()-age),Math.max(0,e.remainingSeconds()-age)))
                    .append("\n").append(message("capture",e.progress(),e.captureSeconds(),e.defenseProgress(),e.defenseSeconds()))
                    .append("\n").append(message("obelisk_status",message("obelisk."+e.obelisk())))
                    .append("\n").append(message("participants",e.attackers(),e.defenders())).append("\n").append(Component.literal(e.participants()));
        }
        if (e.completedAt()>0) result.append("\n").append(message("completed",date(e.completedAt())));
        if (e.pendingConquest()) result.append("\n\n").append(message("pending_conquest"));
        result.append("\n\n").append(message("rules")); return result;
    }
    private static String date(long at) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(at));
    }
    private List<Action> actions(Entry e) {
        if (state==null||e.city()==null||e.state()!=null&&e.state().completed()) return List.of();
        boolean attack=e.attacker().equals(state.kingdom());
        if (e.id()==null) return attack?List.of(Action.CAPTURE_OBJECTIVE,Action.PEACE_OBJECTIVE):List.of(Action.DEFEND_OBJECTIVE);
        if (e.state()==State.PLANNED) {
            List<Action> result=new ArrayList<>();
            if (attack&&e.objective()!=Objective.NEGOTIATED_PEACE) result.add(Action.DECLARE_SIEGE);
            if ((e.objective()==Objective.DEFEND_CITY)!=attack) result.add(Action.CANCEL_OBJECTIVE);
            return result;
        }
        return attack?List.of(Action.WITHDRAW):List.of();
    }
    private boolean authorized(Action action) {
        if (state==null) return false;
        return switch (action) {
            case DECLARE_SIEGE, WITHDRAW -> state.permissions().contains(KingdomPermission.DECLARE_SIEGE);
            case PEACE_OBJECTIVE -> state.permissions().contains(KingdomPermission.MANAGE_WAR_OBJECTIVES)&&state.permissions().contains(KingdomPermission.NEGOTIATE_PEACE);
            default -> state.permissions().contains(KingdomPermission.MANAGE_WAR_OBJECTIVES);
        };
    }
    private void confirm(Action action,Entry target) {
        minecraft.setScreen(new NationsConfirmationScreen(this,message("action."+key(action)),
                message("confirm",message("action."+key(action)),target.cityName()).copy().append("\n\n").append(message("rules")),true,() -> send(action,target)));
    }
    private void turn(int direction) {
        if (state==null) return;
        if (direction<0&&offset>0) { offset=Math.max(0,offset-visible); rebuildWidgets(); }
        else if (direction>0&&offset+visible<state.entries().size()) { offset+=visible; rebuildWidgets(); }
        else { page=Math.max(0,page+direction); offset=0; selected=null; read(); }
    }
    private void read() {
        if (!canRead()) { pendingRead=true; return; }
        pendingRead=false; readSent(); send(Action.VIEW,null);
    }
    private void send(Action action,Entry target) {
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { feedback=text("unavailable"); failed=true; return; }
        if (action!=Action.VIEW) mutationSent();
        sequence=nextSequence(); sent=System.currentTimeMillis(); failed=false;
        ClientPlayNetworking.send(new Request(sequence,action,tab,page,state==null?null:state.kingdom(),target==null?null:target.city(),
                target==null?null:target.war(),target==null?null:target.id(),target==null?null:target.revision()));
        availability.forEach(Runnable::run);
    }
    public void accept(Snapshot reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        sent=0; received=System.currentTimeMillis(); pendingRead=false; state=reply; tab=reply.tab(); page=reply.page();
        if (selected!=null) {
            Entry old=selected;
            selected=reply.entries().stream().filter(e -> Objects.equals(e.id(),old.id())&&Objects.equals(e.city(),old.city())&&e.war().equals(old.war())).findFirst().orElse(null);
        }
        offset=Math.clamp(offset,0,Math.max(0,reply.entries().size()-1));
        failed=!reply.result().isEmpty()&&!reply.success(); feedback=reply.result().isEmpty()?message("ready"):Component.translatable(reply.result());
        rebuildWidgets();
    }
    public void invalidate() { state=null; selected=null; parent.invalidate(); if (sent==0) read(); }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (pendingRead&&canRead()) read();
        if (sent!=0&&System.currentTimeMillis()-sent>10000) {
            sent=0; pendingRead=false; state=null; selected=null; failed=true; feedback=text("timeout"); rebuildWidgets();
        }
        // Refresh only while viewing live siege status; all mutation results remain server-owned.
        if (!busy()&&(tab==Tab.SIEGES||selected!=null&&(selected.state()==State.PREPARING||selected.state()==State.ACTIVE))&&System.currentTimeMillis()-received>=2000&&canRead()) read();
        availability.forEach(Runnable::run);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(feedback); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth());
        g.drawString(font,ellipsize(font,feedback,bounds.contentWidth()),bounds.left(),bounds.y()+96,failed?ERROR:MUTED,false);
        if (selected==null&&state!=null) g.drawCenteredString(font,message("page",page+1,Math.max(1,(state.matches()+PAGE_SIZE-1)/PAGE_SIZE)),
                bounds.left()+bounds.contentWidth()/2,bounds.footerY()-CONTROL-GAP+6,MUTED);
        super.render(g,mx,my,delta);
    }
}
