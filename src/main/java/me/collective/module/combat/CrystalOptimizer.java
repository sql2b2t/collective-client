package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.RotationUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

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
        if (mc.player == null || mc.level == null) return;
        if (!(event.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket spawnPacket)) return;
        if (spawnPacket.getType() != net.minecraft.world.entity.EntityTypes.END_CRYSTAL) return;

        handleSpawnPacket(spawnPacket);
    }

    private void handleSpawnPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket spawnPacket) {
        if (!instantBreak.getValue()) return;
        if (onlyHolding.getValue() && !isCrystalHeld()) return;

        int entityId = spawnPacket.getId();
        if (antiGhost.getValue() && attackedCrystals.containsKey(entityId)) return;

        Vec3 eyePos = mc.player.getEyePosition();
        double dx = spawnPacket.getX() - eyePos.x;
        double dy = spawnPacket.getY() - eyePos.y;
        double dz = spawnPacket.getZ() - eyePos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxReach = reach.getValue();
        if (distSq > maxReach * maxReach) return;

        if (rotate.getValue()) {
            rotateTo(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());
        }

        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundAttackPacket(entityId));
        mc.getConnection().send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
        attackedCrystals.put(entityId, System.currentTimeMillis());

        if (fastDespawn.getValue()) {
            Entity existing = mc.level.getEntity(entityId);
            if (existing != null) {
                existing.discard();
            } else {
                despawnQueue.add(entityId);
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;
        if (event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundAttackPacket attackPacket) {
            Entity target = mc.level.getEntity(attackPacket.entityId());
            if (target instanceof EndCrystal crystal) {
                attackedCrystals.put(crystal.getId(), System.currentTimeMillis());
                if (fastDespawn.getValue()) {
                    crystal.discard();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        cleanupExpiredCrystals();
        flushDespawnQueue();
        handleFastPlace();

        if (autoPlace.getValue() && mc.options.keyUse.isDown() && isCrystalHeld()) {
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
            Entity entity = mc.level.getEntity(it.next());
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
            if (((me.collective.mixin.MinecraftAccessor) mc).collective$getRightClickDelay() > 0) {
                ((me.collective.mixin.MinecraftAccessor) mc).collective$setRightClickDelay(0);
            }
        }
    }

    private void handleAutoPlace() {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystal) {
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 lookVec = mc.player.getViewVector(1.0f);
            double reachDist = mc.player.blockInteractionRange();
            Vec3 endPos = eyePos.add(lookVec.scale(reachDist));

            BlockHitResult bhr = mc.level.clip(new ClipContext(
                    eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player
            ));

            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = bhr.getBlockPos();
                BlockState state = mc.level.getBlockState(pos);
                if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
                    InteractionHand hand = mc.player.getMainHandItem().is(Items.END_CRYSTAL) ? InteractionHand.MAIN_HAND
                            : mc.player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : null;
                    if (hand != null && mc.gameMode != null) {
                        mc.gameMode.useItemOn(mc.player, hand, bhr);
                    }
                }
            }
        }
    }

    private void handleBreakExisting() {
        if (onlyHolding.getValue() && !isCrystalHeld()) return;

        double maxReachSq = reach.getValue() * reach.getValue();
        Vec3 eyePos = mc.player.getEyePosition();
        EndCrystal best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive() || crystal.isRemoved()) continue;
            if (antiGhost.getValue() && attackedCrystals.containsKey(crystal.getId())) continue;

            double dSq = eyePos.distanceToSqr(crystal.getX(), crystal.getY(), crystal.getZ());
            if (dSq <= maxReachSq && dSq < bestDistSq) {
                bestDistSq = dSq;
                best = crystal;
            }
        }

        if (best != null) {
            if (rotate.getValue()) {
                rotateTo(best.getX(), best.getY(), best.getZ());
            }
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundAttackPacket(best.getId()));
            mc.getConnection().send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            attackedCrystals.put(best.getId(), System.currentTimeMillis());

            if (fastDespawn.getValue()) {
                best.discard();
            }
        }
    }

    private void rotateTo(double x, double y, double z) {
        Vec3 eyePos = mc.player.getEyePosition();
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
        return mc.player.getMainHandItem().is(Items.END_CRYSTAL)
                || mc.player.getOffhandItem().is(Items.END_CRYSTAL);
    }

    private boolean isTargetingBaseBlock() {
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
        }
        return false;
    }
}