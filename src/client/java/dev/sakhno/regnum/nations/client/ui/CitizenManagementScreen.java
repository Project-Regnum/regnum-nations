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
    private boolean composingActions;
    private int actionLeft, actionWidth, actionTop;
    private final List<Runnable> availability=new ArrayList<>();

    public CitizenManagementScreen(KingdomManagementScreen parent) { super(message("title")); this.parent=parent; }
    private static net.minecraft.network.chat.MutableComponent message(String key,Object... args) { return Component.translatable("citizen_ui.regnum-nations."+key,args); }
    private static Component role(CityRole role) { return Component.translatable("citizen.regnum-nations.role."+role.name().toLowerCase(Locale.ROOT)); }
    private static Component name(Entry entry) { return entry.name().isEmpty()?text("unknown_name"):Component.literal(entry.name()); }
    private Entry selected() { return state==null?null:state.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private boolean busy() { return sent!=0||pendingRead; }
    private boolean ready() { return !busy()&&state!=null&&state.token()!=null; }
    private boolean mutable() { return ready()&&confirmation==null&&canMutate(); }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        if (composingActions) { x=actionLeft; y=actionTop; width=actionWidth; actionTop+=CONTROL+4; }
        Button button=addRenderableWidget(new RoyalButton(x,y,width,label,b -> action.run(),style));
        availability.add(() -> button.active=enabled.getAsBoolean()); return button;
    }
    private Button control(int x,int y,int width,Component label,Runnable action,BooleanSupplier enabled) { return control(x,y,width,label,action,enabled,ButtonStyle.NORMAL); }

    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,640,360);
        int content=bounds.contentWidth();
        boolean wide=content>=440&&height>=310;
        int rail=wide?174:content;
        int listWidth=wide?content-rail-GAP:content;
        boolean showList=wide||selected()==null;
        sections(bounds.left(),bounds.y()+43,content,tab,Tab.values(),
                value -> message("tab."+value.name().toLowerCase(Locale.ROOT)),
                value -> { tab=value; selected=null; offset=0; page=0; read(); },
                button -> { addRenderableWidget(button); availability.add(() -> button.active=!busy()&&confirmation==null); },
                () -> !busy()&&confirmation==null);
        int searchWidth=Math.max(48,content-76-GAP);
        search=addRenderableWidget(field(font,bounds.left(),bounds.y()+69,searchWidth,message("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(message("search_hint")); search.setResponder(value -> query=value);
        search.setEditable(!busy()&&confirmation==null);
        control(bounds.left()+searchWidth+GAP,bounds.y()+69,content-searchWidth-GAP,!showList?text("selection_clear"):message("search"),() -> { if (!showList) { selected=null; rebuildWidgets(); } else { page=0; offset=0; selected=null; read(); } },() -> !busy()&&confirmation==null);
        actionsY=bounds.footerY()-CONTROL-GAP; listTop=bounds.y()+108;
        visible=Math.clamp((actionsY-listTop-GAP)/(CONTROL+2),1,PAGE_SIZE);
        Button confirmationButton=null;
        if (confirmation!=null) {
            addRenderableWidget(paragraph(font,bounds.left(),listTop,content-GAP,Math.max(CONTROL,actionsY-listTop-GAP),confirmationText));
            int w=(content-GAP)/2;
            confirmationButton=control(bounds.left(),bounds.footerY()-CONTROL-GAP,w,message("confirm"),this::confirm,() -> ready()&&canMutate(),ButtonStyle.DANGER);
            control(bounds.left()+w+GAP,bounds.footerY()-CONTROL-GAP,w,text("cancel"),() -> { confirmation=null; rebuildWidgets(); },() -> !busy());
        } else {
            if (showList&&state!=null&&!state.entries().isEmpty()) {
                offset=Math.clamp(offset,0,Math.max(0,state.entries().size()-1));
                for (int i=offset;i<Math.min(state.entries().size(),offset+visible);i++) {
                    Entry entry=state.entries().get(i);
                    Component label=entry.role()!=null?message("member",name(entry),role(entry.role()),message(entry.online()?"online":"offline"))
                            :entry.city()!=null?message("invitation",entry.cityName(),name(entry),entry.seconds()):message("online_player",name(entry));
                    var row=(RoyalButton)control(bounds.left(),listTop+(i-offset)*(CONTROL+2),listWidth,label,
                            () -> { selected=entry.id(); rebuildWidgets(); },() -> ready());
                    row.selected(entry.id().equals(selected));
                    row.setTooltip(Tooltip.create(label));
                }
            } else if (showList) addRenderableWidget(paragraph(font,bounds.left(),listTop,listWidth-GAP,Math.max(CONTROL,actionsY-listTop-GAP),
                    state==null?feedback:message(state.matches()==0&&!query.isBlank()?"no_matches":"empty."+tab.name().toLowerCase(Locale.ROOT))));
            actionLeft=wide?bounds.right()-rail:bounds.left(); actionWidth=rail;
            Entry chosen=selected();
            if (chosen!=null) {
                if (wide) addRenderableWidget(paragraph(font,actionLeft,listTop,rail-GAP,32,
                        name(chosen).copy().append(chosen.role()==null?Component.empty():Component.literal("\n").append(role(chosen.role())))));
                actionTop=wide?listTop+38:listTop;
                composingActions=true; actions(rail); composingActions=false;
                if (wide) control(actionLeft,bounds.footerY()-CONTROL-GAP,rail,text("selection_clear"),
                        () -> { selected=null; rebuildWidgets(); },() -> !busy());
            } else if (wide) {
                addRenderableWidget(paragraph(font,actionLeft,listTop,rail-GAP,Math.max(CONTROL,actionsY-listTop),text("select_entry")));
            }
        }
        int pagerY=bounds.footerY()-CONTROL-GAP;
        if (showList&&confirmation==null) control(bounds.left(),pagerY,54,message("previous"),() -> turn(-1),() -> !busy()&&confirmation==null&&(offset>0||page>0));
        if (showList&&confirmation==null) control(bounds.left()+listWidth-54,pagerY,54,message("next"),() -> turn(1),() -> !busy()&&confirmation==null&&state!=null&&(offset+visible<state.entries().size()||(page+1)*PAGE_SIZE<state.matches()));
        if (confirmation==null) control(bounds.left(),bounds.footerY(),70,text("back"),this::onClose,() -> true);
        if (confirmation==null) control(bounds.right()-76,bounds.footerY(),76,text("refresh"),this::read,() -> !busy()&&confirmation==null);
        availability.forEach(Runnable::run);
        setInitialFocus(search); // Never focus a destructive confirmation by default.
        if (initial) { initial=false; read(); }
    }
    private void actions(int width) {
        Entry entry=selected(); int w=(width-GAP*2)/3;
        switch (tab) {
            case MEMBERS -> {
                boolean officer=entry!=null&&entry.role()==CityRole.OFFICER;
                if (entry!=null&&entry.role()!=CityRole.OWNER&&state.manage()) {
                control(bounds.left(),actionsY,w,message(officer?"demote":"promote"),() -> ask(officer?Action.DEMOTE:Action.PROMOTE),
                        () -> mutable()&&state.manage()&&entry!=null&&(officer||entry.role()==CityRole.CITIZEN));
                control(bounds.left()+w+GAP,actionsY,w,message("kick"),() -> ask(Action.KICK),
                        () -> mutable()&&state.manage()&&entry!=null&&entry.role()!=CityRole.OWNER,ButtonStyle.DANGER);
                }
                if (state!=null&&state.leave()) control(bounds.right()-w,actionsY,w,message("leave"),() -> ask(Action.LEAVE),() -> mutable()&&state.leave(),ButtonStyle.DANGER);
            }
            case ONLINE -> { if (entry!=null) control(bounds.left(),actionsY,width,message("invite"),() -> mutate(Action.INVITE,entry.id(),state.token()),
                    () -> mutable()&&state.invite()&&entry!=null,ButtonStyle.PRIMARY); }
            case INBOX -> {
                if (entry==null) break;
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
                action==Action.LEAVE?"":"");
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
        invalidated=false; sent=0; received=System.currentTimeMillis(); confirmation=null; state=reply; tab=reply.tab(); page=reply.page();
        failed=!reply.result().isEmpty()&&!reply.success(); feedback=reply.result().isEmpty()?Component.empty():Component.translatable(reply.result());
        if (reply.success()) { selected=null; parent.invalidate(); }
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
    @Override public void onClose() {
        if (confirmation!=null) { confirmation=null; rebuildWidgets(); return; } parent.refreshOnOpen(); minecraft.setScreen(parent); }
    private Component summary() { return state==null||state.city()==null?message("no_city"):message("summary",state.cityName(),state.citizens()); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(summary()).append(". ").append(feedback)
            .append(". ").append(confirmation==null?Component.empty():confirmationText); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth());
        if (confirmation==null&&bounds.contentWidth()>=440&&height>=310) {
            int panelLeft=bounds.right()-174;
            panel(g,panelLeft-4,listTop-4,bounds.right()+4,bounds.footerY()-GAP);
        }
        Component summary=summary();
        g.drawString(font,ellipsize(font,summary,bounds.contentWidth()),bounds.left(),bounds.y()+27,INK,false);
        g.drawString(font,ellipsize(font,(bounds.contentWidth()<440||height<310)&&selected()!=null&&confirmation==null?name(selected()):feedback,bounds.contentWidth()),bounds.left(),bounds.y()+94,failed?ERROR:MUTED,false);
        if (state!=null) {
            int start=state.matches()==0?0:page*PAGE_SIZE+offset+1,end=Math.min(state.matches(),page*PAGE_SIZE+Math.min(state.entries().size(),offset+visible));
            Component paging=message("page",start,end,state.matches());
            int listWidth=bounds.contentWidth()>=440&&height>=310?bounds.contentWidth()-174-GAP:bounds.contentWidth();
            if (confirmation==null&&(bounds.contentWidth()>=440&&height>=310||selected()==null))
                g.drawCenteredString(font,ellipsize(font,paging,listWidth-120),bounds.left()+listWidth/2,bounds.footerY()-CONTROL-GAP+6,MUTED);
        }
        super.render(g,mx,my,delta);
        if (my>=bounds.y()+25&&my<bounds.y()+39&&font.width(summary)>bounds.contentWidth()) tooltip(g,font,summary,mx,my);
        if (my>=bounds.y()+92&&my<listTop&&font.width(feedback)>bounds.contentWidth()) tooltip(g,font,feedback,mx,my);
    }
}
