package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.NumberSetting;
import me.collective.manager.FriendManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Порт Tracers (Meteor/ThunderHack).
 *
 * <p>Линии до игроков от камеры. Друзья — своим цветом.
 */
public class Tracers extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 64.0, 8.0, 256.0, 4.0));
    public final BooleanSetting players = add(new BooleanSetting("Игроки", true));
    public final BooleanSetting friends = add(new BooleanSetting("Друзья", true));
    public final BooleanSetting items = add(new BooleanSetting("Предметы", false));
    public final BooleanSetting mobs = add(new BooleanSetting("Мобы", false));
    public final ColorSetting playerColor = add(new ColorSetting("Цвет игроков", new Color(255, 80, 80, 200)));
    public final ColorSetting friendColor = add(new ColorSetting("Цвет друзей", new Color(80, 255, 120, 200)));
    public final ColorSetting itemColor = add(new ColorSetting("Цвет предметов", new Color(255, 200, 80, 200)));
    public final ColorSetting mobColor = add(new ColorSetting("Цвет мобов", new Color(200, 120, 255, 200)));

    public Tracers() {
        super("Tracers", "Линии до игроков и предметов.", Category.RENDER);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!isToggled() || mc.player == null || mc.level == null) return;
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double rangeSq = this.range.getFloatValue() * this.range.getFloatValue();

        // Игроки — через готовый маленький список игроков; полный скан всех
        // сущностей мира стартует только когда включены предметы или мобы.
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            boolean friend = FriendManager.getInstance().isFriend(p);
            if (friend && !friends.getValue()) continue;
            if (!friend && !players.getValue()) continue;
            if (mc.player.distanceToSqr(p) > rangeSq) continue;

            Vec3 target = p.getBoundingBox().getCenter();
            Renderer3D.line(cam.x, cam.y, cam.z, target.x, target.y, target.z,
                    friend ? friendColor.getValue() : playerColor.getValue());
        }

        if (items.getValue() || mobs.getValue()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !e.isAlive() || e instanceof Player) continue;
                if (mc.player.distanceToSqr(e) > rangeSq) continue;

                Color c;
                if (e instanceof ItemEntity) {
                    if (!items.getValue()) continue;
                    c = itemColor.getValue();
                } else if (e instanceof LivingEntity) {
                    if (!mobs.getValue()) continue;
                    c = mobColor.getValue();
                } else {
                    continue;
                }

                Vec3 target = e.getBoundingBox().getCenter();
                Renderer3D.line(cam.x, cam.y, cam.z, target.x, target.y, target.z, c);
            }
        }
    }
}