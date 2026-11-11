package dev.sakhno.regnum.nations.block;

import dev.sakhno.regnum.nations.RegnumNations;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final KingdomObeliskBlock KINGDOM_OBELISK = Registry.register(
			BuiltInRegistries.BLOCK, RegnumNations.legacyId("kingdom_obelisk"),
			new KingdomObeliskBlock(BlockBehaviour.Properties.of()
					.noOcclusion()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(3.5F, 6.0F)
					.sound(SoundType.DEEPSLATE)
					.requiresCorrectToolForDrops()
					.pushReaction(PushReaction.BLOCK)));

	public static final BlockItem KINGDOM_OBELISK_ITEM = Registry.register(
			BuiltInRegistries.ITEM, RegnumNations.legacyId("kingdom_obelisk"),
			new BlockItem(KINGDOM_OBELISK, new Item.Properties()));

	public static final BlockEntityType<KingdomObeliskBlockEntity> KINGDOM_OBELISK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, RegnumNations.legacyId("kingdom_obelisk"),
			BlockEntityType.Builder.of(KingdomObeliskBlockEntity::new, KINGDOM_OBELISK).build(null));

	public static final CityObeliskBlock CITY_OBELISK=Registry.register(BuiltInRegistries.BLOCK,RegnumNations.legacyId("city_obelisk"),
			new CityObeliskBlock(BlockBehaviour.Properties.of().noOcclusion().mapColor(MapColor.COLOR_BLUE).strength(3.5F,6.0F)
					.sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
	public static final BlockItem CITY_OBELISK_ITEM=Registry.register(BuiltInRegistries.ITEM,RegnumNations.legacyId("city_obelisk"),new BlockItem(CITY_OBELISK,new Item.Properties()));
	public static final BlockEntityType<CityObeliskBlockEntity> CITY_OBELISK_ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,RegnumNations.legacyId("city_obelisk"),
			BlockEntityType.Builder.of(CityObeliskBlockEntity::new,CITY_OBELISK).build(null));

	private ModBlocks() {
	}

	public static void initialize() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(entries -> { entries.accept(KINGDOM_OBELISK_ITEM); entries.accept(CITY_OBELISK_ITEM); });
	}
}
