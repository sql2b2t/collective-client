package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * AutoTnt: автоматически поджигает размещённый динамит с необязательной задержкой в тиках.
 * 1:1 порт из b2xy.
 */
public class AutoTnt extends Module {

    public final BooleanSetting useTickDelay = add(new BooleanSetting("UseTickDelay", false));
    public final NumberSetting tickDelay = add(new NumberSetting("TickDelay", 5, 1, 40, 1, (v) -> useTickDelay.getValue()));
    public final NumberSetting reach = add(new NumberSetting("Reach", 5.5, 1.0, 10.0, 0.5, (v) -> useTickDelay.getValue()));

    private final Set<BlockPos> candidatePositions = new HashSet<>();
    private final Queue<IgnitionTask> ignitionQueue = new LinkedList<>();
    private BlockPos ignitePos;

    private boolean igniting = false;
    private int originalSlot = -1;
    private int flintSlot = -1;

    private record IgnitionTask(BlockPos pos, int ticksRemaining) {}

    public AutoTnt() {
        super("AutoTnt", "Автоматически поджигает размещённый динамит с необязательной задержкой в тиках.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        ignitePos = null;
        candidatePositions.clear();
        ignitionQueue.clear();
        igniting = false;
        originalSlot = -1;
        flintSlot = -1;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null || igniting) return;

        if (event.packet instanceof ServerboundUseItemOnPacket packet) {
            BlockHitResult hit = packet.hitResult();
            BlockPos clicked = hit.getBlockPos();
            BlockPos offset = clicked.relative(hit.getDirection());

            boolean clickedIsTnt = mc.level.getBlockState(clicked).is(Blocks.TNT);
            boolean offsetIsTnt = mc.level.getBlockState(offset).is(Blocks.TNT);

            if (clickedIsTnt) candidatePositions.add(clicked);
            if (offsetIsTnt) candidatePositions.add(offset);
        }

        if (!candidatePositions.isEmpty() && ignitePos == null && !igniting) {
            Iterator<BlockPos> it = candidatePositions.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (mc.level.getBlockState(pos).getBlock() == Blocks.TNT) {
                    if (ignitionQueue.stream().anyMatch(task -> task.pos().equals(pos))) {
                        it.remove();
                        continue;
                    }

                    it.remove();
                    if (useTickDelay.getValue()) {
                        ignitionQueue.add(new IgnitionTask(pos, tickDelay.getIntValue()));
                    } else {
                        ignitePos = pos;
                        candidatePositions.clear();
                        break;
                    }
                } else {
                    it.remove();
                }
            }
        }

        if (useTickDelay.getValue()) return;

        if (ignitePos != null && !igniting && flintSlot == -1) {
            if (originalSlot == -1) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
            }

            FindItemResult flint = InventoryUtil.findInHotbarOrOffhand(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
            if (!flint.found()) {
                sendClientMessage("§cЗажигалка или огненный заряд не найдены!");
                ignitePos = null;
                originalSlot = -1;
                flintSlot = -1;
                return;
            }

            if (flint.isOffhand()) {
                igniting = true;
                Vec3 hitVec = Vec3.atCenterOf(ignitePos);
                BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, ignitePos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, hit);
                me.collective.util.Utils.swingHand(InteractionHand.OFF_HAND);
                ignitePos = null;
                flintSlot = -1;
                originalSlot = -1;
                igniting = false;
                return;
            }

            flintSlot = flint.slot();
            mc.player.getInventory().setSelectedSlot(flintSlot);
            me.collective.util.InventoryUtil.syncSelectedSlot();
            return;
        }

        if (ignitePos != null && !igniting && mc.player.getInventory().getSelectedSlot() == flintSlot) {
            igniting = true;
            Vec3 hitVec = Vec3.atCenterOf(ignitePos);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, ignitePos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);

            mc.player.getInventory().setSelectedSlot(originalSlot);
            me.collective.util.InventoryUtil.syncSelectedSlot();
            ignitePos = null;
            flintSlot = -1;
            originalSlot = -1;
            igniting = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!useTickDelay.getValue()) return;
        if (igniting) return;
        if (ignitionQueue.isEmpty()) return;

        List<IgnitionTask> readyList = new ArrayList<>();
        Queue<IgnitionTask> updated = new LinkedList<>();

        while (!ignitionQueue.isEmpty()) {
            IgnitionTask task = ignitionQueue.poll();
            int newTicks = task.ticksRemaining() - 1;
            if (newTicks <= 0) {
                readyList.add(task);
            } else {
                updated.add(new IgnitionTask(task.pos(), newTicks));
            }
        }

        ignitionQueue.addAll(updated);

        FindItemResult flint = InventoryUtil.findInHotbarOrOffhand(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
        if (!flint.found()) {
            sendClientMessage("§cЗажигалка или огненный заряд не найдены!");
            readyList.clear();
            ignitionQueue.clear();
            return;
        }
        for (IgnitionTask task : readyList) igniteNow(task.pos(), flint);
    }

    private void igniteNow(BlockPos pos, FindItemResult flint) {
        if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > reach.getValue()) return;
        igniting = true;
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);

        if (flint.isOffhand()) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, hit);
            me.collective.util.Utils.swingHand(InteractionHand.OFF_HAND);
        } else {
            int prevSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(flint.slot());
            me.collective.util.InventoryUtil.syncSelectedSlot();

            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);

            mc.player.getInventory().setSelectedSlot(prevSlot);
            me.collective.util.InventoryUtil.syncSelectedSlot();
        }
        igniting = false;
    }

    private void sendClientMessage(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[§cAutoTnt§7] §f" + msg));
        }
    }
}