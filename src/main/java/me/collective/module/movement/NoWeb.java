package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;

public class NoWeb extends Module {
    public static NoWeb INSTANCE;

    public enum Mode {
        Фиксированный,
        Кастомный,
        Игнор
    }

    private static final double HORIZONTAL_SPEED = 0.64045;
    private static final double VERTICAL_SPEED = 1.27927;

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Фиксированный));
    public final BooleanSetting grim = add(new BooleanSetting("Grim", false, (v) -> this.mode.getValue() == Mode.Игнор));
    public final BooleanSetting jumpBypass = add(new BooleanSetting("JumpBypass", false, (v) -> this.mode.getValue() == Mode.Игнор));

    private static final BlockPos.Mutable MUTABLE_POS = new BlockPos.Mutable();
    private boolean sprintBlocked;

    public NoWeb() {
        super("NoWeb", "Убирает или настраивает замедление при перемещении в паутине.", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static boolean isIgnoreActive() {
        NoWeb module = INSTANCE;
        return module != null && module.isToggled() && module.mode.getValue() == Mode.Игнор;
    }

    public static void onEntityCollideCobweb(BlockPos pos) {
        NoWeb module = INSTANCE;
        if (module == null || !module.isToggled() || module.mode.getValue() != Mode.Игнор) return;
        if (!module.grim.getValue()) return;
        if (module.mc.player == null || !(module.mc.world instanceof ClientWorld world)) return;

        PendingUpdateManager pending = world.getPendingUpdateManager();
        try (PendingUpdateManager updates = pending.incrementSequence()) {
            module.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                Direction.UP,
                updates.getSequence()
            ));
        }
    }

    @Override
    public void onDisable() {
        this.sprintBlocked = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) return;

        if (this.mode.getValue() == Mode.Игнор) {
            if (this.jumpBypass.getValue()) this.handleJumpBypass();
            return;
        }

        if (!isInWeb()) return;

        double targetY = 0.0;
        if (this.mc.options.jumpKey.isPressed()) {
            targetY = VERTICAL_SPEED;
        } else if (this.mc.options.sneakKey.isPressed()) {
            targetY = -VERTICAL_SPEED;
        }

        this.mc.player.setVelocity(0.0, targetY, 0.0);
        setSpeed(HORIZONTAL_SPEED);
    }

    private void handleJumpBypass() {
        if (isNearCobweb(2.0)) {
            if (this.mc.options.jumpKey.isPressed()) {
                if (this.mc.player.isSprinting()) {
                    this.mc.player.setSprinting(false);
                    this.sprintBlocked = true;
                }
            } else if (this.mc.player.isOnGround() && this.sprintBlocked) {
                this.mc.player.setSprinting(true);
                this.sprintBlocked = false;
            }
        } else if (this.sprintBlocked && this.mc.player.isOnGround()) {
            this.sprintBlocked = false;
        }
    }

    private void setSpeed(double speed) {
        if (this.mc.player == null) return;

        Vec2f input = this.mc.player.input.getMovementInput();
        float forward = input.y;
        float strafe = input.x;
        double yaw = this.mc.player.getYaw();

        if (forward != 0) {
            if (strafe > 0) {
                yaw += (forward > 0 ? -45 : 45);
            } else if (strafe < 0) {
                yaw += (forward > 0 ? 45 : -45);
            }

            strafe = 0;
            if (forward > 0) {
                forward = 1;
            } else if (forward < 0) {
                forward = -1;
            }
        }

        double radians = Math.toRadians(yaw + 90);
        double velocityY = this.mc.player.getVelocity().y;

        this.mc.player.setVelocity(
            forward * speed * Math.cos(radians) + strafe * speed * Math.sin(radians),
            velocityY,
            forward * speed * Math.sin(radians) - strafe * speed * Math.cos(radians)
        );
    }

    private boolean isNearCobweb(double maxDistance) {
        if (this.mc.player == null || this.mc.world == null) return false;

        double playerX = this.mc.player.getX();
        double playerY = this.mc.player.getY();
        double playerZ = this.mc.player.getZ();
        int blockX = this.mc.player.getBlockX();
        int blockY = this.mc.player.getBlockY();
        int blockZ = this.mc.player.getBlockZ();
        double maxDistanceSq = maxDistance * maxDistance;

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    int cx = blockX + x;
                    int cy = blockY + y;
                    int cz = blockZ + z;
                    MUTABLE_POS.set(cx, cy, cz);
                    if (this.mc.world.getBlockState(MUTABLE_POS).getBlock() instanceof CobwebBlock) {
                        double dx = playerX - (cx + 0.5);
                        double dy = playerY - (cy + 0.5);
                        double dz = playerZ - (cz + 0.5);
                        if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isInWeb() {
        if (this.mc.player == null || this.mc.world == null) return false;

        Box playerBox = this.mc.player.getBoundingBox();
        BlockPos playerPosition = this.mc.player.getBlockPos();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = playerPosition.add(x, y, z);
                    if (playerBox.intersects(new Box(pos))
                        && this.mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return true;
                }
            }
        }

        return false;
    }
}
