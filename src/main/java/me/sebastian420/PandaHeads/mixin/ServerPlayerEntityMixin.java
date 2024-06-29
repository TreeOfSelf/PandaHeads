package me.sebastian420.PandaHeads.mixin;

import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import me.sebastian420.PandaHeads.SkinUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.StatType;
import net.minecraft.stat.Stats;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiling.jfr.sample.FileIoSample;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Shadow @Nullable public abstract Text getPlayerListName();

	@Shadow public abstract ServerWorld getServerWorld();

	@Shadow @Final public ServerPlayerInteractionManager interactionManager;

	@Shadow @Nullable private PublicPlayerSession session;

	private static final Style DEATH_TIME = Style.EMPTY.withColor(Formatting.WHITE).withItalic(false);
	private static final Style DEATH_REASON_STYLE = Style.EMPTY.withColor(Formatting.RED).withItalic(true).withItalic(false);
	private static final Style DATE_STYLE = Style.EMPTY.withColor(Formatting.YELLOW).withBold(true).withItalic(false);



	@Unique
	private static String formatSeconds(int totalSeconds) {
		int seconds = totalSeconds % 60;
		int totalMinutes = totalSeconds / 60;
		int minutes = totalMinutes % 60;
		int totalHours = totalMinutes / 60;
		int hours = totalHours % 24;
		int days = totalHours / 24;

		StringBuilder prettyTime = new StringBuilder();

		if (days > 0) {
			prettyTime.append(days).append(days == 1 ? " Day " : " Days ");
			prettyTime.append(hours).append(hours == 1 ? " Hour " : " Hours ");
			prettyTime.append(minutes).append(minutes == 1 ? " Minute " : " Minutes ");
			prettyTime.append(seconds).append(seconds == 1 ? " Second" : " Seconds");
		} else if (hours > 0) {
			prettyTime.append(hours).append(hours == 1 ? " Hour " : " Hours ");
			prettyTime.append(minutes).append(minutes == 1 ? " Minute " : " Minutes ");
			prettyTime.append(seconds).append(seconds == 1 ? " Second" : " Seconds");
		} else if (minutes > 0) {
			prettyTime.append(minutes).append(minutes == 1 ? " Minute " : " Minutes ");
			prettyTime.append(seconds).append(seconds == 1 ? " Second" : " Seconds");
		} else {
			prettyTime.append(seconds).append(seconds == 1 ? " Second" : " Seconds");
		}

		return prettyTime.toString().trim();
	}

	@Inject(at = @At("HEAD"), method = "onDeath")
	private void ourOnDeath(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity)(Object)this;

		NbtCompound tag = new NbtCompound();
		tag.putString("id", "minecraft:player_head");
		tag.putInt("count", 1);

		LocalDate currentDate = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
		String formattedDate = currentDate.format(formatter);

		int lastDeathTime = serverPlayerEntity.getStatHandler().getStat(Stats.CUSTOM, Stats.TIME_SINCE_DEATH)/20;
		int playHours = serverPlayerEntity.getStatHandler().getStat(Stats.CUSTOM, Stats.PLAY_TIME)/20/60/60;

		String nameColor = "f";
		if (playHours>=1 && playHours<3){
			nameColor="7";
		}else if(playHours>=3 && playHours<6){
			nameColor="a";
		}else if(playHours>=6 && playHours<10){
			nameColor="2";
		}else if(playHours>=10 && playHours<24){
			nameColor="b";
		}else if(playHours>=24 && playHours<48) {
			nameColor = "9";
		}else if(playHours>=48 && playHours<72) {
			nameColor = "3";
		}else if(playHours>=72 && playHours<168) {
			nameColor = "1";
		}else if(playHours>=168 && playHours<336) {
			nameColor = "d";
		}else if(playHours>=336 && playHours<504) {
			nameColor = "5";
		}else if(playHours>=504 && playHours<672) {
			nameColor = "e";
		}else if(playHours>=672 && playHours<1344) {
			nameColor = "6";
		}else if(playHours>=1344 && playHours<2016) {
			nameColor = "c";
		}else if(playHours>=2016) {
			nameColor = "4";
		}



		List<Text> loreList = new ArrayList<>();
		loreList.add((damageSource.getDeathMessage(serverPlayerEntity).copy().setStyle(DEATH_REASON_STYLE)));
		loreList.add(Text.of("Alive for: "+formatSeconds(lastDeathTime)).getWithStyle(DEATH_TIME).getFirst());
		loreList.add(Text.of(formattedDate).getWithStyle(DATE_STYLE).getFirst());

		Text nameText = Text.of("§"+nameColor+"§l" +serverPlayerEntity.getName().getString()+"'s §f§lHead");

		ItemStack player_skull = ItemStack.fromNbtOrEmpty(this.getServerWorld().getRegistryManager(), tag);
		player_skull.set(DataComponentTypes.ITEM_NAME,nameText);
		player_skull.set(DataComponentTypes.LORE, new LoreComponent(loreList));
		player_skull.set(DataComponentTypes.PROFILE, new ProfileComponent(serverPlayerEntity.getGameProfile()));
		serverPlayerEntity.getInventory().insertStack(player_skull);
	}
}
