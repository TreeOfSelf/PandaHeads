package me.sebastian420.PandaHeads.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.SkinUtils;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Shadow @Nullable public abstract Text getPlayerListName();

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow @Final public ServerPlayerInteractionManager interactionManager;

	@Shadow @Nullable private PublicPlayerSession session;


	@Inject(at = @At("HEAD"), method = "onDeath")
	private void init(CallbackInfo info) {
		ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity)(Object)this;

		NbtCompound tag = new NbtCompound();
		tag.putString("id", "minecraft:player_head");
		tag.putInt("count", 1);

		@Nullable String[] skinValues = SkinUtils.fetchSkinByName("JayDay420");
		ProfileComponent profileComponent = new ProfileComponent(serverPlayerEntity.getGameProfile());
		System.out.println("Before");
		System.out.println(profileComponent.properties().toString());
		profileComponent.properties().clear();
		profileComponent.properties().put("textures",new Property("textures",skinValues[0],skinValues[1]));
		System.out.println("After");
		System.out.println(profileComponent.properties().toString());
		//new GameProfile(UUID.randomUUID(),"Jenkem").getProperties().put("name",5);

		ItemStack player_skull = ItemStack.fromNbtOrEmpty(this.getServerWorld().getRegistryManager(), tag);
		player_skull.set(DataComponentTypes.ITEM_NAME,Text.of("Some Fuckboys Head"));
		//player_skull.set(DataComponentTypes.CUSTOM_NAME,Text.of("FUCK2"));
		Style kek = Style.EMPTY.withColor(Formatting.RED).withBold(true);
		player_skull.set(DataComponentTypes.LORE, LoreComponent.DEFAULT.with(Text.of("YO SOME LORE BOi").getWithStyle(kek).getFirst()));
		player_skull.set(DataComponentTypes.PROFILE, profileComponent);
		serverPlayerEntity.getInventory().insertStack(player_skull);
	}
}