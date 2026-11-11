package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** City administration and personal ownership are independent of kingdom leadership. */
public final class SettlementService {
    private final KingdomService kingdoms;
    private final MinecraftServer server;
    private final KingdomSavedData data;
    private final Map<UUID,Pending> pending=new HashMap<>();
    private final Map<UUID,Invitation> invitations=new HashMap<>();
    private record Pending(ServerPlayer player,ResourceKey<Level> dimension,BlockPos pos,UUID obelisk,long at) { }
    private record Invitation(ServerPlayer recipient,UUID kingdom,UUID leader,long at) { }
    SettlementService(KingdomService kingdoms,MinecraftServer server,KingdomSavedData data) { this.kingdoms=kingdoms; this.server=server; this.data=data; }
    void forget(UUID player) { pending.remove(player); }
    void disconnect(UUID player) { pending.remove(player); invitations.remove(player); invitations.values().removeIf(i -> i.leader().equals(player)); }
    public Settlement owned(ServerPlayer player) throws FoundingException {
        kingdoms.validatePlayer(player);
        var cities=data.ownedSettlements(player.getUUID());
        if (cities.size()>1) throw new FoundingException(DOMAIN_REPAIR);
        if (cities.isEmpty()) throw new FoundingException(NO_KINGDOM);
        return cities.getFirst();
    }
    public void select(ServerPlayer player,BlockPos pos) throws FoundingException {
        kingdoms.forget(player.getUUID()); kingdoms.validatePlayer(player);
        if (data.byOwner(player.getUUID())!=null) throw new FoundingException(DOMAIN_LIMIT);
        if (data.citizenCity(player.getUUID())!=null) throw new FoundingException(ALREADY_CITIZEN);
        KingdomService.checkDistance(player,pos);
        CityObeliskBlockEntity block=cityBlock(player,pos);
        if (block.getSettlementId()!=null||data.anchorAt(player.level().dimension(),pos)||data.usesObelisk(block.getObeliskId())) throw new FoundingException(ALREADY_BOUND);
        long now=server.overworld().getGameTime(); pending.values().removeIf(p -> now-p.at()>=KingdomService.SELECTION_TICKS);
        pending.put(player.getUUID(),new Pending(player,player.level().dimension(),pos.immutable(),block.getObeliskId(),now));
    }
    public Settlement found(ServerPlayer player,UUID expected,String input,int color) throws FoundingException {
        kingdoms.data(); Pending selection=pending.remove(player.getUUID()); kingdoms.validatePlayer(player);
        if (data.byOwner(player.getUUID())!=null) throw new FoundingException(DOMAIN_LIMIT);
        if (data.citizenCity(player.getUUID())!=null) throw new FoundingException(ALREADY_CITIZEN);
        if (selection==null||selection.player()!=player||!selection.obelisk().equals(expected)) throw new FoundingException(NO_SELECTION);
        long age=server.overworld().getGameTime()-selection.at();
        if (age<0||age>=KingdomService.SELECTION_TICKS) throw new FoundingException(EXPIRED);
        if (!player.level().dimension().equals(selection.dimension())) throw new FoundingException(WRONG_DIMENSION);
        KingdomService.checkDistance(player,selection.pos());
        CityObeliskBlockEntity block=cityBlock(player,selection.pos());
        if (!block.getObeliskId().equals(expected)) throw new FoundingException(OBELISK_UNAVAILABLE);
        if (block.getSettlementId()!=null) throw new FoundingException(ALREADY_BOUND);
        if (!PoliticalColors.valid(color)) throw new FoundingException(INVALID_COLOR);
        KingdomName name; try { name=new KingdomName(input); } catch (IllegalArgumentException e) { throw new FoundingException(INVALID_NAME); }
        UUID id; do { id=UUID.randomUUID(); } while (data.settlement(id)!=null||data.byId(id)!=null||data.deletedAnchor(id)!=null||id.equals(expected));
        Settlement city=new Settlement(id,name,player.getUUID(),player.getUUID(),selection.dimension(),selection.pos(),expected,
                System.currentTimeMillis(),Kingdom.CapitalStatus.PRESENT,null,color,false);
        data.addSettlement(city); block.bindSettlement(id); return city;
    }
    private CityObeliskBlockEntity cityBlock(ServerPlayer player,BlockPos pos) throws FoundingException {
        var chunk=player.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if (chunk==null||!chunk.getBlockState(pos).is(ModBlocks.CITY_OBELISK)||!(chunk.getBlockEntity(pos) instanceof CityObeliskBlockEntity block)||block.isRemoved()) throw new FoundingException(OBELISK_UNAVAILABLE);
        return block;
    }
    public Kingdom.CapitalStatus resolve(Settlement city) {
        var level=server.getLevel(city.dimension()); var status=Kingdom.CapitalStatus.UNRESOLVED;
        if (level!=null) {
            var chunk=level.getChunkSource().getChunkNow(city.obeliskPos().getX()>>4,city.obeliskPos().getZ()>>4);
            if (chunk!=null) {
                status=Kingdom.CapitalStatus.MISSING;
                if (chunk.getBlockEntity(city.obeliskPos()) instanceof KingdomObeliskBlockEntity block && !block.isRemoved()
                        && block.getObeliskId().equals(city.obeliskId())) {
                    if (city.legacyAnchor()&&chunk.getBlockState(city.obeliskPos()).is(ModBlocks.KINGDOM_OBELISK)) {
                        kingdoms.cleanDeletedBinding(level,city.obeliskPos(),block);
                        if (block.getKingdomId()==null||Objects.equals(block.getKingdomId(),city.sovereign())) status=Kingdom.CapitalStatus.PRESENT;
                    } else if (!city.legacyAnchor()&&block instanceof CityObeliskBlockEntity cityBlock&&chunk.getBlockState(city.obeliskPos()).is(ModBlocks.CITY_OBELISK)
                            && (cityBlock.getSettlementId()==null||cityBlock.getSettlementId().equals(city.id()))) {
                        if (cityBlock.getSettlementId()==null) cityBlock.bindSettlement(city.id()); status=Kingdom.CapitalStatus.PRESENT;
                    }
                }
            }
        }
        data.settlementStatus(city,status); return status;
    }
    Settlement stateAnchor(ServerPlayer player,BlockPos statePos) throws FoundingException {
        Settlement city=owned(player);
        if (city.sovereign()!=null) throw new FoundingException(ALREADY_AFFILIATED);
        int distance=city.obeliskPos().distManhattan(statePos);
        if (!city.dimension().equals(player.level().dimension())||!ClaimPos.at(city.dimension(),city.obeliskPos()).equals(ClaimPos.at(city.dimension(),statePos))
                || !(distance==1||distance==0&&city.legacyAnchor())) throw new FoundingException(STATE_ANCHOR);
        if (resolve(city)!=Kingdom.CapitalStatus.PRESENT) throw new FoundingException(OBELISK_UNAVAILABLE);
        if (!city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),city.obeliskPos())))) throw new FoundingException(CAPITAL_CONFLICT);
        return city;
    }
    public UUID invite(ServerPlayer leader,ServerPlayer recipient) throws FoundingException {
        kingdoms.validatePlayer(leader); owned(recipient);
        Kingdom kingdom=data.byFounder(leader.getUUID());
        if (kingdom==null) throw new FoundingException(NO_KINGDOM);
        Settlement city=owned(recipient); if (city.sovereign()!=null) throw new FoundingException(ALREADY_AFFILIATED);
        long now=server.overworld().getGameTime(); invitations.values().removeIf(i -> now-i.at()>=KingdomService.SELECTION_TICKS);
        invitations.put(recipient.getUUID(),new Invitation(recipient,kingdom.id(),leader.getUUID(),now)); return kingdom.id();
    }
    public void join(ServerPlayer player,UUID kingdomId) throws FoundingException {
        Invitation invitation=invitations.remove(player.getUUID()); Settlement city=owned(player);
        long age=invitation==null?-1:server.overworld().getGameTime()-invitation.at();
        Kingdom kingdom=data.byId(kingdomId);
        if (invitation==null||invitation.recipient()!=player||!invitation.kingdom().equals(kingdomId)||age<0||age>=KingdomService.SELECTION_TICKS
                ||kingdom==null||!kingdom.founderId().equals(invitation.leader())) throw new FoundingException(INVITATION_REQUIRED);
        data.affiliate(city,kingdomId);
    }
}
