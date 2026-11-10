package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Personal domain and city administration; sovereignty never transfers this ownership. */
public record Settlement(UUID id, KingdomName name, UUID founderId, UUID ownerId, ResourceKey<Level> dimension,
                         BlockPos obeliskPos, UUID obeliskId, long createdAt, Kingdom.CapitalStatus status,
                         UUID sovereign, int color, boolean legacyAnchor) {
    public Settlement {
        obeliskPos=obeliskPos.immutable();
        if (!PoliticalColors.valid(color)) throw new IllegalArgumentException("Invalid settlement color");
    }
    public Settlement withSovereign(UUID kingdom) {
        return new Settlement(id,name,founderId,ownerId,dimension,obeliskPos,obeliskId,createdAt,status,kingdom,color,legacyAnchor);
    }
    public Settlement withStatus(Kingdom.CapitalStatus status) {
        return new Settlement(id,name,founderId,ownerId,dimension,obeliskPos,obeliskId,createdAt,status,sovereign,color,legacyAnchor);
    }
}
