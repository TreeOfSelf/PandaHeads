package me.TreeOfSelf.PandaHeads.mixin;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import me.TreeOfSelf.PandaHeads.SkinUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(BlockBehaviour.class)
public class AbstractBlockMixin {

    @Unique
    private static final Style UNKNOWN_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(true);
    @Unique
    private static final Style UNKNOWN_STYLE_LORE = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true);

    @Unique
    private static String removeQuotes(String input) {
        if (input != null && input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    @Inject(at = @At("TAIL"), method = "getDrops", cancellable = true)
    private void getDrops(BlockState state, LootParams.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (state.is(Blocks.PLAYER_HEAD) || state.is(Blocks.PLAYER_WALL_HEAD)) {
            ServerLevel level = builder.getLevel();
            var tool = builder.getParameter(LootContextParams.TOOL);
            int silkLevel = EnchantmentHelper.getItemEnchantmentLevel(
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),
                tool
            );
            List<ItemStack> itemStackList = new ArrayList<>();

            BlockEntity blockEntity = builder.getParameter(LootContextParams.BLOCK_ENTITY);

            DataComponentMap componentMap = blockEntity.components();

            if (!componentMap.has(DataComponents.PROFILE)) return;

            ResolvableProfile baseProfile = componentMap.get(DataComponents.PROFILE);
            if (baseProfile == null) return;

            DataComponentMap created = blockEntity.collectComponents();
            ResolvableProfile createdProfile = created.get(DataComponents.PROFILE);
            if (createdProfile != null && createdProfile.partialProfile().properties().containsKey("textures")) {
                Property property = createdProfile.partialProfile().properties().get("textures").iterator().next();
                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                propBuilder.put("textures", new Property(property.name(), property.value(), property.signature()));
                PropertyMap propertyMap = new PropertyMap(propBuilder.build());
                GameProfile newProfile = new GameProfile(
                    baseProfile.partialProfile().id(),
                    baseProfile.partialProfile().name(),
                    propertyMap
                );
                componentMap = DataComponentMap.builder()
                        .addAll(componentMap)
                        .set(DataComponents.PROFILE, ResolvableProfile.createResolved(newProfile))
                        .build();
            }

            ItemStack headStack = Items.PLAYER_HEAD.getDefaultInstance();
            UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
            ResolvableProfile profileComponent = componentMap.get(DataComponents.PROFILE);
            if (profileComponent == null) return;
            boolean brokenHead = false;

            if (profileComponent.partialProfile().name().isEmpty() || profileComponent.partialProfile().name().isBlank()) {

                String name = SkinUtils.getNameFromComponentMap(componentMap);
                if (name == null) {
                    headStack.set(DataComponents.ITEM_NAME, Component.literal("Unknown Head").copy().withStyle(UNKNOWN_STYLE));
                    name = "Unknown";
                    brokenHead = true;
                }

                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                if (profileComponent.partialProfile().properties().containsKey("textures")) {
                    Property property = profileComponent.partialProfile().properties().get("textures").iterator().next();
                    propBuilder.put("textures", new Property(property.name(), property.value(), property.signature()));
                }
                PropertyMap propertyMap = new PropertyMap(propBuilder.build());

                GameProfile newGameProfile = new GameProfile(uuid, name, propertyMap);
                profileComponent = ResolvableProfile.createResolved(newGameProfile);
            }

            if (!brokenHead) {
                if (componentMap.has(DataComponents.ITEM_NAME)) headStack.set(DataComponents.ITEM_NAME, componentMap.get(DataComponents.ITEM_NAME));

                if (componentMap.has(DataComponents.ITEM_NAME)) {
                    if (silkLevel < 1) {
                        try {
                            @Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(profileComponent.partialProfile().id());
                            if (skinValues != null) {
                                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                                propBuilder.put("textures", new Property("textures", skinValues[0], skinValues[1]));
                                PropertyMap propertyMap = new PropertyMap(propBuilder.build());
                                GameProfile newGameProfile = new GameProfile(uuid, skinValues[2], propertyMap);
                                profileComponent = ResolvableProfile.createResolved(newGameProfile);

                                var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                                DataResult<JsonElement> json = ComponentSerialization.CODEC.encodeStart(ops, componentMap.get(DataComponents.ITEM_NAME));
                                JsonElement jsonElement = json.getOrThrow();
                                String nameString = jsonElement.isJsonPrimitive() ? jsonElement.getAsString() : jsonElement.toString();

                                int index = nameString.indexOf('§');
                                if (index >= 0 && index < nameString.length() - 1) {
                                    char nameColor = nameString.charAt(index + 1);
                                    Component nameText = Component.literal("§" + nameColor + "§l" + skinValues[2] + "'s §f§lHead");
                                    headStack.set(DataComponents.ITEM_NAME, nameText);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }

            if (componentMap.has(DataComponents.LORE)) {
                ItemLore lore = componentMap.get(DataComponents.LORE);
                List<Component> newLoreLines = new ArrayList<>();
                for (Component line : lore.lines()) {
                    Component newLine = line;
                    if (line.getString().startsWith("{")) {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(line.getString());
                            var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                            DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
                            newLine = result.getOrThrow().getFirst();
                        } catch (Exception ignored) {
                        }
                    }
                    newLoreLines.add(newLine);
                }
                headStack.set(DataComponents.LORE, new ItemLore(newLoreLines));
            }

            if (!componentMap.has(DataComponents.LORE) || (componentMap.get(DataComponents.LORE).lines().isEmpty())) {
                headStack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("This head's origin has been lost to time").copy().withStyle(UNKNOWN_STYLE_LORE))));
            }

            if (componentMap.has(DataComponents.CUSTOM_DATA)) {
                CompoundTag customData = componentMap.get(DataComponents.CUSTOM_DATA).copyTag();
                if (customData.contains("custom_name")) {
                    java.util.Optional<String> optionalCustomNameString = customData.getString("custom_name");

                    if (optionalCustomNameString.isPresent()) {
                        String customNameString = removeQuotes(optionalCustomNameString.get());
                        if (!customNameString.isEmpty()) {
                            Component customNameText;
                            if (customNameString.startsWith("{")) {
                                try {
                                    JsonElement jsonElement = JsonParser.parseString(customNameString);
                                    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                                    DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
                                    customNameText = result.getOrThrow().getFirst();
                                } catch (Exception ignored) {
                                    customNameText = Component.literal(customNameString);
                                }
                            } else {
                                customNameText = Component.literal(customNameString);
                            }

                            headStack.set(DataComponents.ITEM_NAME, customNameText);
                            headStack.set(DataComponents.CUSTOM_NAME, customNameText);
                        }
                    }
                }
            } else if (componentMap.has(DataComponents.CUSTOM_NAME)) {
                Component customName = componentMap.get(DataComponents.CUSTOM_NAME);
                if (customName != null && !customName.getString().isEmpty()) {
                    String plainString = customName.getString();
                    String cleanedString = removeQuotes(plainString);

                    if (!cleanedString.equals(plainString)) {
                        Component finalCustomNameText;
                        if (cleanedString.startsWith("{")) {
                            try {
                                JsonElement jsonElement = JsonParser.parseString(cleanedString);
                                var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                                DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
                                finalCustomNameText = result.getOrThrow().getFirst();
                            } catch (Exception ignored) {
                                finalCustomNameText = Component.literal(cleanedString);
                            }
                        } else {
                            finalCustomNameText = Component.literal(cleanedString);
                        }
                        headStack.set(DataComponents.ITEM_NAME, finalCustomNameText);
                        headStack.set(DataComponents.CUSTOM_NAME, finalCustomNameText);
                    } else {
                        headStack.set(DataComponents.ITEM_NAME, customName);
                        headStack.set(DataComponents.CUSTOM_NAME, customName);
                    }
                }
            } else if (componentMap.has(DataComponents.ITEM_NAME)) {
                Component itemName = componentMap.get(DataComponents.ITEM_NAME);
                if (itemName != null && !itemName.getString().isEmpty()) {
                    String itemNameString = removeQuotes(itemName.getString());
                    Component finalCustomNameText;
                    if (itemNameString.startsWith("{")) {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(itemNameString);
                            var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
                            DataResult<Pair<Component, JsonElement>> result = ComponentSerialization.CODEC.decode(ops, jsonElement);
                            finalCustomNameText = result.getOrThrow().getFirst();
                        } catch (Exception ignored) {
                            finalCustomNameText = Component.literal(itemNameString);
                        }
                    } else {
                        finalCustomNameText = Component.literal(itemNameString);
                    }
                    headStack.set(DataComponents.ITEM_NAME, finalCustomNameText);
                    headStack.set(DataComponents.CUSTOM_NAME, finalCustomNameText);
                }
            }

            if (componentMap.has(DataComponents.NOTE_BLOCK_SOUND)) {
                headStack.set(DataComponents.NOTE_BLOCK_SOUND, componentMap.get(DataComponents.NOTE_BLOCK_SOUND));
            }

            headStack.set(DataComponents.PROFILE, profileComponent);
            itemStackList.add(headStack);
            cir.setReturnValue(itemStackList);
        }
    }
}
