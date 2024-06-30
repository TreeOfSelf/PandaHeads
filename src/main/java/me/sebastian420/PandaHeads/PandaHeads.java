package me.sebastian420.PandaHeads;

import com.mojang.authlib.GameProfile;
import me.sebastian420.PandaHeads.json.JsonReader;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");


	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
		// Hook into block break event
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			//if we are placing
			if (hitResult.getType() == BlockHitResult.Type.BLOCK) {
				// a player_head
				if (player.getStackInHand(hand).getItem() == Items.PLAYER_HEAD){
					ItemStack itemStack = player.getStackInHand(hand).copy();
					BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
					world.getServer().execute(() -> onHeadPlace(world, player, targetPos, itemStack));
				}
				return ActionResult.PASS;
			}
			return ActionResult.PASS;
		});
	}

	private void onServerStarted(MinecraftServer minecraftServer) {
		JsonReader.run(minecraftServer);
	}



	private static void onHeadPlace(World world, PlayerEntity player, BlockPos pos, ItemStack itemStack) {
		// Perform your custom logic after the block is placed
		BlockState placedBlockState = world.getBlockState(pos);
		BlockEntity placedBlockEntity = world.getBlockEntity(pos);

		if (placedBlockEntity == null || placedBlockEntity.getType() != BlockEntityType.SKULL) return;

		ComponentMap componentMap = itemStack.getComponents();
		UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
		String name = SkinUtils.getNameFromComponentMap(componentMap);
		ComponentMap.Builder newBlockEntityComponents = ComponentMap.builder();
		if (name != null) {
			newBlockEntityComponents.add(DataComponentTypes.PROFILE, new ProfileComponent(new GameProfile(uuid, name)));
		} else {
			newBlockEntityComponents.add(DataComponentTypes.PROFILE, itemStack.get(DataComponentTypes.PROFILE));
		}
		newBlockEntityComponents.add(DataComponentTypes.LORE, itemStack.get(DataComponentTypes.LORE));

		if ( componentMap.contains(DataComponentTypes.CUSTOM_DATA) &&
				((componentMap.get(DataComponentTypes.CUSTOM_DATA).contains("PublicBukkitValues") &&
				componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").contains("head-drop:headdrop-user")) || componentMap.get(DataComponentTypes.CUSTOM_DATA).contains("HeadDrops_Owner") )  ) {

			@Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(uuid);

			if (skinValues != null) {
				newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, Text.of("§f§l" + skinValues[2] + "'s §f§lHead"));
			} else {
				newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, itemStack.get(DataComponentTypes.CUSTOM_NAME));
			}

		} else {
			newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, itemStack.get(DataComponentTypes.ITEM_NAME));

		}

		placedBlockEntity.setComponents(newBlockEntityComponents.build());


	}


}