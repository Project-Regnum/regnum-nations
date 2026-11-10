package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.kingdom.PoliticalColors;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;

/** Royal Cartography: logical pixels, native widget lifecycle, no gameplay authority. */
final class KingdomUi {
    static final int BACKGROUND = 0xff171a1f, PANEL = AtlasGraphics.PAPER, RECESS = AtlasGraphics.SHADOW;
    static final int GOLD = 0xffc5a46a, INK = 0xffe5d9be, MUTED = 0xffa8a9ad;
    static final int BORDER = AtlasGraphics.ETCH, HOVER = 0xff30342c, DISABLED = AtlasGraphics.SHADOW;
    static final int BURGUNDY = 0xff834751, ERROR = 0xffefa9b3, SELECTED = 0xfff5eedc;
    static final int UNKNOWN = 0xff141b18, WILDERNESS = 0xff2c322b, GRID = 0x184f594c;
    static final int OVERLAY = 0xe0171a1f, LABEL_BACK = 0xed171e1b, SWATCH_DISABLED = 0x7020242b;
    static final int MARGIN = 8, PAD = 12, GAP = 6, CONTROL = 20, LINE = 10, ROW = 18;
    static final int HEADER = 34, FOOTER = 32;
    /** Shared folio edges and pinned control bands, in logical GUI pixels. */
    record FolioBounds(int x,int y,int width,int height,int pad) {
        int left() { return x+pad; }
        int right() { return x+width-pad; }
        int contentWidth() { return width-pad*2; }
        int footerY() { return y+height-MARGIN-GAP-CONTROL; }
        int disclosureY() { return footerY()-GAP-CONTROL; }
        int contentBottom() { return disclosureY()-GAP; }
    }
    static FolioBounds folioBounds(int screenWidth,int screenHeight,int maxWidth,int maxHeight) {
        int width=Math.min(screenWidth-MARGIN*2,maxWidth),height=Math.min(screenHeight-MARGIN*2,maxHeight);
        return new FolioBounds((screenWidth-width)/2,(screenHeight-height)/2,width,height,width<400?16:24);
    }
    private static int sequence;
    private static long lastReadAt, lastMutationAt;
    private static final ClientTooltipPositioner TOOLTIP_POSITION = (w,h,x,y,tw,th) -> {
        int tx = x + 12;
        if (tx + tw + 6 > w) tx = x - tw - 8;
        return new Vector2i(Math.clamp(tx,4,Math.max(4,w-tw-6)),Math.clamp(y+8,4,Math.max(4,h-th-6)));
    };
    private KingdomUi() { }
    static boolean canRead() { return System.currentTimeMillis()-lastReadAt >= 175; }
    static void readSent() { lastReadAt = System.currentTimeMillis(); }
    static boolean canMutate() { return System.currentTimeMillis()-lastMutationAt >= 550; }
    static void mutationSent() { lastMutationAt = System.currentTimeMillis(); }
    static int nextSequence() { return ++sequence; }
    static Component text(String key, Object... args) { return Component.translatable("ui.regnum-nations." + key, args); }
    static Component dimension(ResourceLocation id) {
        if (id.getNamespace().equals("minecraft")) {
            String key = switch (id.getPath()) { case "overworld" -> "overworld"; case "the_nether" -> "nether"; case "the_end" -> "end"; default -> ""; };
            if (!key.isEmpty()) return Component.translatable("map.regnum-nations." + key);
        }
        String fallback = id.getPath().replace('_', ' ').replace('/', ' ');
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'), fallback);
    }
    static void panel(GuiGraphics g, int l, int t, int r, int b) {
        g.fill(l,t,r,b,BORDER); g.fill(l+1,t+1,r-1,b-1,PANEL);
    }
    static void frame(GuiGraphics g, Font font, Component title, int l, int t, int w, int h) {
        AtlasGraphics.folio(g,l,t,w,h);
        g.drawString(font,ellipsize(font,title,w-PAD*2),l+PAD,t+PAD,AtlasGraphics.LIGHT,false);
        AtlasGraphics.divider(g,l+PAD,t+HEADER-5,w-PAD*2);
    }
    static void heading(GuiGraphics g, Font font, Component label, int x, int y, int width) {
        g.drawString(font,ellipsize(font,label,width),x,y,GOLD,false);
    }
    static String ellipsize(Font font, Component line, int width) {
        String value = line.getString();
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value,Math.max(0,width-font.width("…")))+"…";
    }
    static void tooltip(GuiGraphics g,Font font,Component value,int mx,int my) {
        tooltip(g,font,font.split(value,Math.max(40,Math.min(200,g.guiWidth()/2))),mx,my);
    }
    static void tooltip(GuiGraphics g,Font font,List<FormattedCharSequence> lines,int mx,int my) {
        tooltip(g,font,lines,mx,my,3);
    }
    static void tooltip(GuiGraphics g,Font font,List<FormattedCharSequence> lines,int mx,int my,int maxLines) {
        int max = Math.max(1,Math.min(maxLines,(g.guiHeight()-16)/LINE));
        if (lines.size() > max) {
            var clipped = new java.util.ArrayList<>(lines.subList(0,Math.max(0,max-1)));
            clipped.add(Component.literal("…").getVisualOrderText()); lines = clipped;
        }
        g.renderTooltip(font,lines,TOOLTIP_POSITION,mx,my);
    }
    static EditBox field(Font font,int x,int y,int width,Component label) {
        EditBox box = new EditBox(font,x,y,width,CONTROL,label) {
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
                g.fill(getX()-1,getY()-1,getRight()+1,getBottom()+1,isFocused()?INK:AtlasGraphics.BRASS);
                super.renderWidget(g,mx,my,delta);
            }
        };
        box.setTextColor(INK); box.setTextColorUneditable(MUTED);
        return box;
    }
    /** Native scrollable paragraph: complete warnings/empty states remain keyboard readable. */
    static FittingMultiLineTextWidget paragraph(Font font,int x,int y,int width,int height,Component message) {
        return new FittingMultiLineTextWidget(x,y,width,height,message,font) {
            @Override protected void renderBackground(GuiGraphics g) {
                if (showingScrollBar() || isFocused()) {
                    panel(g,getX()-1,getY()-1,getRight()+1,getBottom()+1);
                    if (isFocused()) g.fill(getX()-1,getY()-1,getRight()+1,getY(),GOLD);
                }
            }
        }.setColor(MUTED);
    }
    /** Scrollable read-only rows, measured once when the authoritative snapshot changes. */
    static final class Readout extends AbstractScrollWidget {
        private final Font font;
        private final List<Component> rows;
        private final List<String> display;
        Readout(Font font,int x,int y,int width,int height,List<Component> rows) {
            super(x,y,width,height,join(rows)); this.font=font; this.rows=List.copyOf(rows);
            display=rows.stream().map(row -> ellipsize(font,row,width-16)).toList();
        }
        private static Component join(List<Component> rows) {
            var result=Component.empty();
            rows.forEach(row -> result.append(row).append(". ")); return result;
        }
        @Override protected int getInnerHeight() { return rows.size()*ROW; }
        @Override protected double scrollRate() { return ROW; }
        @Override protected void renderBackground(GuiGraphics g) {
            // Let the folio remain visible under engraved information rows.
            if (isFocused()) g.fill(getX(),getY(),getRight(),getY()+1,GOLD);
        }
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta) {
            for (int i=0;i<rows.size();i++) {
                int y=getY()+innerPadding()+i*ROW;
                if (y-scrollAmount()+ROW<getY() || y-scrollAmount()>getBottom()) continue;
                g.drawString(font,display.get(i),getX()+innerPadding(),y+3,INK,false);
                g.fill(getX()+innerPadding(),y+ROW-1,getRight()-innerPadding(),y+ROW,0x40595c4a);
            }
        }
        double scrollPosition() { return scrollAmount(); }
        void restoreScroll(double amount) { setScrollAmount(amount); }
        void renderTooltip(GuiGraphics g,int mx,int my) {
            if (!isMouseOver(mx,my)) return;
            int row=(int)((my-getY()-innerPadding()+scrollAmount())/ROW);
            if (row>=0&&row<rows.size()&&font.width(rows.get(row))>getWidth()-16) tooltip(g,font,rows.get(row),mx,my);
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE,getMessage());
            output.add(NarratedElementType.USAGE,text("scroll_details"));
        }
    }
    enum ButtonStyle { NORMAL, PRIMARY, DANGER }
    /** One frame and state implementation for text controls, map icons, tabs and swatches. */
    static class RoyalButton extends Button {
        protected static final int CONTENT_PAD=6,ICON_SIZE=11,ICON_GAP=4;
        private ButtonStyle style;
        private boolean selected;
        private long pressedUntil;
        RoyalButton(int x,int y,int width,Component label,OnPress press) { this(x,y,width,label,press,ButtonStyle.NORMAL); }
        RoyalButton(int x,int y,int width,Component label,OnPress press,ButtonStyle style) {
            super(x,y,width,CONTROL,label,press,DEFAULT_NARRATION); this.style = style;
            setTooltip(Tooltip.create(label));
        }
        void style(ButtonStyle value) { style=value; }
        void selected(boolean value) { selected=value; }
        protected boolean isSelected() { return selected; }
        @Override public void onPress() {
            // Native mouse/keyboard activation still invokes the existing callback exactly once.
            pressedUntil=System.nanoTime()+120_000_000L;
            super.onPress();
        }
        @Override protected final void renderWidget(GuiGraphics g,int mx,int my,float delta) {
            if (getWidth()<3 || getHeight()<3) return;
            boolean hovered=active&&isHoveredOrFocused();
            boolean pressed=active&&pressedUntil!=0&&System.nanoTime()<pressedUntil;
            boolean chosen=isSelected();
            int accent=style==ButtonStyle.DANGER?BURGUNDY:AtlasGraphics.BRASS;
            int border=!active?AtlasGraphics.ETCH:pressed?GOLD:hovered?AtlasGraphics.LIGHT:chosen||style!=ButtonStyle.NORMAL?accent:AtlasGraphics.RULE;
            int x=getX(),y=getY(),r=getRight(),b=getBottom();
            g.fill(x+1,y,r-1,b,border); g.fill(x,y+1,r,b-1,border);
            g.fill(x+1,y+1,r-1,b-1,!active?DISABLED:pressed?AtlasGraphics.LEATHER:hovered?HOVER:chosen?AtlasGraphics.PAPER:RECESS);
            g.fill(x+2,y+1,r-2,y+2,pressed?AtlasGraphics.SHADOW:hovered?AtlasGraphics.LIGHT:AtlasGraphics.ETCH);
            g.fill(x+2,b-3,r-2,b-1,pressed?AtlasGraphics.ETCH:AtlasGraphics.LEATHER);
            int color=!active?MUTED:style==ButtonStyle.DANGER?ERROR:chosen||hovered||pressed?AtlasGraphics.LIGHT:INK;
            // Content stays inside the existing hitbox, even with compressed or localized labels.
            g.enableScissor(x+1,y+1,r-1,b-1);
            renderContent(g,color,pressed?1:0);
            g.disableScissor();
            if (isFocused()) {
                g.fill(x+3,y+3,x+5,y+4,INK); g.fill(r-5,y+3,r-3,y+4,INK);
            }
            renderIndicator(g,active?accent:AtlasGraphics.ETCH);
        }
        protected void renderContent(GuiGraphics g,int color,int offset) { renderLabel(g,color,offset,0); }
        /** Returns the start of the centered icon/label group for icon-bearing subclasses. */
        protected final int renderLabel(GuiGraphics g,int color,int offset,int leadingWidth) {
            var font=Minecraft.getInstance().font;
            int available=Math.max(0,getWidth()-CONTENT_PAD*2-leadingWidth);
            String label=font.plainSubstrByWidth(ellipsize(font,getMessage(),available),available);
            int left=getX()+(getWidth()-font.width(label)-leadingWidth)/2+offset;
            g.drawString(font,label,left+leadingWidth,getY()+(getHeight()-font.lineHeight)/2+offset,color,false);
            return left;
        }
        protected void renderIndicator(GuiGraphics g,int accent) {
            if (isSelected()) g.fill(getX()+4,getBottom()-3,getRight()-4,getBottom()-2,accent);
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (isSelected()) output.add(NarratedElementType.HINT,text("selected"));
        }
    }
    static final class ColorSwatch extends RoyalButton {
        private final int index;
        private final IntSupplier selection;
        ColorSwatch(int x,int y,int width,int index,IntSupplier selection,IntConsumer select) {
            super(x,y,width,text("color_"+index),b -> select.accept(index));
            this.index=index; this.selection=selection;
        }
        @Override protected boolean isSelected() { return selection.getAsInt()==index; }
        @Override protected void renderContent(GuiGraphics g,int color,int offset) {
            g.fill(getX()+3+offset,getY()+3+offset,getRight()-3+offset,getBottom()-3+offset,PoliticalColors.rgb(index));
            if (isSelected()) {
                // Geometric check: works with every font and never relies on color alone.
                int x=getX()+getWidth()/2-3+offset,y=getY()+9+offset;
                g.fill(x-1,y-1,x+3,y+5,BACKGROUND); g.fill(x+2,y-4,x+7,y+3,BACKGROUND);
                g.fill(x,y,x+2,y+4,SELECTED); g.fill(x+2,y+2,x+4,y+4,SELECTED); g.fill(x+4,y-3,x+6,y+3,SELECTED);
            }
            if (!active) g.fill(getX()+2,getY()+2,getRight()-2,getBottom()-2,SWATCH_DISABLED);
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (!isSelected()) output.add(NarratedElementType.HINT,text("select_color"));
        }
    }
}
