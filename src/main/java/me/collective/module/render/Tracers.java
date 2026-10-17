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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

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
        if (!isToggled() || mc.player == null || mc.world == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !e.isAlive()) continue;
            if (mc.player.distanceTo(e) > range.getFloatValue()) continue;

            Color c = null;
            if (e instanceof PlayerEntity p) {
                if (p.isSpectator()) continue;
                boolean friend = FriendManager.getInstance().isFriend(p);
                if (friend && !friends.getValue()) continue;
                if (!friend && !players.getValue()) continue;
                c = friend ? friendColor.getValue() : playerColor.getValue();
            } else if (e instanceof ItemEntity) {
                if (!items.getValue()) continue;
                c = itemColor.getValue();
            } else if (e instanceof LivingEntity) {
                if (!mobs.getValue()) continue;
                c = mobColor.getValue();
            } else {
                continue;
            }

            Vec3d target = e.getBoundingBox().getCenter();
            Renderer3D.line(cam.x, cam.y, cam.z, target.x, target.y, target.z, c);
        }
    }
}
