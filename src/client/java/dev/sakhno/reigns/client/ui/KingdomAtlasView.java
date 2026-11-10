package dev.sakhno.reigns.client.ui;

import dev.sakhno.reigns.kingdom.PoliticalColors;
import dev.sakhno.reigns.networking.KingdomManagementPayloads.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.reigns.client.ui.KingdomUi.*;

/** A composed, read-only kingdom folio inside the existing management/network lifecycle. */
final class KingdomAtlasView {
    private static final int MAX_WIDTH=620,MAX_HEIGHT=286,NAV_Y=13;
    private static final int COMPACT_HEADER=110,WIDE_HEADER=158;
    private final Font font;
    private final Overview kingdom;
    private final int x,y,width,height,headerBottom,textX,textWidth,bannerX,bannerY,bannerScale,color,bodyY,bodyWidth,column;
    private final boolean expanded;
    private final FolioBounds bounds;
    private final int pad,feedbackX,feedbackWidth;
    private final int eyebrowY,nameY,rulerY,countScale;
    private final AtlasGraphics.Heraldry heraldry;
    private final Component name,ruler,capital,access,sealHint,status,recordLabel,claimsHint,empty;
    private final String displayName,displayRuler,displayCapital,displayAccess,displayCount,displayAnchor,dominionLabel,anchorLabel,realmLabel,colorLabel,claimedLabel;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    final Button refresh;
    final Button initialFocus;
    final Button disclosure;
    final Readout record;

