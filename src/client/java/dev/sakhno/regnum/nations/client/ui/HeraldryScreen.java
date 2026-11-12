package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.client.heraldry.*;
import dev.sakhno.regnum.nations.kingdom.Heraldry;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import dev.sakhno.regnum.nations.networking.HeraldryPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** A local draft; only a fresh server-authorized response can commit it. Escape always discards. */
public final class HeraldryScreen extends RoyalScreen {
    private final Screen parent;
    private UUID kingdom;
    private Heraldry draft;
    private State state;
    private FolioBounds bounds;
    private final List<Button> mutationButtons=new ArrayList<>();
    private int page,slot,sequence;
    private boolean initial=true,pendingRead,feedbackPriority,failed;
    private long sent;
    private Action lastAction=Action.VIEW;
    private Component feedback=message("loading");
    public HeraldryScreen(Screen parent,UUID kingdom) { super(message("title")); this.parent=parent; this.kingdom=kingdom; }
    public static Component message(String key,Object... args) { return Component.translatable("heraldry.regnum-nations."+key,args); }
    private static Component option(String category,Named value) { return message(category+"."+value.id()); }
    private boolean editable() { return sent==0&&!pendingRead&&state!=null&&state.editable()&&state.token()!=null; }
    private Button button(int x,int y,int w,Component label,Runnable action,boolean mutation) {
        var b=addRenderableWidget(new RoyalButton(x,y,w,label,ignored -> action.run())); b.setTooltip(null);
        if (mutation) { b.active=editable(); mutationButtons.add(b); } return b;
    }
    @Override protected void init() {
        mutationButtons.clear(); bounds=folioBounds(width,height,660,380);
        int content=bounds.contentWidth(),third=(content-2*GAP)/3;
        for (int i=0;i<3;i++) {
            int tab=i; var button=button(bounds.left()+i*(third+GAP),bounds.y()+34,third,message("tab."+i),() -> { page=tab; rebuildKeepingFocus(); },false);
            ((RoyalButton)button).selected(page==i);
        }
        int preview=Math.min(150,Math.max(70,content/3)),x=bounds.left()+preview+GAP,w=bounds.right()-x;
        int y=bounds.y()+61;
        int step=Math.clamp((bounds.footerY()-CONTROL-GAP-18-y-CONTROL)/3,22,42);
        if (draft!=null) {
            if (page==0) {
                button(x,y,w,message("choice",message("shield"),option("shield",draft.shield())),() -> change(next(Shield.values(),draft.shield()),null,null,null,null,null,null),true);
                button(x,y+step,w,message("choice",message("division"),option("division",draft.division())),() -> change(null,null,next(Division.values(),draft.division()),null,null,null,null),true);
                button(x,y+step*2,w,message("choice",message("ordinary"),option("ordinary",draft.ordinary())),() -> change(null,null,null,null,next(Ordinary.values(),draft.ordinary()),null,null),true);
                button(x,y+step*3,w,message("arrangement."+draft.charges().size()),this::arrange,true);
            } else if (page==1) {
                button(x,y,w,message("choice",message("field"),option("tincture",draft.field())),() -> change(null,next(Tincture.values(),draft.field()),null,null,null,null,null),true);
                button(x,y+step,w,message("choice",message("secondary"),option("tincture",draft.secondary())),() -> change(null,null,null,next(Tincture.values(),draft.secondary()),null,null,null),true);
                button(x,y+step*2,w,message("choice",message("ordinary"),option("tincture",draft.ordinaryTincture())),() -> change(null,null,null,null,null,next(Tincture.values(),draft.ordinaryTincture()),null),true);
                if (!draft.charges().isEmpty()) button(x,y+step*3,w,message("choice",message("charge_tincture",slot+1),option("tincture",charge().tincture())),() -> editCharge(charge().emblem(),next(Tincture.values(),charge().tincture()),charge().scale(),charge().rotation(),charge().mirrored()),true);
            } else if (!draft.charges().isEmpty()) {
                button(x,y,w,message("charge_slot",slot+1,draft.charges().size()),() -> { slot=(slot+1)%draft.charges().size(); rebuildKeepingFocus(); },true);
                button(x,y+step,w,message("choice",message("charge"),option("charge",charge().emblem())),() -> editCharge(next(Emblem.values(),charge().emblem()),charge().tincture(),charge().scale(),charge().rotation(),charge().mirrored()),true);
                int half=(w-GAP)/2;
                button(x,y+step*2,half,message("rotate",charge().rotation()*90),() -> editCharge(charge().emblem(),charge().tincture(),charge().scale(),(charge().rotation()+1)%4,charge().mirrored()),true);
                button(x+half+GAP,y+step*2,w-half-GAP,message(charge().mirrored()?"mirrored":"mirror"),() -> editCharge(charge().emblem(),charge().tincture(),charge().scale(),charge().rotation(),!charge().mirrored()),true);
                button(x,y+step*3,w,message("scale",charge().scale()),() -> editCharge(charge().emblem(),charge().tincture(),charge().scale()>=75?25:charge().scale()+10,charge().rotation(),charge().mirrored()),true);
            } else addRenderableWidget(paragraph(font,x,y,w,70,message("no_charges")));
        }
        int actions=bounds.footerY()-CONTROL-GAP;
        button(bounds.left(),actions,third,message("reset"),() -> { draft=Heraldry.defaults(kingdom); slot=0; editing(); rebuildKeepingFocus(); },true);
        button(bounds.left()+third+GAP,actions,third,message("randomize"),this::randomize,true);
        button(bounds.left()+2*(third+GAP),actions,content-2*(third+GAP),message("seal"),() -> send(Action.SEAL),true);
        int half=(content-GAP)/2;
        button(bounds.left(),bounds.footerY(),half,message("save"),() -> send(Action.SAVE),true);
        button(bounds.left()+half+GAP,bounds.footerY(),content-half-GAP,text("cancel"),this::onClose,false);
        if (initial) { initial=false; pendingRead=true; }
    }
    private void rebuildKeepingFocus() {
        int x=getFocused() instanceof AbstractWidget widget?widget.getX():-1;
        int y=getFocused() instanceof AbstractWidget widget?widget.getY():-1;
        rebuildWidgets();
        for (var child:children()) if (child instanceof AbstractWidget widget&&widget.getX()==x&&widget.getY()==y&&widget.active) {
            setFocused(widget); break;
        }
    }
    private static <T> T next(T[] values,T current) { return values[(Arrays.asList(values).indexOf(current)+1)%values.length]; }
    private Charge charge() { slot=Math.clamp(slot,0,draft.charges().size()-1); return draft.charges().get(slot); }
    private void editing() { feedbackPriority=false; failed=false; feedback=message("ready"); }
    private void change(Shield shield,Tincture field,Division division,Tincture secondary,Ordinary ordinary,Tincture ordinaryTincture,List<Charge> charges) {
        draft=new Heraldry(1,shield==null?draft.shield():shield,field==null?draft.field():field,division==null?draft.division():division,
                secondary==null?draft.secondary():secondary,ordinary==null?draft.ordinary():ordinary,ordinaryTincture==null?draft.ordinaryTincture():ordinaryTincture,charges==null?draft.charges():charges);
        editing(); rebuildKeepingFocus();
    }
    private void editCharge(Emblem emblem,Tincture tincture,int size,int rotation,boolean mirrored) {
        var list=new ArrayList<>(draft.charges()); var old=charge(); list.set(slot,new Charge(emblem,tincture,old.x(),old.y(),size,rotation,mirrored));
        change(null,null,null,null,null,null,list);
    }
    private void arrange() {
        int count=(draft.charges().size()+1)%4; var list=new ArrayList<Charge>();
        var base=draft.charges().isEmpty()?Heraldry.defaults(kingdom).charges().getFirst():draft.charges().getFirst();
        for (int i=0;i<count;i++) {
            var old=i<draft.charges().size()?draft.charges().get(i):base;
            int x=count==1||i==2?50:i==0?30:70,y=count==1?46:count==2?44:i==2?66:30;
            list.add(new Charge(old.emblem(),old.tincture(),x,y,count==1?65:38,old.rotation(),old.mirrored()));
        }
        slot=0; change(null,null,null,null,null,null,list);
    }
    private void randomize() {
        var r=new Random(); var base=Heraldry.defaults(UUID.randomUUID());
        draft=new Heraldry(1,base.shield(),base.field(),Division.values()[r.nextInt(6)],r.nextBoolean()?Tincture.OR:Tincture.ARGENT,
                Ordinary.values()[r.nextInt(7)],Tincture.ARGENT,base.charges()); slot=0; editing(); rebuildKeepingFocus();
    }
    private void send(Action action) {
        if (sent!=0) return;
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { pendingRead=false; feedback=text("unavailable"); return; }
        if (action!=Action.VIEW&&!editable()) return;
        pendingRead=false; lastAction=action; sequence=nextSequence(); sent=System.currentTimeMillis();
        if (action!=Action.VIEW||!failed) { feedback=text("working"); feedbackPriority=true; failed=false; }
        if (action==Action.VIEW) readSent(); else mutationSent();
        ClientPlayNetworking.send(new Request(sequence,action,kingdom,state==null?null:state.token(),action==Action.SAVE?draft:null));
        mutationButtons.forEach(b -> b.active=false);
    }
    public void accept(State reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        sent=0; pendingRead=false;
        if (reply.arms()!=null) {
            kingdom=reply.kingdom(); state=reply;
            if (draft==null||reply.success()&&reply.result().equals("heraldry.regnum-nations.saved")) draft=reply.arms();
        } else { state=null; pendingRead=lastAction!=Action.VIEW; }
        if (!(lastAction==Action.VIEW&&failed&&reply.result().isEmpty())) {
            feedback=reply.result().isEmpty()?message(reply.editable()?"ready":"read_only"):Component.translatable(reply.result());
            feedbackPriority=!reply.result().isEmpty(); failed=!reply.result().isEmpty()&&!reply.success();
        }
        rebuildKeepingFocus(); triggerImmediateNarration(false);
    }
    public void invalidate() { sequence=nextSequence(); sent=0; state=null; pendingRead=true; rebuildKeepingFocus(); }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { minecraft.setScreen(null); return; }
        if (pendingRead&&sent==0&&canRead()) send(Action.VIEW);
        if (sent!=0&&System.currentTimeMillis()-sent>10000) { sent=0; state=null; pendingRead=true; feedback=text("timeout"); feedbackPriority=true; failed=true; }
        mutationButtons.forEach(b -> b.active=editable()&&canMutate());
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(state==null?Component.empty():Component.literal(state.name())).append(". ").append(feedback).append(". ").append(draft!=null&&draft.tinctureWarning()?message("warning"):message("contrast")); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,state==null?title:message("armorial",state.name()),bounds.left(),bounds.y()+12,bounds.contentWidth());
        int preview=Math.min(150,Math.max(70,bounds.contentWidth()/3)),h=Math.min(preview*5/4,bounds.footerY()-CONTROL-GAP-(bounds.y()+61)-16);
        if (draft!=null) {
            int px=bounds.left()+(preview-h*4/5)/2,py=bounds.y()+61;
            g.fill(px-3,py-3,px+h*4/5+3,py+h+3,AtlasGraphics.SHADOW);
            HeraldryTextures.draw(g,draft,px,py,h*4/5,h);
        }
        int feedbackY=bounds.footerY()-CONTROL-GAP-12;
        boolean warning=draft!=null&&draft.tinctureWarning()&&!feedbackPriority;
        Component status=warning?message("warning"):feedback;
        g.drawString(font,ellipsize(font,status,bounds.contentWidth()),bounds.left(),feedbackY,failed?ERROR:warning?0xffaa6a35:MUTED,false);
        super.render(g,mx,my,delta);
        if (mx>=bounds.left()&&mx<bounds.right()&&my>=feedbackY&&my<feedbackY+LINE) tooltip(g,font,status,mx,my);
    }
}
