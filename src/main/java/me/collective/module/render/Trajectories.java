package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Траектории полета снарядов (жемчуг, стрелы, трезубцы, зелья, снежки).
 */
public class Trajectories extends Module {
    public final BooleanSetting pearls = add(new BooleanSetting("Pearls", true));
    public final BooleanSetting bows = add(new BooleanSetting("Bows", true));
    public final BooleanSetting tridents = add(new BooleanSetting("Tridents", true));
    public final BooleanSetting potions = add(new BooleanSetting("Potions", true));
    public final BooleanSetting snowballs = add(new BooleanSetting("Snowballs", true));
    public final BooleanSetting windCharges = add(new BooleanSetting("WindCharges", true));
    public final BooleanSetting box = add(new BooleanSetting("AABB", true));

    public final ColorSetting color = add(new ColorSetting("Color", new Color(0, 200, 255, 220)));
    public final ColorSetting hitColor = add(new ColorSetting("HitColor", new Color(50, 255, 100, 255)));
    public final ColorSetting dangerColor = add(new ColorSetting("DangerColor", new Color(255, 50, 50, 255)));
    public final ColorSetting waterColor = add(new ColorSetting("WaterColor", new Color(50, 150, 255, 255)));

    private record ProjectileInfo(double speed, double gravity, double drag, float pitchOffset) {}

    public Trajectories() {
        super("Trajectories", "Отображает траекторию полёта жемчугов эндера, стрел, трезубцев и зелий.", Category.RENDER);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = getHeldProjectileStack();
        if (stack == null || stack.isEmpty()) return;

        ProjectileInfo info = getProjectileInfo(stack);
        if (info == null) return;

        List<Vec3> points = new ArrayList<>();
        HitResult hit = simulateTrajectory(info, points);

        renderTrajectory(points, hit);
    }

    private ItemStack getHeldProjectileStack() {
        ItemStack main = mc.player.getMainHandItem();
        if (getProjectileInfo(main) != null) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (getProjectileInfo(off) != null) return off;
        return null;
    }

    private ProjectileInfo getProjectileInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        if (stack.is(Items.ENDER_PEARL) && pearls.getValue()) {
            return new ProjectileInfo(1.5, 0.03, 0.99, 0.0f);
        }
        if (stack.is(Items.BOW) && bows.getValue()) {
            float pull = mc.player.isUsingItem() && mc.player.getActiveItem() == stack
                    ? BowItem.getPowerForTime(mc.player.getTicksUsingItem())
                    : 1.0f;
            return new ProjectileInfo(pull * 3.0, 0.05, 0.99, 0.0f);
        }
        if (stack.is(Items.CROSSBOW) && bows.getValue()) {
            return new ProjectileInfo(3.15, 0.05, 0.99, 0.0f);
        }
        if (stack.is(Items.TRIDENT) && tridents.getValue()) {
            return new ProjectileInfo(2.5, 0.05, 0.99, 0.0f);
        }
        if ((stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) && snowballs.getValue()) {
            return new ProjectileInfo(1.5, 0.03, 0.99, 0.0f);
        }
        if ((stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) && potions.getValue()) {
            return new ProjectileInfo(0.5, 0.05, 0.99, -20.0f);
        }
        if (stack.is(Items.WIND_CHARGE) && windCharges.getValue()) {
            return new ProjectileInfo(1.5, 0.0, 1.0, 0.0f);
        }

        return null;
    }

    private HitResult simulateTrajectory(ProjectileInfo info, List<Vec3> points) {
        Vec3 eyePos = mc.player.getEyePosition();
        float pitch = mc.player.getXRot() + info.pitchOffset;
        float yaw = mc.player.getYRot();

        float cosPitch = Mth.cos(-pitch * Mth.DEG_TO_RAD);
        float sinPitch = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        float cosYaw = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float sinYaw = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);

        Vec3 dir = new Vec3(-sinYaw * cosPitch, sinPitch, cosYaw * cosPitch).normalize();
        Vec3 velocity = dir.scale(info.speed);
        Vec3 pos = eyePos;

        points.add(pos);
        HitResult finalHit = null;

        for (int i = 0; i < 200; i++) {
            Vec3 nextPos = pos.add(velocity);

            BlockHitResult bhr = mc.level.clip(new ClipContext(
                    pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
            ));

            AABB stepBox = new AABB(pos, nextPos).inflate(1.0);
            EntityHitResult ehr = ProjectileUtil.getEntityHitResult(
                    mc.player, pos, nextPos, stepBox,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity != mc.player, 0.0
            );

            if (ehr != null) {
                finalHit = ehr;
                points.add(ehr.getLocation());
                break;
            }

            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                finalHit = bhr;
                points.add(bhr.getLocation());
                break;
            }

            points.add(nextPos);
            pos = nextPos;
            velocity = new Vec3(velocity.x * info.drag, velocity.y * info.drag - info.gravity, velocity.z * info.drag);

            if (pos.y < mc.level.getMinY() - 30) {
                break;
            }
        }

        return finalHit;
    }

    private void renderTrajectory(List<Vec3> points, HitResult hit) {
        Color lineColor = color.getValue();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            Renderer3D.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, lineColor);
        }

        if (hit != null && box.getValue()) {
            renderHitBox(hit);
        }
    }

    private void renderHitBox(HitResult hit) {
        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            Color targetColor = hitColor.getValue();

            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                targetColor = dangerColor.getValue();
            } else if (state.is(Blocks.WATER)) {
                targetColor = waterColor.getValue();
            } else if (pos.getY() < mc.level.getMinY()) {
                targetColor = dangerColor.getValue();
            }

            Color fill = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 60);
            Renderer3D.box(new AABB(pos), fill, targetColor, ShapeMode.Both, false);
        } else if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            Color targetColor = dangerColor.getValue();
            Color fill = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 60);
            Renderer3D.box(target.getBoundingBox(), fill, targetColor, ShapeMode.Both, false);
        }
    }
}