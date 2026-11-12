package dev.sakhno.regnum.nations.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Small native atlas controls shared by the folios; no independent widget lifecycle. */
final class AtlasWidgets {
    private AtlasWidgets() { }
    /** Natural label widths compress together within one bounded row. */
    static int[] rowWidths(Font font,int width,int minimum,Component... labels) {
        int available=width-GAP*(labels.length-1),desired=0;
        int[] widths=new int[labels.length];
        for (int i=0;i<labels.length;i++) { widths[i]=Math.max(minimum,font.width(labels[i])+PAD*2); desired+=widths[i]; }
        if (desired>available) {
            int used=0;
            for (int i=0;i<widths.length;i++) {
                widths[i]=i==widths.length-1?available-used:widths[i]*available/desired;
                used+=widths[i];
            }
        }
        return widths;
    }
    static List<Button> tabs(Font font,int x,int y,int width,int selected,int color,IntConsumer navigate) {
        return tabs(font,x,y,width,selected,color,navigate,false);
    }
    static List<Button> tabs(Font font,int x,int y,int width,int selected,int color,IntConsumer navigate,boolean economy) {
        String[] keys=economy?new String[]{"city_section","kingdom_section","territories","map_section","economy_section"}
                :new String[]{"city_section","kingdom_section","territories","map_section"};
        Component[] labels=java.util.Arrays.stream(keys).map(KingdomUi::text).toArray(Component[]::new);
        int[] widths=rowWidths(font,width,CONTROL,labels);
        int used=0;
        var tabs=new ArrayList<Button>();
        for (int i=0;i<keys.length;i++) {
            final int index=i;
            Component label=text(keys[i]);
            int w=widths[i];
            tabs.add(new Tab(x+used,y,w,label,i==selected,color,b -> navigate.accept(index)));
            used+=w+GAP;
        }
        return tabs;
    }
    /** Navigation tabs share the button frame; a small inset stripe retains polity identity. */
    static final class Tab extends RoyalButton {
        private final int color;
        Tab(int x,int y,int width,Component label,boolean selected,int color,OnPress action) {
            super(x,y,width,label,action); selected(selected); this.color=selected?color:0;
        }
        @Override protected void renderIndicator(GuiGraphics g,int accent) {
            super.renderIndicator(g,accent);
            if (color!=0 && active) g.fill(getX()+6,getBottom()-3,getRight()-6,getBottom()-2,color);
        }
    }
}
