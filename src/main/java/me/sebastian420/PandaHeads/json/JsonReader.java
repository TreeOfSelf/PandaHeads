package me.sebastian420.PandaHeads.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.SkinUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JsonReader {

    public static void run(MinecraftServer server) {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path filePath = configDir.resolve("headLocations.json");

        try (FileReader reader = new FileReader(filePath.toFile())) {
            JsonElement element = JsonParser.parseReader(reader);

            JsonArray headLocationsArray = element.getAsJsonArray();


            for (JsonElement headLocationElement : headLocationsArray) {
                JsonObject headLocation = headLocationElement.getAsJsonObject();

                String owningPlayerUUID = headLocation.get("owningPlayerUUID").getAsString();
                String displayName = headLocation.get("displayName").getAsString();
                JsonArray loreArray = headLocation.getAsJsonArray("lore");
                JsonObject location = headLocation.getAsJsonObject("location");

                String world = location.get("world").getAsString();
                int x = location.get("x").getAsInt();
                int y = location.get("y").getAsInt();
                int z = location.get("z").getAsInt();

                ServerWorld serverWorld = null;
                switch(world){
                    case "world":
                        serverWorld = server.getWorld(ServerWorld.OVERWORLD);
                        break;
                    case "world_nether":
                        serverWorld = server.getWorld(ServerWorld.NETHER);
                        break;
                    case "world_the_end":
                        serverWorld = server.getWorld(ServerWorld.END);
                        break;
                }

                BlockState blockState = serverWorld.getBlockState(new BlockPos(x, y, z));
                BlockEntity blockEntity = serverWorld.getBlockEntity(new BlockPos(x,y,z));

                if(blockState.getBlock() == Blocks.PLAYER_HEAD || blockState.getBlock() == Blocks.PLAYER_WALL_HEAD){

                    System.out.println("Player UUID: " + owningPlayerUUID);
                    System.out.println("Display Name: " + displayName);
                    System.out.println("Lore: " + loreArray);
                    System.out.println("Location: [World: " + world + ", X: " + x + ", Y: " + y + ", Z: " + z + "]");


                    ComponentMap.Builder newBlockEntityComponents = ComponentMap.builder();
                    UUID uuid = UUID.fromString(owningPlayerUUID);
                    @Nullable String[] skinValues = SkinUtils.fetchSkinByUUID(uuid);

                    if (skinValues != null) {
                        String name = skinValues[2];
                        ProfileComponent profileComponent = new ProfileComponent(new GameProfile(uuid, name));
                        profileComponent.properties().clear();
                        profileComponent.properties().put("textures",new Property("textures",skinValues[0],skinValues[1]));
                        newBlockEntityComponents.add(DataComponentTypes.PROFILE, profileComponent);
                    }else {
                        newBlockEntityComponents.add(DataComponentTypes.PROFILE, new ProfileComponent(new GameProfile(uuid,"Unknown")));
                        System.out.println("UUID No longer exists!");
                    }


                    List<Text> loreList = new ArrayList<>();
                    for (JsonElement loreElement : loreArray) {
                        loreList.add(Text.of(loreElement.getAsString()));
                    }

                    newBlockEntityComponents.add(DataComponentTypes.LORE, new LoreComponent(loreList));
                    newBlockEntityComponents.add(DataComponentTypes.ITEM_NAME, Text.of(displayName));
                    newBlockEntityComponents.add(DataComponentTypes.CUSTOM_NAME, Text.of(displayName));

                    blockEntity.setComponents(newBlockEntityComponents.build());

                    System.out.println();


                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
