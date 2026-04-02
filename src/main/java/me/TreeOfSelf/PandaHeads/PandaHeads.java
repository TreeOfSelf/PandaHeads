package me.TreeOfSelf.PandaHeads;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");
    private static final Style UNKNOWN_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(true);
    private static final Style UNKNOWN_STYLE_LORE = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true);

    private static class ClickKey {
        private final UUID playerId;
        private final BlockPos blockPos;

        public ClickKey(UUID playerId, BlockPos blockPos) {
            this.playerId = playerId;
            this.blockPos = blockPos;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ClickKey clickKey = (ClickKey) obj;
            return Objects.equals(playerId, clickKey.playerId) && Objects.equals(blockPos, clickKey.blockPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, blockPos);
        }
    }

    private static final java.util.Map<ClickKey, Long> lastClickTime = new java.util.HashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
		UseBlockCallback.EVENT.register(this::useBlock);
	}

	private static String removeQuotes(String input) {
		if (input != null && input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
			return input.substring(1, input.length() - 1);
		}
		return input;
	}

	private InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult blockHitResult) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		long currentTime = System.currentTimeMillis();
		ClickKey clickKey = new ClickKey(serverPlayer.getUUID(), blockHitResult.getBlockPos());
		if (lastClickTime.containsKey(clickKey) && currentTime - lastClickTime.get(clickKey) < 1000) {
			return InteractionResult.PASS;
		}
		lastClickTime.put(clickKey, currentTime);

		var state = level.getBlockState(blockHitResult.getBlockPos());
		if (!state.is(Blocks.PLAYER_HEAD) && !state.is(Blocks.PLAYER_WALL_HEAD)) {
			return InteractionResult.PASS;
		}

		BlockEntity blockEntity = level.getBlockEntity(blockHitResult.getBlockPos());
		if (blockEntity == null) return InteractionResult.PASS;

		DataComponentMap componentMap = blockEntity.components();
		if (!componentMap.has(DataComponents.PROFILE)) return InteractionResult.PASS;

		ResolvableProfile profile = componentMap.get(DataComponents.PROFILE);
		if (profile == null) return InteractionResult.PASS;

		String playerName = "Unknown";
		if (!profile.partialProfile().name().isEmpty()) {
			playerName = profile.partialProfile().name();
		}

		Component nameText = null;
		if (componentMap.has(DataComponents.ITEM_NAME)) {
			nameText = componentMap.get(DataComponents.ITEM_NAME);
		} else if (componentMap.has(DataComponents.CUSTOM_NAME)) {
			nameText = componentMap.get(DataComponents.CUSTOM_NAME);
		}

		if (nameText != null) {
			String nameString = nameText.getString();
			nameString = removeQuotes(nameString);
			if (nameString.startsWith("{")) {
				try {
					JsonElement jsonElement = JsonParser.parseString(nameString);
					var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
					DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
					nameText = result.getOrThrow().getFirst();
				} catch (Exception ignored) {
					nameText = Component.literal(nameString);
				}
			} else {
				nameText = Component.literal(nameString);
			}
		} else {
			nameText = Component.literal(playerName + "'s Head").copy().withStyle(UNKNOWN_STYLE);
		}

		Component deathReasonText = Component.literal("This head's origin has been lost to time").copy().withStyle(UNKNOWN_STYLE_LORE);
		Component aliveForText = null;
		Component dateText = null;

		if (componentMap.has(DataComponents.LORE)) {
			ItemLore lore = componentMap.get(DataComponents.LORE);
			if (lore != null && !lore.lines().isEmpty()) {
				for (Component line : lore.lines()) {
					Component processedLine = line;
					String lineText = line.getString();

					if (lineText.startsWith("{")) {
						try {
							JsonElement jsonElement = JsonParser.parseString(lineText);
							var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
							DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
							processedLine = result.getOrThrow().getFirst();
							lineText = processedLine.getString();
						} catch (Exception ignored) {
						}
					}

					if (lineText.toLowerCase().contains("killed") || lineText.toLowerCase().contains("died") || lineText.toLowerCase().contains("death")) {
						deathReasonText = processedLine;
					} else if (lineText.toLowerCase().contains("alive") || lineText.toLowerCase().contains("lived")) {
						aliveForText = processedLine;
					} else if (lineText.matches(".*\\d{4}.*") || lineText.toLowerCase().contains("date")) {
						dateText = processedLine;
					}
				}

				if (deathReasonText.getString().equals("This head's origin has been lost to time") && !lore.lines().isEmpty()) {
					Component firstLine = lore.lines().get(0);
					String firstLineText = firstLine.getString();
					if (firstLineText.startsWith("{")) {
						try {
							JsonElement jsonElement = JsonParser.parseString(firstLineText);
							var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
							DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
							deathReasonText = result.getOrThrow().getFirst();
						} catch (Exception ignored) {
							deathReasonText = firstLine;
						}
					} else {
						deathReasonText = firstLine;
					}
				}
			}
		}

		serverPlayer.sendSystemMessage(Component.literal("-------------------------------").copy().withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)), false);
		serverPlayer.sendSystemMessage(nameText, false);
		serverPlayer.sendSystemMessage(deathReasonText, false);
		if (aliveForText != null) {
			serverPlayer.sendSystemMessage(aliveForText, false);
		}
		if (dateText != null) {
			serverPlayer.sendSystemMessage(dateText, false);
		}

		return InteractionResult.SUCCESS;
	}

}
