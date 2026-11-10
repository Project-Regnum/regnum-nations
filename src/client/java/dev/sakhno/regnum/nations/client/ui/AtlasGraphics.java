package dev.sakhno.regnum.nations.client.ui;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Axis;

/** The Living Atlas's small tiled folio and pixel heraldry. No world or persistent state. */
final class AtlasGraphics {
    static final int LEATHER=0xff101714, PAPER=0xff252926, BRASS=0xffa78e61;
    static final int ETCH=0xff595c4a, RULE=0xff77715a, SHADOW=0xff171e1b, LIGHT=0xffd3bc89;
    static final int EDGE=16, TILE=64;
    private static final ResourceLocation ORNAMENTS=RegnumNations.id("textures/gui/atlas/ornaments.png");
    private static final ResourceLocation FOLIO=RegnumNations.id("textures/gui/atlas/folio_tile.png");
    private AtlasGraphics() { }

    /** Stable UUID bits choose one of four original charges and four field divisions. */
    record Heraldry(int charge,int division) {
        static Heraldry of(UUID id) {
            long bits=id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),23);
            bits=(bits^(bits>>>30))*0xbf58476d1ce4e5b9L;
            bits=(bits^(bits>>>27))*0x94d049bb133111ebL;
            bits^=bits>>>31;
            return new Heraldry((int)(bits&3),(int)((bits>>>2)&3));
        }
    }
    static void sprite(GuiGraphics g,int x,int y,int u,int v,int w,int h,int scale) {
        g.blit(ORNAMENTS,x,y,w*scale,h*scale,(float)u,(float)v,w,h,256,128);
    }
    static void folio(GuiGraphics g,int x,int y,int width,int height) {
        // Chamfer the outer leather silhouette; tile rather than stretch the manuscript surface.
        g.fill(x+4,y,x+width-4,y+height,LEATHER);
        g.fill(x,y+4,x+width,y+height-4,LEATHER);
        g.enableScissor(x+4,y+4,x+width-4,y+height-4);
        for (int yy=y+4;yy<y+height-4;yy+=TILE) for (int xx=x+4;xx<x+width-4;xx+=TILE)
            g.blit(FOLIO,xx,yy,0f,0f,TILE,TILE,TILE,TILE);
        g.disableScissor();
        g.fill(x+8,y+8,x+10,y+height-8,SHADOW);
        g.fill(x+11,y+16,x+12,y+height-16,ETCH);
        g.fill(x+width-12,y+16,x+width-11,y+height-16,ETCH);
        for (int xx=x+EDGE;xx<x+width-EDGE;xx+=16) {
            int w=Math.min(16,x+width-EDGE-xx);
            sprite(g,xx,y,0,16,w,8,1);
            sprite(g,xx,y+height-8,0,24,w,8,1);
        }
        for (int yy=y+EDGE;yy<y+height-EDGE;yy+=16) {
            int h=Math.min(16,y+height-EDGE-yy);
            sprite(g,x,yy,16,16,8,h,1);
            sprite(g,x+width-8,yy,24,16,8,h,1);
        }
        sprite(g,x,y,0,0,16,16,1);
        sprite(g,x+width-16,y,16,0,16,16,1);
        sprite(g,x,y+height-16,32,0,16,16,1);
        sprite(g,x+width-16,y+height-16,48,0,16,16,1);
    }
    static void divider(GuiGraphics g,int x,int y,int width) {
        g.fill(x,y+4,x+width,y+5,RULE);
        g.fill(x,y+5,x+width,y+6,SHADOW);
        sprite(g,x+width/2-16,y,32,16,32,8,1);
    }
    static void banner(GuiGraphics g,int x,int y,int scale,int politicalColor,Heraldry heraldry) {
        // Rod, trim and seal are not tinted. Only the cloth uses the persisted political color.
        sprite(g,x-4*scale,y-3*scale,72,16,41,8,scale);
        g.setColor(((politicalColor>>>16)&255)/255f,((politicalColor>>>8)&255)/255f,(politicalColor&255)/255f,1);
        sprite(g,x,y,0,32,32,56,scale);
        g.setColor(1,1,1,1);
        if (heraldry!=null) sprite(g,x,y,64+heraldry.division()*32,64,32,56,scale);
        sprite(g,x,y,32,32,32,56,scale);
        sprite(g,x+4*scale,y+13*scale,208,32,24,28,scale);
        if (heraldry!=null) sprite(g,x+8*scale,y+17*scale,64+heraldry.charge()*16,32,16,16,scale);
    }
    static void mark(GuiGraphics g,int x,int y,boolean capital) {
        sprite(g,x,y,capital?80:64,48,12,12,1);
    }
    /** Fixed-size map symbols; roof and royal banner carry the disclosed political color. */
    static void mapSettlement(GuiGraphics g,int x,int y,boolean capital,int color) {
        int u=capital?160:128;
        sprite(g,x-7,y-7,u,0,15,15,1);
        g.setColor(((color>>>16)&255)/255f,((color>>>8)&255)/255f,(color&255)/255f,1);
        sprite(g,x-7,y-7,u+16,0,15,15,1);
        g.setColor(1,1,1,1);
    }
    static void mapPlayer(GuiGraphics g,int x,int y,float yaw) {
        g.pose().pushPose();
        g.pose().translate(x+0.5f,y+0.5f,0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(yaw+180));
        sprite(g,-3,-3,192,0,7,7,1);
        g.pose().popPose();
    }
    /** Static parchment hatching beneath transparent unknown terrain, never a terrain sample. */
    static void mapFog(GuiGraphics g,int left,int top,int right,int bottom) {
        g.fill(left,top,right,bottom,0xff303936);
        for (int y=top;y<bottom;y+=32) for (int x=left;x<right;x+=32) {
            g.fill(x+5,y+10,x+11,y+11,0xff35403b);
            g.fill(x+8,y+7,x+9,y+14,0xff35403b);
            g.fill(x+22,y+25,x+26,y+26,0xff2c3532);
        }
    }
    /** A municipal stamp, deliberately smaller and simpler than the sovereign standard. */
    static void civicSeal(GuiGraphics g,int x,int y,int color) {
        g.fill(x+6,y,x+30,y+36,BRASS);
        g.fill(x,y+6,x+36,y+30,BRASS);
        g.fill(x+7,y+2,x+29,y+34,SHADOW);
        g.fill(x+2,y+7,x+34,y+29,SHADOW);
        g.fill(x+8,y+4,x+28,y+32,color);
        g.fill(x+4,y+8,x+32,y+28,color);
        sprite(g,x+6,y+4,208,32,24,28,1);
        sprite(g,x+10,y+8,64,32,16,16,1);
    }
}
