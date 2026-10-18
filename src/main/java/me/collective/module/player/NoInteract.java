package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;

/**
 * Блокирует ПКМ-взаимодействия игрока с блоками.
 *
 * <p>Обычный режим: у любого блока глушится ветка {@code onUse} — не открываются ГУИ
 * (сундуки, верстаки, печи и т.д.), не нажимаются кнопки, двери, плиты, рычаги.
 * Установка блоков и использование предметов на блоках при этом работают как обычно
 * (включая установку без Shift прямо на контейнеры). Взаимодействия, инициированные
 * модулями клиента (ауры и т.п.), не затрагиваются — реагируем только на реальное
 * зажатие клавиши «Использовать».
 *
 * <p>Строгий режим: ПКМ по любому блоку полностью игнорируется — в том числе установка
 * блоков и использование предметов (взгляд в воздух при этом работает штатно).
 */
public class NoInteract extends Module {
    public static NoInteract INSTANCE;

    public final BooleanSetting strict = add(new BooleanSetting("Строгий режим", false));

    public NoInteract() {
        super("NoInteract", "Блокирует взаимодействие с блоками по ПКМ на всех блоках: ГУИ не открываются, двери и кнопки не срабатывают. Установка блоков и предметы работают; строгий режим игнорирует ПКМ по блокам полностью.", Category.PLAYER);
        INSTANCE = this;
    }

    public boolean isStrict() {
        return strict.getValue();
    }

    /** Активен ли запрет для текущего ручного нажатия ПКМ. */
    public boolean shouldBlockManual() {
        return isToggled() && mc != null && mc.options != null && mc.options.useKey.isPressed();
    }
}
