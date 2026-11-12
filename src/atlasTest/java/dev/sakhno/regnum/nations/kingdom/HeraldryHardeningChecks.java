package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.client.heraldry.*;
import dev.sakhno.regnum.nations.kingdom.Heraldry.*;
import dev.sakhno.regnum.nations.networking.HeraldryPayloads.*;
import static dev.sakhno.regnum.nations.networking.HeraldryPayloads.BATCH_SIZE;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Opt-in checks on the real render thread, texture manager and public packet codecs. */
final class HeraldryHardeningChecks {
    private HeraldryHardeningChecks() { }
    static void synchronization(Minecraft mc) {
        UUID stale=new UUID(1,1);
        for (int count:new int[]{0,1,32,33,64,129,300}) {
            var expected=new LinkedHashMap<UUID,Heraldry>();
            for (int i=0;i<count;i++) { var id=new UUID(0,i+1); expected.put(id,design(i)); }
            HeraldryClient.accept(new Sync(true,List.of(new Entry(stale,design(901)))));
            var entries=expected.entrySet().stream().map(e -> new Entry(e.getKey(),e.getValue())).toList();
            var packets=HeraldryServer.batches(true,entries);
            require(packets.size()==Math.max(1,(count+BATCH_SIZE-1)/BATCH_SIZE),"Bounded login batches: "+count);
            for (int i=0;i<packets.size();i++) {
                require(packets.get(i).reset()==(i==0),"Only the first login packet resets");
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.level.registryAccess());
                try {
                    Sync.CODEC.encode(buffer,packets.get(i));
                    require(buffer.readableBytes()<=2+BATCH_SIZE*(16+1+473),"Bounded public payload");
                    HeraldryClient.accept(Sync.CODEC.decode(buffer)); require(!buffer.isReadable(),"Codec consumed entire batch");
                } finally { buffer.release(); }
            }
            expected.forEach((id,arms) -> require(HeraldryClient.arms(id).equals(arms),"All login batches survive: "+count));
            require(!HeraldryClient.arms(stale).equals(design(901)),"Empty and nonempty logins clear previous server state");
            if (count>0) {
                var id=expected.keySet().iterator().next();
                for (var packet:HeraldryServer.batches(false,List.of(new Entry(id,design(902)),new Entry(stale,design(903))))) HeraldryClient.accept(packet);
                require(HeraldryClient.arms(id).equals(design(902))&&HeraldryClient.arms(stale).equals(design(903)),"Active edit and creation converge");
                HeraldryClient.accept(new Sync(false,List.of(new Entry(id,null))));
                require(!HeraldryClient.arms(id).equals(design(902)),"Deletion removes authoritative arms");
            }
        }
        require(HeraldryServer.batches(false,List.of()).isEmpty(),"Unchanged state sends no traffic");
        HeraldryClient.accept(new Sync(true,List.of()));
    }
    static Heraldry design(int i) {
        return new Heraldry(1,Shield.values()[i%5],Tincture.AZURE,Division.values()[i/5%6],Tincture.ARGENT,
                Ordinary.values()[i/30%7],Tincture.OR,List.of(new Charge(Emblem.values()[i/210%11],Tincture.OR,50,46,65,0,false)));
    }
    static void textures(Minecraft mc,Path output) throws Exception {
        var textures=HeraldryTextures.INSTANCE; textures.clear();
        var first=textures.texture(design(0),true);
        var original=(net.minecraft.client.renderer.texture.DynamicTexture)mc.getTextureManager().getTexture(first,null);
        for (int i=0;i<1000;i++) {
            var id=textures.texture(design(0),true);
            require(id.equals(first)&&mc.getTextureManager().getTexture(id,null)==original,"Identical arms reuse one native/GPU texture");
        }
        var expected=new ArrayList<net.minecraft.client.renderer.texture.AbstractTexture>();
        long start=System.nanoTime();
        for (int i=0;i<129;i++) expected.add(mc.getTextureManager().getTexture(textures.texture(design(i),true),null));
        long cold=System.nanoTime()-start; int recreated=0; start=System.nanoTime();
        for (int i=0;i<129;i++) if (expected.get(i)!=mc.getTextureManager().getTexture(textures.texture(design(i),true),null)) recreated++;
        long warm=System.nanoTime()-start;
        String result="129 distinct cloth representations: cold_ms="+cold/1_000_000.0+", second_pass_ms="+warm/1_000_000.0+", recreated="+recreated+"\n";
        Files.writeString(output.resolve("cache-measurements.txt"),result);
        dev.sakhno.regnum.nations.RegnumNations.LOGGER.info("HERALDRY CACHE MEASUREMENT {}",result.strip());
        if (!Boolean.getBoolean("regnum.cacheBaseline")) require(recreated==0,"129 visible designs must not rasterize again every frame");
        // Exceed the limit, exercise eviction and recreation, then prove resource-manager removal.
        var field=HeraldryTextures.class.getDeclaredField("cache"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var cache=(Map<Object,ResourceLocation>)field.get(textures);
        var bound=HeraldryTextures.class.getDeclaredField("MAX_TEXTURES"); bound.setAccessible(true); int limit=bound.getInt(null);
        for (int i=0;i<limit+17;i++) textures.texture(design(i),true);
        require(cache.size()==limit,"GPU cache remains bounded");
        require(original.getPixels()==null,"Eviction closes both the native pixels and the GPU texture");
        var recreatedFirst=textures.texture(design(0),true); require(mc.getTextureManager().getTexture(recreatedFirst,null)!=original,"Evicted design can be reconstructed in a recycled slot");
        var identifiers=new HashSet<ResourceLocation>(cache.values());
        for (int round=0;round<3;round++) {
            textures.clear();
            for (int i=0;i<limit+17;i++) {
                var id=textures.texture(design(i),true); identifiers.add(id);
                net.minecraft.client.renderer.RenderType.entityCutoutNoCull(id);
            }
        }
        require(identifiers.size()<=limit,"Eviction/reload cannot leak unbounded vanilla-memoized render types");
        var live=List.copyOf(cache.values()); textures.clear();
        require(cache.isEmpty()&&live.stream().allMatch(id -> mc.getTextureManager().getTexture(id,null)==null),"Clear releases every registered GPU texture");
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
