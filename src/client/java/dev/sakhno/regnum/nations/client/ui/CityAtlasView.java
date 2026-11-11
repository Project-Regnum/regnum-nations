package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.PoliticalColors;
import dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Municipal folio and personal-land register composed from the existing bounded city view. */
final class CityAtlasView {
    private final Font font;
    private final CityOverview city;
    private final boolean territories,expanded;
    private final int x,y,width,height,pad,textX,textWidth,bodyY,column,color,nameScale,countScale;
    private final FolioBounds bounds;
    private final int feedbackX,feedbackWidth;
    private final Component name,owner,affiliation,access,anchor,empty;
    private final String displayName,displayOwner,displayAffiliation,eyebrow,claims,claimsLabel,anchorLabel,anchorValue,colorName;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    final Button refresh,initialFocus,disclosure;
    final Readout record;

    CityAtlasView(Font font,int screenWidth,int screenHeight,State state,boolean territories,boolean expanded,
                  Runnable refreshAction,Runnable closeAction,Runnable relocateAction,IntConsumer navigate,Runnable toggle,boolean loading,boolean failed) {
        this.font=font; this.territories=territories; this.expanded=expanded;
        city=state==null?null:state.city();
        bounds=folioBounds(screenWidth,screenHeight,620,256);
        width=bounds.width(); height=bounds.height(); x=bounds.x(); y=bounds.y(); pad=bounds.pad();
        textX=x+pad+50; textWidth=x+width-pad-textX;
        nameScale=2; bodyY=y+117; column=(width-pad*2)/2;
        color=city==null?AtlasGraphics.ETCH:PoliticalColors.rgb(city.color());
        name=city==null?text("no_city_title"):Component.literal(city.name());
        owner=city==null?Component.empty():text(city.manager()?"city_owner_self":"city_owner_visitor",city.owner().isEmpty()?text("unknown_founder"):Component.literal(city.owner()));
        affiliation=city==null?Component.empty():city.sovereign()==null?text("independent_city"):text("sovereign_kingdom",city.sovereignName());
        access=text(city!=null&&city.manager()?"personal_domain":"visitor_access");
        anchor=city==null?Component.empty():Component.translatable("message.regnum-nations.capital."+city.status());
        empty=loading?text("loading"):failed?text("view_error"):text("no_city");
        displayName=ellipsize(font,name,textWidth/nameScale);
        displayOwner=ellipsize(font,territories&&city!=null?text("claim_count",city.claims()):owner,textWidth);
        displayAffiliation=ellipsize(font,territories?access:affiliation,textWidth);
        eyebrow=ellipsize(font,text(territories?"atlas_domain_register":"atlas_city_register"),textWidth);
        claims=city==null?"":Long.toString(city.claims());
        countScale=font.width(claims)*2<=column-12?2:1;
        claimsLabel=ellipsize(font,text("atlas_claimed_chunks"),column-24);
        anchorLabel=ellipsize(font,text("atlas_city_seat"),column-28);
        anchorValue=city==null?"":ellipsize(font,text(switch(city.status()) {
            case "present" -> "atlas_anchor_present"; case "missing" -> "atlas_anchor_missing"; default -> "atlas_anchor_unresolved";
        }),column-12);
        colorName=city==null?"":ellipsize(font,text("color_"+city.color()),column-24);
        var tabs=AtlasWidgets.tabs(font,x+pad,y+13,width-pad*2,territories?2:0,color,navigate);
        widgets.addAll(tabs); initialFocus=tabs.get(territories?2:0);
        int bodyWidth=width-pad*2;
        Readout technicalRecord=null;
        Button toggleButton=null;
        if (territories) {
            Component rules=city==null?empty:text("expansion_rules").copy().append("\n").append(text("capital_rule"));
            widgets.add(paragraph(font,bounds.left(),bodyY,bodyWidth-GAP,Math.max(CONTROL,bounds.contentBottom()-bodyY),rules));
            widgets.add(new RoyalButton(bounds.left(),bounds.disclosureY(),Math.min(bodyWidth,font.width(text("open_map"))+PAD*2),
                    text("open_map"),b -> navigate.accept(3),ButtonStyle.PRIMARY));
        } else if (city==null) {
            widgets.add(paragraph(font,bounds.left(),bodyY,bodyWidth-GAP,Math.max(CONTROL,bounds.footerY()-GAP-bodyY),empty));
        } else {
            toggleButton=new RoyalButton(bounds.left(),bounds.disclosureY(),Math.min(city.manager()?bodyWidth/2-GAP:bodyWidth,font.width(text(expanded?"atlas_record_close":"atlas_record_open"))+PAD*2),
                    text(expanded?"atlas_record_close":"atlas_record_open"),b -> toggle.run());
            widgets.add(toggleButton);
            if (expanded) {
                List<Component> technical=List.of(owner,affiliation,access,text("political_color_value",text("color_"+city.color())),
                        text("city_anchor",anchor),text("capital_location",dimension(city.dimension()),city.capital().toShortString()),text("identity_uuid",city.id().toString()));
                technicalRecord=new Readout(font,bounds.left(),bodyY,bodyWidth-GAP,Math.max(CONTROL,bounds.contentBottom()-bodyY),technical);
                widgets.add(technicalRecord);
            }
        }
        record=technicalRecord; disclosure=toggleButton;
        if (!territories&&city!=null&&city.manager()) {
            int relocationWidth=Math.min(bodyWidth/2,font.width(Component.translatable("relocation.regnum-nations.title"))+PAD*2);
            widgets.add(new RoyalButton(bounds.right()-relocationWidth,bounds.disclosureY(),relocationWidth,
                    Component.translatable("relocation.regnum-nations.title"),b -> relocateAction.run()));
        }
        int[] actions=AtlasWidgets.rowWidths(font,bodyWidth,CONTROL*3,text("refresh"),text("close"));
        refresh=new RoyalButton(bounds.left(),bounds.footerY(),actions[0],text("refresh"),b -> refreshAction.run());
        widgets.add(refresh);
        widgets.add(new RoyalButton(bounds.right()-actions[1],bounds.footerY(),actions[1],text("close"),b -> closeAction.run()));
        feedbackX=refresh.getRight()+GAP; feedbackWidth=bounds.right()-actions[1]-GAP-feedbackX;
    }
    List<AbstractWidget> widgets() { return widgets; }
    Component narration() {
        return text(territories?"atlas_domain_register":"city_section").copy().append(". ").append(name).append(". ").append(owner)
                .append(". ").append(affiliation).append(". ").append(access).append(". ").append(city==null?empty:text("claim_count",city.claims()))
                .append(". ").append(anchor).append(". ").append(city==null?Component.empty():text("political_color_value",text("color_"+city.color())));
    }
    void render(GuiGraphics g,Component feedback,boolean failed) {
        g.fill(0,0,g.guiWidth(),g.guiHeight(),BACKGROUND);
        AtlasGraphics.folio(g,x,y,width,height);
        g.fill(x+pad,y+36,x+width-pad,y+37,AtlasGraphics.ETCH);
        AtlasGraphics.civicSeal(g,x+pad,y+48,color);
        g.drawString(font,eyebrow,textX,y+42,AtlasGraphics.LIGHT,false);
        g.pose().pushPose(); g.pose().translate(textX,y+56,0); g.pose().scale(nameScale,nameScale,1);
        g.drawString(font,displayName,0,0,INK,false); g.pose().popPose();
        g.drawString(font,displayOwner,textX,y+78,INK,false);
        g.drawString(font,displayAffiliation,textX,y+91,MUTED,false);
        AtlasGraphics.divider(g,x+pad,y+105,width-pad*2);
        if (!territories&&city!=null&&!expanded) {
            int left=bounds.left(),right=left+column+GAP;
            AtlasGraphics.mark(g,left,bodyY,false);
            g.drawString(font,claimsLabel,left+17,bodyY+2,AtlasGraphics.LIGHT,false);
            g.pose().pushPose(); g.pose().translate(left,bodyY+19,0); g.pose().scale(countScale,countScale,1);
            g.drawString(font,claims,0,0,INK,false); g.pose().popPose();
            g.fill(left+column-1,bodyY,left+column,bodyY+41,AtlasGraphics.ETCH);
            AtlasGraphics.mark(g,right,bodyY,true);
            g.drawString(font,anchorLabel,right+17,bodyY+2,AtlasGraphics.LIGHT,false);
            g.drawString(font,anchorValue,right,bodyY+18,INK,false);
            g.fill(right,bodyY+32,right+7,bodyY+39,color);
            g.drawString(font,colorName,right+12,bodyY+32,MUTED,false);
        }
        if (!feedback.getString().isEmpty()) g.drawString(font,ellipsize(font,feedback,feedbackWidth),feedbackX,bounds.footerY()+(CONTROL-LINE)/2,failed?ERROR:MUTED,false);
    }
    void renderHover(GuiGraphics g,int mx,int my,Component feedback) {
        if (record!=null) record.renderTooltip(g,mx,my);
        if (font.width(name)>textWidth/nameScale&&hit(mx,my,textX,y+54,textWidth,CONTROL+2)) tooltip(g,font,name,mx,my);
        if (font.width(feedback)>feedbackWidth&&hit(mx,my,feedbackX,bounds.footerY(),feedbackWidth,CONTROL)) tooltip(g,font,feedback,mx,my);
    }
    private static boolean hit(int mx,int my,int x,int y,int w,int h) { return mx>=x&&mx<x+w&&my>=y&&my<y+h; }
}
