package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.IPlayerInteractEntity;
import me.collective.util.RotationUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оптимизатор кристаллов: убирает задержку использования,
 * мгновенно взрывает кристаллы по пакету спавна и очищает призрачные сущности.
 */
public class CrystalOptimizer extends Module {
    public final BooleanSetting fastPlace = add(new BooleanSetting("FastPlace", true));
    public final BooleanSetting instantBreak = add(new BooleanSetting("InstantBreak", true));
    public final BooleanSetting fastDespawn = add(new BooleanSetting("FastDespawn", true));
    public final BooleanSetting autoPlace = add(new BooleanSetting("AutoPlace", true));
    public final BooleanSetting antiGhost = add(new BooleanSetting("AntiGhost", true));
    public final BooleanSetting breakExisting = add(new BooleanSetting("BreakExisting", false));
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    public final BooleanSetting onlyHolding = add(new BooleanSetting("OnlyHolding", false));
    public final NumberSetting reach = add(new NumberSetting("Reach", 3.0, 2.0, 4.5, 0.1));

    private final Map<Integer, Long> attackedCrystals = new ConcurrentHashMap<>();
    private final Set<Integer> despawnQueue = new HashSet<>();

    public CrystalOptimizer() {
        super("CrystalOptimizer", "Убирает задержку кристаллов, мгновенно взрывает их по пакету спавна и убирает призраков.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        attackedCrystals.clear();
        despawnQueue.clear();
    }

    @Override
    public void onDisable() {
        attackedCrystals.clear();
        despawnQueue.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof EntitySpawnS2CPacket spawnPacket)) return;
        if (spawnPacket.getEntityType() != EntityType.END_CRYSTAL) return;

        handleSpawnPacket(spawnPacket);
    }

    private void handleSpawnPacket(EntitySpawnS2CPacket spawnPacket) {
        if (!instantBreak.getValue()) return;
        if (onlyHolding.getValue() && !isCrystalHeld()) return;

        int entityId = spawnPacket.getEntityId();
        if (antiGhost.getValue() && attackedCrystals.containsKey(entityId)) return;

        Vec3d eyePos = mc.player.getEyePos();
        double dx = spawnPacket.getX() - eyePos.x;
        double dy = spawnPacket.getY() - eyePos.y;
        double dz = spawnPacket.getZ() - eyePos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxReach = reach.getValue();
        if (distSq > maxReach * maxReach) return;

        if (rotate.getValue()) {
            rotateTo(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());
        }

        mc.getNetworkHandler().sendPacket(IPlayerInteractEntity.createAttack(entityId, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        attackedCrystals.put(entityId, System.currentTimeMillis());

        if (fastDespawn.getValue()) {
            Entity existing = mc.world.getEntityById(entityId);
            if (existing != null) {
                existing.discard();
            } else {
                despawnQueue.add(entityId);
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket) || !(event.getPacket() instanceof IPlayerInteractEntity packet)) return;

        if (packet.collective$getType() == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
            Entity target = packet.collective$getEntity();
            if (target instanceof EndCrystalEntity crystal) {
                attackedCrystals.put(crystal.getId(), System.currentTimeMillis());
                if (fastDespawn.getValue()) {
                    crystal.discard();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        cleanupExpiredCrystals();
        flushDespawnQueue();
        handleFastPlace();

        if (autoPlace.getValue() && mc.options.useKey.isPressed() && isCrystalHeld()) {
            handleAutoPlace();
        }

        if (breakExisting.getValue()) {
            handleBreakExisting();
        }
    }

    private void cleanupExpiredCrystals() {
        long now = System.currentTimeMillis();
        attackedCrystals.entrySet().removeIf(entry -> now - entry.getValue() > 2000L);
    }

    private void flushDespawnQueue() {
        if (despawnQueue.isEmpty()) return;
        for (Iterator<Integer> it = despawnQueue.iterator(); it.hasNext(); ) {
            Entity entity = mc.world.getEntityById(it.next());
            if (entity != null) {
                entity.discard();
                it.remove();
            }
        }
    }

    private void handleFastPlace() {
        if (!fastPlace.getValue()) return;

        boolean crystalHeld = isCrystalHeld();
        if (onlyHolding.getValue() && !crystalHeld) return;

        if (crystalHeld || isTargetingBaseBlock()) {
            if (mc.itemUseCooldown > 0) {
                mc.itemUseCooldown = 0;
            }
        }
    }

    private void handleAutoPlace() {
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystalEntity) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            double reachDist = mc.player.getBlockInteractionRange();
            Vec3d endPos = eyePos.add(lookVec.multiply(reachDist));

            BlockHitResult bhr = mc.world.raycast(new RaycastContext(
                    eyePos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
            ));

            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = bhr.getBlockPos();
                BlockState state = mc.world.getBlockState(pos);
                if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) {
                    Hand hand = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND
                            : mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
                    if (hand != null && mc.interactionManager != null) {
                        mc.interactionManager.interactBlock(mc.player, hand, bhr);
                    }
                }
            }
        }
    }

    private void handleBreakExisting() {
        if (onlyHolding.getValue() && !isCrystalHeld()) return;

        double maxReachSq = reach.getValue() * reach.getValue();
        Vec3d eyePos = mc.player.getEyePos();
        EndCrystalEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive() || crystal.isRemoved()) continue;
            if (antiGhost.getValue() && attackedCrystals.containsKey(crystal.getId())) continue;

            double dSq = eyePos.squaredDistanceTo(crystal.getX(), crystal.getY(), crystal.getZ());
            if (dSq <= maxReachSq && dSq < bestDistSq) {
                bestDistSq = dSq;
                best = crystal;
            }
        }

        if (best != null) {
            if (rotate.getValue()) {
                rotateTo(best.getX(), best.getY(), best.getZ());
            }
            mc.getNetworkHandler().sendPacket(IPlayerInteractEntity.createAttack(best.getId(), mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            attackedCrystals.put(best.getId(), System.currentTimeMillis());

            if (fastDespawn.getValue()) {
                best.discard();
            }
        }
    }

    private void rotateTo(double x, double y, double z) {
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = x - eyePos.x;
        double diffY = y - eyePos.y;
        double diffZ = z - eyePos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        RotationUtils.getInstance().setRotationInstant(this, 90, yaw, pitch, false);
        RotationUtils.getInstance().flushServerRotation();
    }

    private boolean isCrystalHeld() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
    }

    private boolean isTargetingBaseBlock() {
        if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.world.getBlockState(bhr.getBlockPos());
            return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
        }
        return false;
    }
}
