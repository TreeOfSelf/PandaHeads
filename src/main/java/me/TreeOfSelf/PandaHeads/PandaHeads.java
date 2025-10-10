package me.TreeOfSelf.PandaHeads;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");
    private static final Style UNKNOWN_STYLE = Style.EMPTY.withColor(Formatting.GRAY).withBold(true);
    private static final Style UNKNOWN_STYLE_LORE = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);
    
    private static class ClickKey {
        private final java.util.UUID playerId;
        private final BlockPos blockPos;
        
        public ClickKey(java.util.UUID playerId, BlockPos blockPos) {
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

	private ActionResult useBlock(PlayerEntity playerEntity, World world, Hand hand, BlockHitResult blockHitResult) {
		if (world.isClient() || !(playerEntity instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}

		long currentTime = System.currentTimeMillis();
		ClickKey clickKey = new ClickKey(serverPlayer.getUuid(), blockHitResult.getBlockPos());
		if (lastClickTime.containsKey(clickKey) && currentTime - lastClickTime.get(clickKey) < 1000) {
			return ActionResult.PASS;
		}
		lastClickTime.put(clickKey, currentTime);

		BlockState state = world.getBlockState(blockHitResult.getBlockPos());
		if (state.getBlock() != Blocks.PLAYER_HEAD && state.getBlock() != Blocks.PLAYER_WALL_HEAD) {
			return ActionResult.PASS;
		}

		BlockEntity blockEntity = world.getBlockEntity(blockHitResult.getBlockPos());
		if (blockEntity == null) return ActionResult.PASS;

		ComponentMap componentMap = blockEntity.getComponents();
		if (!componentMap.contains(DataComponentTypes.PROFILE)) return ActionResult.PASS;

		ProfileComponent profileComponent = componentMap.get(DataComponentTypes.PROFILE);
		if (profileComponent == null) return ActionResult.PASS;

		String playerName = "Unknown";
		if (!profileComponent.getGameProfile().name().isEmpty()) {
			playerName = profileComponent.getGameProfile().name();
		}

		Text nameText = null;
		if (componentMap.contains(DataComponentTypes.ITEM_NAME)) {
			nameText = componentMap.get(DataComponentTypes.ITEM_NAME);
		} else if (componentMap.contains(DataComponentTypes.CUSTOM_NAME)) {
			nameText = componentMap.get(DataComponentTypes.CUSTOM_NAME);
		}

		if (nameText != null) {
			String nameString = nameText.getString();
			nameString = removeQuotes(nameString);
			if (nameString.startsWith("{")) {
				try {
					JsonElement jsonElement = JsonParser.parseString(nameString);
					DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
					nameText = result.getOrThrow().getFirst();
				} catch (Exception ignored) {
					nameText = Text.of(nameString);
				}
			} else {
				nameText = Text.of(nameString);
			}
		} else {
			nameText = Text.of(playerName + "'s Head").getWithStyle(UNKNOWN_STYLE).getFirst();
		}

		Text deathReasonText = Text.of("This head's origin has been lost to time").getWithStyle(UNKNOWN_STYLE_LORE).getFirst();
		Text aliveForText = null;
		Text dateText = null;

		if (componentMap.contains(DataComponentTypes.LORE)) {
			LoreComponent lore = componentMap.get(DataComponentTypes.LORE);
			if (!lore.lines().isEmpty()) {
				for (Text line : lore.lines()) {
					Text processedLine = line;
					String lineText = line.getString();
					
					if (lineText.startsWith("{")) {
						try {
							JsonElement jsonElement = JsonParser.parseString(lineText);
							DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
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
					Text firstLine = lore.lines().get(0);
					String firstLineText = firstLine.getString();
					if (firstLineText.startsWith("{")) {
						try {
							JsonElement jsonElement = JsonParser.parseString(firstLineText);
							DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
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

		serverPlayer.sendMessage(Text.of("-------------------------------").getWithStyle(Style.EMPTY.withColor(Formatting.WHITE)).getFirst(), false);
		serverPlayer.sendMessage(nameText, false);
		serverPlayer.sendMessage(deathReasonText, false);
		if (aliveForText != null) {
			serverPlayer.sendMessage(aliveForText, false);
		}
		if (dateText != null) {
			serverPlayer.sendMessage(dateText, false);
		}

		return ActionResult.SUCCESS;
	}

}