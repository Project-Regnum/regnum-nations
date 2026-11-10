package dev.sakhno.regnum.nations.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Framed folio control, retaining RoyalButton's native activation, focus and narration. */
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
    private boolean selected;
    private ButtonStyle style=ButtonStyle.NORMAL;
    private int layerState=-1;
    private Component layerDescription=Component.empty();

    RoyalControlButton(int x,int y,int width,Component label,Icon icon,boolean iconOnly,OnPress press) {
        super(x,y,width,label,press);
        this.icon=icon; this.iconOnly=iconOnly;
    }
    @Override void selected(boolean value) { super.selected(value); selected=value; }
    @Override void style(ButtonStyle value) { super.style(value); style=value; }
    void layerState(int value,Component description) {
        layerState=value; layerDescription=description; selected(true);
    }
    @Override protected MutableComponent createNarrationMessage() {
        MutableComponent message=super.createNarrationMessage();
        return layerState<0?message:message.append(". ").append(layerDescription);
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
        boolean hovered=active&&isHoveredOrFocused();
        int accent=style==ButtonStyle.DANGER?BURGUNDY:AtlasGraphics.BRASS;
        int border=!active?AtlasGraphics.ETCH:hovered?AtlasGraphics.LIGHT:selected||style!=ButtonStyle.NORMAL?accent:AtlasGraphics.RULE;
        int x=getX(),y=getY(),r=getRight(),b=getBottom();
        // One-pixel chamfer, complete frame and inset bevel replace the old separator strokes.
        g.fill(x+1,y,r-1,b,border); g.fill(x,y+1,r,b-1,border);
        g.fill(x+1,y+1,r-1,b-1,!active?DISABLED:hovered?HOVER:selected?AtlasGraphics.PAPER:RECESS);
        g.fill(x+2,y+1,r-2,y+2,hovered?AtlasGraphics.LIGHT:AtlasGraphics.ETCH);
        g.fill(x+2,b-3,r-2,b-1,AtlasGraphics.LEATHER);
        if (isFocused()) {
            g.fill(x+3,y+3,x+5,y+4,INK); g.fill(r-5,y+3,r-3,y+4,INK);
        }
        int color=!active?MUTED:style==ButtonStyle.DANGER?ERROR:selected||hovered?AtlasGraphics.LIGHT:INK;
        if (iconOnly) icon.draw(g,x+(getWidth()-11)/2,y+(CONTROL-11)/2,color);
        else {
            var font=Minecraft.getInstance().font;
            int iconWidth=icon==Icon.NONE?0:15;
            String label=ellipsize(font,getMessage(),getWidth()-12-iconWidth);
            int left=x+(getWidth()-font.width(label)-iconWidth)/2;
            if (icon!=Icon.NONE) icon.draw(g,left,y+(CONTROL-11)/2,color);
            g.drawString(font,label,left+iconWidth,y+(CONTROL-font.lineHeight)/2,color,false);
        }
        if (layerState>=0) {
            int left=x+getWidth()/2-7;
            for (int i=0;i<3;i++) g.fill(left+i*5,b-3,left+i*5+4,b-1,i==layerState?GOLD:AtlasGraphics.ETCH);
        } else if (selected) g.fill(x+4,b-3,r-4,b-2,accent);
    }
}
