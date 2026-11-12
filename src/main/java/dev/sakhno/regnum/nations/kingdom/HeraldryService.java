package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.block.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Political authorization and server-only banner seals. A captured item keeps its original identity. */
public final class HeraldryService {
    private final KingdomSavedData data;
    public HeraldryService(KingdomSavedData data) { this.data=data; }
    public boolean canEdit(UUID kingdom,UUID player) { return KingdomPermissions.allows(data,kingdom,player,KingdomPermission.MANAGE_HERALDRY); }
    public void save(UUID kingdom,UUID player,Heraldry arms) throws FoundingException {
        data.requireAvailable();
        if (!canEdit(kingdom,player)) throw new FoundingException(FoundingException.Reason.KINGDOM_ROLE_FORBIDDEN);
        data.setHeraldry(kingdom,Objects.requireNonNull(arms));
    }
    /** Seals exactly one unsealed main-hand banner; the crafting recipe pays the material cost. */
    public void seal(ServerPlayer player,UUID kingdom) throws FoundingException {
        data.requireAvailable();
        if (!canEdit(kingdom,player.getUUID())) throw new FoundingException(FoundingException.Reason.KINGDOM_ROLE_FORBIDDEN);
        var held=player.getMainHandItem();
        if (!held.is(ModBlocks.ROYAL_BANNER_ITEM)||held.getCount()!=1||identity(held)!=null)
            throw new IllegalArgumentException("Hold one unsealed royal banner");
        held.set(DataComponents.CUSTOM_DATA,signedData(kingdom));
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }
    public ItemStack banner(UUID kingdom) throws FoundingException {
        var stack=new ItemStack(ModBlocks.ROYAL_BANNER_ITEM); stack.set(DataComponents.CUSTOM_DATA,signedData(kingdom)); return stack;
    }
    private CustomData signedData(UUID kingdom) throws FoundingException {
        var tag=new CompoundTag(); tag.putUUID("KingdomId",kingdom); tag.putString("RoyalSeal",signature(kingdom)); return CustomData.of(tag);
    }
    public static UUID identity(ItemStack stack) {
        var custom=stack.get(DataComponents.CUSTOM_DATA);
        if (custom==null) return null;
        var tag=custom.copyTag(); return tag.hasUUID("KingdomId")?tag.getUUID("KingdomId"):null;
    }
    public UUID verifiedIdentity(ItemStack stack) throws FoundingException {
        data.requireAvailable(); UUID id=identity(stack);
        if (!stack.is(ModBlocks.ROYAL_BANNER_ITEM)||id==null) return null;
        var tag=stack.get(DataComponents.CUSTOM_DATA).copyTag(); String seal=tag.getString("RoyalSeal");
        if (seal.length()!=64) return null;
        return MessageDigest.isEqual(signature(id).getBytes(StandardCharsets.US_ASCII),seal.getBytes(StandardCharsets.US_ASCII))?id:null;
    }
    private String signature(UUID kingdom) throws FoundingException {
        try {
            var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(data.bannerSecret(),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("regnum-royal-banner-v1:"+kingdom).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
}
