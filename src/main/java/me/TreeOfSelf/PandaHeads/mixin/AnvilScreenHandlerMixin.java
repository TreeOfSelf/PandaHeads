package me.TreeOfSelf.PandaHeads.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(AnvilMenu.class)
public class AnvilScreenHandlerMixin {
    @Redirect(
        method = "createInputSlotDefinitions",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;withSlot(IIILjava/util/function/Predicate;)Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;"
        )
    )
    private static ItemCombinerMenuSlotDefinition.Builder modifyInputSlots(
        ItemCombinerMenuSlotDefinition.Builder builder,
        int slotIndex,
        int x,
        int y,
        Predicate<ItemStack> validator
    ) {
        if (slotIndex == 0) {
            Predicate<ItemStack> newValidator = stack -> {
                if (stack.is(Items.PLAYER_HEAD)) {
                    return false;
                }
                return validator.test(stack);
            };
            return builder.withSlot(slotIndex, x, y, newValidator);
        }
        return builder.withSlot(slotIndex, x, y, validator);
    }
}
