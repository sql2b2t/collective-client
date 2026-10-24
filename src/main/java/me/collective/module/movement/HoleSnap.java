package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.PlayerMoveEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.RotationUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.List;

/**
 * HoleSnap — затягивает игрока в ближайшую дыру (безопасную позицию).
 * 1:1 порт из b2xy.
 */
public class HoleSnap extends Module {
    private static final int ROTATION_PRIORITY = 45;
    private static final double BASE_SPEED = 0.2873;
    private static final long TELEPORT_COOLDOWN = 250L;
    private static final long NEVER = -1L;

    public final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    public final BooleanSetting directional = add(new BooleanSetting("Directional", true));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final NumberSetting height = add(new NumberSetting("Height", 1.0, 0.0, 2.0, 0.1));
    public final NumberSetting timeout = add(new NumberSetting("Timeout", 0.0, 0.0, 30.0, 0.5));
    public final NumberSetting range = add(new NumberSetting("Range", 2.0, 0.25, 4.0, 0.25));
    public final NumberSetting speedMult = add(new NumberSetting("SpeedMult", 1.0, 0.1, 3.0, 0.1));
    public final NumberSetting maxPitch = add(new NumberSetting("MaxPitch", 0.0, 0.0, 90.0, 5.0));
    public final NumberSetting searchRadius = add(new NumberSetting("SearchRadius", 4, 1, 12, 1));

    private final List<Hole> holes = new ArrayList<>();
    private final BlockPos.MutableBlockPos searchCursor = new BlockPos.MutableBlockPos();

    private long lastHoleTime = NEVER;
    private volatile long lastTeleportTime = NEVER;
    private boolean active;
    private Vec3 target;

    public HoleSnap() {
        super("HoleSnap", "Затягивает игрока в ближайшую дыру, мимо которой он пробежал.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        this.lastHoleTime = NEVER;
        this.lastTeleportTime = NEVER;
        this.active = false;
        this.target = null;
    }

    @Override
    public void onDisable() {
        this.active = false;
        this.target = null;
        this.holes.clear();
        RotationUtils.getInstance().clearRotations(this);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            this.lastTeleportTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }

        searchHoles();

        long now = System.currentTimeMillis();
        if (isInHole()) {
            if (this.autoDisable.getValue()) {
                toggle();
                return;
            }
            this.lastHoleTime = now;
        }

        if (blocked() || now - this.lastHoleTime < this.timeout.getValue() * 1000.0) {
            stop();
            return;
        }

        Vec3 hole = findHole();
        if (hole == null) {
            stop();
            return;
        }

        this.target = hole;
        this.active = true;

        if (this.rotate.getValue()) {
            float[] rotation = rotationTo(hole);
            RotationUtils.getInstance().setRotationSilentInstant(this, ROTATION_PRIORITY, rotation[0], rotation[1]);
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (!this.active || this.target == null || mc.player == null) return;

        double hDistSq = (this.target.x - mc.player.getX()) * (this.target.x - mc.player.getX())
                + (this.target.z - mc.player.getZ()) * (this.target.z - mc.player.getZ());
        if (hDistSq < 0.08 && mc.player.getY() > this.target.y && !mc.player.onGround()) {
            event.movement = new Vec3(0, Math.min(event.movement.y, -0.4), 0);
            return;
        }

        Vec3 delta = this.target.subtract(playerPos());
        double length = delta.length();
        if (length < 1.0E-4) return;

        Vec3 motion = delta.normalize().scale(Math.min(baseSpeed() * this.speedMult.getValue(), length));
        event.movement = new Vec3(motion.x, event.movement.y, motion.z);
    }

    private Vec3 playerPos() {
        return new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void stop() {
        this.active = false;
        this.target = null;
        RotationUtils.getInstance().clearRotations(this);
    }

    private boolean blocked() {
        if (mc.player.isSpectator() || mc.player.isFallFlying()) return true;

        double limit = this.maxPitch.getValue();
        if (limit != 0.0 && mc.player.getXRot() < limit) return true;

        return System.currentTimeMillis() - this.lastTeleportTime < TELEPORT_COOLDOWN;
    }

    private double baseSpeed() {
        double speed = BASE_SPEED;

        if (mc.player.hasEffect(MobEffects.SPEED)) {
            speed *= 1.0 + 0.2 * (mc.player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
        }
        if (mc.player.hasEffect(MobEffects.SLOWNESS)) {
            speed /= 1.0 + 0.2 * (mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1);
        }

        return speed;
    }

    private void searchHoles() {
        this.holes.clear();

        BlockPos origin = mc.player.blockPosition();
        int radius = this.searchRadius.getIntValue();
        BlockPos.MutableBlockPos cursor = this.searchCursor;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    Hole hole = getHoleData(cursor, null);
                    if (hole == null) continue;

                    boolean overlapping = false;
                    for (Hole other : this.holes) {
                        if (hole.box().intersects(other.box())) {
                            overlapping = true;
                            break;
                        }
                    }
                    if (!overlapping) this.holes.add(hole);
                }
            }
        }
    }

