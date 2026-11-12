package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.KingdomRole;
import dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.PAGE_SIZE;

/** Server snapshots drive availability; buttons submit UUIDs and never assign local membership or roles. */
public final class KingdomAdministrationScreen extends RoyalScreen {
    private final KingdomManagementScreen parent;
    private State state;
    private Tab tab=Tab.GOVERNMENT;
    private String query="";
    private UUID selected;
    private Action confirmation;
    private Action selectedRole=Action.OFFICER;
    private UUID confirmationTarget,confirmationToken;
    private Component confirmationText;
    private Component feedback=message("loading");
    private boolean failed,initial=true,pendingRead;
    private int sequence,page,offset,visible,listTop,actionsY;
    private long sent,received;
    private FolioBounds bounds;
    private EditBox search;
    private final List<Runnable> availability=new ArrayList<>();

    public KingdomAdministrationScreen(KingdomManagementScreen parent) { super(message("title")); this.parent=parent; }
    private static net.minecraft.network.chat.MutableComponent message(String key,Object... args) { return Component.translatable("kingdom_admin.regnum-nations."+key,args); }
    private static Component role(KingdomRole role) { return Component.translatable("kingdom_admin.regnum-nations.role."+role.name().toLowerCase(Locale.ROOT)); }
    private static Component name(Entry entry) { return Component.literal(entry.name().isEmpty()?entry.id().toString():entry.name()); }
    private Entry selected() { return state==null?null:state.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private boolean busy() { return sent!=0||pendingRead; }
    private boolean ready() { return !busy()&&state!=null&&state.token()!=null; }
    private boolean mutable() { return ready()&&confirmation==null&&canMutate(); }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        Button button=addRenderableWidget(new RoyalButton(x,y,width,label,b -> action.run(),style));
        button.setTooltip(null);
        availability.add(() -> button.active=enabled.getAsBoolean()); return button;
    }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled) { return control(x,y,width,label,action,enabled,ButtonStyle.NORMAL); }

    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,720,440);
        int content=bounds.contentWidth();
        for (Tab value:Tab.values()) {
            int index=value.ordinal(),w=(content-GAP*4)/5;
            var button=(RoyalButton)control(bounds.left()+index*(w+GAP),bounds.y()+43,w,message("tab."+value.name().toLowerCase(Locale.ROOT)),
                    () -> { tab=value; selected=null; offset=0; page=0; read(); },
                    () -> !busy()&&confirmation==null&&(value==Tab.INBOX||state!=null&&state.kingdom()!=null&&(value!=Tab.AVAILABLE&&value!=Tab.OUTGOING||state.role()==KingdomRole.RULER||state.role()==KingdomRole.CHANCELLOR||state.role()==KingdomRole.OFFICER)));
            button.selected(tab==value);
        }
        int searchWidth=Math.max(48,content-76-GAP);
        search=addRenderableWidget(field(font,bounds.left(),bounds.y()+69,searchWidth,message("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(message("search_hint")); search.setResponder(value -> query=value);
        search.setEditable(!busy()&&confirmation==null);
        control(bounds.left()+searchWidth+GAP,bounds.y()+69,content-searchWidth-GAP,message("search"),() -> { page=0; offset=0; selected=null; read(); },() -> !busy()&&confirmation==null);
        actionsY=bounds.footerY()-2*(CONTROL+GAP); listTop=bounds.y()+108;
        visible=Math.clamp((actionsY-listTop-GAP)/(CONTROL+2),1,PAGE_SIZE);
        Button confirmationButton=null;
        if (confirmation!=null) {
            addRenderableWidget(paragraph(font,bounds.left(),listTop,content-GAP,Math.max(CONTROL,actionsY-listTop-GAP),confirmationText));
            int w=(content-GAP)/2;
            confirmationButton=control(bounds.left(),actionsY,w,message("confirm"),this::confirm,() -> ready()&&canMutate(),ButtonStyle.DANGER);
            control(bounds.left()+w+GAP,actionsY,w,text("cancel"),() -> { confirmation=null; rebuildWidgets(); },() -> !busy());
        } else {
            if (state!=null&&!state.entries().isEmpty()) {
                offset=Math.clamp(offset,0,Math.max(0,state.entries().size()-1));
                for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
                    Entry entry=state.entries().get(i);
                    Component label=entry.role()!=null?message("member",name(entry),role(entry.role()),message(entry.online()?"online":"offline"))
                            :message("entry",name(entry),entry.detail());
                    var row=(RoyalButton)control(bounds.left(),listTop+(i-offset)*(CONTROL+2),content,label,
                            () -> { selected=entry.id(); rebuildWidgets(); },() -> ready());
                    row.selected(entry.id().equals(selected));
                }
            } else addRenderableWidget(paragraph(font,bounds.left(),listTop,content-GAP,Math.max(CONTROL,actionsY-listTop-GAP),
                    state==null?feedback:message(state.matches()==0&&!query.isBlank()?"no_matches":"empty."+tab.name().toLowerCase(Locale.ROOT))));
            actions(content);
        }
        int pagerY=bounds.footerY()-CONTROL-GAP;
        control(bounds.left(),pagerY,54,message("previous"),() -> turn(-1),() -> !busy()&&confirmation==null&&(offset>0||page>0));
        control(bounds.right()-54,pagerY,54,message("next"),() -> turn(1),() -> !busy()&&confirmation==null&&state!=null&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches()));
        int w=(content-GAP)/2;
        control(bounds.left(),bounds.footerY(),w,text("refresh"),this::read,() -> !busy()&&confirmation==null);
        control(bounds.left()+w+GAP,bounds.footerY(),w,text("close"),this::onClose,() -> true);
        availability.forEach(Runnable::run);
        setInitialFocus(search); // Confirmation never receives keyboard focus automatically.
        if (initial) { initial=false; read(); }
    }
    private boolean allowed(Action action) { Entry entry=selected(); return mutable()&&entry!=null&&entry.actions().contains(action); }
    private void actions(int width) {
        Entry entry=selected(); int w=(width-GAP*2)/3;
        switch (tab) {
            case GOVERNMENT -> {
                List<Action> roles=entry==null?List.of():entry.actions().stream().filter(a -> a==Action.MEMBER||a==Action.OFFICER||a==Action.CHANCELLOR).toList();
                if (!roles.contains(selectedRole)&&!roles.isEmpty()) selectedRole=roles.getFirst();
                control(bounds.left(),actionsY,w,message("role."+selectedRole.name().toLowerCase(Locale.ROOT)),() -> {
                    selectedRole=roles.get((roles.indexOf(selectedRole)+1)%roles.size()); rebuildWidgets();
                },() -> mutable()&&!roles.isEmpty());
                control(bounds.left()+w+GAP,actionsY,w,message("assign"),() -> ask(selectedRole),() -> allowed(selectedRole));
                control(bounds.right()-w,actionsY,w,message("transfer"),() -> send(Action.PREPARE_TRANSFER,entry.id(),state.token()),() -> allowed(Action.PREPARE_TRANSFER),ButtonStyle.DANGER);
            }
            case CITIES -> {
                int half=(width-GAP)/2;
                control(bounds.left(),actionsY,half,message("remove"),() -> ask(Action.REMOVE),() -> allowed(Action.REMOVE),ButtonStyle.DANGER);
                control(bounds.left()+half+GAP,actionsY,half,message("leave"),() -> ask(Action.LEAVE),() -> allowed(Action.LEAVE),ButtonStyle.DANGER);
            }
            case AVAILABLE -> control(bounds.left(),actionsY,width,message("invite"),() -> send(Action.INVITE,entry.id(),state.token()),() -> allowed(Action.INVITE),ButtonStyle.PRIMARY);
            case INBOX -> {
                int half=(width-GAP)/2;
                control(bounds.left(),actionsY,half,message("accept"),() -> ask(Action.ACCEPT),() -> allowed(Action.ACCEPT),ButtonStyle.PRIMARY);
                control(bounds.left()+half+GAP,actionsY,half,message("reject"),() -> send(Action.REJECT,entry.id(),state.token()),() -> allowed(Action.REJECT));
            }
            case OUTGOING -> control(bounds.left(),actionsY,width,message("cancel_invitation"),() -> send(Action.CANCEL,entry.id(),state.token()),() -> allowed(Action.CANCEL));
        }
    }
    private void ask(Action action) {
        Entry entry=selected(); confirmation=action; confirmationTarget=entry.id(); confirmationToken=state.token();
        confirmationText=message("confirm."+action.name().toLowerCase(Locale.ROOT),name(entry),entry.id().toString());
        rebuildWidgets();
    }
    private void confirm() { send(confirmation,confirmationTarget,confirmationToken); }
    private void turn(int direction) {
        selected=null;
        if (direction>0&&offset+visible<state.entries().size()) { offset+=visible; rebuildWidgets(); }
        else if (direction<0&&offset>0) { offset=Math.max(0,offset-visible); rebuildWidgets(); }
        else { page=Math.max(0,page+direction); offset=direction<0?Math.max(0,PAGE_SIZE-visible):0; read(); }
    }
    private void read() { send(Action.VIEW,null,null); }
    private void send(Action action,UUID target,UUID token) {
        if (sent!=0) return;
        if (action==Action.VIEW&&!canRead()) { pendingRead=true; feedback=text("loading"); rebuildWidgets(); return; }
        pendingRead=false;
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { state=null; feedback=text("unavailable"); failed=true; rebuildWidgets(); return; }
        sequence=nextSequence(); sent=System.currentTimeMillis(); failed=false;
        feedback=action==Action.VIEW?text("loading"):text("working");
        if (action!=Action.VIEW) mutationSent(); else readSent();
        ClientPlayNetworking.send(new Request(sequence,action,tab,query,page,state==null?null:state.kingdom(),target,token));
        rebuildWidgets();
    }
    public void accept(State reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        invalidated=false; sent=0; received=System.currentTimeMillis(); confirmation=null; state=reply; tab=reply.tab(); page=reply.page();
        failed=!reply.result().isEmpty()&&!reply.success(); feedback=reply.result().isEmpty()?Component.empty():Component.translatable(reply.result());
        if (reply.success()) {
            if (reply.transferTarget()!=null) {
                selected=reply.transferTarget(); confirmation=Action.TRANSFER; confirmationTarget=selected; confirmationToken=reply.token();
                Entry entry=selected(); confirmationText=message("confirm.transfer",entry==null?selected.toString():name(entry),selected.toString());
            } else { selected=null; parent.invalidate(); }
        }
        rebuildWidgets(); triggerImmediateNarration(false);
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
        if (sent!=0&&System.currentTimeMillis()-sent>10000) { sent=0; state=null; confirmation=null; feedback=text("timeout"); failed=true; rebuildWidgets(); }
        availability.forEach(Runnable::run);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ENTER&&search.isFocused()&&!busy()&&confirmation==null) { page=0; offset=0; selected=null; read(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (!busy()&&confirmation==null&&state!=null&&x>=bounds.left()&&x<bounds.right()&&y>=listTop&&y<actionsY&&vertical!=0) {
            if (vertical<0&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches())||vertical>0&&(offset>0||page>0)) turn(vertical<0?1:-1);
            return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void onClose() { parent.refreshOnOpen(); minecraft.setScreen(parent); }
    private Component summary() { return state==null||state.kingdom()==null?message("no_kingdom"):message("summary",state.name(),state.cities(),state.members(),role(state.role())); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(summary()).append(". ").append(feedback)
            .append(". ").append(confirmation==null?Component.empty():confirmationText); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth());
        Component summary=summary();
        g.drawString(font,ellipsize(font,summary,bounds.contentWidth()),bounds.left(),bounds.y()+27,INK,false);
        g.drawString(font,ellipsize(font,feedback,bounds.contentWidth()),bounds.left(),bounds.y()+94,failed?ERROR:MUTED,false);
        if (state!=null) {
            int start=state.matches()==0?0:page*PAGE_SIZE+offset+1,end=Math.min(state.matches(),page*PAGE_SIZE+Math.min(state.entries().size(),offset+visible));
            Component paging=message("page",start,end,state.matches());
            g.drawCenteredString(font,ellipsize(font,paging,bounds.contentWidth()-120),bounds.left()+bounds.contentWidth()/2,bounds.footerY()-CONTROL-GAP+6,MUTED);
        }
        super.render(g,mx,my,delta);
    }
}