    KingdomAtlasView(Font font,int screenWidth,int screenHeight,State state,boolean expanded,
                     Runnable refreshAction,Runnable closeAction,IntConsumer navigate,Runnable toggle,boolean loading,boolean failed) {
        this.font=font; this.expanded=expanded;
        kingdom=state==null?null:state.overview();
        bounds=folioBounds(screenWidth,screenHeight,MAX_WIDTH,screenWidth-MARGIN*2>=500?MAX_HEIGHT:256);
        width=bounds.width(); height=bounds.height(); x=bounds.x(); y=bounds.y(); pad=bounds.pad();
        boolean wide=width>=500&&bounds.contentBottom()>=y+WIDE_HEADER+PAD+49;
        bannerScale=wide?2:1; bannerX=x+(wide?pad+4:pad); bannerY=y+40;
        headerBottom=y+(wide?WIDE_HEADER:COMPACT_HEADER);
        textX=x+pad+(wide?94:50); textWidth=bounds.right()-textX;
        eyebrowY=y+(wide?50:42); nameY=eyebrowY+14; rulerY=eyebrowY+(wide?42:36);
        bodyY=headerBottom+(wide?PAD:GAP); bodyWidth=bounds.contentWidth(); column=bodyWidth/2;
        color=kingdom==null?AtlasGraphics.ETCH:PoliticalColors.rgb(kingdom.color());
        heraldry=kingdom==null?null:AtlasGraphics.Heraldry.of(kingdom.id());
        name=kingdom==null?text("atlas_no_realm"):Component.literal(kingdom.name());
        ruler=kingdom==null||kingdom.founder().isEmpty()?Component.empty():text("atlas_ruled_by",kingdom.founder());
        capital=kingdom==null?Component.empty():text("atlas_capital_in",dimension(kingdom.dimension()));
        access=kingdom==null?text("atlas_independent"):text(kingdom.manager()?"atlas_ruler_access":state.ownKingdom()!=null&&state.ownKingdom().equals(kingdom.id())?"atlas_affiliated_access":"visitor_access");
        status=kingdom==null?Component.empty():Component.translatable("message.project-reigns.capital."+kingdom.status());
        sealHint=kingdom==null?text("atlas_no_realm"):text("atlas_seal",text("atlas_charge_"+heraldry.charge()),text("color_"+kingdom.color()));
        claimsHint=kingdom==null?Component.empty():text("kingdom_claim_count",kingdom.claims());
        empty=loading?text("loading"):failed?text("view_error"):state==null||state.city()==null?text("no_city"):text(state.city().manager()?"city_state_hint":"city_state_visitor_hint");
        recordLabel=text(expanded?"atlas_record_close":"atlas_record_open");
        displayName=ellipsize(font,name,textWidth/2);
        displayRuler=ellipsize(font,ruler,textWidth);
        displayCapital=ellipsize(font,capital,textWidth);
        displayAccess=ellipsize(font,access,textWidth);
        displayCount=kingdom==null?"":Long.toString(kingdom.claims());
        countScale=font.width(displayCount)*2<=column-16?2:1;
        Component anchorStatus=kingdom==null?Component.empty():text(switch(kingdom.status()) {
            case "present" -> "atlas_anchor_present"; case "missing" -> "atlas_anchor_missing"; default -> "atlas_anchor_unresolved";
        });
        displayAnchor=ellipsize(font,anchorStatus,column-24);
        dominionLabel=ellipsize(font,text(wide?"atlas_dominion":"atlas_claimed_chunks"),column-20);
        anchorLabel=ellipsize(font,text("atlas_anchor"),column-30);
        realmLabel=ellipsize(font,kingdom==null?text("atlas_volume"):!kingdom.manager()?access:text("atlas_sovereign"),textWidth);
        claimedLabel=ellipsize(font,text("atlas_claimed_chunks"),column-16);
        colorLabel=kingdom==null?"":ellipsize(font,text("color_"+kingdom.color()),column-24);

        var tabs=AtlasWidgets.tabs(font,bounds.left(),y+NAV_Y,bodyWidth,1,color,navigate);
        widgets.addAll(tabs);
        initialFocus=tabs.get(1);
        if (kingdom==null) {
            record=null; disclosure=null;
            widgets.add(paragraph(font,bounds.left(),bodyY,bodyWidth-GAP,Math.max(CONTROL,bounds.footerY()-GAP-bodyY),empty));
        } else {
            disclosure=new RoyalButton(bounds.left(),bounds.disclosureY(),Math.min(bodyWidth,font.width(recordLabel)+PAD*2),recordLabel,b -> toggle.run());
            widgets.add(disclosure);
            var technical=new ArrayList<Component>();
            technical.add(text("identity_uuid",kingdom.id().toString()));
            technical.add(text("atlas_raw_anchor",kingdom.capital().toShortString()));
            technical.add(text("dimension",kingdom.dimension().toString()));
            technical.add(text("state_anchor",status));
            technical.add(text("sovereign_color",text("color_"+kingdom.color())));
            record=expanded?new Readout(font,bounds.left(),bodyY,bodyWidth-GAP,Math.max(CONTROL,bounds.contentBottom()-bodyY),technical):null;
            if (record!=null) widgets.add(record);
        }
        int[] actions=AtlasWidgets.rowWidths(font,bodyWidth,CONTROL*3,text("refresh"),text("close"));
        refresh=new RoyalButton(bounds.left(),bounds.footerY(),actions[0],text("refresh"),b -> refreshAction.run());
        widgets.add(refresh);
        widgets.add(new RoyalButton(bounds.right()-actions[1],bounds.footerY(),actions[1],text("close"),b -> closeAction.run()));
        feedbackX=refresh.getRight()+GAP; feedbackWidth=bounds.right()-actions[1]-GAP-feedbackX;
    }
    List<AbstractWidget> widgets() { return widgets; }
    Component narration() {
        var result=text("atlas_title").copy().append(". ").append(name).append(". ").append(ruler).append(". ").append(capital)
                .append(". ").append(access).append(". ").append(sealHint).append(". ").append(claimsHint).append(". ").append(status);
        if (kingdom==null) result.append(". ").append(empty);
        return result;
    }
    void render(GuiGraphics g,Component feedback,boolean failed) {
        g.fill(0,0,g.guiWidth(),g.guiHeight(),BACKGROUND);
        // Subtle offsets give the folio thickness without adding another large content panel.
        g.fill(x+7,y+height,x+width-4,y+height+2,AtlasGraphics.SHADOW);
        AtlasGraphics.folio(g,x,y,width,height);
        g.fill(bounds.left(),y+36,bounds.right(),y+37,AtlasGraphics.ETCH);
        AtlasGraphics.banner(g,bannerX,bannerY,bannerScale,color,heraldry);
        g.drawString(font,realmLabel,textX,eyebrowY,AtlasGraphics.LIGHT,false);
        g.pose().pushPose(); g.pose().translate(textX,nameY,0); g.pose().scale(2,2,1);
        g.drawString(font,displayName,0,0,INK,false); g.pose().popPose();
        if (!displayRuler.isEmpty()) g.drawString(font,displayRuler,textX,rulerY,INK,false);
        g.drawString(font,displayCapital,textX,rulerY+14,MUTED,false);
        if (bannerScale==2) g.drawString(font,displayAccess,textX,rulerY+28,MUTED,false);
        AtlasGraphics.divider(g,bounds.left(),headerBottom,bodyWidth);
        if (kingdom!=null&&record==null) {
            int left=bounds.left(),right=left+column+GAP;
            AtlasGraphics.mark(g,left,bodyY,false);
            g.drawString(font,dominionLabel,left+18,bodyY+2,AtlasGraphics.LIGHT,false);
            g.pose().pushPose(); g.pose().translate(left,bodyY+18,0); g.pose().scale(countScale,countScale,1);
            g.drawString(font,displayCount,0,0,INK,false); g.pose().popPose();
            if (bannerScale==2) g.drawString(font,claimedLabel,left,bodyY+40,MUTED,false);
            g.fill(left+column-1,bodyY+2,left+column,bodyY+(bannerScale==2?49:39),AtlasGraphics.ETCH);
            AtlasGraphics.mark(g,right,bodyY,true);
            g.drawString(font,anchorLabel,right+18,bodyY+2,AtlasGraphics.LIGHT,false);
            g.drawString(font,displayAnchor,right,bodyY+20,INK,false);
            int colorY=bodyY+(bannerScale==2?36:32);
            g.fill(right,colorY,right+7,colorY+7,color);
            g.drawString(font,colorLabel,right+12,colorY,MUTED,false);
        }
        // Feedback is separate from primary information; full text remains in its hover and narration.
        if (!feedback.getString().isEmpty()) g.drawString(font,ellipsize(font,feedback,feedbackWidth),feedbackX,bounds.footerY()+(CONTROL-LINE)/2,failed?ERROR:MUTED,false);
    }
    void renderHover(GuiGraphics g,int mx,int my,Component feedback) {
        if (record!=null) record.renderTooltip(g,mx,my);
        if (font.width(name)>textWidth/2&&hit(mx,my,textX,nameY-2,textWidth,CONTROL+2)) tooltip(g,font,name,mx,my);
        if (font.width(feedback)>feedbackWidth&&hit(mx,my,feedbackX,bounds.footerY(),feedbackWidth,CONTROL)) tooltip(g,font,feedback,mx,my);
    }
    private static boolean hit(int mx,int my,int x,int y,int w,int h) { return mx>=x&&mx<x+w&&my>=y&&my<y+h; }

}
