package me.TreeOfSelf.PandaHeads;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class HeadPlacer {
    public static void place(Level level, BlockPos pos, ItemStack itemStack) {
        if (itemStack.is(Items.PLAYER_HEAD)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity != null && blockEntity.getType() != BlockEntityTypes.SKULL) {
                level.removeBlockEntity(pos);
                BlockEntity fresh = BlockEntityTypes.SKULL.create(pos, level.getBlockState(pos));
                if (fresh != null) {
                    level.setBlockEntity(fresh);
                }
                blockEntity = level.getBlockEntity(pos);
            }

            if (blockEntity == null || blockEntity.getType() != BlockEntityTypes.SKULL) return;

            DataComponentMap componentMap = itemStack.getComponents();

            if (!componentMap.has(DataComponents.PROFILE)) return;

            UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
            DataComponentMap.Builder newBlockEntityComponents = DataComponentMap.builder();

            ResolvableProfile stackProfile = itemStack.get(DataComponents.PROFILE);
            if (stackProfile == null) return;

            boolean headDrop = SkinUtils.isHeadDrop(componentMap);
            String profileName = stackProfile.partialProfile().name();
            SkinUtils.Lookup lookup = null;
            if (headDrop || profileName.isBlank()) {
                // Not cached yet: place it as it is now and fill the name in once Mojang answered
                BlockEntity placed = blockEntity;
                ItemStack placedStack = itemStack.copy();
                lookup = SkinUtils.lookupSkin(uuid, level.getServer(), () -> {
                    if (level.getBlockEntity(pos) == placed) place(level, pos, placedStack);
                });
            }
            String name;
            if (headDrop) {
                name = lookup.ok() ? lookup.skin()[2] : null;
            } else {
                name = profileName.isBlank() ? (lookup.ok() ? lookup.skin()[2] : null) : profileName;
            }

            if (name != null) {
                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                if (stackProfile.partialProfile().properties().containsKey("textures")) {
                    Property property = stackProfile.partialProfile().properties().get("textures").iterator().next();
                    propBuilder.put("textures", new Property(property.name(), property.value(), property.signature()));
                }
                PropertyMap propertyMap = new PropertyMap(propBuilder.build());
                GameProfile newProfile = new GameProfile(uuid, name, propertyMap);
                ResolvableProfile profileComponent = ResolvableProfile.createResolved(newProfile);
                newBlockEntityComponents.set(DataComponents.PROFILE, profileComponent);
            } else {
                newBlockEntityComponents.set(DataComponents.PROFILE, stackProfile);
            }

            if (itemStack.has(DataComponents.LORE)) {
                newBlockEntityComponents.set(DataComponents.LORE, itemStack.get(DataComponents.LORE));
            }

            if (componentMap.has(DataComponents.CUSTOM_DATA)) {
                CustomData customData = componentMap.get(DataComponents.CUSTOM_DATA);
                var nbt = customData.copyTag();
                boolean bukkitHeadDrop = nbt.contains("PublicBukkitValues")
                    && nbt.getCompound("PublicBukkitValues").map(c -> c.contains("head-drop:headdrop-user")).orElse(false);
                boolean headDropsOwner = nbt.contains("HeadDrops_Owner");
                if (bukkitHeadDrop || headDropsOwner) {
                    if (lookup != null && lookup.ok()) {
                        newBlockEntityComponents.set(DataComponents.ITEM_NAME, Component.literal("§f§l" + lookup.skin()[2] + "'s §f§lHead"));
                    }
                } else if (itemStack.has(DataComponents.ITEM_NAME)) {
                    newBlockEntityComponents.set(DataComponents.ITEM_NAME, itemStack.get(DataComponents.ITEM_NAME));
                }
            } else if (itemStack.has(DataComponents.ITEM_NAME)) {
                newBlockEntityComponents.set(DataComponents.ITEM_NAME, itemStack.get(DataComponents.ITEM_NAME));
            }

            if (componentMap.has(DataComponents.CUSTOM_NAME)) {
                newBlockEntityComponents.set(DataComponents.CUSTOM_NAME, componentMap.get(DataComponents.CUSTOM_NAME));
            }

            if (componentMap.has(DataComponents.NOTE_BLOCK_SOUND)) {
                newBlockEntityComponents.set(DataComponents.NOTE_BLOCK_SOUND, componentMap.get(DataComponents.NOTE_BLOCK_SOUND));
            }
            blockEntity.setComponents(newBlockEntityComponents.build());
        }
    }
}
