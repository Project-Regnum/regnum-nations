package dev.sakhno.regnum.nations;

import dev.sakhno.regnum.nations.block.ModBlocks;
import dev.sakhno.regnum.nations.kingdom.KingdomCommands;
import dev.sakhno.regnum.nations.kingdom.KingdomService;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegnumNations implements ModInitializer {
	public static final String MOD_ID = "regnum-nations";
	// Registry and wire identifiers are compatibility contracts, not branding.
	public static final String LEGACY_NAMESPACE = "project-reigns";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		dev.sakhno.regnum.nations.kingdom.PoliticalMapServer.initialize();
		dev.sakhno.regnum.nations.kingdom.KingdomManagementServer.initialize();
		dev.sakhno.regnum.nations.kingdom.ObeliskRelocationServer.initialize();
        dev.sakhno.regnum.nations.kingdom.CitizenManagementServer.initialize();
        ModBlocks.initialize();
		KingdomService.initialize();
		KingdomCommands.initialize();
		TerritoryProtection.initialize();
		LOGGER.info("Regnum Nations initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
	/** Existing world registries and payload channels must retain their identifiers. */
	public static ResourceLocation legacyId(String path) {
		return ResourceLocation.fromNamespaceAndPath(LEGACY_NAMESPACE, path);
	}
}
