package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.CityRole;
import dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.PAGE_SIZE;

/** Server snapshots drive availability; buttons submit UUIDs and never assign local membership or roles. */
public final class CitizenManagementScreen extends RoyalScreen {
    private final KingdomManagementScreen parent;
    private State state;
    private Tab tab=Tab.MEMBERS;
    private String query="";
    private UUID selected;
    private Action confirmation;
    private UUID confirmationTarget,confirmationToken;
    private Component confirmationText;
    private Component feedback=message("loading");
    private boolean failed,initial=true,pendingRead;
    private int sequence,page,offset,visible,listTop,actionsY;
    private long sent,received;
    private FolioBounds bounds;
    private EditBox search;
    private final List<Runnable> availability=new ArrayList<>();

    public CitizenManagementScreen(KingdomManagementScreen parent) { super(message("title")); this.parent=parent; }
    private static net.minecraft.network.chat.MutableComponent message(String key,Object... args) { return Component.translatable("citizen_ui.regnum-nations."+key,args); }
    private static Component role(CityRole role) { return Component.translatable("citizen.regnum-nations.role."+role.name().toLowerCase(Locale.ROOT)); }
    private static Component name(Entry entry) { return Component.literal(entry.name().isEmpty()?entry.player().toString():entry.name()); }
    private Entry selected() { return state==null?null:state.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private boolean busy() { return sent!=0||pendingRead; }
    private boolean ready() { return !busy()&&state!=null&&state.token()!=null; }
    private boolean mutable() { return ready()&&confirmation==null&&canMutate(); }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        Button button=addRenderableWidget(new RoyalButton(x,y,width,label,b -> action.run(),style));
        availability.add(() -> button.active=enabled.getAsBoolean()); return button;
    }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled) { return control(x,y,width,label,action,enabled,ButtonStyle.NORMAL); }

    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,680,420);
        int content=bounds.contentWidth();
        for (Tab value:Tab.values()) {
            int index=value.ordinal(),w=(content-GAP*3)/4;
            var button=(RoyalButton)control(bounds.left()+index*(w+GAP),bounds.y()+43,w,message("tab."+value.name().toLowerCase(Locale.ROOT)),
                    () -> { tab=value; selected=null; offset=0; page=0; read(); },
                    () -> !busy()&&confirmation==null&&(value==Tab.MEMBERS||value==Tab.INBOX||state!=null&&state.invite()));
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
                            :entry.city()!=null?message("invitation",entry.cityName(),name(entry),entry.seconds()):message("online_player",name(entry));
                    var row=(RoyalButton)control(bounds.left(),listTop+(i-offset)*(CONTROL+2),content,label,
                            () -> { selected=entry.id(); rebuildWidgets(); },() -> ready());
                    row.selected(entry.id().equals(selected));
                    row.setTooltip(Tooltip.create(label.copy().append("\n").append(Component.literal(entry.player().toString()))));
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
        setInitialFocus(confirmationButton==null?search:confirmationButton);
        if (initial) { initial=false; read(); }
    }
    private void actions(int width) {
        Entry entry=selected(); int w=(width-GAP*2)/3;
        switch (tab) {
            case MEMBERS -> {
                boolean officer=entry!=null&&entry.role()==CityRole.OFFICER;
                control(bounds.left(),actionsY,w,message(officer?"demote":"promote"),() -> ask(officer?Action.DEMOTE:Action.PROMOTE),
                        () -> mutable()&&state.manage()&&entry!=null&&(officer||entry.role()==CityRole.CITIZEN));
                control(bounds.left()+w+GAP,actionsY,w,message("kick"),() -> ask(Action.KICK),
                        () -> mutable()&&state.manage()&&entry!=null&&entry.role()!=CityRole.OWNER,ButtonStyle.DANGER);
                control(bounds.right()-w,actionsY,w,message("leave"),() -> ask(Action.LEAVE),() -> mutable()&&state.leave(),ButtonStyle.DANGER);
            }
            case ONLINE -> control(bounds.left(),actionsY,width,message("invite"),() -> mutate(Action.INVITE,entry.id(),state.token()),
                    () -> mutable()&&state.invite()&&entry!=null,ButtonStyle.PRIMARY);
            case INBOX -> {
                int half=(width-GAP)/2;
                control(bounds.left(),actionsY,half,message("accept"),() -> mutate(Action.ACCEPT,entry.id(),state.token()),
                        () -> mutable()&&state.city()==null&&entry!=null&&notExpired(entry),ButtonStyle.PRIMARY);
                control(bounds.left()+half+GAP,actionsY,half,message("decline"),() -> mutate(Action.DECLINE,entry.id(),state.token()),
                        () -> mutable()&&entry!=null&&notExpired(entry));
            }
            case OUTGOING -> { }
        }
    }
    private boolean notExpired(Entry entry) { return System.currentTimeMillis()-received<entry.seconds()*1000L; }
    private void ask(Action action) {
        Entry entry=selected(); confirmation=action; confirmationTarget=action==Action.LEAVE?null:entry.id(); confirmationToken=state.token();
        confirmationText=message("confirm."+action.name().toLowerCase(Locale.ROOT),action==Action.LEAVE?state.cityName():name(entry),
                action==Action.LEAVE?"":entry.player().toString());
        rebuildWidgets();
    }
    private void confirm() { mutate(confirmation,confirmationTarget,confirmationToken); }
    private void turn(int direction) {
        selected=null;
        if (direction>0&&offset+visible<state.entries().size()) { offset+=visible; rebuildWidgets(); }
        else if (direction<0&&offset>0) { offset=Math.max(0,offset-visible); rebuildWidgets(); }
        else { page=Math.max(0,page+direction); offset=direction<0?Math.max(0,PAGE_SIZE-visible):0; read(); }
    }
    private void read() { send(Action.VIEW,null,null); }
    private void mutate(Action action,UUID target,UUID token) { send(action,target,token); }
    private void send(Action action,UUID target,UUID token) {
        if (sent!=0) return;
        if (action==Action.VIEW&&!canRead()) { pendingRead=true; feedback=text("loading"); rebuildWidgets(); return; }
        pendingRead=false;
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { state=null; feedback=text("unavailable"); failed=true; rebuildWidgets(); return; }
        sequence=nextSequence(); sent=System.currentTimeMillis(); failed=false;
        feedback=action==Action.VIEW?text("loading"):text("working");
        if (action!=Action.VIEW) mutationSent(); else readSent();
        ClientPlayNetworking.send(new Request(sequence,action,tab,query,page,state==null?null:state.city(),target,token));
        rebuildWidgets();
    }
    public void accept(State reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        sent=0; received=System.currentTimeMillis(); confirmation=null; state=reply; tab=reply.tab(); page=reply.page();
        failed=!reply.result().isEmpty()&&!reply.success(); feedback=reply.result().isEmpty()?Component.empty():Component.translatable(reply.result());
        if (reply.success()) { selected=null; parent.invalidate(); }
        rebuildWidgets(); triggerImmediateNarration(false);
    }
    @Override public void tick() {
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
    private Component summary() { return state==null||state.city()==null?message("no_city"):message("summary",state.cityName(),state.citizens()); }
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
        if (my>=bounds.y()+25&&my<bounds.y()+39&&font.width(summary)>bounds.contentWidth()) tooltip(g,font,summary,mx,my);
        if (my>=bounds.y()+92&&my<listTop&&font.width(feedback)>bounds.contentWidth()) tooltip(g,font,feedback,mx,my);
    }
}
