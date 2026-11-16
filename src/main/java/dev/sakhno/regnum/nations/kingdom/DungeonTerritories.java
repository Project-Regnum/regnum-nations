package dev.sakhno.regnum.nations.kingdom;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Optional World integration. Fail closed when an installed provider cannot be read. */
public final class DungeonTerritories {
    private static Class<?> provider() throws ClassNotFoundException {return Class.forName("zooller.regnumworld.dungeon.Dungeons");}
    public static boolean neutral(MinecraftServer server,String dimension,int x,int z) {
        if(!FabricLoader.getInstance().isModLoaded("regnum-world"))return false;
        try{return (boolean)provider().getMethod("neutral",MinecraftServer.class,String.class,int.class,int.class).invoke(null,server,dimension,x,z);}
        catch(Exception e){throw new IllegalStateException("Dungeon territory provider failed",e);}
    }
    public static ListTag snapshot(MinecraftServer server) {
        if(!FabricLoader.getInstance().isModLoaded("regnum-world"))return new ListTag();
        try{return (ListTag)provider().getMethod("snapshot",MinecraftServer.class).invoke(null,server);}
        catch(Exception e){throw new IllegalStateException("Dungeon atlas provider failed",e);}
    }
    public static boolean inside(ServerLevel world,net.minecraft.core.BlockPos pos) {
        if(!FabricLoader.getInstance().isModLoaded("regnum-world"))return false;
        try{return provider().getMethod("at",net.minecraft.world.level.Level.class,net.minecraft.core.BlockPos.class).invoke(null,world,pos)!=null;}
        catch(Exception e){throw new IllegalStateException("Dungeon region provider failed",e);}
    }
    public static boolean unclaimed(ServerLevel world,int x,int z) {
        var data=KingdomService.get(world.getServer()).data();
        return data.available()&&data.claimOwner(new ClaimPos(world.dimension(),x,z))==null;
    }
}