    private Hole getHoleData(BlockPos pos, Direction direction) {
        if (isSolid(pos) || !isSolid(pos.below()) || isSolid(pos.above())) return null;
        if (mc.level.getBlockState(pos.below()).is(Blocks.END_PORTAL)) return null;

        boolean covered = isSolid(pos.above(2));
        Direction stretch = null;

        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (direction != null && side == direction.getOpposite()) continue;

            BlockPos offset = pos.relative(side);
            if (isSolid(offset)) {
                BlockState state = mc.level.getBlockState(offset);

                if (state.is(Blocks.RESPAWN_ANCHOR)) return null;

                float resistance = state.getBlock().getExplosionResistance();
                float hardness = state.getDestroySpeed(mc.level, offset);
                if (resistance < 600.0f && resistance >= 0.0f || hardness == 0.0f) return null;
            } else {
                if (stretch != null || direction != null || getHoleData(offset, side) == null) return null;
                if (!isSolid(offset.above(2))) covered = false;

                stretch = side;
            }
        }

        AABB box = stretch == null ? new AABB(pos) : new AABB(pos).expandTowards(stretch.getStepX(), 0.0, stretch.getStepZ());
        return new Hole(pos.immutable(), box, covered);
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir()
            && !state.is(Blocks.FIRE)
            && !state.is(Blocks.SOUL_FIRE)
            && !(state.getBlock() instanceof ButtonBlock)
            && !(state.getBlock() instanceof TorchBlock)
            && !(state.getBlock() instanceof RailBlock)
            && !state.is(Blocks.LIGHT);
    }

    private Vec3 findHole() {
        Vec3 pos = playerPos();
        double best = Double.MAX_VALUE;
        Vec3 result = null;

        for (Hole hole : this.holes) {
            if (hole.pos().getY() >= pos.y || hole.covered()) continue;

            Vec3 center = hole.box().getCenter();
            Vec3 target = center.with(Direction.Axis.Y, Mth.clamp(pos.y, center.y, center.y + this.height.getValue() - 0.5));

            double distance = pos.distanceTo(target);
            if (distance > this.range.getValue()) continue;
            if (this.directional.getValue() && !movingTowards(target) && distance > 0.05) continue;

            if (!mc.level.noCollision(hole.box().setMaxY(mc.player.getBoundingBox().maxY))) continue;

            if (blockedBetween(pos, target)) continue;
            if (blockedBetween(pos.add(0.0, 1.8, 0.0), target.add(0.0, 1.8, 0.0))) continue;

            if (distance < best) {
                best = distance;
                result = target;
            }
        }

        return result;
    }

    private boolean movingTowards(Vec3 target) {
        Vec2 input = mc.player.input.getMoveVector();
        if (input.x == 0.0f && input.y == 0.0f) return false;

        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 pos = playerPos();
        return pos.add(velocity.x * 10000.0, 0.0, velocity.z * 10000.0).distanceToSqr(target)
            < pos.add(velocity.x * -10000.0, 0.0, velocity.z * -10000.0).distanceToSqr(target);
    }

    private boolean blockedBetween(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        )).getType() != HitResult.Type.MISS;
    }

    private float[] rotationTo(Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[]{yaw, pitch};
    }

    private boolean isInHole() {
        BlockPos pos = BlockPos.containing(mc.player.getX(), Math.round(mc.player.getY()), mc.player.getZ());
        for (Hole hole : this.holes) {
            if (hole.box().intersects(new AABB(pos))) return true;
        }
        return false;
    }

    private record Hole(BlockPos pos, AABB box, boolean covered) {}
}