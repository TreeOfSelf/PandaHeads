package me.sebastian420.PandaHeads;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaHeads implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("panda-heads");

	@Override
	public void onInitialize() {
		LOGGER.info("PandaHeads loaded");
		// Hook into block break event
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (state.getBlock() != Blocks.PLAYER_HEAD) return true;
			onBlockBreak(world, player, pos, state);
            return false;
        });


		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hitResult.getType() == BlockHitResult.Type.BLOCK) {
				// Run the action after the block is placed
				BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
				ItemStack itemStack = player.getStackInHand(hand).copy();
				world.getServer().execute(() -> onBlockPlace(world, player, targetPos, itemStack));
				return ActionResult.PASS;
			}
			return ActionResult.PASS;
		});
	}

	private static ActionResult onBlockPlace(World world, net.minecraft.entity.player.PlayerEntity player, BlockPos pos, ItemStack itemStack) {
		// Get the item used to place the block

		// Perform your custom logic after the block is placed
		BlockState placedBlockState = world.getBlockState(pos);
		System.out.println("Block placed: " + placedBlockState.getBlock().getTranslationKey() + " using item: " + itemStack.getItem().getTranslationKey());

		// Add your custom behavior here
		// For example, you can replace the placed block with another block or perform other actions

		return ActionResult.PASS; // Returning PASS allows the block to remain placed
	}

	private void onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state) {

		// Cancel the default drops
		world.removeBlockEntity(pos);
		world.setBlockState(pos, Blocks.AIR.getDefaultState());

		// Drop a piece of wheat
		ItemEntity wheatDrop = new ItemEntity(
				world,
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				Items.WHEAT.getDefaultStack()
		);

		world.spawnEntity(wheatDrop);

	}

}