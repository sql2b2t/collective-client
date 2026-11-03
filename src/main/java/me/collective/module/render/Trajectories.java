package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
    public final BooleanSetting box = add(new BooleanSetting("Box", true));

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
        if (mc.player == null || mc.world == null) return;

        ItemStack stack = getHeldProjectileStack();
        if (stack == null || stack.isEmpty()) return;

        ProjectileInfo info = getProjectileInfo(stack);
        if (info == null) return;

        List<Vec3d> points = new ArrayList<>();
        HitResult hit = simulateTrajectory(info, points);

        renderTrajectory(points, hit);
    }

    private ItemStack getHeldProjectileStack() {
        ItemStack main = mc.player.getMainHandStack();
        if (getProjectileInfo(main) != null) return main;
        ItemStack off = mc.player.getOffHandStack();
        if (getProjectileInfo(off) != null) return off;
        return null;
    }

    private ProjectileInfo getProjectileInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        if (stack.isOf(Items.ENDER_PEARL) && pearls.getValue()) {
            return new ProjectileInfo(1.5, 0.03, 0.99, 0.0f);
        }
        if (stack.isOf(Items.BOW) && bows.getValue()) {
            float pull = mc.player.isUsingItem() && mc.player.getActiveItem() == stack
                    ? BowItem.getPullProgress(mc.player.getItemUseTime())
                    : 1.0f;
            return new ProjectileInfo(pull * 3.0, 0.05, 0.99, 0.0f);
        }
        if (stack.isOf(Items.CROSSBOW) && bows.getValue()) {
            return new ProjectileInfo(3.15, 0.05, 0.99, 0.0f);
        }
        if (stack.isOf(Items.TRIDENT) && tridents.getValue()) {
            return new ProjectileInfo(2.5, 0.05, 0.99, 0.0f);
        }
        if ((stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) && snowballs.getValue()) {
            return new ProjectileInfo(1.5, 0.03, 0.99, 0.0f);
        }
        if ((stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) && potions.getValue()) {
            return new ProjectileInfo(0.5, 0.05, 0.99, -20.0f);
        }
        if (stack.isOf(Items.WIND_CHARGE) && windCharges.getValue()) {
            return new ProjectileInfo(1.5, 0.0, 1.0, 0.0f);
        }

        return null;
    }

    private HitResult simulateTrajectory(ProjectileInfo info, List<Vec3d> points) {
        Vec3d eyePos = mc.player.getEyePos();
        float pitch = mc.player.getPitch() + info.pitchOffset;
        float yaw = mc.player.getYaw();

        float cosPitch = MathHelper.cos(-pitch * MathHelper.RADIANS_PER_DEGREE);
        float sinPitch = MathHelper.sin(-pitch * MathHelper.RADIANS_PER_DEGREE);
        float cosYaw = MathHelper.cos(-yaw * MathHelper.RADIANS_PER_DEGREE - MathHelper.PI);
        float sinYaw = MathHelper.sin(-yaw * MathHelper.RADIANS_PER_DEGREE - MathHelper.PI);

        Vec3d dir = new Vec3d(-sinYaw * cosPitch, sinPitch, cosYaw * cosPitch).normalize();
        Vec3d velocity = dir.multiply(info.speed);
        Vec3d pos = eyePos;

        points.add(pos);
        HitResult finalHit = null;

        for (int i = 0; i < 200; i++) {
            Vec3d nextPos = pos.add(velocity);

            BlockHitResult bhr = mc.world.raycast(new RaycastContext(
                    pos, nextPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
            ));

            Box stepBox = new Box(pos, nextPos).expand(1.0);
            EntityHitResult ehr = ProjectileUtil.raycast(
                    mc.player, pos, nextPos, stepBox,
                    entity -> !entity.isSpectator() && entity.isAlive() && entity != mc.player, 0.0
            );

            if (ehr != null) {
                finalHit = ehr;
                points.add(ehr.getPos());
                break;
            }

            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                finalHit = bhr;
                points.add(bhr.getPos());
                break;
            }

            points.add(nextPos);
            pos = nextPos;
            velocity = new Vec3d(velocity.x * info.drag, velocity.y * info.drag - info.gravity, velocity.z * info.drag);

            if (pos.y < mc.world.getBottomY() - 30) {
                break;
            }
        }

        return finalHit;
    }

    private void renderTrajectory(List<Vec3d> points, HitResult hit) {
        Color lineColor = color.getValue();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get(i + 1);
            Renderer3D.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, lineColor);
        }

        if (hit != null && box.getValue()) {
            renderHitBox(hit);
        }
    }

    private void renderHitBox(HitResult hit) {
        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            Color targetColor = hitColor.getValue();

            if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
                targetColor = dangerColor.getValue();
            } else if (state.isOf(Blocks.WATER)) {
                targetColor = waterColor.getValue();
            } else if (pos.getY() < mc.world.getBottomY()) {
                targetColor = dangerColor.getValue();
            }

            Color fill = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 60);
            Renderer3D.box(new Box(pos), fill, targetColor, ShapeMode.Both, false);
        } else if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            Color targetColor = dangerColor.getValue();
            Color fill = new Color(targetColor.getRed(), targetColor.getGreen(), targetColor.getBlue(), 60);
            Renderer3D.box(target.getBoundingBox(), fill, targetColor, ShapeMode.Both, false);
        }
    }
}
