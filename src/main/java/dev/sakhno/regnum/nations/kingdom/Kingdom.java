package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Immutable history. The placed obelisk's identity is deliberately separate from id. */
public record Kingdom(UUID id, KingdomName name, UUID founderId, ResourceKey<Level> dimension,
		BlockPos capitalPos, UUID capitalObeliskId, long createdAt, CapitalStatus capitalStatus, UUID capitalSettlementId, int color, String government, UUID rulerId) {
	public Kingdom {
		capitalPos = capitalPos.immutable();
		java.util.Objects.requireNonNull(rulerId, "Kingdom requires a ruler");
		if (!PoliticalColors.valid(color) || !government.equals("unspecified")) throw new IllegalArgumentException("Invalid kingdom politics");
	}

    /** Older records and founding callers start with the historical founder as ruler. */
    public Kingdom(UUID id, KingdomName name, UUID founderId, ResourceKey<Level> dimension,
            BlockPos pos, UUID obelisk, long createdAt, CapitalStatus status, UUID city, int color, String government) {
        this(id,name,founderId,dimension,pos,obelisk,createdAt,status,city,color,government,founderId);
    }

    public Kingdom withRuler(UUID ruler) {
        return new Kingdom(id,name,founderId,dimension,capitalPos,capitalObeliskId,createdAt,capitalStatus,capitalSettlementId,color,government,ruler);
    }

	/** Compatibility constructor for historical combined capital records. */
	public Kingdom(UUID id, KingdomName name, UUID founderId, ResourceKey<Level> dimension,
			BlockPos pos, UUID obelisk, long createdAt, CapitalStatus status) {
		this(id,name,founderId,dimension,pos,obelisk,createdAt,status,id,PoliticalColors.fallback(id),"unspecified");
	}

	public enum CapitalStatus { PRESENT, MISSING, UNRESOLVED }

    public Kingdom withCapitalPos(BlockPos pos) {
        return new Kingdom(id,name,founderId,dimension,pos,capitalObeliskId,createdAt,CapitalStatus.PRESENT,capitalSettlementId,color,government,rulerId);
    }

	public Kingdom withName(KingdomName name) {
		return new Kingdom(id, name, founderId, dimension, capitalPos, capitalObeliskId, createdAt, capitalStatus, capitalSettlementId, color, government, rulerId);
	}

	public Kingdom withCapitalStatus(CapitalStatus status) {
		return new Kingdom(id, name, founderId, dimension, capitalPos, capitalObeliskId, createdAt, status, capitalSettlementId, color, government, rulerId);
	}
}
