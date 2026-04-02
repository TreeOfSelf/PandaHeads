package me.TreeOfSelf.PandaHeads.mixin;

import me.TreeOfSelf.PandaHeads.HeadPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(at = @At("TAIL"), method = "setPlacedBy")
    private void onSetPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (itemStack.is(Items.PLAYER_HEAD)) {
            HeadPlacer.place(level, pos, itemStack);
        }
    }
}
