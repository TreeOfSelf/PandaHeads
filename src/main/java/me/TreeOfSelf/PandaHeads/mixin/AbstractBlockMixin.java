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
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
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

    @Unique
    private static String removeQuotes(String input) {
        if (input != null && input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    @Inject(at = @At("TAIL"), method = "getDroppedStacks", cancellable = true)
    private void getDroppedStacks(BlockState state, LootWorldContext.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (state.getBlock() == Blocks.PLAYER_HEAD || state.getBlock() == Blocks.PLAYER_WALL_HEAD) {
            ServerWorld world = builder.getWorld();
            int silkLevel = builder.get(LootContextParameters.TOOL).getEnchantments().getLevel(world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH));
            List<ItemStack> itemStackList = new ArrayList<>();

            BlockEntity blockEntity = builder.get(LootContextParameters.BLOCK_ENTITY);

            ComponentMap componentMap = blockEntity.getComponents();

            if (!componentMap.contains(DataComponentTypes.PROFILE)) return;

            if (!componentMap.contains(DataComponentTypes.PROFILE)) {
                componentMap = blockEntity.createComponentMap();
            } else {
                if (blockEntity.createComponentMap().get(DataComponentTypes.PROFILE).getGameProfile().properties().containsKey("textures")){
                    Property property = blockEntity.createComponentMap().get(DataComponentTypes.PROFILE).getGameProfile().properties().get("textures").iterator().next();
                    ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                    propBuilder.put("textures", new Property(property.name(), property.value(), property.signature()));
                    PropertyMap propertyMap = new PropertyMap(propBuilder.build());
                    GameProfile newProfile = new GameProfile(
                            componentMap.get(DataComponentTypes.PROFILE).getGameProfile().id(),
                            componentMap.get(DataComponentTypes.PROFILE).getGameProfile().name(),
                            propertyMap
                    );
                    componentMap = ComponentMap.builder()
                            .addAll(componentMap)
                            .add(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(newProfile))
                            .build();
                }
            }


            ItemStack headStack = Items.PLAYER_HEAD.getDefaultStack();
            UUID uuid = SkinUtils.getUUIDFromComponentMap(componentMap);
            ProfileComponent profileComponent = componentMap.get(DataComponentTypes.PROFILE);
            boolean brokenHead = false;

            //Try and fix broken names
            if (profileComponent.getGameProfile().name().isEmpty() || profileComponent.getGameProfile().name().isBlank()) {

                String name = SkinUtils.getNameFromComponentMap(componentMap);
                if(name == null){
                    headStack.set(DataComponentTypes.ITEM_NAME, Text.of("Unknown Head").getWithStyle(UNKNOWN_STYLE).getFirst());
                    name = "Unknown";
                    brokenHead = true;
                }

                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                if (profileComponent.getGameProfile().properties().containsKey("textures")) {
                    Property property = profileComponent.getGameProfile().properties().get("textures").iterator().next();
                    propBuilder.put("textures", new Property(property.name(), property.value(), property.signature()));
                }
                PropertyMap propertyMap = new PropertyMap(propBuilder.build());

                GameProfile newGameProfile = new GameProfile(uuid, name, propertyMap);
                profileComponent = ProfileComponent.ofStatic(newGameProfile);
            }




            if (!brokenHead) {
                if (componentMap.contains(DataComponentTypes.ITEM_NAME)) headStack.set(DataComponentTypes.ITEM_NAME, componentMap.get(DataComponentTypes.ITEM_NAME));

                //Update skin and name (with fallback to original data if API fails)
                if(componentMap.contains(DataComponentTypes.ITEM_NAME)) {
                    if (silkLevel < 1) {
                        try {
                            @Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(profileComponent.getGameProfile().id());
                            if (skinValues != null) {
                                ImmutableMultimap.Builder<String, Property> propBuilder = ImmutableMultimap.builder();
                                propBuilder.put("textures", new Property("textures", skinValues[0], skinValues[1]));
                                PropertyMap propertyMap = new PropertyMap(propBuilder.build());
                                GameProfile newGameProfile = new GameProfile(uuid, skinValues[2], propertyMap);
                                profileComponent = ProfileComponent.ofStatic(newGameProfile);

                                DataResult<JsonElement> json = TextCodecs.CODEC.encodeStart(world.getRegistryManager().getOps(JsonOps.INSTANCE), componentMap.get(DataComponentTypes.ITEM_NAME));
                                JsonElement jsonElement = json.getOrThrow();
                                String nameString = jsonElement.isJsonPrimitive() ? jsonElement.getAsString() : jsonElement.toString();

                                int index = nameString.indexOf('§');
                                if (index >= 0 && index < nameString.length() - 1) {
                                    char nameColor = nameString.charAt(index + 1);
                                    Text nameText = Text.of("§" + nameColor + "§l" + skinValues[2] + "'s §f§lHead");
                                    headStack.set(DataComponentTypes.ITEM_NAME, nameText);
                                }
                            }
                            // If skinValues is null (API failed), profileComponent keeps original data
                        } catch (Exception e) {
                            // If any exception occurs during skin update, keep original profile data
                            // profileComponent already contains original data from line 83
                        }
                    }
                }
            }

            if (componentMap.contains(DataComponentTypes.LORE)) {
                LoreComponent lore = componentMap.get(DataComponentTypes.LORE);
                List<Text> newLoreLines = new ArrayList<>();
                DynamicRegistryManager registryManager = world.getRegistryManager();
                for (Text line : lore.lines()) {
                    Text newLine = line;
                    if (line.getString().startsWith("{")) {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(line.getString());
                            DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
                            newLine = result.getOrThrow().getFirst();
                        } catch (Exception ignored) {
                        }
                    }
                    newLoreLines.add(newLine);
                }
                headStack.set(DataComponentTypes.LORE, new LoreComponent(newLoreLines));
            }

            //If missing lore
            if (!componentMap.contains(DataComponentTypes.LORE) || (componentMap.get(DataComponentTypes.LORE).lines().isEmpty())){
                headStack.set(DataComponentTypes.LORE, LoreComponent.DEFAULT.with(Text.of("This head's origin has been lost to time").getWithStyle(UNKNOWN_STYLE_LORE).getFirst()));
            }

            if(componentMap.contains(DataComponentTypes.CUSTOM_DATA)){
                NbtCompound customData = componentMap.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
                if (customData.contains("custom_name")) {
                    java.util.Optional<String> optionalCustomNameString = customData.getString("custom_name");

                    if (optionalCustomNameString.isPresent()) {
                        String customNameString = removeQuotes(optionalCustomNameString.get());
                        if (!customNameString.isEmpty()) {
                            Text customNameText;
                            if (customNameString.startsWith("{")) {
                                try {
                                    JsonElement jsonElement = JsonParser.parseString(customNameString);
                                    DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
                                    customNameText = result.getOrThrow().getFirst();
                                } catch (Exception ignored) {
                                    customNameText = Text.of(customNameString);
                                }
                            } else {
                                customNameText = Text.of(customNameString);
                            }

                            headStack.set(DataComponentTypes.ITEM_NAME, customNameText);
                            headStack.set(DataComponentTypes.CUSTOM_NAME, customNameText);
                        }
                    }
                }
            } else if (componentMap.contains(DataComponentTypes.CUSTOM_NAME)) {
                Text customName = componentMap.get(DataComponentTypes.CUSTOM_NAME);
                if (customName != null && !customName.getString().isEmpty()) {
                    String plainString = customName.getString();
                    String cleanedString = removeQuotes(plainString);

                    if (!cleanedString.equals(plainString)) {
                        // Quotes were removed, need to recreate Text
                        Text finalCustomNameText;
                        if (cleanedString.startsWith("{")) {
                            try {
                                JsonElement jsonElement = JsonParser.parseString(cleanedString);
                                DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
                                finalCustomNameText = result.getOrThrow().getFirst();
                            } catch (Exception ignored) {
                                finalCustomNameText = Text.of(cleanedString);
                            }
                        } else {
                            finalCustomNameText = Text.of(cleanedString);
                        }
                        headStack.set(DataComponentTypes.ITEM_NAME, finalCustomNameText);
                        headStack.set(DataComponentTypes.CUSTOM_NAME, finalCustomNameText);
                    } else {
                        // No quotes to remove, preserve original Text with formatting
                        headStack.set(DataComponentTypes.ITEM_NAME, customName);
                        headStack.set(DataComponentTypes.CUSTOM_NAME, customName);
                    }
                }
            } else if (componentMap.contains(DataComponentTypes.ITEM_NAME)) {
                Text itemName = componentMap.get(DataComponentTypes.ITEM_NAME);
                if (itemName != null && !itemName.getString().isEmpty()) {
                    String itemNameString = removeQuotes(itemName.getString());
                    Text finalCustomNameText;
                    if (itemNameString.startsWith("{")) {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(itemNameString);
                            DataResult<Pair<Text, JsonElement>> result = TextCodecs.CODEC.decode(world.getRegistryManager().getOps(JsonOps.INSTANCE), jsonElement);
                            finalCustomNameText = result.getOrThrow().getFirst();
                        } catch (Exception ignored) {
                            finalCustomNameText = Text.of(itemNameString);
                        }
                    } else {
                        finalCustomNameText = Text.of(itemNameString);
                    }
                    headStack.set(DataComponentTypes.ITEM_NAME, finalCustomNameText);
                    headStack.set(DataComponentTypes.CUSTOM_NAME, finalCustomNameText);
                }
            }

            if(componentMap.contains(DataComponentTypes.NOTE_BLOCK_SOUND)){
                headStack.set(DataComponentTypes.NOTE_BLOCK_SOUND, componentMap.get(DataComponentTypes.NOTE_BLOCK_SOUND));
            }

            headStack.set(DataComponentTypes.PROFILE, profileComponent);
            itemStackList.add(headStack);
            cir.setReturnValue(itemStackList);

        }

    }
}