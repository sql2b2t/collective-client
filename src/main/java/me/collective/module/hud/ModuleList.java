package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
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
        super("ModuleList", "Отображает список включённых модулей чита (Arraylist).", 4, 30);
    }

    /** Строка списка: ширина посчитана один раз на пересборку, а не на каждый кадр. */
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
            list.sort(Comparator.comparing(e -> e.module.getName()));
        }

        return this.cachedEntries = list;
    }

    @Override
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled()) return;

        List<Entry> activeModules = entries();

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        float curY = y;
        float maxWidth = 40;
        int idx = 0;
        boolean rightAlign = mc.getWindow() != null && x > (mc.getWindow().getGuiScaledWidth() / 2f);

        for (Entry e : activeModules) {
            float w = e.width;
            maxWidth = Math.max(maxWidth, w);

            Color color = HUD.INSTANCE.getHudColor(idx);

            float textX = rightAlign ? (x + maxWidth - w) : x;
            fm.drawStringWithShadow(context, e.module.getName(), textX, curY, color);
            curY += fontH + 1;
            idx++;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y);
    }
}