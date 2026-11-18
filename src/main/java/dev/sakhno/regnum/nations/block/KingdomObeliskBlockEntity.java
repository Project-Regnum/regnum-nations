package dev.sakhno.regnum.nations.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class KingdomObeliskBlockEntity extends BlockEntity {
	private static final String ID_TAG = "ObeliskId";

	private UUID obeliskId;
	private UUID kingdomId;

	public KingdomObeliskBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlocks.KINGDOM_OBELISK_ENTITY, pos, state);
	}

	protected KingdomObeliskBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type,pos,state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		// Attachment can run during chunk post-load. setChanged() would request that same unfinished chunk.
		if (!level.isClientSide && obeliskId == null) obeliskId = UUID.randomUUID();
	}

	/**
	 * Returns the server's persistent identity, repairing missing data on an attached entity.
	 * Chunk loading restores NBT before attaching this entity to its level.
	 */
	public UUID getObeliskId() {
		if (level == null || level.isClientSide) {
			throw new IllegalStateException("Obelisk identity is only available on the logical server");
		}
		if (obeliskId == null) {
			obeliskId = UUID.randomUUID();
			setChanged();
		}
		return obeliskId;
	}

	public UUID getKingdomId() {
		return kingdomId;
	}

	/** Called only by the authoritative kingdom service, after all founding checks pass. */
	public void bindKingdom(UUID kingdomId) {
		if (level == null || level.isClientSide || !level.getServer().isSameThread()) {
			throw new IllegalStateException("Binding requires the logical server thread");
		}
		java.util.Objects.requireNonNull(kingdomId);
		if (kingdomId.equals(getObeliskId()) || (this.kingdomId != null && !this.kingdomId.equals(kingdomId))) {
			throw new IllegalStateException("Obelisk cannot be rebound");
		}
		this.kingdomId = kingdomId;
		setChanged();
	}

	protected String bindingTag() { return "KingdomId"; }
	public void clearKingdom(UUID expected) {
		if (level==null||level.isClientSide||!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
		if (java.util.Objects.equals(kingdomId,expected)) { kingdomId=null; setChanged(); }
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		obeliskId = tag.hasUUID(ID_TAG) ? tag.getUUID(ID_TAG) : null;
		kingdomId = tag.hasUUID(bindingTag()) ? tag.getUUID(bindingTag()) : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (obeliskId != null) {
			tag.putUUID(ID_TAG, obeliskId);
		}
		if (kingdomId != null) tag.putUUID(bindingTag(), kingdomId);
	}
}
