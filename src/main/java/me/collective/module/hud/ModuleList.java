package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleList extends HudModule {
    public enum SortMode {
        LENGTH,
        ALPHABET
    }

    public final BooleanSetting onlyBound = add(new BooleanSetting("OnlyBound", false));
    public final EnumSetting<SortMode> sort = add(new EnumSetting<>("Sort", SortMode.LENGTH));

    public ModuleList() {
        super("ModuleList", "Displays active client modules.", 4, 30);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        List<Module> activeModules = ModuleManager.INSTANCE.getModules().stream()
                .filter(m -> m.isToggled() && !(m instanceof HudModule))
                .filter(m -> !onlyBound.getValue() || m.getKeybind().getValue() != -1)
                .collect(Collectors.toList());

        if (sort.getValue() == SortMode.LENGTH) {
            activeModules.sort((m1, m2) -> Float.compare(
                    FontManager.INSTANCE.getStringWidth(m2.getName()),
                    FontManager.INSTANCE.getStringWidth(m1.getName())
            ));
        } else {
            activeModules.sort(Comparator.comparing(Module::getName));
        }

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        boolean rightAlign = mc.getWindow() != null && x > (mc.getWindow().getScaledWidth() / 2f);

        if (!background.getValue()) {
            float curY = y;
            float maxWidth = 40;
            int idx = 0;
            for (Module m : activeModules) {
                String name = m.getName();
                float w = fm.getStringWidth(name);
                maxWidth = Math.max(maxWidth, w);
                float textX = rightAlign ? (x + maxWidth - w) : x;
                fm.drawStringWithShadow(context, name, textX, curY, HudTheme.TEXT);
                curY += fontH + 1;
                idx++;
            }
            this.width = maxWidth;
            this.height = Math.max(fontH, curY - y);
            return;
        }

        float padX = 6f;
        float padY = 3.5f;
        float pillH = fontH + padY * 2f;
        float gap = 2.5f;
        float dotR = 2f;
        float dotGap = 3.5f;

        // Первый проход: колонка = максимальная ширина пилюли.
        float columnW = 40f;
        for (Module m : activeModules) {
            columnW = Math.max(columnW, padX * 2 + dotR * 2 + dotGap + fm.getStringWidth(m.getName()));
        }

        float curY = y;
        for (Module m : activeModules) {
            String name = m.getName();
            float nameW = fm.getStringWidth(name);
            float pillW = padX * 2 + dotR * 2 + dotGap + nameW;
            float rowX = rightAlign ? (x + columnW - pillW) : x;

            GuiGfx.fillRounded(context, rowX, curY, pillW, pillH, 4f, HudTheme.PLATE.getRGB());
            GuiGfx.strokeRounded(context, rowX, curY, pillW, pillH, 4f, 1f, HudTheme.BORDER.getRGB());
            GuiGfx.dot(context, rowX + padX + dotR, curY + pillH / 2f, dotR, HudTheme.accent().getRGB());
            fm.drawStringWithShadow(context, name, rowX + padX + dotR * 2 + dotGap, curY + padY, HudTheme.TEXT);
            curY += pillH + gap;
        }

        this.width = columnW;
        this.height = activeModules.isEmpty() ? fontH : curY - y - gap;
    }
}
