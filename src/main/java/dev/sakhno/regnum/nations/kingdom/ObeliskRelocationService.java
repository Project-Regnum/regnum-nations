package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** Server-thread two-phase transfer. SavedData is the authority if chunk saves disagree after a crash. */
public final class ObeliskRelocationService {
    static final long REQUEST_NANOS = 30_000_000_000L;
    private final KingdomService kingdoms;
    private final MinecraftServer server;
    private final KingdomSavedData data;
    private final LongSupplier clock;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private BlockPos removingSource;
    private ResourceKey<Level> removingDimension;
    private record Anchor(Settlement city, Kingdom state, ResourceKey<Level> dimension, BlockPos pos, UUID identity, UUID binding, boolean cityBlock) { }
    private record Pending(ServerPlayer player, UUID token, UUID target, boolean state, Anchor source, BlockPos destination, long at) { }

    ObeliskRelocationService(KingdomService kingdoms, MinecraftServer server, LongSupplier clock) {
        this.kingdoms=kingdoms; this.server=server; this.data=kingdoms.data(); this.clock=clock;
    }
    void disconnect(UUID player) { pending.remove(player); }
    private void prune() { long now=clock.getAsLong(); pending.values().removeIf(p -> now-p.at()<0||now-p.at()>=REQUEST_NANOS); }

    /** A live reservation cannot be replaced or extended by repeated previews. */
    public UUID select(ServerPlayer player, UUID target, boolean state, ResourceKey<Level> dimension, BlockPos destination) throws FoundingException {
        kingdoms.validatePlayer(player); prune();
        if (pending.containsKey(player.getUUID())) throw new FoundingException(RELOCATION_PENDING);
        Anchor anchor=anchor(target,state);
        authorize(player,anchor);
        validate(player,anchor,dimension,destination);
        for (Pending other:pending.values()) {
            if (other.source().identity().equals(anchor.identity())
                    || other.source().dimension().equals(dimension)&&other.destination().equals(destination)) throw new FoundingException(RELOCATION_PENDING);
        }
        UUID token=UUID.randomUUID();
        pending.put(player.getUUID(),new Pending(player,token,target,state,anchor,destination.immutable(),clock.getAsLong()));
        return token;
    }

