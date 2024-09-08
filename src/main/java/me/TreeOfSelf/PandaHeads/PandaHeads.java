package me.TreeOfSelf.PandaHeads;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");

	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
	}

}