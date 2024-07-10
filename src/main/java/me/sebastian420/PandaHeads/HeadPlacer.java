package me.sebastian420.PandaHeads;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

public class HeadPlacer {
    public static void place(World world, BlockPos pos, ItemStack itemStack) {
        if (itemStack.getItem() == Items.PLAYER_HEAD) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity == null || blockEntity.getType() != BlockEntityType.SKULL) return;

            ComponentMap componentMap = itemStack.getComponents();

            if (!componentMap.contains(DataComponentTypes.PROFILE)) return;

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
                newBlockEntityComponents.add(DataComponentTypes.PROFILE, itemStack.get(DataComponentTypes.PROFILE));
            }

            newBlockEntityComponents.add(DataComponentTypes.LORE, itemStack.get(DataComponentTypes.LORE));


            if (componentMap.contains(DataComponentTypes.CUSTOM_DATA) &&
                    ((componentMap.get(DataComponentTypes.CUSTOM_DATA).contains("PublicBukkitValues") &&
                            componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getCompound("PublicBukkitValues").contains("head-drop:headdrop-user")) || componentMap.get(DataComponentTypes.CUSTOM_DATA).contains("HeadDrops_Owner"))) {

                @Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(uuid);

                if (skinValues != null) {
                    newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, Text.of("§f§l" + skinValues[2] + "'s §f§lHead"));
                } else {
                    if (itemStack.contains(DataComponentTypes.CUSTOM_DATA))
                        newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, itemStack.get(DataComponentTypes.CUSTOM_NAME));
                }

            } else {
                if (itemStack.contains(DataComponentTypes.ITEM_NAME))
                    newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, itemStack.get(DataComponentTypes.ITEM_NAME));
            }

            if (componentMap.contains(DataComponentTypes.CUSTOM_NAME)) {
                String jsonString = Text.Serialization.toJsonString(componentMap.get(DataComponentTypes.CUSTOM_NAME), DynamicRegistryManager.EMPTY);
                NbtCompound nbtCompound = new NbtCompound();
                nbtCompound.putString("custom_name", jsonString);
                newBlockEntityComponents.add(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));
            }

            if (componentMap.contains(DataComponentTypes.NOTE_BLOCK_SOUND)) {
                newBlockEntityComponents.add(DataComponentTypes.NOTE_BLOCK_SOUND, componentMap.get(DataComponentTypes.NOTE_BLOCK_SOUND));
            }
            blockEntity.setComponents(newBlockEntityComponents.build());
        }
    }
}
