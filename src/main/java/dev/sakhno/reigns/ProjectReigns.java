package dev.sakhno.reigns;

import dev.sakhno.reigns.block.ModBlocks;
import dev.sakhno.reigns.kingdom.KingdomCommands;
import dev.sakhno.reigns.kingdom.KingdomService;
import dev.sakhno.reigns.kingdom.TerritoryProtection;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectReigns implements ModInitializer {
	public static final String MOD_ID = "project-reigns";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		dev.sakhno.reigns.kingdom.PoliticalMapServer.initialize();
		dev.sakhno.reigns.kingdom.KingdomManagementServer.initialize();
		ModBlocks.initialize();
		KingdomService.initialize();
		KingdomCommands.initialize();
		TerritoryProtection.initialize();
		LOGGER.info("Project Reigns initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
