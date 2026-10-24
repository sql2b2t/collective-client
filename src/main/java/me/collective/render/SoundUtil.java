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
            Identifier id = Identifier.fromNamespaceAndPath("collective", "click");
            SoundEvent event = SoundEvent.createVariableRangeEvent(id);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        } catch (Exception e) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
        }
    }

    public static void playHover(float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Identifier id = Identifier.fromNamespaceAndPath("collective", "hover");
            SoundEvent event = SoundEvent.createVariableRangeEvent(id);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        } catch (Exception ignored) {
        }
    }
}