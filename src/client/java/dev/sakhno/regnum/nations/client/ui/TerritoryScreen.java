package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.networking.TerritoryPayloads.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;
import static dev.sakhno.regnum.nations.networking.TerritoryPayloads.PAGE_SIZE;

/** Territory folio: server snapshots, local adjacency preview and explicit destructive confirmation. */
public final class TerritoryScreen extends RoyalScreen {
    private final Screen parent;
    private State state;
    private Tab tab=Tab.LOCAL;
    private int page,sequence,offset,visible=PAGE_SIZE;
    private UUID city,kingdom,demand,confirmation;
    private DemandEntry selectedDemand;
    private ResourceLocation dimension;
    private int x,z,days=7;
    private boolean initial=true,queued,invalidated,failed;
    private long sent;
    private FolioBounds bounds;
    private Component feedback=text("loading");
    private final List<Runnable> availability=new ArrayList<>();
    public TerritoryScreen(Screen parent) { super(message("title")); this.parent=parent; }
    public TerritoryScreen(Screen parent,ResourceLocation dimension,int x,int z) { this(parent); this.dimension=dimension; this.x=x; this.z=z; }
    private static Component message(String key,Object... args) { return Component.translatable("territory.regnum-nations."+key,args); }
    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private boolean busy() { return sent!=0||queued; }
    private void button(int x,int y,int w,int h,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) {
        Button button=addRenderableWidget(new RoyalButton(x,y,w,label,b -> action.run(),style));
        button.setHeight(h);
        button.setTooltip(null); availability.add(() -> button.active=enabled.getAsBoolean());
    }
    private void button(int x,int y,int w,Component label,Runnable action,BooleanSupplier enabled,ButtonStyle style) { button(x,y,w,CONTROL,label,action,enabled,style); }
    @Override protected void init() {
        availability.clear(); bounds=folioBounds(width,height,760,480); int w=bounds.contentWidth(),quarter=(w-3*GAP)/4;
        for (Tab value:Tab.values()) button(bounds.left()+value.ordinal()*(quarter+GAP),bounds.y()+48,quarter,message("tab."+key(value)),() -> {
            tab=value; page=offset=0; selectedDemand=null; demand=null; confirmation=null; read();
        },() -> !busy()&&confirmation==null&&(value!=Tab.KINGDOM||state!=null&&state.kingdom()!=null),value==tab?ButtonStyle.PRIMARY:ButtonStyle.NORMAL);
        int top=bounds.y()+78,actionsY=bounds.footerY()-2*(CONTROL+GAP),bodyHeight=Math.max(15,actionsY-top-GAP);
        if (confirmation!=null) {
            addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,bodyHeight,message("confirm_abandon",dimension.toString(),x,z).copy().append("\n\n").append(details())));
        } else if (selectedDemand!=null) {
            DemandEntry d=selectedDemand;
            var description=message("demand_details",d.claimantName(),d.ownerName(),d.territory().cityName(),d.territory().dimension().toString(),d.territory().x(),d.territory().z(),date(d.createdAt()),d.expiresAt()==0?message("never"):Component.literal(date(d.expiresAt()))).copy().append("\n\n").append(message("political_only"));
            addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,bodyHeight,description));
        } else if (tab==Tab.LOCAL&&state!=null) {
            int size=Math.clamp(bodyHeight/7,8,26),grid=size*7,detailX=bounds.left()+grid+GAP;
            for (int i=0;i<state.cells().size();i++) {
                Cell c=state.cells().get(i); boolean chosen=c.dimension().equals(dimension)&&c.x()==x&&c.z()==z;
                String glyph=c.disputes()>0?"!":c.city()==null?(c.adjacent()?"+":"·"):Objects.equals(c.city(),state.city())?"◆":Objects.equals(c.kingdom(),state.kingdom())&&c.kingdom()!=null?"◇":"×";
                if (minecraft.player!=null&&minecraft.level!=null&&c.dimension().equals(minecraft.level.dimension().location())&&c.x()==minecraft.player.chunkPosition().x&&c.z()==minecraft.player.chunkPosition().z) glyph="@";
                Button tile=addRenderableWidget(new TileButton(bounds.left()+i%7*size,top+i/7*size,size,c,glyph,chosen));
                availability.add(() -> tile.active=!busy());
            }
            addRenderableWidget(paragraph(font,detailX,top,w-grid-GAP*2,bodyHeight,details()));
        } else if (state!=null) {
            visible=Math.clamp((bodyHeight-CONTROL-GAP)/(CONTROL+2),1,PAGE_SIZE);
            int total=tab==Tab.CITY?state.cells().size():tab==Tab.KINGDOM?state.cities().size():state.demands().size();
            offset=Math.clamp(offset,0,Math.max(0,total-1));
            for (int i=offset;i<Math.min(total,offset+visible);i++) {
                int row=top+(i-offset)*(CONTROL+2);
                if (tab==Tab.CITY) {
                    Cell c=state.cells().get(i);
                    button(bounds.left(),row,w,message("chunk_row",c.x(),c.z(),c.foreignEdges(),c.disputes()),() -> { tab=Tab.LOCAL; select(c); },() -> !busy(),ButtonStyle.NORMAL);
                } else if (tab==Tab.KINGDOM) {
                    CityEntry c=state.cities().get(i);
                    button(bounds.left(),row,w,message("city_row",c.name(),c.claims(),Math.max(0,state.limit()-c.claims()),c.borders(),c.disputes()),() -> { city=c.id(); tab=Tab.CITY; page=offset=0; read(); },() -> !busy(),ButtonStyle.NORMAL);
                } else {
                    DemandEntry d=state.demands().get(i);
                    button(bounds.left(),row,w,message("demand_row",d.claimantName(),d.ownerName(),d.territory().x(),d.territory().z()),() -> { selectedDemand=d; demand=d.id(); rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
                }
            }
            if (total==0) addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,Math.max(12,bodyHeight-CONTROL-GAP),message("empty")));
            int y=top+bodyHeight-CONTROL;
            button(bounds.left(),y,54,Component.literal("←"),() -> turn(-1,total),() -> !busy()&&(page>0||offset>0),ButtonStyle.NORMAL);
            button(bounds.right()-54,y,54,Component.literal("→"),() -> turn(1,total),() -> !busy()&&(offset+visible<total||(page+1)*PAGE_SIZE<state.matches()),ButtonStyle.NORMAL);
        }
        if (state==null&&confirmation==null&&selectedDemand==null) addRenderableWidget(paragraph(font,bounds.left(),top,w-GAP,bodyHeight,feedback));
        int third=(w-2*GAP)/3,half=(w-GAP)/2;
        if (confirmation!=null) {
            button(bounds.left(),actionsY,half,message("confirm"),() -> send(Action.ABANDON),() -> !busy()&&canMutate(),ButtonStyle.DANGER);
            button(bounds.left()+half+GAP,actionsY,half,text("cancel"),() -> { confirmation=null; read(); },() -> !busy(),ButtonStyle.NORMAL);
        } else if (selectedDemand!=null) {
            button(bounds.left(),actionsY,half,message("withdraw"),() -> send(Action.WITHDRAW),() -> !busy()&&selectedDemand.withdraw()&&canMutate(),ButtonStyle.DANGER);
            button(bounds.left()+half+GAP,actionsY,half,message("back"),() -> { selectedDemand=null; demand=null; rebuildWidgets(); },() -> !busy(),ButtonStyle.NORMAL);
        } else {
            button(bounds.left(),actionsY,third,message("expand"),() -> send(Action.EXPAND),() -> !busy()&&state!=null&&state.claimReason().isEmpty()&&standing()&&canMutate(),ButtonStyle.PRIMARY);
            button(bounds.left()+third+GAP,actionsY,third,message("abandon"),() -> send(Action.PREPARE_ABANDON),() -> !busy()&&state!=null&&state.abandonReason().isEmpty()&&standing()&&canMutate(),ButtonStyle.DANGER);
            button(bounds.left()+2*(third+GAP),actionsY,third,message("demand"),() -> send(Action.DEMAND),() -> !busy()&&state!=null&&state.politicalAllowed()&&!state.existingDemand()&&state.selection().kingdom()!=null&&!state.selection().kingdom().equals(state.kingdom())&&canMutate(),ButtonStyle.NORMAL);
        }
        int secondary=actionsY+CONTROL+GAP;
        button(bounds.left(),secondary,third,message("here"),() -> { city=null; current(); tab=Tab.LOCAL; confirmation=null; selectedDemand=null; read(); },() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left()+third+GAP,secondary,third,message("duration",days==0?message("never"):message("days",days)),() -> { days=switch(days) { case 7 -> 30; case 30 -> 90; case 90 -> 0; default -> 7; }; rebuildWidgets(); },() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left()+2*(third+GAP),secondary,third,message("map"),() -> minecraft.setScreen(new PoliticalMapScreen(null,false,this)),() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left(),bounds.footerY(),half,text("refresh"),this::read,() -> !busy()&&confirmation==null,ButtonStyle.NORMAL);
        button(bounds.left()+half+GAP,bounds.footerY(),half,text("close"),this::onClose,() -> true,ButtonStyle.NORMAL);
        availability.forEach(Runnable::run);
        if (initial) { initial=false; if (dimension==null) current(); read(); }
    }
    private final class TileButton extends RoyalButton {
        private final String glyph;
        TileButton(int x,int y,int size,Cell c,String glyph,boolean chosen) {
            super(x,y,size,message("selection",c.dimension().toString(),c.x(),c.z(),message(c.city()==null?"wilderness":"city_territory"),c.cityName(),c.kingdomName(),c.foreignEdges(),c.disputes()),b -> select(c),c.disputes()>0?ButtonStyle.DANGER:chosen?ButtonStyle.PRIMARY:ButtonStyle.NORMAL);
            this.glyph=glyph; setHeight(size); selected(chosen); setTooltip(null);
        }
        @Override protected void renderContent(GuiGraphics g,int color,int offset) {
            if (getWidth()<12) g.fill(getX()+3,getY()+3,getRight()-3,getBottom()-3,color);
            else g.drawString(font,glyph,getX()+(getWidth()-font.width(glyph))/2+offset,getY()+(getHeight()-font.lineHeight)/2+offset,color,false);
        }
    }
    private boolean standing() { return minecraft.player!=null&&minecraft.level!=null&&minecraft.level.dimension().location().equals(dimension)&&minecraft.player.chunkPosition().x==x&&minecraft.player.chunkPosition().z==z; }
    private void current() { if (minecraft.player!=null&&minecraft.level!=null) { dimension=minecraft.level.dimension().location(); x=minecraft.player.chunkPosition().x; z=minecraft.player.chunkPosition().z; } }
    private void select(Cell c) { dimension=c.dimension(); x=c.x(); z=c.z(); confirmation=null; read(); }
    private Component details() {
        if (state==null) return text("loading"); Cell c=state.selection();
        String type=c.city()==null?"wilderness":Objects.equals(c.city(),state.city())||c.kingdom()!=null&&c.kingdom().equals(state.kingdom())?"city_territory":"foreign_territory";
        var detail=message("summary",state.cityName(),state.cityClaims(),state.limit(),Math.max(0,state.limit()-state.cityClaims()),state.kingdomName(),state.kingdomClaims()).copy().append("\n\n").append(message("selection",c.dimension().toString(),c.x(),c.z(),message(type),c.cityName(),c.kingdomName(),c.foreignEdges(),c.disputes()));
        detail.append("\n").append(message("adjacency",message(c.adjacent()?"yes":"no")));
        detail.append("\n\n").append(message("expansion_status",state.claimReason().isEmpty()?message(standing()?"ready":"stand_here"):Component.translatable(state.claimReason())));
        detail.append("\n").append(message("abandon_status",state.abandonReason().isEmpty()?message(standing()?"ready":"stand_here"):Component.translatable(state.abandonReason())));
        boolean nearDispute=state.cells().stream().anyMatch(n -> n.disputes()>0&&n.dimension().equals(c.dimension())&&Math.abs((long)n.x()-c.x())+Math.abs((long)n.z()-c.z())<=1);
        if (c.foreignEdges()>0||c.disputes()>0||nearDispute) detail.append("\n\n").append(message("disputed_warning"));
        if (state.existingDemand()) detail.append("\n").append(message("already_demanded"));
        detail.append("\n\n").append(message("legend")); return detail;
    }
    private static String date(long millis) { return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(millis)); }
    private void turn(int direction,int total) {
        if (direction<0&&offset>0) { offset=Math.max(0,offset-visible); rebuildWidgets(); }
        else if (direction>0&&offset+visible<total) { offset+=visible; rebuildWidgets(); }
        else { page=Math.max(0,page+direction); offset=0; read(); }
    }
    private void read() { if (!canRead()) { queued=true; return; } queued=false; readSent(); send(Action.VIEW); }
    private void send(Action action) {
        if (dimension==null||!ClientPlayNetworking.canSend(Request.TYPE)) { feedback=text("unavailable"); failed=true; return; }
        if (action!=Action.VIEW) mutationSent(); sequence=nextSequence(); sent=System.currentTimeMillis(); failed=false;
        ClientPlayNetworking.send(new Request(sequence,action,tab,page,city,kingdom,dimension,x,z,demand,confirmation,days));
        availability.forEach(Runnable::run);
    }
    public void accept(State reply) {
        if (sent==0||sequence!=reply.sequence()) return;
        sent=0; queued=false; invalidated=false; failed=!reply.result().isEmpty()&&!reply.success(); confirmation=reply.confirmation();
        if (!failed) { state=reply; city=reply.city(); kingdom=reply.kingdom(); page=reply.page(); dimension=reply.selection().dimension(); x=reply.selection().x(); z=reply.selection().z(); }
        else { state=null; selectedDemand=null; }
        if (reply.success()&&confirmation==null) { demand=null; selectedDemand=null; }
        feedback=reply.result().isEmpty()?message("preview"):Component.translatable(reply.result()); rebuildWidgets();
    }
    public void invalidate() { invalidated=true; confirmation=null; selectedDemand=null; demand=null; state=null; if (minecraft!=null&&minecraft.screen==this) rebuildWidgets(); }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (invalidated&&sent==0) { invalidated=false; read(); }
        if (queued&&canRead()) read();
        if (sent!=0&&System.currentTimeMillis()-sent>10000) { sent=0; queued=false; state=null; confirmation=null; failed=true; feedback=text("timeout"); rebuildWidgets(); }
        availability.forEach(Runnable::run);
    }
    @Override public void onClose() {
        if (parent instanceof KingdomManagementScreen menu) menu.refreshOnOpen();
        else if (parent instanceof PoliticalMapScreen map) map.invalidate();
        minecraft.setScreen(parent);
    }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(feedback); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+12,bounds.contentWidth());
        Component summary=state==null?feedback:message("summary",state.cityName(),state.cityClaims(),state.limit(),Math.max(0,state.limit()-state.cityClaims()),state.kingdomName(),state.kingdomClaims());
        g.drawString(font,ellipsize(font,summary,bounds.contentWidth()),bounds.left(),bounds.y()+28,INK,false);
        g.drawString(font,ellipsize(font,feedback,bounds.contentWidth()),bounds.left(),bounds.y()+39,failed?ERROR:MUTED,false);
        if (state!=null&&tab!=Tab.LOCAL&&selectedDemand==null&&confirmation==null) {
            int top=bounds.y()+78,actionsY=bounds.footerY()-2*(CONTROL+GAP),bodyHeight=Math.max(15,actionsY-top-GAP);
            g.drawCenteredString(font,message("page",page+1,Math.max(1,(state.matches()+PAGE_SIZE-1)/PAGE_SIZE)),bounds.left()+bounds.contentWidth()/2,top+bodyHeight-CONTROL+6,MUTED);
        }
        super.render(g,mx,my,delta);
    }
}
