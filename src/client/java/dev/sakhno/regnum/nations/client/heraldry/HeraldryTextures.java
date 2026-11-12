package dev.sakhno.regnum.nations.client.heraldry;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.Heraldry;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import java.util.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** One deterministic compositor for shield and cloth representations. Cached GPU textures never touch disk. */
public final class HeraldryTextures implements SimpleSynchronousResourceReloadListener {
    public static final HeraldryTextures INSTANCE=new HeraldryTextures();
    private static final int WIDTH=256,HEIGHT=320,CLOTH_HEIGHT=384,SAMPLES=2,MAX_TEXTURES=256;
    // Native pixels and GPU storage are each bounded to 48 MiB, even during live editing.
    private static final long MAX_BYTES=48L*1024*1024;
    private record Transform(double xx,double xy,double yx,double yy,double tx,double ty) {
        static Transform shield(boolean cloth) {
            return cloth?new Transform(.84,0,0,.70,8,8):new Transform(1,0,0,1,0,0);
        }
        Transform charge(Charge charge) {
            double xx=charge.mirrored()?-1:1,xy=0,yx=0,yy=1;
            for (int turn=0;turn<charge.rotation();turn++) {
                double oldX=xx,oldY=xy; xx=-yx; xy=-yy; yx=oldX; yy=oldY;
            }
            double sx=charge.scale()/100.0,sy=charge.scale()/125.0;
            return new Transform(this.xx*sx*xx,this.xx*sx*xy,this.yy*sy*yx,this.yy*sy*yy,
                    tx+this.xx*(charge.x()-sx*(xx+xy)*50),
                    ty+this.yy*(charge.y()-sy*(yx+yy)*50));
        }
    }
    private record Shape(List<double[]> solid,List<double[]> holes) {
        BitSet raster(int width,int height,Transform transform) {
            var mask=new BitSet(width*height);
            for (var polygon:solid) scan(mask,polygon,width,height,transform,true);
            for (var polygon:holes) scan(mask,polygon,width,height,transform,false);
            return mask;
        }
        private static void scan(BitSet mask,double[] polygon,int width,int height,Transform t,boolean fill) {
            double[] points=new double[polygon.length],crossings=new double[polygon.length/2];
            double minY=height,maxY=0;
            for (int i=0;i<points.length;i+=2) {
                points[i]=(t.xx*polygon[i]+t.xy*polygon[i+1]+t.tx)*width/100;
                points[i+1]=(t.yx*polygon[i]+t.yy*polygon[i+1]+t.ty)*height/100;
                minY=Math.min(minY,points[i+1]); maxY=Math.max(maxY,points[i+1]);
            }
            int start=Math.max(0,(int)Math.ceil(minY-.5)),end=Math.min(height,(int)Math.ceil(maxY-.5));
            for (int row=start;row<end;row++) {
                double y=row+.5; int count=0;
                for (int i=0,j=points.length-2;i<points.length;j=i,i+=2) {
                    if ((points[i+1]>y)!=(points[j+1]>y))
                        crossings[count++]=points[i]+(points[j]-points[i])*(y-points[i+1])/(points[j+1]-points[i+1]);
                }
                Arrays.sort(crossings,0,count);
                for (int i=0;i+1<count;i+=2) {
                    int left=Math.max(0,(int)Math.ceil(crossings[i]-.5));
                    int right=Math.min(width,(int)Math.ceil(crossings[i+1]-.5));
                    if (left<right) mask.set(row*width+left,row*width+right,fill);
                }
            }
        }
    }
    private record Key(Heraldry arms,boolean cloth) { }
    private final Map<String,Shape> shapes=new HashMap<>();
    private record Cached(ResourceLocation id,int bytes) { }
    private static final class HeraldryTexture extends DynamicTexture {
        private final boolean cloth;
        HeraldryTexture(NativeImage pixels,boolean cloth) {
            super(pixels); this.cloth=cloth;
            if (cloth) {
                var levels=MipmapGenerator.generateMipLevels(new NativeImage[]{pixels},4);
                try {
                    TextureUtil.prepareImage(getId(),4,pixels.getWidth(),pixels.getHeight());
                    for (int i=0;i<levels.length;i++) levels[i].upload(i,0,0,false);
                } finally {
                    // Only the base image remains on the CPU after upload.
                    for (int i=1;i<levels.length;i++) levels[i].close();
                }
            }
            setFilter(false,cloth);
        }
        @Override public void setFilter(boolean blur,boolean mipmap) {
            // Entity RenderTypes request unmipped filtering by default. Keep our prepared
            // cloth mip chain active for stable silhouettes at oblique angles and distance.
            super.setFilter(false,cloth);
        }
    }
    private final LinkedHashMap<Key,Cached> cache=new LinkedHashMap<>(16,.75f,true);
    private final Deque<ResourceLocation> freeSlots=new ArrayDeque<>();
    private long cachedBytes;
    private int nextSlot;
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
    public void clear() {
        var manager=Minecraft.getInstance().getTextureManager();
        cache.values().forEach(entry -> manager.release(entry.id)); cache.clear();
        freeSlots.clear(); cachedBytes=0; nextSlot=0;
    }
    public ResourceLocation texture(Heraldry arms,boolean cloth) {
        var key=new Key(arms,cloth); var cached=cache.get(key); if (cached!=null) return cached.id;
        int bytes=WIDTH*(cloth?CLOTH_HEIGHT:HEIGHT)*4*(cloth?341:256)/256;
        var client=Minecraft.getInstance();
        if (cache.size()>=MAX_TEXTURES||cachedBytes+bytes>MAX_BYTES) {
            // Submit pending vertices before a bounded texture/RenderType slot is reused.
            client.renderBuffers().bufferSource().endBatch();
            client.renderBuffers().outlineBufferSource().endOutlineBatch();
            var iterator=cache.entrySet().iterator();
            while (cache.size()>=MAX_TEXTURES||cachedBytes+bytes>MAX_BYTES) {
                var old=iterator.next().getValue();
                client.getTextureManager().release(old.id); freeSlots.addLast(old.id);
                cachedBytes-=old.bytes; iterator.remove();
            }
        }
        ResourceLocation id=freeSlots.isEmpty()?RegnumNations.id("heraldry/cache/"+nextSlot++):freeSlots.removeFirst();
        var pixels=raster(arms,cloth); var texture=new HeraldryTexture(pixels,cloth);
        // Supersampled silhouettes with crisp magnification and mipmapped cloth minification.
        client.getTextureManager().register(id,texture); cache.put(key,new Cached(id,bytes)); cachedBytes+=bytes;
        return id;
    }
    /** The caller owns the returned image. Rasterization occurs only on a design/cache miss. */
    public NativeImage raster(Heraldry arms,boolean cloth) {
        int height=cloth?CLOTH_HEIGHT:HEIGHT,w=WIDTH*SAMPLES,h=height*SAMPLES;
        Shape shield=shapes.get("shields/"+arms.shield().id());
        if (shield==null) throw new IllegalStateException("Heraldry resources have not loaded");
        var transform=Transform.shield(cloth);
        BitSet silhouette=shield.raster(w,h,transform),interior=new BitSet(w*h);
        int[] pixels=new int[w*h];
        int borderX=Math.max(1,(int)(w*transform.xx*.012)),borderY=Math.max(1,(int)(h*transform.yy*.010));
        for (int py=0;py<h;py++) for (int px=0;px<w;px++) {
            int i=py*w+px;
            double x=(px+.5)*100/w,y=(py+.5)*100/h;
            double ending=100-Math.max(0,18-Math.abs(x-50)*.36);
            if (cloth&&(x<2||x>98||y>ending)) continue;
            if (!silhouette.get(i)) {
                if (cloth) pixels[i]=abgr(x<3.2||x>96.8||y>ending-1.2?0xd6b775:arms.field().rgb);
                continue;
            }
            boolean edge=px<borderX||px>=w-borderX||py<borderY||py>=h-borderY
                    ||!silhouette.get(i-borderX)||!silhouette.get(i+borderX)
                    ||!silhouette.get(i-borderY*w)||!silhouette.get(i+borderY*w);
            pixels[i]=abgr(edge?0xd6b775:color(arms,(x-transform.tx)/transform.xx,(y-transform.ty)/transform.yy));
            if (!edge) interior.set(i);
        }
        for (var charge:arms.charges()) {
            BitSet mask=shapes.get("charges/"+charge.emblem().id()).raster(w,h,transform.charge(charge));
            mask.and(interior); int color=abgr(charge.tincture().rgb);
            for (int i=mask.nextSetBit(0);i>=0;i=mask.nextSetBit(i+1)) pixels[i]=color;
        }
        var image=new NativeImage(WIDTH,height,true);
        for (int py=0;py<height;py++) for (int px=0;px<WIDTH;px++) {
            int red=0,green=0,blue=0,coverage=0;
            for (int sy=0;sy<SAMPLES;sy++) for (int sx=0;sx<SAMPLES;sx++) {
                int color=pixels[(py*SAMPLES+sy)*w+px*SAMPLES+sx];
                if (color==0) continue;
                coverage++; red+=color&255; green+=(color>>>8)&255; blue+=(color>>>16)&255;
            }
            // Average covered samples only: transparent edges cannot introduce dark fringes.
            if (coverage>0) image.setPixelRGBA(px,py,((coverage*255/(SAMPLES*SAMPLES))<<24)
                    |(blue/coverage<<16)|(green/coverage<<8)|red/coverage);
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
        return color;
    }
    private static int abgr(int rgb) { return 0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255); }
    public static void draw(GuiGraphics graphics,Heraldry arms,int x,int y,int width,int height) {
        graphics.blit(INSTANCE.texture(arms,false),x,y,width,height,0,0,WIDTH,HEIGHT,WIDTH,HEIGHT);
    }
}
