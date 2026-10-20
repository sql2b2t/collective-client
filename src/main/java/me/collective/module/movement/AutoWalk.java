package me.collective.module.movement;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.input.Input;
import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ModeSetting;

/**
 * Порт AutoWalk из ThunderHack + способ ходьбы через Baritone.
 *
 * <p>Режим Keys держит нажатыми игровые клавиши (как раньше). Режим
 * Baritone заводит движение через InputOverrideHandler: баритон сам
 * зацикливает ввод и обходит препятствия, а у нас руки свободны.
 */
public class AutoWalk extends Module {
    public final ModeSetting mode = add(new ModeSetting("Режим", "Keys", "Keys", "Baritone"));
    public final ModeSetting direction = add(new ModeSetting("Направление", "Forward", "Forward", "Back", "Left", "Right"));
    public final BooleanSetting sprint = add(new BooleanSetting("Спринт", true));
    public final BooleanSetting jump = add(new BooleanSetting("Прыжки", false));

    public AutoWalk() {
        super("AutoWalk", "Автоматическая ходьба (обычно или через Baritone).", Category.MOVEMENT);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.options == null) {
            releaseAll();
            return;
        }
        releaseAll();

        if (mode.process("Baritone")) {
            baritoneWalk();
        } else {
            keyWalk();
        }
    }

    private void keyWalk() {
        String dir = direction.compute().trim();
        // Нажимаем только текущее направление: при смене направления старая
        // клавиша обязана отжаться, иначе игрок поедет двумя клавишами сразу.
        mc.options.forwardKey.setPressed(dir.equals("Forward"));
        mc.options.backKey.setPressed(dir.equals("Back"));
        mc.options.leftKey.setPressed(dir.equals("Left"));
        mc.options.rightKey.setPressed(dir.equals("Right"));
        mc.player.setSprinting(sprint.getValue());
        if (jump.getValue() && mc.player.isOnGround() && mc.player.horizontalCollision) {
            mc.player.jump();
        }
    }

    private void baritoneWalk() {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getInputOverrideHandler().clearAllKeys();

            Input input = switch (direction.compute().trim()) {
                case "Back" -> Input.MOVE_BACK;
                case "Left" -> Input.MOVE_LEFT;
                case "Right" -> Input.MOVE_RIGHT;
                default -> Input.MOVE_FORWARD;
            };
            baritone.getInputOverrideHandler().setInputForceState(input, true);

            if (sprint.getValue()) {
                baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
            }

            // При упоре в стену — баритонов принудительный прыжок
            if (jump.getValue() && mc.player.isOnGround() && mc.player.horizontalCollision) {
                baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            }
        } catch (Throwable ignored) {
            releaseAll();
        }
    }

    @Override
    public void onDisable() {
        releaseAll();
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
        } catch (Throwable ignored) {
        }
    }

    private void releaseAll() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }
}
