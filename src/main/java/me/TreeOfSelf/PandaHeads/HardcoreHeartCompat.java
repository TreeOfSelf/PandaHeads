package me.TreeOfSelf.PandaHeads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.UUID;

// PandaHardcoreHeart (optional): when a death takes a player's heart it's their first death, so their head gets
// a "First Death" line. Its die hook runs before this mod's and leaves the player in HeartData.justLostHeart.
public final class HardcoreHeartCompat {
	private static final AtlasSprite HEART = new AtlasSprite(Identifier.withDefaultNamespace("gui"), Identifier.withDefaultNamespace("hud/heart/hardcore_full"));
	private static Field justLostHeart;

	static {
		if (FabricLoader.getInstance().isModLoaded("panda-hardcore-heart")) {
			try {
				justLostHeart = Class.forName("me.TreeOfSelf.PandaHardcoreHeart.HeartData").getField("justLostHeart");
			} catch (ReflectiveOperationException e) {
				PandaHeads.LOGGER.warn("PandaHardcoreHeart found but HeartData.justLostHeart is missing", e);
			}
		}
	}

	private HardcoreHeartCompat() {}

	public static boolean isFirstDeath(UUID uuid) {
		if (justLostHeart == null) return false;
		try {
			return uuid.equals(justLostHeart.get(null));
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	public static Component firstDeathLine() {
		return Component.empty().withStyle(s -> s.withColor(ChatFormatting.DARK_RED).withBold(true).withItalic(false))
			.append(Component.object(HEART, Component.literal("❤")))
			.append(" First Death");
	}
}
