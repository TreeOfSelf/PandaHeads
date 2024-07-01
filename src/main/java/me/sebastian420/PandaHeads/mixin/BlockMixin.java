package me.sebastian420.PandaHeads.mixin;


import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.SkinUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
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
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity == null ||
                    blockEntity.getType() != BlockEntityType.SKULL ||
                    !blockEntity.getComponents().contains(DataComponentTypes.LORE) ||
                    blockEntity.getComponents().get(DataComponentTypes.LORE).lines().isEmpty()) return;

            ComponentMap componentMap = itemStack.getComponents();
            UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
            String name = SkinUtils.getNameFromComponentMap(componentMap);
            ComponentMap.Builder newBlockEntityComponents = ComponentMap.builder();


            if (name != null) {
                ProfileComponent profileComponent = new ProfileComponent(new GameProfile(uuid, name));
                if (itemStack.get(DataComponentTypes.PROFILE).properties().containsKey("textures")) {
                    Property property = itemStack.get(DataComponentTypes.PROFILE).properties().get("textures").iterator().next();
                    profileComponent.properties().clear();
                    profileComponent.properties().put("textures", new Property(property.name(), property.value(), property.signature()));
                }
                newBlockEntityComponents.add(DataComponentTypes.PROFILE, profileComponent);

            } else {
                System.out.println("Profile set like dat");
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

            blockEntity.setComponents(newBlockEntityComponents.build());
        }
    }
}
