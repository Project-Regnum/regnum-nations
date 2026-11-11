package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.networking.ObeliskRelocationPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Small confirmation form using the existing medieval fields, folio and buttons. */
public final class ObeliskRelocationScreen extends RoyalScreen {
    private final KingdomManagementScreen parent;
    private final UUID target;
    private final boolean state;
    private FolioBounds bounds;
    private EditBox x,y,z;
    private Button select,confirm;
    private UUID token;
    private int sequence;
    private long sent;
    private boolean complete;
    private Component feedback=message("description");
    private String[] coordinates;

    public ObeliskRelocationScreen(KingdomManagementScreen parent,UUID target,boolean state) {
        super(message("title")); this.parent=parent; this.target=target; this.state=state;
    }
    private static Component message(String key) { return Component.translatable("relocation.regnum-nations."+key); }
    @Override protected void init() {
        bounds=folioBounds(width,height,480,270);
        if (coordinates==null) {
            BlockPos pos=minecraft.player.blockPosition();
            coordinates=new String[]{Integer.toString(pos.getX()),Integer.toString(pos.getY()),Integer.toString(pos.getZ())};
        }
        int w=(bounds.contentWidth()-GAP*2)/3;
        EditBox[] fields=new EditBox[3];
        for (int i=0;i<3;i++) {
            final int axis=i;
            fields[i]=addRenderableWidget(field(font,bounds.left()+i*(w+GAP),bounds.y()+126,w,Component.literal(new String[]{"X","Y","Z"}[i])));
            fields[i].setMaxLength(11); fields[i].setValue(coordinates[i]);
            fields[i].setResponder(value -> { coordinates[axis]=value; token=null; });
            fields[i].setEditable(sent==0&&!complete&&token==null);
        }
        x=fields[0]; y=fields[1]; z=fields[2];
        addRenderableWidget(paragraph(font,bounds.left(),bounds.y()+38,bounds.contentWidth()-GAP,70,feedback));
        int actionWidth=(bounds.contentWidth()-GAP*2)/3;
        select=addRenderableWidget(new RoyalButton(bounds.left(),bounds.footerY(),actionWidth,message("select"),b -> send(false)));
        confirm=addRenderableWidget(new RoyalButton(bounds.left()+actionWidth+GAP,bounds.footerY(),actionWidth,message("confirm"),b -> send(true),ButtonStyle.PRIMARY));
        addRenderableWidget(new RoyalButton(bounds.right()-actionWidth,bounds.footerY(),actionWidth,text("close"),b -> onClose()));
        setInitialFocus(complete?select:token==null?x:confirm);
    }
    private BlockPos destination() { return new BlockPos(Integer.parseInt(x.getValue()),Integer.parseInt(y.getValue()),Integer.parseInt(z.getValue())); }
    private void send(boolean commit) {
        if (sent!=0||complete||commit&&token==null) return;
        if (!ClientPlayNetworking.canSend(Request.TYPE)) { feedback=text("unavailable"); rebuildWidgets(); return; }
        try {
            BlockPos destination=destination();
            if (!commit) token=null;
            sequence=nextSequence(); sent=System.currentTimeMillis(); mutationSent(); feedback=text("working");
            ClientPlayNetworking.send(new Request(sequence,target,state,minecraft.level.dimension().location(),destination,commit?token:null));
            rebuildWidgets();
        } catch (NumberFormatException e) { feedback=Component.translatable("message.regnum-nations.founding.relocation_destination"); rebuildWidgets(); }
    }
    public void accept(Reply reply) {
        if (sent==0||reply.sequence()!=sequence) return;
        sent=0; token=reply.token(); complete=reply.success()&&token==null;
        feedback=Component.translatable(reply.result()); rebuildWidgets();
        if (complete) parent.invalidate();
    }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (sent!=0&&System.currentTimeMillis()-sent>10000) { sent=0; token=null; feedback=text("timeout"); rebuildWidgets(); }
        select.active=sent==0&&!complete&&token==null&&canMutate();
        confirm.active=sent==0&&!complete&&token!=null&&canMutate();
    }
    @Override public void onClose() { parent.refreshOnOpen(); minecraft.setScreen(parent); }
    @Override public Component getNarrationMessage() { return title.copy().append(". ").append(feedback); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND); AtlasGraphics.folio(g,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        heading(g,font,title,bounds.left(),bounds.y()+16,bounds.contentWidth());
        int w=(bounds.contentWidth()-GAP*2)/3;
        for (int i=0;i<3;i++) g.drawString(font,new String[]{"X","Y","Z"}[i],bounds.left()+i*(w+GAP),bounds.y()+112,MUTED,false);
        super.render(g,mx,my,delta);
    }
}
