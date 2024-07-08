package me.sebastian420.PandaHeads.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.SkinUtils;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(net.minecraft.block.AbstractBlock.class)
public class AbstractBlockMixin {

    @Unique
    private static final Style UNKNOWN_STYLE = Style.EMPTY.withColor(Formatting.GRAY).withBold(true);
    @Unique
    private static final Style UNKNOWN_STYLE_LORE  = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);

    @Inject(at = @At("TAIL"), method = "getDroppedStacks", cancellable = true)
    private void getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (state.getBlock() == Blocks.PLAYER_HEAD || state.getBlock() == Blocks.PLAYER_WALL_HEAD) {
            ServerWorld world = builder.getWorld();
            int silkLevel = builder.get(LootContextParameters.TOOL).getEnchantments().getLevel(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.SILK_TOUCH).get());
            List<ItemStack> itemStackList = new ArrayList<>();

            BlockEntity blockEntity = builder.get(LootContextParameters.BLOCK_ENTITY);

            ComponentMap componentMap = blockEntity.getComponents();
            if (componentMap == null || !componentMap.contains(DataComponentTypes.PROFILE)) {
                componentMap = blockEntity.createComponentMap();
            } else {
                if (blockEntity.createComponentMap().get(DataComponentTypes.PROFILE).properties().containsKey("textures")){
                Property property = blockEntity.createComponentMap().get(DataComponentTypes.PROFILE).properties().get("textures").iterator().next();
                componentMap.get(DataComponentTypes.PROFILE).properties().clear();
                componentMap.get(DataComponentTypes.PROFILE).properties().put("textures", new Property(property.name(), property.value(), property.signature()));
                }
            }


            ItemStack headStack = Items.PLAYER_HEAD.getDefaultStack();
            UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
            ProfileComponent profileComponent = componentMap.get(DataComponentTypes.PROFILE);
            boolean brokenHead = false;

            //Try and fix broken names
            if (profileComponent.name().get().isEmpty() || profileComponent.name().get().isBlank()) {

                String name = SkinUtils.getNameFromComponentMap(componentMap);
                if(name == null){
                    headStack.set(DataComponentTypes.ITEM_NAME, Text.of("Unknown Head").getWithStyle(UNKNOWN_STYLE).getFirst());
                    name = "Unknown";
                    brokenHead = true;
                }

                ProfileComponent newProfile = new ProfileComponent(new GameProfile(uuid, name));
                if (profileComponent.properties().containsKey("textures")) {
                    newProfile.properties().clear();
                    Property property = profileComponent.properties().get("textures").iterator().next();
                    newProfile.properties().put("textures", new Property(property.name(), property.value(), property.signature()));
                }

                profileComponent = newProfile;
            }

            //If missing lore
            if (!componentMap.contains(DataComponentTypes.LORE) || (componentMap.get(DataComponentTypes.LORE).lines().isEmpty())){
                headStack.set(DataComponentTypes.LORE, LoreComponent.DEFAULT.with(Text.of("This head's origin has been lost to time").getWithStyle(UNKNOWN_STYLE_LORE).getFirst()));
            }


            if (!brokenHead) {
                if (componentMap.contains(DataComponentTypes.ITEM_NAME)) headStack.set(DataComponentTypes.ITEM_NAME, componentMap.get(DataComponentTypes.ITEM_NAME));
                headStack.set(DataComponentTypes.LORE, componentMap.get(DataComponentTypes.LORE));

                //Update skin and name
                if(profileComponent.id().isPresent() && componentMap.contains(DataComponentTypes.ITEM_NAME)) {
                    if (silkLevel < 1) {
                        @Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(profileComponent.id().get());
                        if (skinValues != null) {
                            ProfileComponent newProfile = new ProfileComponent(new GameProfile(uuid, skinValues[2]));
                            newProfile.properties().clear();
                            newProfile.properties().put("textures", new Property("textures", skinValues[0], skinValues[1]));
                            profileComponent = newProfile;
                            String nameString = Text.Serialization.toJsonString(componentMap.get(DataComponentTypes.ITEM_NAME), DynamicRegistryManager.EMPTY);
                            int index = nameString.indexOf('§');
                            char nameColor = nameString.charAt(index + 1);
                            Text nameText = Text.of("§" + nameColor + "§l" + skinValues[2] + "'s §f§lHead");
                            headStack.set(DataComponentTypes.ITEM_NAME, nameText);

                        }
                    }
                }

            }

            if(componentMap.contains(DataComponentTypes.CUSTOM_DATA)){
                NbtCompound customData = componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
                if(customData.contains("custom_name")){
                    headStack.set(DataComponentTypes.CUSTOM_NAME, Text.Serialization.fromJson(customData.getString("custom_name"), DynamicRegistryManager.EMPTY));
                }
            }

            headStack.set(DataComponentTypes.PROFILE, profileComponent);
            itemStackList.add(headStack);
            cir.setReturnValue(itemStackList);

        }

    }
}
