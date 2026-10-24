package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.module.exploit.WaypointCoordExploit;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled() || mc.player == null) return;

        WaypointCoordExploit exploit = WaypointCoordExploit.INSTANCE;
        if (exploit == null || !exploit.isToggled()) return;

        Map<UUID, WaypointCoordExploit.TriangulationResult> results = WaypointCoordExploit.getLastResults();
        Map<UUID, WaypointCoordExploit.PendingAzimuth> azimuths = WaypointCoordExploit.getLastAzimuths();
        if (results.isEmpty() && azimuths.isEmpty()) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();

        // строки по игрокам: точные/расчётные координаты + пеленги без расчёта
        Map<UUID, String> coordLines = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        for (WaypointCoordExploit.TriangulationResult r : results.values()) {
            double dist = Math.hypot(r.wx - px, r.wz - pz);
            String distText = showDistance.getValue()
                ? String.format(" §8[§f%dм§8]", (int) Math.round(dist))
                : "";
            String line;
            if (r.isDirect) {
                line = String.format("§a%s §7: §f%d§7, §f%d%s", r.playerName, (int) r.wx, (int) r.wz, distText);
            } else {
                line = String.format("§e%s §7: §f~%d§7, §f~%d%s", r.playerName, (int) Math.round(r.wx), (int) Math.round(r.wz), distText);
            }
            coordLines.put(r.uuid, line);
        }

        List<Map.Entry<UUID, String>> coordEntries = new ArrayList<>(coordLines.entrySet());
        coordEntries.sort((a, b) -> {
            double da = distanceOf(results.get(a.getKey()), px, pz);
            double db = distanceOf(results.get(b.getKey()), px, pz);
            return Double.compare(da, db);
        });

        if (showHeader.getValue()) {
            lines.add(String.format("§d%s §8[%d]", getName(), coordEntries.size() + azimuths.size()));
        }
        for (Map.Entry<UUID, String> e : coordEntries) {
            lines.add(e.getValue());
        }

        if (showPending.getValue()) {
            for (WaypointCoordExploit.PendingAzimuth az : azimuths.values()) {
                if (results.containsKey(az.uuid)) continue; // уже есть координаты
                lines.add(String.format("§7%s §fпеленг §7%.0f°", az.playerName, az.azimuthDeg));
            }
        }

        if (lines.isEmpty()) return;
        if (lines.size() > MAX_ROWS + 1) lines = lines.subList(0, MAX_ROWS + 1);

        int fh = FontManager.INSTANCE.getFontHeight();
        int lineH = fh + 2;
        float textWidth = 0;
        for (String line : lines) {
            float w = FontManager.INSTANCE.getStringWidth(line);
            if (w > textWidth) textWidth = w;
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

    private double distanceOf(WaypointCoordExploit.TriangulationResult r, double px, double pz) {
        if (r == null) return Double.MAX_VALUE;
        return (r.wx - px) * (r.wx - px) + (r.wz - pz) * (r.wz - pz);
    }
}