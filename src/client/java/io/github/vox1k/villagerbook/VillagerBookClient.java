package io.github.vox1k.villagerbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

public class VillagerBookClient implements ClientModInitializer {
	public static final String MOD_ID = "villagerbook";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		Recorder.init();
		VbCommand.register();
		LOGGER.info("VillagerBook loaded");
	}
}
