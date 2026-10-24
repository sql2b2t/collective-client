package me.collective.module.misc;

import me.collective.category.Category;
import me.collective.emotes.EmoteEngine;
import me.collective.emotes.EmoteWheelScreen;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.KeybindSetting;

/**
 * Порт PlayerEmotes (Колесо эмоций) из декомпиля.
 *
 * <p>Держишь бинд "Emote menu" — открывается колесо {@link EmoteWheelScreen},
 * отпускаешь — колесо закрывается и проигрывает выбранный эмоут само.
 */
public class EmotesModule extends Module {
    public static EmotesModule INSTANCE;

    public final KeybindSetting emoteMenu = add(new KeybindSetting("Emote menu", -1));
    public final BooleanSetting keepWhileMoving = add(new BooleanSetting("Keep While Moving", false));
    public final BooleanSetting debugLog = add(new BooleanSetting("Debug", false));
    private Object playbackWorld;
    private boolean keyWasPressed;
    private EmoteWheelScreen ownedWheel;

    public EmotesModule() {
        super("Emotes", "Колесо эмоций: зажми бинд, выбери эмоут и отпусти.", Category.MISC);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        playbackWorld = mc.level;
        keyWasPressed = emoteMenu.isDown();
        ownedWheel = null;
    }

    @Override
    public void onUpdate() {
        if (mc.level != playbackWorld || mc.player == null) {
            EmoteEngine.stop();
            if (ownedWheel != null && mc.gui.screen() == ownedWheel) mc.gui.setScreen(null);
            ownedWheel = null;
            playbackWorld = mc.level;
            keyWasPressed = emoteMenu.isDown();
        }
        if (mc.player == null || mc.level == null) return;
        EmoteEngine.tick();
        boolean pressed = emoteMenu.isDown();
        if (pressed && !keyWasPressed && mc.gui.screen() == null && EmoteEngine.millisSincePlay() > 500) {
            ownedWheel = new EmoteWheelScreen();
            mc.gui.setScreen(ownedWheel);
        } else if (!pressed && keyWasPressed && ownedWheel != null && mc.gui.screen() == ownedWheel) {
            me.collective.emotes.Emote selected = ownedWheel.getSelectedEmote();
            mc.gui.setScreen(null);
            ownedWheel = null;
            if (selected != null) EmoteEngine.play(selected);
        }
        // Escape or another screen cancels selection; holding the bind must not reopen it.
        if (mc.gui.screen() != ownedWheel) ownedWheel = null;
        keyWasPressed = pressed;
    }

    @Override
    public void onDisable() {
        EmoteEngine.stop();
        if (ownedWheel != null && mc.gui.screen() == ownedWheel) mc.gui.setScreen(null);
        ownedWheel = null;
        playbackWorld = null;
        keyWasPressed = false;
    }

    public boolean keepWhileMoving() {
        return keepWhileMoving.getValue();
    }
}