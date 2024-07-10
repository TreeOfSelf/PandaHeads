package me.sebastian420.PandaHeads.mixin;

import me.sebastian420.PandaHeads.HeadPlacer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(at = @At("TAIL"), method = "onPlaced")
    private void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (itemStack.getItem() == Items.PLAYER_HEAD){
            HeadPlacer.place(world, pos, itemStack);
        }
    }
}
