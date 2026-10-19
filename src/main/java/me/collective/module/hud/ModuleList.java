package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


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

    /**
     * Готовая строка списка: имя и ширина посчитаны один раз на пересборку,
     * а не заново меряются у шрифта на каждый кадр.
     */
    private static final class Entry {
        final Module module;
        final float width;

        Entry(Module module, float width) {
            this.module = module;
            this.width = width;
        }
    }

    /** Стрим с фильтром и сортировкой (~90 модулей) — не чаще, чем раз в 50 мс. */
    private List<Entry> cachedEntries = List.of();
    private long lastRebuild;

    private List<Entry> entries() {
        long now = System.currentTimeMillis();
        if (now - this.lastRebuild < 50) return this.cachedEntries;
        this.lastRebuild = now;

        FontManager fm = FontManager.INSTANCE;
        boolean onlyBound = this.onlyBound.getValue();
        List<Entry> list = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (!m.isToggled() || m instanceof HudModule) continue;
            if (onlyBound && m.getKeybind().getValue() == -1) continue;
            list.add(new Entry(m, fm.getStringWidth(m.getName())));
        }

        if (this.sort.getValue() == SortMode.LENGTH) {
            list.sort((e1, e2) -> Float.compare(e2.width, e1.width));
        } else {
            list.sort(Comparator.comparing(e -> fm.getStringWidth(e.module.getName())));
        }

        return this.cachedEntries = list;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        List<Entry> activeModules = entries();

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        boolean rightAlign = mc.getWindow() != null && x > (mc.getWindow().getScaledWidth() / 2f);

        if (!background.getValue()) {
            float curY = y;
            float maxWidth = 40;
            for (Entry e : activeModules) {
                float w = e.width;
                maxWidth = Math.max(maxWidth, w);
                float textX = rightAlign ? (x + maxWidth - w) : x;
                fm.drawStringWithShadow(context, e.module.getName(), textX, curY, HudTheme.TEXT);
                curY += fontH + 1;
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
        for (Entry e : activeModules) {
            columnW = Math.max(columnW, padX * 2 + dotR * 2 + dotGap + e.width);
        }

        float curY = y;
        for (Entry e : activeModules) {
            String name = e.module.getName();
            float pillW = padX * 2 + dotR * 2 + dotGap + e.width;
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
