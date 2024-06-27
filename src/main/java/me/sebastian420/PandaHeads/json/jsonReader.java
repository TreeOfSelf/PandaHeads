package me.sebastian420.PandaHeads.json;

import net.fabricmc.loader.api.FabricLoader;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

public class jsonReader {

    public static void run() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path filePath = configDir.resolve("headLocations.json");

        JSONParser parser = new JSONParser();

        try {
            Object obj = parser.parse(new FileReader(filePath.toFile()));
            JSONArray headLocationsArray = (JSONArray) obj;

            for (Object headLocationObj : headLocationsArray) {
                JSONObject headLocation = (JSONObject) headLocationObj;

                String owningPlayerUUID = (String) headLocation.get("owningPlayerUUID");
                String displayName = (String) headLocation.get("displayName");
                JSONArray loreArray = (JSONArray) headLocation.get("lore");
                JSONObject location = (JSONObject) headLocation.get("location");

                String world = (String) location.get("world");
                long x = (long) location.get("x");
                long y = (long) location.get("y");
                long z = (long) location.get("z");

                System.out.println("Player UUID: " + owningPlayerUUID);
                System.out.println("Display Name: " + displayName);
                System.out.println("Lore: " + loreArray);
                System.out.println("Location: [World: " + world + ", X: " + x + ", Y: " + y + ", Z: " + z + "]");
                System.out.println();
            }
        } catch (IOException | ParseException e) {
            //   e.printStackTrace();
        }
    }
}
