package me.TreeOfSelf.PandaHeads.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {

	private static final Style DEATH_TIME = Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false);
	private static final Style DEATH_REASON_STYLE = Style.EMPTY.withColor(ChatFormatting.RED).withItalic(true).withItalic(false);
	private static final Style DATE_STYLE = Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true).withItalic(false);

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

	@Inject(at = @At("HEAD"), method = "die")
	private void ourOnDeath(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer serverPlayerEntity = (ServerPlayer) (Object) this;

		CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:player_head");
		tag.putInt("count", 1);

		LocalDate currentDate = LocalDate.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
		String formattedDate = currentDate.format(formatter);

		int lastDeathTime = serverPlayerEntity.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH) / 20;
		int playHours = serverPlayerEntity.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 20 / 60 / 60;

		Identifier sound = null;

		String nameColor = "f";
		if (playHours >= 1 && playHours < 3) {
			nameColor = "7";
		} else if (playHours >= 3 && playHours < 6) {
			nameColor = "a";
			sound = NoteBlockInstrument.HAT.getSoundEvent().value().location();
		} else if (playHours >= 6 && playHours < 10) {
			nameColor = "2";
			sound = NoteBlockInstrument.BASEDRUM.getSoundEvent().value().location();
		} else if (playHours >= 10 && playHours < 24) {
			nameColor = "b";
			sound = NoteBlockInstrument.SNARE.getSoundEvent().value().location();
		} else if (playHours >= 24 && playHours < 48) {
			nameColor = "9";
			sound = NoteBlockInstrument.BASS.getSoundEvent().value().location();
		} else if (playHours >= 48 && playHours < 72) {
			nameColor = "3";
			sound = NoteBlockInstrument.BELL.getSoundEvent().value().location();
		} else if (playHours >= 72 && playHours < 168) {
			nameColor = "1";
			sound = NoteBlockInstrument.COW_BELL.getSoundEvent().value().location();
		} else if (playHours >= 168 && playHours < 336) {
			nameColor = "d";
			sound = NoteBlockInstrument.CHIME.getSoundEvent().value().location();
		} else if (playHours >= 336 && playHours < 504) {
			nameColor = "5";
			sound = NoteBlockInstrument.IRON_XYLOPHONE.getSoundEvent().value().location();
		} else if (playHours >= 504 && playHours < 672) {
			nameColor = "e";
			sound = NoteBlockInstrument.PLING.getSoundEvent().value().location();
		} else if (playHours >= 672 && playHours < 1344) {
			nameColor = "6";
			sound = NoteBlockInstrument.BANJO.getSoundEvent().value().location();
		} else if (playHours >= 1344 && playHours < 2016) {
			nameColor = "c";
			sound = NoteBlockInstrument.BIT.getSoundEvent().value().location();
		} else if (playHours >= 2016) {
			nameColor = "4";
			sound = NoteBlockInstrument.DRAGON.getSoundEvent().value().location();
		}

		List<Component> loreList = new ArrayList<>();
		loreList.add(serverPlayerEntity.getCombatTracker().getDeathMessage().copy().withStyle(DEATH_REASON_STYLE));
		loreList.add(Component.literal("Alive for: " + formatSeconds(lastDeathTime)).copy().withStyle(DEATH_TIME));
		loreList.add(Component.literal(formattedDate).copy().withStyle(DATE_STYLE));

		Component nameText = Component.literal("§" + nameColor + "§l" + serverPlayerEntity.getName().getString() + "'s §f§lHead");

		var ops = RegistryOps.create(NbtOps.INSTANCE, serverPlayerEntity.level().registryAccess());
		ItemStack player_skull = ItemStack.CODEC.parse(ops, tag)
				.getOrThrow(error -> new RuntimeException("Failed to parse ItemStack: " + error));

		player_skull.set(DataComponents.ITEM_NAME, nameText);
		player_skull.set(DataComponents.CUSTOM_NAME, nameText);
		player_skull.set(DataComponents.LORE, new ItemLore(loreList));
		player_skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(serverPlayerEntity.getGameProfile()));
		if (sound != null) player_skull.set(DataComponents.NOTE_BLOCK_SOUND, sound);
		if (serverPlayerEntity.getInventory().getFreeSlot() == -1) {
			serverPlayerEntity.drop(player_skull, false, false);
		} else {
			serverPlayerEntity.getInventory().add(player_skull);
		}
	}
}
