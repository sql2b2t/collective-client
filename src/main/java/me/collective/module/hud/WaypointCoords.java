package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.module.exploit.WaypointCoordExploit;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HUD-модуль для {@link WaypointCoordExploit}: живой список найденных
 * координат игроков. Зелёным — точные позиции от сервера (внутри 332
 * блоков), жёлтым — триангулированные по пеленгам, серым — только пеленг.
 */
public class WaypointCoords extends HudModule {
    public final BooleanSetting showDistance = add(new BooleanSetting("ShowDistance", true));
    public final BooleanSetting showPending = add(new BooleanSetting("ShowPending", true));
    public final BooleanSetting showHeader = add(new BooleanSetting("ShowHeader", true));

    private static final int MAX_ROWS = 15;

    public WaypointCoords() {
        super("WaypointCoords", "Список координат игроков, найденных WaypointCoordExploit.", 4, 320);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        WaypointCoordExploit exploit = WaypointCoordExploit.INSTANCE;
        if (exploit == null || !exploit.isToggled()) return;

        Map<UUID, WaypointCoordExploit.TriangulationResult> results = WaypointCoordExploit.getLastResults();
        if (results.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();

        List<WaypointCoordExploit.TriangulationResult> list = new ArrayList<>();
        for (WaypointCoordExploit.TriangulationResult r : results.values()) {
            if (!r.hasCoords && Float.isNaN(r.currentAzimuth)) {
                // нет ни пеленга, ни координат — пропускаем
                continue;
            }
            if (!r.hasCoords && !showPending.getValue()) continue;
            list.add(r);
        }

        if (list.isEmpty()) return;

        list.sort((a, b) -> {
            double da = a.hasCoords ? (a.wx - px) * (a.wx - px) + (a.wz - pz) * (a.wz - pz) : Double.MAX_VALUE;
            double db = b.hasCoords ? (b.wx - px) * (b.wx - px) + (b.wz - pz) * (b.wz - pz) : Double.MAX_VALUE;
            int c = Double.compare(da, db);
            if (c != 0) return c;
            return Long.compare(b.lastUpdated, a.lastUpdated);
        });

        if (list.size() > MAX_ROWS) list = list.subList(0, MAX_ROWS);

        int fh = FontManager.INSTANCE.getFontHeight();
        int lineH = fh + 2;
        List<String> lines = new ArrayList<>();
        float textWidth = 0;

        if (showHeader.getValue()) {
            String header = String.format("§d%s §8[%d]", getName(), list.size());
            lines.add(header);
            textWidth = FontManager.INSTANCE.getStringWidth(header);
        }

        for (WaypointCoordExploit.TriangulationResult r : list) {
            double dist = r.hasCoords ? Math.hypot(r.wx - px, r.wz - pz) : Double.MAX_VALUE;
            String distText = showDistance.getValue() && dist != Double.MAX_VALUE
                ? String.format(" §8[§f%dм§8]", (int) Math.round(dist))
                : "";
            String line;
            if (r.hasCoords && r.isDirect) {
                line = String.format("§a%s §7: §f%d§7, §f%d%s", r.playerName, (int) r.wx, (int) r.wz, distText);
            } else if (r.hasCoords) {
                line = String.format("§e%s §7: §f~%d§7, §f~%d%s", r.playerName, (int) Math.round(r.wx), (int) Math.round(r.wz), distText);
            } else {
                float deg = (float) Math.toDegrees(r.currentAzimuth);
                deg = (deg % 360 + 360) % 360;
                line = String.format("§7%s §fпеленг §7%.0f°", r.playerName, deg);
            }
            float w = FontManager.INSTANCE.getStringWidth(line);
            if (w > textWidth) textWidth = w;
            lines.add(line);
        }

        this.width = textWidth + 6;
        this.height = lines.size() * lineH + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        float curY = y + 2;
        for (String line : lines) {
            FontManager.INSTANCE.drawStringWithShadow(context, line, x + 3, curY, HUD.INSTANCE.getHudColor(0));
            curY += lineH;
        }
    }
}
