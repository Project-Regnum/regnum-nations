package dev.sakhno.reigns.client.ui;

import dev.sakhno.reigns.kingdom.KingdomName;
import dev.sakhno.reigns.kingdom.PoliticalColors;
import dev.sakhno.reigns.networking.KingdomManagementPayloads.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.network.chat.Component;
import static dev.sakhno.reigns.client.ui.KingdomUi.*;

/** Explicit confirmation shared by native city, city elevation and legacy combined founding. */
public final class KingdomFoundingScreen extends RoyalScreen {
    private static final int MAX_WIDTH=480,MAX_HEIGHT=330,FORM_HEIGHT=118;
    private static final int INPUT_Y=12,VALIDATION_Y=36,COLOR_LABEL_Y=50,SWATCH_Y=62,PREVIEW_Y=90;
    private final UUID obelisk;
    private final boolean city;
    private final List<Button> palette = new ArrayList<>();
    private EditBox name;
    private Button confirm;
    private FittingMultiLineTextWidget explanation;
    private int left,top,panelWidth,panelHeight,formY,sequence,color,titleScale,headerHeight;
    private FolioBounds bounds;
    private String displayTitle,documentLabel,previewLabel;
    private long sent;
    private boolean attempted,validName;
    private String input = "";
    private Component feedback = Component.empty();
    private Component validation = text("name_rules");
    public KingdomFoundingScreen(UUID obelisk) { this(obelisk,false); }
    public KingdomFoundingScreen(UUID obelisk,boolean city) {
        super(text(city?"city_founding_title":"founding_title")); this.obelisk=obelisk; this.city=city;
    }
    private Component context() {
        if (attempted) return sent!=0?feedback:feedback.copy().append("\n").append(text("reselect"));
        return text(city?"city_founding_description":"state_founding_description").copy().append("\n").append(text("domain_slot_note"));
    }
    @Override protected void init() {
        bounds=folioBounds(width,height,MAX_WIDTH,MAX_HEIGHT);
        panelWidth=bounds.width(); panelHeight=bounds.height(); left=bounds.x(); top=bounds.y();
        // Pin the interactive form and footer; only descriptive prose gives up space.
        formY = bounds.footerY()-FORM_HEIGHT;
        titleScale=panelWidth>=400&&panelHeight>=280?2:1;
        headerHeight=titleScale==2?64:44;
        displayTitle=ellipsize(font,title,(bounds.contentWidth()-48)/titleScale);
        documentLabel=ellipsize(font,text("atlas_founding_document"),bounds.contentWidth()-48);
        previewLabel=ellipsize(font,text("preview_only"),bounds.contentWidth()-30);
        explanation = addRenderableWidget(paragraph(font,bounds.left(),top+headerHeight+GAP,bounds.contentWidth()-GAP,
                Math.max(16,formY-headerHeight-GAP*2-top),context()));
        explanation.setColor(attempted&&sent==0?ERROR:MUTED);
        name = addRenderableWidget(field(font,bounds.left(),formY+INPUT_Y,bounds.contentWidth(),text(city?"city_name":"kingdom_name")));
        name.setMaxLength(128); name.setValue(input); name.setEditable(!attempted); name.setHint(text("name_hint"));
        name.setResponder(this::validateName);
        palette.clear();
        int size = (bounds.contentWidth()-GAP*(PoliticalColors.COUNT-1))/PoliticalColors.COUNT;
        int remainder=(bounds.contentWidth()-GAP*(PoliticalColors.COUNT-1))%PoliticalColors.COUNT;
        int swatchX=bounds.left();
        for (int i=0;i<PoliticalColors.COUNT;i++) {
            int swatchWidth=size+(i<remainder?1:0);
            Button swatch = addRenderableWidget(new ColorSwatch(swatchX,formY+SWATCH_Y,swatchWidth,i,() -> color,chosen -> {
                color=chosen; triggerImmediateNarration(false);
            }));
            palette.add(swatch); swatch.active=!attempted;
            swatchX+=swatchWidth+GAP;
        }
        int buttonWidth=(bounds.contentWidth()-GAP)/2,buttonY=bounds.footerY();
        confirm = addRenderableWidget(new RoyalButton(bounds.left(),buttonY,buttonWidth,text(city?"confirm_city":"confirm_found"),b -> submit(),ButtonStyle.PRIMARY));
        addRenderableWidget(new RoyalButton(bounds.right()-buttonWidth,buttonY,buttonWidth,text(attempted?"close":"cancel"),b -> onClose()));
        validateName(input);
        setInitialFocus(attempted?explanation:name);
    }
    private void validateName(String value) {
        input=value; validName=false;
        try { new KingdomName(value); validName=true; }
        catch (IllegalArgumentException ignored) { }
        validation=text(validName?"name_valid":"name_rules");
        if (confirm!=null) confirm.active=!attempted&&validName;
    }
    private void showFeedback(Component message) {
        feedback=message;
        // The native text widget caches its layout at construction; rebuilding preserves form state.
        rebuildWidgets(); triggerImmediateNarration(false);
    }
    private void submit() {
        if (attempted||!validName) return;
        attempted=true;
        if (!ClientPlayNetworking.canSend(Found.TYPE)) { showFeedback(text("unavailable")); return; }
        mutationSent(); sequence=nextSequence(); sent=System.currentTimeMillis();
        showFeedback(text("working"));
        ClientPlayNetworking.send(new Found(sequence,obelisk,input,color,city));
    }
    public void accept(State state) {
        if (!attempted||state.sequence()!=sequence) return;
        sent=0;
        if (state.success()&&state.city()!=null) minecraft.setScreen(new KingdomManagementScreen(state));
        else showFeedback(Component.translatable(state.result()));
    }
    @Override public void tick() {
        if (minecraft.player==null||minecraft.level==null) { onClose(); return; }
        if (sent!=0&&System.currentTimeMillis()-sent>10000) { sent=0; showFeedback(text("timeout")); }
    }
    @Override public Component getNarrationMessage() {
        return title.copy().append(". ").append(context()).append(". ").append(validation).append(". ").append(text("color_preview",text("color_"+color)));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,BACKGROUND);
        AtlasGraphics.folio(g,left,top,panelWidth,panelHeight);
        AtlasGraphics.civicSeal(g,bounds.left(),top+10,PoliticalColors.rgb(color));
        g.drawString(font,documentLabel,bounds.left()+48,top+12,AtlasGraphics.LIGHT,false);
        g.pose().pushPose(); g.pose().translate(bounds.left()+48,top+27,0); g.pose().scale(titleScale,titleScale,1);
        g.drawString(font,displayTitle,0,0,INK,false); g.pose().popPose();
        AtlasGraphics.divider(g,bounds.left(),top+headerHeight,bounds.contentWidth());
        heading(g,font,text(city?"city_name":"kingdom_name"),bounds.left(),formY,bounds.contentWidth());
        g.drawString(font,ellipsize(font,validation,bounds.contentWidth()),bounds.left(),formY+VALIDATION_Y,validName||input.isBlank()?MUTED:ERROR,false);
        heading(g,font,text("political_color"),bounds.left(),formY+COLOR_LABEL_Y,bounds.contentWidth());
        int previewY=formY+PREVIEW_Y;
        g.fill(bounds.left(),previewY,bounds.left()+22,previewY+22,BORDER);
        g.fill(bounds.left()+1,previewY+1,bounds.left()+21,previewY+21,PoliticalColors.map(color));
        AtlasGraphics.sprite(g,bounds.left()+3,previewY+3,64,32,16,16,1);
        g.drawString(font,ellipsize(font,text("color_preview",text("color_"+color)),bounds.contentWidth()-30),bounds.left()+30,previewY+1,INK,false);
        g.drawString(font,previewLabel,bounds.left()+30,previewY+12,MUTED,false);
        super.render(g,mx,my,delta);
        if (font.width(title)>(bounds.contentWidth()-48)/titleScale&&mx>=bounds.left()+48&&mx<bounds.right()&&my>=top+24&&my<top+headerHeight)
            tooltip(g,font,title,mx,my);
    }
}