    /** Consumes failed confirmations too; rechecks every mutable input immediately before any writes. */
    public void commit(ServerPlayer player, UUID token) throws FoundingException {
        kingdoms.validatePlayer(player);
        Pending request=pending.remove(player.getUUID());
        if (request==null||request.player()!=player||!request.token().equals(token)) throw new FoundingException(RELOCATION_EXPIRED);
        long age=clock.getAsLong()-request.at();
        if (age<0||age>=REQUEST_NANOS) throw new FoundingException(RELOCATION_EXPIRED);
        Anchor anchor=anchor(request.target(),request.state()); authorize(player,anchor);
        if (!anchor.equals(request.source())) throw new FoundingException(RELOCATION_CONFLICT);
        validate(player,anchor,player.level().dimension(),request.destination());
        ServerLevel level=server.getLevel(anchor.dimension());
        BlockState original=level.getBlockState(anchor.pos());
        var originalNbt=block(level,anchor.pos(),anchor.cityBlock()).saveWithoutMetadata(level.registryAccess());
        // Suppress neighbor callbacks until the block, entity and registry agree. No drops/items are created.
        if (!level.setBlock(request.destination(),original,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) throw new FoundingException(RELOCATION_DESTINATION);
        removingSource=anchor.pos(); removingDimension=anchor.dimension();
        boolean sourceRemoved=false;
        try {
            KingdomObeliskBlockEntity destination=block(level,request.destination(),anchor.cityBlock());
            install(destination,anchor);
            if (!level.setBlock(anchor.pos(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) throw new FoundingException(OBELISK_UNAVAILABLE);
            sourceRemoved=true;
            // All fallible block writes precede the single registry update.
            data.relocate(anchor.city(),anchor.state(),request.destination());
        } catch (FoundingException | RuntimeException failure) {
            if (sourceRemoved) {
                level.setBlock(anchor.pos(),original,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                var restored=block(level,anchor.pos(),anchor.cityBlock());
                restored.loadWithComponents(originalNbt,level.registryAccess()); restored.setChanged();
            }
            level.setBlock(request.destination(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            throw failure;
        } finally { removingSource=null; removingDimension=null; }
        level.updateNeighborsAt(anchor.pos(),original.getBlock());
        level.updateNeighborsAt(request.destination(),original.getBlock());
        KingdomManagementServer.invalidate(server);
    }

    private Anchor anchor(UUID target, boolean state) throws FoundingException {
        data.requireAvailable();
        Kingdom kingdom=state?data.byId(target):null;
        Settlement city=state?(kingdom==null?null:data.settlement(kingdom.capitalSettlementId())):data.settlement(target);
        if (city==null||state&&kingdom==null) throw new FoundingException(CITY_MISSING);
        // Historical combined city/state obelisks must move both references together.
        if (city.legacyAnchor()) {
            for (Kingdom candidate:data.kingdoms()) if (candidate.capitalSettlementId().equals(city.id())) kingdom=candidate;
        }
        if (kingdom!=null&&(!kingdom.dimension().equals(city.dimension())||!kingdom.id().equals(city.sovereign()))) throw new FoundingException(RELOCATION_CONFLICT);
        boolean cityBlock=!state&&!city.legacyAnchor();
        BlockPos pos=cityBlock||kingdom==null?city.obeliskPos():kingdom.capitalPos();
        UUID identity=cityBlock||kingdom==null?city.obeliskId():kingdom.capitalObeliskId();
        UUID binding=cityBlock?city.id():kingdom==null?null:kingdom.id();
        return new Anchor(cityBlock||city.legacyAnchor()?city:null,kingdom,city.dimension(),pos,identity,binding,cityBlock);
    }
    private Settlement owner(Anchor anchor) {
        return anchor.city()!=null?anchor.city():data.settlement(anchor.state().capitalSettlementId());
    }
    private void authorize(ServerPlayer player,Anchor anchor) throws FoundingException {
        Settlement city=owner(anchor);
        if (kingdoms.warfare().blocksRelocation(city.id())) throw new FoundingException(RELOCATION_CONFLICT);
        if (!city.ownerId().equals(player.getUUID())||anchor.state()!=null&&!anchor.state().rulerId().equals(player.getUUID())) throw new FoundingException(NOT_OWNER);
        if (data.ownedSettlements(player.getUUID()).size()!=1) throw new FoundingException(DOMAIN_REPAIR);
    }
    private void validate(ServerPlayer player,Anchor anchor,ResourceKey<Level> dimension,BlockPos destination) throws FoundingException {
        if (!anchor.dimension().equals(dimension)||!player.level().dimension().equals(dimension)) throw new FoundingException(WRONG_DIMENSION);
        ServerLevel level=server.getLevel(dimension);
        KingdomService.checkDistance(player,destination);
        if (level==null||!level.isInWorldBounds(destination)||!level.getWorldBorder().isWithinBounds(destination)
                || level.getChunkSource().getChunkNow(destination.getX()>>4,destination.getZ()>>4)==null) throw new FoundingException(RELOCATION_DESTINATION);
        Settlement city=owner(anchor);
        if (!city.id().equals(data.claimOwner(ClaimPos.at(dimension,anchor.pos())))
                ||!city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),city.obeliskPos())))) throw new FoundingException(CAPITAL_CONFLICT);
        if (!city.id().equals(data.claimOwner(ClaimPos.at(dimension,destination)))) throw new FoundingException(RELOCATION_DESTINATION);
        Kingdom capital=anchor.state();
        if (capital==null) for (Kingdom candidate:data.kingdoms()) if (candidate.capitalSettlementId().equals(city.id())) capital=candidate;
        if (capital!=null&&!ClaimPos.at(capital.dimension(),capital.capitalPos()).equals(ClaimPos.at(dimension,destination))) throw new FoundingException(RELOCATION_CAPITAL);
        if (data.anchorAt(dimension,destination)||!level.getBlockState(destination).isAir()||level.getBlockEntity(destination)!=null) throw new FoundingException(RELOCATION_CONFLICT);
        KingdomObeliskBlockEntity source=block(level,anchor.pos(),anchor.cityBlock());
        if (!source.getObeliskId().equals(anchor.identity())||!Objects.equals(source.getKingdomId(),anchor.binding())) throw new FoundingException(RELOCATION_CONFLICT);
    }
    private KingdomObeliskBlockEntity block(ServerLevel level,BlockPos pos,boolean city) throws FoundingException {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if (chunk==null||!chunk.getBlockState(pos).is(city?ModBlocks.CITY_OBELISK:ModBlocks.KINGDOM_OBELISK)
                ||!(chunk.getBlockEntity(pos) instanceof KingdomObeliskBlockEntity block)||block.isRemoved()
                ||city!=(block instanceof CityObeliskBlockEntity)) throw new FoundingException(OBELISK_UNAVAILABLE);
        return block;
    }
    private static void install(KingdomObeliskBlockEntity block,Anchor anchor) {
        // Restore only in this service after validating a fresh destination or an explicit admin recovery.
        var tag=block.saveWithoutMetadata(block.getLevel().registryAccess());
        tag.putUUID("ObeliskId",anchor.identity()); tag.remove("KingdomId"); tag.remove("SettlementId");
        if (anchor.binding()!=null) tag.putUUID(anchor.cityBlock()?"SettlementId":"KingdomId",anchor.binding());
        block.loadWithComponents(tag,block.getLevel().registryAccess()); block.setChanged();
    }

    /** Recovery restores the persisted canonical location only. It never chooses a new political anchor. */
    public void recover(CommandSourceStack source,UUID target,boolean state) throws FoundingException,CommandSyntaxException {
        KingdomService.requireAdministrator(source);
        if (source.getServer()!=server) throw KingdomService.adminError("permission");
        Anchor anchor=anchor(target,state); ServerLevel level=server.getLevel(anchor.dimension());
        if (level==null||level.getChunkSource().getChunkNow(anchor.pos().getX()>>4,anchor.pos().getZ()>>4)==null) throw new FoundingException(OBELISK_UNAVAILABLE);
        Settlement city=owner(anchor);
        if (!city.id().equals(data.claimOwner(ClaimPos.at(anchor.dimension(),anchor.pos())))
                ||data.ownedSettlements(city.ownerId()).size()!=1) throw new FoundingException(CAPITAL_CONFLICT);
        Settlement atCity=data.cityAt(anchor.dimension(),anchor.pos()); Kingdom atState=data.atCapital(anchor.dimension(),anchor.pos());
        if (atCity!=null&&(anchor.city()==null||!atCity.id().equals(anchor.city().id()))
                ||atState!=null&&(anchor.state()==null||!atState.id().equals(anchor.state().id()))) throw new FoundingException(RELOCATION_CONFLICT);
        Block expected=anchor.cityBlock()?ModBlocks.CITY_OBELISK:ModBlocks.KINGDOM_OBELISK;
        BlockState existing=level.getBlockState(anchor.pos());
        if (!existing.isAir()&&!existing.is(expected)) throw new FoundingException(RELOCATION_CONFLICT);
        var entity=level.getBlockEntity(anchor.pos());
        if (entity!=null) {
            if (!(entity instanceof KingdomObeliskBlockEntity block)||anchor.cityBlock()!=(block instanceof CityObeliskBlockEntity)) throw new FoundingException(RELOCATION_CONFLICT);
            if (block.getKingdomId()!=null&&!Objects.equals(block.getKingdomId(),anchor.binding())
                    ||!block.getObeliskId().equals(anchor.identity())&&(block.getKingdomId()!=null||data.usesObelisk(block.getObeliskId()))) throw new FoundingException(RELOCATION_CONFLICT);
        }
        // Reject registry conflicts with another canonical anchor using this same identity.
        for (Settlement other:data.settlements()) if (other.obeliskId().equals(anchor.identity())&&(anchor.city()==null||!other.id().equals(anchor.city().id()))) throw new FoundingException(RELOCATION_CONFLICT);
        for (Kingdom other:data.kingdoms()) if (other.capitalObeliskId().equals(anchor.identity())&&(anchor.state()==null||!other.id().equals(anchor.state().id()))) throw new FoundingException(RELOCATION_CONFLICT);
        if (existing.isAir()) level.setBlock(anchor.pos(),expected.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(anchor.pos())==null) level.setBlockEntity(((dev.sakhno.regnum.nations.block.KingdomObeliskBlock)expected).newBlockEntity(anchor.pos(),expected.defaultBlockState()));
        install(block(level,anchor.pos(),anchor.cityBlock()),anchor);
        if (anchor.city()!=null) data.settlementStatus(anchor.city(),Kingdom.CapitalStatus.PRESENT);
        if (anchor.state()!=null) data.capitalStatus(anchor.state(),Kingdom.CapitalStatus.PRESENT);
        KingdomManagementServer.invalidate(server);
    }

    /** Only the service's source removal skips ordinary missing-anchor notification. */
    public boolean transferring(ServerLevel level,BlockPos pos) {
        return pos.equals(removingSource)&&level.dimension().equals(removingDimension);
    }

    /** Position-based protection survives missing or invalid block entities. */
    public static boolean protectedAnchor(ServerLevel level,BlockPos pos) {
        var data=KingdomService.get(level.getServer()).data();
        try { data.requireAvailable(); }
        catch (FoundingException e) { return level.getBlockState(pos).getBlock() instanceof dev.sakhno.regnum.nations.block.KingdomObeliskBlock; }
        return data.anchorAt(level.dimension(),pos);
    }
}
