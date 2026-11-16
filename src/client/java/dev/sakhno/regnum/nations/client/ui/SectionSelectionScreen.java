package dev.sakhno.regnum.nations.client.ui;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static dev.sakhno.regnum.nations.client.ui.KingdomUi.*;

/** Compact destination dialog with bounded pages at small GUI sizes. */
final class SectionSelectionScreen extends RoyalScreen {
    private final Screen parent;
    private final List<Component> labels;
    private final IntConsumer select;
    private FolioBounds bounds;
    private int page, capacity;
    SectionSelectionScreen(Screen parent,List<Component> labels,IntConsumer select) {
        super(text("sections")); this.parent=parent; this.labels=List.copyOf(labels); this.select=select;
    }
    @Override protected void init() {
        bounds=folioBounds(width,height,360,Math.min(300,labels.size()*(CONTROL+GAP)+86));
        capacity=Math.max(1,(bounds.footerY()-bounds.y()-HEADER-GAP*2)/(CONTROL+GAP));
        page=Math.clamp(page,0,Math.max(0,(labels.size()-1)/capacity));
        for (int i=page*capacity;i<Math.min(labels.size(),(page+1)*capacity);i++) {
            final int index=i;
            addRenderableWidget(new RoyalButton(bounds.left(),bounds.y()+HEADER+GAP+(i%capacity)*(CONTROL+GAP),bounds.contentWidth(),labels.get(i),b -> {
                minecraft.setScreen(parent); select.accept(index);
            }));
        }
        addRenderableWidget(new RoyalButton(bounds.left(),bounds.footerY(),70,text("back"),b -> onClose()));
        if (labels.size()>capacity) {
            var previous=addRenderableWidget(new RoyalButton(bounds.right()-86,bounds.footerY(),40,Component.literal("←"),b -> { page--; rebuildWidgets(); }));
            previous.active=page>0;
            var next=addRenderableWidget(new RoyalButton(bounds.right()-40,bounds.footerY(),40,Component.literal("→"),b -> { page++; rebuildWidgets(); }));
            next.active=(page+1)*capacity<labels.size();
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        int next=Math.clamp(page+(vertical<0?1:-1),0,Math.max(0,(labels.size()-1)/capacity));
        if (vertical!=0&&next!=page) { page=next; rebuildWidgets(); return true; }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,OVERLAY); frame(g,font,title,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        super.render(g,mx,my,delta);
    }
}
