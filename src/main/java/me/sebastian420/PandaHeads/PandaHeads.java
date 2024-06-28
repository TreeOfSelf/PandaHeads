package me.sebastian420.PandaHeads;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.json.JsonReader;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");
	private static final Style UNKNOWN_STYLE = Style.EMPTY.withColor(Formatting.GRAY).withBold(true);
	private static final Style UNKNOWN_STYLE_LORE  = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);


	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
		// Hook into block break event
		ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);



		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {

			if (state.getBlock() != Blocks.PLAYER_HEAD && state.getBlock() != Blocks.PLAYER_WALL_HEAD) return true;
            assert blockEntity != null;
            onHeadBreak(world, player, pos, state, blockEntity);
			return false;

        });


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
		UUID uuid = getUUIDFromComponentMap(componentMap);
		String name = getNameFromComponentMap(componentMap);
		ComponentMap.Builder newBlockEntityComponents = ComponentMap.builder();
		if (name != null) {
			newBlockEntityComponents.add(DataComponentTypes.PROFILE, new ProfileComponent(new GameProfile(uuid, name)));
		} else {
			newBlockEntityComponents.add(DataComponentTypes.PROFILE, itemStack.get(DataComponentTypes.PROFILE));
		}
		newBlockEntityComponents.add(DataComponentTypes.LORE, itemStack.get(DataComponentTypes.LORE));
		newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, itemStack.get(DataComponentTypes.ITEM_NAME));
		newBlockEntityComponents.add(DataComponentTypes.CUSTOM_NAME, itemStack.get(DataComponentTypes.CUSTOM_NAME));

		placedBlockEntity.setComponents(newBlockEntityComponents.build());

	}

	private static UUID getUUIDFromComponentMap(ComponentMap componentMap){
		UUID uuid;
		if (componentMap.contains(DataComponentTypes.CUSTOM_DATA) &&
				componentMap.get(DataComponentTypes.CUSTOM_DATA).contains("PublicBukkitValues") &&
				componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").contains("head-drop:headdrop-user")) {
					uuid =  UUID.fromString(componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").getString("head-drop:headdrop-user"));
		}else{
			uuid = componentMap.get(DataComponentTypes.PROFILE).id().get();
		}
		return(uuid);
	}

	private static String getNameFromComponentMap(ComponentMap componentMap){
		UUID uuid;
		if(componentMap.contains(DataComponentTypes.CUSTOM_DATA) &&
				componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().contains("PublicBukkitValues") &&
				componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").contains("head-drop:headdrop-user")) {
			uuid =  UUID.fromString(componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").getString("head-drop:headdrop-user"));
			return(SkinUtils.fetchSkinByUUID(uuid)[2]);
		}else{
			uuid = componentMap.get(DataComponentTypes.PROFILE).id().get();
			String name = componentMap.get(DataComponentTypes.PROFILE).name().get();
			if (name.isEmpty() || name.isBlank()){
				@Nullable String[] skinVals = SkinUtils.fetchSkinByUUID(uuid);
				if(skinVals != null){
					return(SkinUtils.fetchSkinByUUID(uuid)[2]);
				} else {
					return null;
				}
			} else {
				return name;
			}
		}
	}

	private void onHeadBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {



		ComponentMap componentMap = blockEntity.getComponents();
		if (componentMap == null || !componentMap.contains(DataComponentTypes.PROFILE)) {
			componentMap = blockEntity.createComponentMap();
		} else {
			Property property = blockEntity.createComponentMap().get(DataComponentTypes.PROFILE).properties().get("textures").iterator().next();
			componentMap.get(DataComponentTypes.PROFILE).properties().clear();
			componentMap.get(DataComponentTypes.PROFILE).properties().put("textures", new Property(property.name(), property.value(), property.signature()));
		}


		ItemStack headStack = Items.PLAYER_HEAD.getDefaultStack();
		UUID uuid = getUUIDFromComponentMap(componentMap);
		ProfileComponent profileComponent = componentMap.get(DataComponentTypes.PROFILE);
		boolean brokenHead = false;


		if (profileComponent.name().get().isEmpty() || profileComponent.name().get().isBlank()) {

			String name = getNameFromComponentMap(componentMap);
			if(name == null){
				headStack.set(DataComponentTypes.ITEM_NAME, Text.of("Unknown Head").getWithStyle(UNKNOWN_STYLE).getFirst());
				name = "Unknown";
				brokenHead = true;
			}
			ProfileComponent newProfile = new ProfileComponent(new GameProfile(uuid, name));
			newProfile.properties().clear();
			Property property = profileComponent.properties().get("textures").iterator().next();
			newProfile.properties().put("textures", new Property(property.name(), property.value(), property.signature()));
			profileComponent = newProfile;
		}

		if(componentMap.get(DataComponentTypes.LORE) == null){
			headStack.set(DataComponentTypes.LORE, LoreComponent.DEFAULT.with(Text.of("This head's origin has been lost to time").getWithStyle(UNKNOWN_STYLE_LORE).getFirst()));
		}


		headStack.set(DataComponentTypes.PROFILE, profileComponent);

		if (!brokenHead) {
			headStack.set(DataComponentTypes.ITEM_NAME, componentMap.get(DataComponentTypes.ITEM_NAME));
			headStack.set(DataComponentTypes.CUSTOM_NAME, componentMap.get(DataComponentTypes.CUSTOM_NAME));
			headStack.set(DataComponentTypes.LORE, componentMap.get(DataComponentTypes.LORE));
		}

		ItemEntity playerHeadDrop = new ItemEntity(
				world,
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				headStack
		);

		world.spawnEntity(playerHeadDrop);

		// Cancel the default drops
		world.removeBlockEntity(pos);
		world.setBlockState(pos, Blocks.AIR.getDefaultState());

	}

}