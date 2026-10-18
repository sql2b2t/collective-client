package me.collective.setting;

import java.util.function.Supplier;

/**
 * Настройка-кнопка без состояния: клик в ClickGUI мгновенно выполняет действие.
 * Используется для служебных операций модулей (например, удаления сохранённых
 * чанк-данных). Значение кнопки в конфиг не сохраняется.
 */
public class ActionSetting extends Setting<Boolean> {
    private Runnable action = () -> {
    };
    private String buttonText = "Выполнить";
    private Supplier<Boolean> visibility;

    public ActionSetting(String name, int dummy) {
        super(name, false);
    }

    /** Действие, выполняемое по клику. */
    public ActionSetting handle(Runnable action) {
        this.action = action;
        return this;
    }

    /** Условие видимости кнопки в панели модуля. */
    public ActionSetting handle(Supplier<Boolean> visibility) {
        this.visibility = visibility;
        return this;
    }

    /** Текст кнопки (по умолчанию — «Выполнить»). */
    public ActionSetting process(String buttonText) {
        this.buttonText = buttonText;
        return this;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void run() {
        action.run();
    }

    @Override
    public boolean isVisible() {
        return visibility == null || Boolean.TRUE.equals(visibility.get());
    }
}
