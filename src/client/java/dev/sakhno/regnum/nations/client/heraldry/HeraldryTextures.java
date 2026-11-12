package dev.sakhno.regnum.nations.client.heraldry;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.Heraldry;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import java.util.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** One deterministic compositor for shield and cloth representations. Cached GPU textures never touch disk. */
public final class HeraldryTextures implements SimpleSynchronousResourceReloadListener {
    public static final HeraldryTextures INSTANCE=new HeraldryTextures();
    private static final int WIDTH=128,HEIGHT=160,MAX_TEXTURES=128;
    private record Shape(List<double[]> solid,List<double[]> holes) {
        boolean contains(double x,double y) { return solid.stream().anyMatch(p -> inside(p,x,y))&&!holes.stream().anyMatch(p -> inside(p,x,y)); }
    }
    private record Key(Heraldry arms,boolean cloth) { }
    private final Map<String,Shape> shapes=new HashMap<>();
    private final LinkedHashMap<Key,ResourceLocation> cache=new LinkedHashMap<>(16,.75f,true);
    private long serial;
    private HeraldryTextures() { }
    @Override public ResourceLocation getFabricId() { return RegnumNations.id("heraldry_textures"); }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        clear(); shapes.clear();
        for (var shield:Shield.values()) load(resources,"shields/"+shield.id());
        for (var charge:Emblem.values()) load(resources,"charges/"+charge.id());
    }
    private void load(ResourceManager resources,String name) {
        try (var reader=resources.openAsReader(RegnumNations.id("heraldry/"+name+".json"))) {
            var json=JsonParser.parseReader(reader).getAsJsonObject(); shapes.put(name,new Shape(polygons(json,"solid"),polygons(json,"holes")));
        } catch (Exception e) { throw new IllegalStateException("Unable to load bundled heraldic silhouette "+name,e); }
    }
    private static List<double[]> polygons(JsonObject json,String key) {
        var result=new ArrayList<double[]>(); if (!json.has(key)) return result;
        for (var polygon:json.getAsJsonArray(key)) {
            var points=polygon.getAsJsonArray(); double[] xy=new double[points.size()*2];
            for (int i=0;i<points.size();i++) { var point=points.get(i).getAsJsonArray(); xy[i*2]=point.get(0).getAsDouble(); xy[i*2+1]=point.get(1).getAsDouble(); }
            result.add(xy);
        }
        return result;
    }
    private static boolean inside(double[] p,double x,double y) {
        boolean in=false;
        for (int i=0,j=p.length-2;i<p.length;j=i,i+=2)
            if ((p[i+1]>y)!=(p[j+1]>y)&&x<(p[j]-p[i])*(y-p[i+1])/(p[j+1]-p[i+1])+p[i]) in=!in;
        return in;
    }
    public void clear() { var manager=Minecraft.getInstance().getTextureManager(); cache.values().forEach(manager::release); cache.clear(); }
    public ResourceLocation texture(Heraldry arms,boolean cloth) {
        var key=new Key(arms,cloth); var cached=cache.get(key); if (cached!=null) return cached;
        if (cache.size()>=MAX_TEXTURES) { var iterator=cache.entrySet().iterator(); Minecraft.getInstance().getTextureManager().release(iterator.next().getValue()); iterator.remove(); }
        var pixels=raster(arms,cloth); var texture=new DynamicTexture(pixels); texture.setFilter(false,false);
        var id=RegnumNations.id("heraldry/cache/"+serial++); Minecraft.getInstance().getTextureManager().register(id,texture); cache.put(key,id); return id;
    }
    /** Exposed to opt-in native client checks; the caller owns the returned image. */
    public NativeImage raster(Heraldry arms,boolean cloth) {
        var image=new NativeImage(WIDTH,HEIGHT,true); Shape shield=shapes.get("shields/"+arms.shield().id());
        if (shield==null) throw new IllegalStateException("Heraldry resources have not loaded");
        for (int py=0;py<HEIGHT;py++) for (int px=0;px<WIDTH;px++) {
            double x=(px+.5)*100/WIDTH,y=(py+.5)*100/HEIGHT;
            boolean cut=cloth&&(x<2||x>98||y>100-Math.max(0,18-Math.abs(x-50)*.36));
            if (cut) continue;
            // Cloth carries a full shield, framed by a narrow field-colored border.
            double sx=cloth?(x-8)/.84:x,sy=cloth?(y-5)/.86:y;
            if (!shield.contains(sx,sy)) { if (cloth) image.setPixelRGBA(px,py,abgr(arms.field().rgb)); continue; }
            boolean edge=!shield.contains(sx-1.4,sy)||!shield.contains(sx+1.4,sy)||!shield.contains(sx,sy-1.1)||!shield.contains(sx,sy+1.1);
            int rgb=edge?0xd6b775:color(arms,sx,sy);
            image.setPixelRGBA(px,py,abgr(rgb));
        }
        return image;
    }
    private int color(Heraldry a,double x,double y) {
        boolean secondary=switch(a.division()) {
            case PLAIN -> false; case PER_PALE -> x>=50; case PER_FESS -> y>=50;
            case PER_BEND -> x>y; case PER_SALTIRE -> Math.abs(x-50)>Math.abs(y-50); case QUARTERLY -> (x>=50)!=(y>=50);
        };
        int color=(secondary?a.secondary():a.field()).rgb;
        boolean ordinary=switch(a.ordinary()) {
            case NONE -> false; case PALE -> Math.abs(x-50)<12; case FESS -> Math.abs(y-46)<11;
            case BEND -> Math.abs(x-y)<14; case CHEVRON -> Math.abs(y-(35+Math.abs(x-50)*.65))<9;
            case CROSS -> Math.abs(x-50)<10||Math.abs(y-46)<9;
            case SALTIRE -> Math.abs(x-y)<11||Math.abs(100-x-y)<11;
        };
        if (ordinary) color=a.ordinaryTincture().rgb;
        for (var charge:a.charges()) {
            double dx=(x-charge.x())*100/charge.scale(),dy=(y-charge.y())*125/charge.scale();
            for (int turn=0;turn<charge.rotation();turn++) { double old=dx; dx=dy; dy=-old; }
            if (charge.mirrored()) dx=-dx;
            if (shapes.get("charges/"+charge.emblem().id()).contains(dx+50,dy+50)) color=charge.tincture().rgb;
        }
        return color;
    }
    private static int abgr(int rgb) { return 0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255); }
    public static void draw(GuiGraphics graphics,Heraldry arms,int x,int y,int width,int height) {
        graphics.blit(INSTANCE.texture(arms,false),x,y,width,height,0,0,WIDTH,HEIGHT,WIDTH,HEIGHT);
    }
}
