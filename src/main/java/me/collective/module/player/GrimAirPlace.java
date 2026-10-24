package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.GrimUtils;
import me.collective.util.InventoryUtil;
import me.collective.util.SwapManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * GrimAirPlace: ставит блок туда, куда смотрит прицел, без реальной опоры.
 * Пакетная связка: START_DESTROY_BLOCK -> PlayerInteractBlock -> HandSwing -> START_DESTROY_BLOCK.
 * 1:1 порт из b2xy.
 */
public class GrimAirPlace extends Module {

    public final NumberSetting placeDelay = add(new NumberSetting("PlaceDelay", 1, 0, 5, 1));
    public final BooleanSetting customRange = add(new BooleanSetting("CustomRange", false));
    public final NumberSetting range = add(new NumberSetting("Range", 5.0, 0.0, 5.5, 0.1, (v) -> customRange.getValue()));

    private HitResult hitResult;
    private int delay;
    private boolean wasPressed;

    public GrimAirPlace() {
        super("GrimAirPlace", "Ставит блок туда, куда смотрит прицел (air place для Grim).", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        this.delay = 0;
        this.wasPressed = false;
    }

    @Override
    public void onDisable() {
        this.hitResult = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (this.delay < this.placeDelay.getIntValue()) this.delay++;

        double distance = this.customRange.getValue() ? this.range.getValue() : mc.player.blockInteractionRange();
        this.hitResult = mc.player.pick(distance, 0.0f, false);

        if (!(this.hitResult instanceof BlockHitResult blockHit) || !placeable()) {
            this.wasPressed = false;
            return;
        }

        boolean isPressed = mc.options.keyUse.isDown();
        if (mc.gui.screen() != null) {
            this.wasPressed = isPressed;
            return;
        }

        if (isPressed && !this.wasPressed && this.delay >= this.placeDelay.getIntValue()) {
            BlockPos targetPos = blockHit.getBlockPos();
            if (!mc.level.getBlockState(targetPos).canBeReplaced()) {
                this.wasPressed = isPressed;
                return;
            }

            if (!SwapManager.getInstance().hold(this, mc.player.getInventory().getSelectedSlot(), SwapManager.Priority.PLACE, 2)) return;

            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, 0, 0), Direction.DOWN));
            mc.getConnection().send(new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND, blockHit, GrimUtils.nextSequence()));
            mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, new BlockPos(0, 0, 0), Direction.DOWN));

            this.delay = 0;
        }

        this.wasPressed = isPressed;
    }

    private boolean placeable() {
        if (mc.player == null) return false;
        Item item = mc.player.getMainHandItem().getItem();
        return item instanceof BlockItem || item instanceof SpawnEggItem;
    }

    public HitResult getHitResult() {
        return hitResult;
    }
}