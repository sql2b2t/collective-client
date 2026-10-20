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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
    private final BlockPos.Mutable searchCursor = new BlockPos.Mutable();

    private long lastHoleTime = NEVER;
    private volatile long lastTeleportTime = NEVER;
    private boolean active;
    private Vec3d target;

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
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            this.lastTeleportTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
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

        Vec3d hole = findHole();
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

        Vec3d delta = this.target.subtract(playerPos());
        double length = delta.length();
        if (length < 1.0E-4) return;

        Vec3d motion = delta.normalize().multiply(Math.min(baseSpeed() * this.speedMult.getValue(), length));
        event.movement = new Vec3d(motion.x, event.movement.y, motion.z);
    }

    private Vec3d playerPos() {
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void stop() {
        this.active = false;
        this.target = null;
        RotationUtils.getInstance().clearRotations(this);
    }

    private boolean blocked() {
        if (mc.player.isInSneakingPose() || mc.player.isSpectator() || mc.player.isGliding()) return true;

        double limit = this.maxPitch.getValue();
        if (limit != 0.0 && mc.player.getPitch() < limit) return true;

        return System.currentTimeMillis() - this.lastTeleportTime < TELEPORT_COOLDOWN;
    }

    private double baseSpeed() {
        double speed = BASE_SPEED;

        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            speed *= 1.0 + 0.2 * (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
        }
        if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            speed /= 1.0 + 0.2 * (mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1);
        }

        return speed;
    }

    private void searchHoles() {
        this.holes.clear();

        BlockPos origin = mc.player.getBlockPos();
        int radius = this.searchRadius.getIntValue();
        BlockPos.Mutable cursor = this.searchCursor;

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
        if (isSolid(pos) || !isSolid(pos.down()) || isSolid(pos.up())) return null;
        if (mc.world.getBlockState(pos.down()).isOf(Blocks.END_PORTAL)) return null;

        boolean covered = isSolid(pos.up(2));
        Direction stretch = null;

        for (Direction side : Direction.Type.HORIZONTAL) {
            if (direction != null && side == direction.getOpposite()) continue;

            BlockPos offset = pos.offset(side);
            if (isSolid(offset)) {
                BlockState state = mc.world.getBlockState(offset);

                if (state.isOf(Blocks.RESPAWN_ANCHOR)) return null;

                float resistance = state.getBlock().getBlastResistance();
                float hardness = state.getHardness(mc.world, offset);
                if (resistance < 600.0f && resistance >= 0.0f || hardness == 0.0f) return null;
            } else {
                if (stretch != null || direction != null || getHoleData(offset, side) == null) return null;
                if (!isSolid(offset.up(2))) covered = false;

                stretch = side;
            }
        }

        Box box = stretch == null ? new Box(pos) : new Box(pos).stretch(stretch.getOffsetX(), 0.0, stretch.getOffsetZ());
        return new Hole(pos.toImmutable(), box, covered);
    }

    private boolean isSolid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir()
            && !state.isOf(Blocks.FIRE)
            && !state.isOf(Blocks.SOUL_FIRE)
            && !(state.getBlock() instanceof ButtonBlock)
            && !(state.getBlock() instanceof TorchBlock)
            && !(state.getBlock() instanceof RailBlock)
            && !state.isOf(Blocks.LIGHT);
    }

    private Vec3d findHole() {
        Vec3d pos = playerPos();
        double best = Double.MAX_VALUE;
        Vec3d result = null;

        for (Hole hole : this.holes) {
            if (hole.pos().getY() >= pos.getY() || hole.covered()) continue;

            Vec3d center = hole.box().getCenter();
            Vec3d target = center.withAxis(Direction.Axis.Y, MathHelper.clamp(pos.getY(), center.y, center.y + this.height.getValue() - 0.5));

            double distance = pos.distanceTo(target);
            if (distance > this.range.getValue()) continue;
            if (this.directional.getValue() && !movingTowards(target) && distance > 0.05) continue;

            if (!mc.world.isSpaceEmpty(hole.box().withMaxY(mc.player.getBoundingBox().maxY))) continue;

            if (blockedBetween(pos, target)) continue;
            if (blockedBetween(pos.add(0.0, 1.8, 0.0), target.add(0.0, 1.8, 0.0))) continue;

            if (distance < best) {
                best = distance;
                result = target;
            }
        }

        return result;
    }

    private boolean movingTowards(Vec3d target) {
        Vec2f input = mc.player.input.getMovementInput();
        if (input.x == 0.0f && input.y == 0.0f) return false;

        Vec3d velocity = mc.player.getVelocity();
        Vec3d pos = playerPos();
        return pos.add(velocity.x * 10000.0, 0.0, velocity.z * 10000.0).squaredDistanceTo(target)
            < pos.add(velocity.x * -10000.0, 0.0, velocity.z * -10000.0).squaredDistanceTo(target);
    }

    private boolean blockedBetween(Vec3d from, Vec3d to) {
        return mc.world.raycast(new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        )).getType() != HitResult.Type.MISS;
    }

    private float[] rotationTo(Vec3d target) {
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[]{yaw, pitch};
    }

    private boolean isInHole() {
        BlockPos pos = BlockPos.ofFloored(mc.player.getX(), Math.round(mc.player.getY()), mc.player.getZ());
        for (Hole hole : this.holes) {
            if (hole.box().intersects(new Box(pos))) return true;
        }
        return false;
    }

    private record Hole(BlockPos pos, Box box, boolean covered) {}
}
