package dev.sakhno.regnum.nations.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Map icon/indicator content; all framing and interaction states belong to RoyalButton. */
final class RoyalControlButton extends RoyalButton {
    enum Icon {
        NONE(),
        REFRESH("..######...", ".##....##..", "##......##.", "##......##.", "##.....####",
                "##......##.", "##.......#.", ".##........", "..##....##.", "...######.."),
        LAYERS(".....#.....", "...#####...", ".#########.", "...#####...", ".....#.....",
                "..#.....#..", "...#####...", ".....#.....", "..#.....#..", "...#####...", ".....#....."),
        MINUS("...........", "...........", "...........", "...........", ".#########.", ".#########."),
        PLUS("...........", "....##.....", "....##.....", "....##.....", ".#########.", ".#########.",
                "....##.....", "....##.....", "....##....."),
        CLOSE("...........", "...........", "..##...##..", "...##.##...", "....###....", ".....#.....",
                "....###....", "...##.##...", "..##...##..");

        private final String[] pixels;
        Icon(String... pixels) { this.pixels=pixels; }
        void draw(GuiGraphics g,int x,int y,int color) {
            for (int row=0;row<pixels.length;row++) {
                String line=pixels[row];
                for (int column=0;column<line.length();column++) if (line.charAt(column)=='#')
                    g.fill(x+column,y+row,x+column+1,y+row+1,color);
            }
        }
    }

    private final Icon icon;
    private final boolean iconOnly;
    private int layerState=-1;
    private Component layerDescription=Component.empty();

    RoyalControlButton(int x,int y,int width,Component label,Icon icon,boolean iconOnly,OnPress press) {
        super(x,y,width,label,press);
        this.icon=icon; this.iconOnly=iconOnly;
    }
    void layerState(int value,Component description) {
        layerState=value; layerDescription=description; selected(true);
    }
    @Override protected MutableComponent createNarrationMessage() {
        MutableComponent message=super.createNarrationMessage();
        return layerState<0?message:message.append(". ").append(layerDescription);
    }
    @Override protected void renderContent(GuiGraphics g,int color,int offset) {
        if (iconOnly) icon.draw(g,getX()+(getWidth()-ICON_SIZE)/2+offset,getY()+(getHeight()-ICON_SIZE)/2+offset,color);
        else {
            int left=renderLabel(g,color,offset,icon==Icon.NONE?0:ICON_SIZE+ICON_GAP);
            if (icon!=Icon.NONE) icon.draw(g,left,getY()+(getHeight()-ICON_SIZE)/2+offset,color);
        }
    }
    @Override protected void renderIndicator(GuiGraphics g,int accent) {
        if (layerState>=0) {
            int left=getX()+getWidth()/2-7;
            for (int i=0;i<3;i++) g.fill(left+i*5,getBottom()-3,left+i*5+4,getBottom()-1,
                    !active?AtlasGraphics.ETCH:i==layerState?GOLD:AtlasGraphics.ETCH);
        } else super.renderIndicator(g,accent);
    }
}
