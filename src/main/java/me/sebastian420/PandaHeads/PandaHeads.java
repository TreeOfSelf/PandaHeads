package me.sebastian420.PandaHeads;

import me.sebastian420.PandaHeads.json.JsonReader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");

	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
	}

	private void onServerStarted(MinecraftServer minecraftServer) {
		JsonReader.run(minecraftServer);
	}

}