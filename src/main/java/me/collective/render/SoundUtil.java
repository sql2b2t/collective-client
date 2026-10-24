package me.collective.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;

public class SoundUtil {
    public static void playClick(float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Identifier id = Identifier.of("collective", "click");
            SoundEvent event = SoundEvent.of(id);
            mc.getSoundManager().play(PositionedSoundInstance.ui(event, pitch, volume));
        } catch (Exception e) {
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
        }
    }

    public static void playHover(float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Identifier id = Identifier.of("collective", "hover");
            SoundEvent event = SoundEvent.of(id);
            mc.getSoundManager().play(PositionedSoundInstance.ui(event, pitch, volume));
        } catch (Exception ignored) {
        }
    }
}