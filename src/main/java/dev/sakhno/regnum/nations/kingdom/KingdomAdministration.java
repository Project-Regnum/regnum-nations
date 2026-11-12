package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.kingdom.KingdomPermission.*;

/** All administration mutations share current server-side political and municipal authorization. */
public final class KingdomAdministration {
    private final KingdomService kingdoms;
    private final KingdomSavedData data;
    KingdomAdministration(KingdomService kingdoms) { this.kingdoms=kingdoms; this.data=kingdoms.data(); }

    Kingdom authorized(ServerPlayer actor,UUID kingdom,KingdomPermission permission) throws FoundingException {
        kingdoms.validatePlayer(actor);
        Kingdom state=data.byId(kingdom);
        if (state==null) throw new FoundingException(NO_KINGDOM);
        if (!KingdomPermissions.allows(data,kingdom,actor.getUUID(),permission)) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        return state;
    }
    public Kingdom current(ServerPlayer actor) throws FoundingException {
        kingdoms.validatePlayer(actor);
        Kingdom state=data.playerKingdom(actor.getUUID());
        if (state==null) throw new FoundingException(NO_KINGDOM);
        return state;
    }
    public void assignRole(ServerPlayer actor,UUID kingdom,UUID target,KingdomRole role) throws FoundingException {
        authorized(actor,kingdom,role==KingdomRole.MEMBER?MANAGE_KINGDOM_MEMBERS:MANAGE_KINGDOM_ROLES);
        if (!KingdomPermissions.canAssign(data.kingdomRole(kingdom,actor.getUUID()),data.kingdomRole(kingdom,target),role)) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        var scope = dev.sakhno.regnum.nations.api.v1.EntityRef.kingdom(kingdom);
        if (!data.economyPermissions(scope, data.economyRole(scope, actor.getUUID()))
                .containsAll(data.economyPermissions(scope, role.name()))) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        data.setKingdomRole(kingdom,target,role);
    }
    public Kingdom rename(ServerPlayer actor,UUID kingdom,String name) throws FoundingException {
        authorized(actor,kingdom,MANAGE_KINGDOM);
        try { return data.rename(kingdom,new KingdomName(name)); }
        catch (IllegalArgumentException e) { throw new FoundingException(INVALID_NAME); }
    }
    /** Confirmation is issued/consumed by the snapshot adapter, bound to actor, ruler, target and session. */
    void transfer(ServerPlayer actor,UUID kingdom,UUID target) throws FoundingException {
        authorized(actor,kingdom,MANAGE_KINGDOM_ROLES);
        if (data.kingdomRole(kingdom,actor.getUUID())!=KingdomRole.RULER) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        data.transferRuler(kingdom,target);
    }
    public KingdomSavedData.CityInvitation inviteCity(ServerPlayer actor,UUID kingdom,UUID cityId) throws FoundingException {
        authorized(actor,kingdom,INVITE_CITY);
        if (!data.canInviteCity(kingdom, actor.getUUID())) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        Settlement city=data.settlement(cityId);
        if (city==null) throw new FoundingException(CITY_MISSING);
        if (city.sovereign()!=null) throw new FoundingException(ALREADY_AFFILIATED);
        var invitation=new KingdomSavedData.CityInvitation(UUID.randomUUID(),kingdom,cityId,actor.getUUID(),System.currentTimeMillis());
        data.addCityInvitation(invitation); return invitation;
    }
    private Settlement representative(ServerPlayer actor,UUID city) throws FoundingException {
        kingdoms.validatePlayer(actor);
        Settlement current=data.settlement(city);
        if (current==null) throw new FoundingException(CITY_MISSING);
        // Sovereignty changes remain the owner's decision; city officer permissions stay unchanged.
        if (!current.ownerId().equals(actor.getUUID())) throw new FoundingException(NOT_OWNER);
        return current;
    }
    public void answer(ServerPlayer actor,UUID invitationId,boolean accept) throws FoundingException {
        kingdoms.validatePlayer(actor);
        var invitation=data.cityInvitation(invitationId);
        if (invitation==null) throw new FoundingException(INVITATION_REQUIRED);
        Settlement city=representative(actor,invitation.city());
        if (city.sovereign()!=null) throw new FoundingException(ALREADY_AFFILIATED);
        if (!data.canInviteCity(invitation.kingdom(),invitation.inviter())) throw new FoundingException(INVITATION_REQUIRED);
        if (accept) data.affiliate(city,invitation.kingdom());
        else data.removeCityInvitation(invitationId);
    }
    public void cancel(ServerPlayer actor,UUID invitationId) throws FoundingException {
        kingdoms.validatePlayer(actor);
        var invitation=data.cityInvitation(invitationId);
        if (invitation==null) throw new FoundingException(INVITATION_REQUIRED);
        authorized(actor,invitation.kingdom(),INVITE_CITY);
        data.removeCityInvitation(invitationId);
    }
    public void leaveCity(ServerPlayer actor,UUID cityId) throws FoundingException {
        representative(actor,cityId); data.detachCity(cityId);
    }
    public void removeCity(ServerPlayer actor,UUID kingdom,UUID cityId) throws FoundingException {
        authorized(actor,kingdom,REMOVE_CITY);
        Settlement city=data.settlement(cityId);
        if (city==null) throw new FoundingException(CITY_MISSING);
        if (!kingdom.equals(city.sovereign())) throw new FoundingException(KINGDOM_NOT_MEMBER);
        data.detachCity(cityId);
    }
}
