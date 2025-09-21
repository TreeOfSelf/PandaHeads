package me.TreeOfSelf.PandaHeads.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.ForgingSlotsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilScreenHandler.class)
public class AnvilScreenHandlerMixin {
    @Redirect(method = "getForgingSlotsManager", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/screen/slot/ForgingSlotsManager$Builder;input(IIILjava/util/function/Predicate;)Lnet/minecraft/screen/slot/ForgingSlotsManager$Builder;"))
    private static ForgingSlotsManager.Builder modifyInputSlots(ForgingSlotsManager.Builder builder, int id, int x, int y, java.util.function.Predicate<ItemStack> validator) {
        java.util.function.Predicate<ItemStack> newValidator = (stack) -> {
            if (stack.isOf(Items.PLAYER_HEAD)) {
                return false;
            }
            return validator.test(stack);
        };
        return builder.input(id, x, y, newValidator);
    }
}