package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.Render3DEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.module.client.Colors;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OpenMio-ported advanced Chams module for Collective Client.
 * Supports wallhack (XQZ), customizable entity targets (Players, Crystals, Animals, Hostiles, Self),
 * totem pop / death ghost tracking with upward floating animations, distance fading,
 * friend highlighting, and Collective theme synchronization.
 */
public class Chams extends Module {
    public static Chams INSTANCE;

    // --- Settings: Ranges & Appearance ---
    public final NumberSetting range = add(new NumberSetting("Range", 64.0, 5.0, 256.0, 1.0));
    public final BooleanSetting fade = add(new BooleanSetting("Fade", true));
    public final NumberSetting fadeRadius = add(new NumberSetting("FadeRadius", 20.0, 0.0, 100.0, 1.0));
    public final NumberSetting lineWidth = add(new NumberSetting("LineWidth", 1.5, 0.1, 5.0, 0.1));
    public final BooleanSetting model = add(new BooleanSetting("Model", true));
    public final BooleanSetting xqz = add(new BooleanSetting("XQZ", true));
    public final NumberSetting opacity = add(new NumberSetting("Opacity", 180.0, 0.0, 255.0, 5.0));
    public final BooleanSetting shine = add(new BooleanSetting("Shine", false));
    public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));
    public final NumberSetting strength = add(new NumberSetting("Strength", 1.0, 0.1, 5.0, 0.1));

    // --- Settings: Entity Targets ---
    public final EnumSetting<ChamsMode> players = add(new EnumSetting<>("Players", ChamsMode.BOTH));
    public final EnumSetting<ChamsMode> self = add(new EnumSetting<>("Self", ChamsMode.OFF));
    public final EnumSetting<ChamsMode> crystals = add(new EnumSetting<>("Crystals", ChamsMode.BOTH));
    public final EnumSetting<ChamsMode> animals = add(new EnumSetting<>("Animals", ChamsMode.OFF));
    public final EnumSetting<ChamsMode> hostiles = add(new EnumSetting<>("Hostiles", ChamsMode.OFF));

    // --- Settings: Totem Pop & Death Ghosts ---
    public final BooleanSetting pop = add(new BooleanSetting("Pop", true));
    public final BooleanSetting death = add(new BooleanSetting("Death", true));
    public final EnumSetting<ChamsMode> popMode = add(new EnumSetting<>("PopMode", ChamsMode.BOTH));
    public final NumberSetting time = add(new NumberSetting("Time", 2.5, 0.5, 10.0, 0.1));
    public final NumberSetting motion = add(new NumberSetting("Motion", 1.0, 0.0, 5.0, 0.1));
    public final BooleanSetting animate = add(new BooleanSetting("Animate", true));
    public final BooleanSetting boost = add(new BooleanSetting("Boost", false));

    // --- Settings: Colors ---
    public final BooleanSetting syncTheme = add(new BooleanSetting("SyncTheme", false));
    public final BooleanSetting friends = add(new BooleanSetting("Friends", true));
    public final ColorSetting fill = add(new ColorSetting("Fill", new Color(180, 100, 255, 120)));
    public final ColorSetting outline = add(new ColorSetting("Outline", new Color(200, 120, 255, 255)));
    public final ColorSetting shineColor = add(new ColorSetting("ShineColor", new Color(255, 255, 255, 200)));
    public final ColorSetting popFill = add(new ColorSetting("PopFill", new Color(255, 60, 60, 140)));
    public final ColorSetting popLine = add(new ColorSetting("PopLine", new Color(255, 60, 60, 255)));

    // Active ghost list for totem pop and death events
    private final List<PopGhost> ghosts = new CopyOnWriteArrayList<>();

    public Chams() {
        super("Chams", "Подсветка сущностей сквозь стены и призрачные силуэты при срабатывании тотема.", Category.RENDER);
        INSTANCE = this;
    }

    public static class PopGhost {
        public final String name;
        public final UUID uuid;
        public final double x;
        public final double y;
        public final double z;
        public final float width;
        public final float height;
        public final float yaw;
        public final float pitch;
        public final long spawnTime;

        public PopGhost(String name, UUID uuid, double x, double y, double z, float width, float height, float yaw, float pitch) {
            this.name = name;
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.yaw = yaw;
            this.pitch = pitch;
            this.spawnTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onDisable() {
        ghosts.clear();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!pop.getValue()) return;
        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            byte status = packet.getEventId();
            boolean isTotemPop = status == EntityEvent.PROTECTED_FROM_DEATH;
            boolean isDeath = status == EntityEvent.DEATH;

            if (isTotemPop || (isDeath && death.getValue())) {
                if (mc.level == null) return;
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player) {
                    if (player == mc.player && self.getValue() == ChamsMode.OFF) {
                        return;
                    }
                    ghosts.add(new PopGhost(
                        player.getName().getString(),
                        player.getUUID(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getBbWidth(),
                        player.getBbHeight(),
                        player.getYRot(),
                        player.getXRot()
                    ));
                }
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        Vec3 cameraPos = event.camera != null ? event.camera.position() : mc.player.getEyePosition();
        float tickDelta = event.tickDelta;
        long now = System.currentTimeMillis();

        // 1. Render Target Entities
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive()) continue;

            ChamsMode mode = getEntityMode(entity);
            if (mode == null || mode == ChamsMode.OFF) continue;

            double dist = cameraPos.distanceTo(entity.position());
            if (dist > range.getValue()) continue;

            float alphaFactor = calculateFadeFactor(dist);
            if (alphaFactor <= 0.01f) continue;

            Color[] colors = getEntityColors(entity, alphaFactor);
            Color entityFill = colors[0];
            Color entityOutline = colors[1];

            ShapeMode shapeMode = toShapeMode(mode);
            float lw = lineWidth.getValue().floatValue();
            boolean ignoreDepth = xqz.getValue();

            // Smooth interpolated bounding box
            AABB box = getInterpolatedBox(entity, tickDelta);
            Renderer3D.box(box, entityFill, entityOutline, shapeMode, ignoreDepth, lw);

            // Shine effect
            if (shine.getValue()) {
                renderShine(box, now, lw, ignoreDepth);
            }
        }

        // 2. Render Totem Pop / Death Ghosts
        if (!ghosts.isEmpty()) {
            float maxDuration = time.getValue().floatValue() * 1000.0f;
            float motionSpeed = motion.getValue().floatValue();
            boolean doBoost = boost.getValue();
            ShapeMode ghostShapeMode = toShapeMode(popMode.getValue());
            float lw = lineWidth.getValue().floatValue();

            Iterator<PopGhost> it = ghosts.iterator();
            while (it.hasNext()) {
                PopGhost ghost = it.next();
                long elapsed = now - ghost.spawnTime;
                if (elapsed >= maxDuration) {
                    ghosts.remove(ghost);
                    continue;
                }

                float progress = elapsed / maxDuration;
                float remaining = Mth.clamp(1.0f - progress, 0.0f, 1.0f);

                double upwardOffset = (1.0 - remaining) * motionSpeed * 2.0;
                if (doBoost) {
                    upwardOffset += Math.sin(progress * Math.PI) * 0.4;
                }

                Color ghostFillColor = applyAlpha(popFill.getValue(), remaining);
                Color ghostLineColor = applyAlpha(popLine.getValue(), remaining);

                double halfW = ghost.width * 0.5;
                AABB ghostBox = new AABB(
                    ghost.x - halfW,
                    ghost.y + upwardOffset,
                    ghost.z - halfW,
                    ghost.x + halfW,
                    ghost.y + upwardOffset + ghost.height,
                    ghost.z + halfW
                );

                // Render floating ghost box with full depth penetration (ignoreOcclusion = true)
                Renderer3D.box(ghostBox, ghostFillColor, ghostLineColor, ghostShapeMode, true, lw);

                // Render floating halo/crown above ghost
                if (animate.getValue()) {
                    double haloY = ghost.y + upwardOffset + ghost.height + 0.15;
                    double haloRadius = halfW * 0.7;
                    AABB haloBox = new AABB(
                        ghost.x - haloRadius,
                        haloY,
                        ghost.z - haloRadius,
                        ghost.x + haloRadius,
                        haloY + 0.05,
                        ghost.z + haloRadius
                    );
                    Renderer3D.box(haloBox, ghostFillColor, ghostLineColor, ShapeMode.Both, true, 1.0f);
                }
            }
        }
    }

    private ChamsMode getEntityMode(Entity entity) {
        if (entity == mc.player) {
            return self.getValue();
        }
        if (entity instanceof Player) {
            return players.getValue();
        }
        if (entity instanceof EndCrystal) {
            return crystals.getValue();
        }
        if (entity instanceof Animal) {
            return animals.getValue();
        }
        if (entity instanceof Monster) {
            return hostiles.getValue();
        }
        return null;
    }

    private float calculateFadeFactor(double dist) {
        if (!fade.getValue()) return 1.0f;
        double maxDist = range.getValue();
        double fadeStartDist = maxDist * (fadeRadius.getValue() / 100.0);
        if (dist <= fadeStartDist) return 1.0f;
        if (dist >= maxDist) return 0.0f;
        return (float) (1.0 - (dist - fadeStartDist) / (maxDist - fadeStartDist));
    }

    private Color[] getEntityColors(Entity entity, float fadeFactor) {
        float globalOpacity = (opacity.getValue().floatValue() / 255.0f) * fadeFactor;
        Color baseFill;
        Color baseOutline;

        if (friends.getValue() && entity instanceof Player player && FriendManager.getInstance().isFriend(player)) {
            // Friend highlight: vibrant cyan / emerald
            baseFill = new Color(43, 227, 165, 140);
            baseOutline = new Color(50, 255, 190, 255);
        } else if (syncTheme.getValue() && Colors.INSTANCE != null) {
            Color theme = Colors.INSTANCE.themeColor.getValue();
            baseFill = new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), fill.getValue().getAlpha());
            baseOutline = new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), outline.getValue().getAlpha());
        } else {
            baseFill = fill.getValue();
            baseOutline = outline.getValue();
        }

        return new Color[] {
            applyAlpha(baseFill, globalOpacity),
            applyAlpha(baseOutline, globalOpacity)
        };
    }

    private Color applyAlpha(Color c, float factor) {
        if (c == null) return new Color(0, 0, 0, 0);
        int a = Mth.clamp((int) (c.getAlpha() * factor), 0, 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private ShapeMode toShapeMode(ChamsMode mode) {
        return switch (mode) {
            case BOTH -> ShapeMode.Both;
            case FILL -> ShapeMode.Sides;
            case LINE -> ShapeMode.Lines;
            case OFF -> ShapeMode.Lines;
        };
    }

    private AABB getInterpolatedBox(Entity entity, float tickDelta) {
        Vec3 pos = entity.getPosition(tickDelta);
        double halfW = entity.getBbWidth() * 0.5;
        double h = entity.getBbHeight();
        return new AABB(
            pos.x - halfW, pos.y, pos.z - halfW,
            pos.x + halfW, pos.y + h, pos.z + halfW
        );
    }

    private void renderShine(AABB box, long now, float lw, boolean ignoreDepth) {
        float pulse = (float) ((Math.sin(now * 0.005 * speed.getValue()) + 1.0) * 0.5);
        float shineAlpha = pulse * (strength.getValue().floatValue() / 5.0f);
        Color sc = applyAlpha(shineColor.getValue(), shineAlpha);
        Renderer3D.box(box, sc, sc, ShapeMode.Both, ignoreDepth, lw);
    }
}