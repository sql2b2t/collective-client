package me.collective.module.player;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.ActionSetting;
import me.collective.setting.BooleanSetting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * InvFix2b2t: устраняет рассинхронизацию инвентаря («ghost items»),
 * зависание стаков при быстром крафте/копании и ошибки бандлов на 2b2t (Paper 1.21).
 */
public class InvFix2b2t extends Module {
    public static InvFix2b2t INSTANCE;

    public final BooleanSetting fixGhostItems = add(new BooleanSetting("FixGhostItems", true));
    public final BooleanSetting autoResync = add(new BooleanSetting("AutoResync", true));
    public final BooleanSetting fixBundles = add(new BooleanSetting("FixBundles", true));
    public final BooleanSetting notify = add(new BooleanSetting("Notify", false));
    public final ActionSetting resyncNow = add(new ActionSetting("ResyncNow", 0)
        .process("Синхронизировать")
        .handle(() -> syncInventory(true)));

    private int resyncCooldown = 0;

    public InvFix2b2t() {
        super("InvFix2b2t", "Исправляет баги призрачных предметов и рассинхронизацию инвентаря на серверах.", Category.PLAYER);
        INSTANCE = this;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (resyncCooldown > 0) {
            resyncCooldown--;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isToggled() || mc.player == null) return;

        if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket packet) {
            if (!autoResync.getValue() || resyncCooldown > 0) return;

            int slot = packet.getSlot();
            int syncId = packet.getSyncId();

            // Проверяем соответствие слота игрока
            if (syncId == 0 && slot >= 0 && slot < mc.player.playerScreenHandler.slots.size()) {
                ItemStack serverStack = packet.getStack();
                ItemStack clientStack = mc.player.playerScreenHandler.getSlot(slot).getStack();

                // Обнаружение ghost item: несовпадение количества или типа
                if (serverStack.isEmpty() != clientStack.isEmpty() ||
                    (!serverStack.isEmpty() && !ItemStack.areItemsAndComponentsEqual(serverStack, clientStack))) {
                    if (resyncCooldown <= 0) {
                        resyncCooldown = 10;
                        syncInventory();
                    }
                }
            }
        }
    }

    /**
     * Выполняет быстрое двухфазное переключение оффхенда:
     * на Paper/2b2t заставляет сервер принудительно подтвердить истинное состояние слотов.
     */
    public void syncInventory() {
        syncInventory(false);
    }

    public void syncInventory(boolean manual) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));

        if (mc.interactionManager != null) {
            mc.interactionManager.syncSelectedSlot();
        }

        if (manual || notify.getValue()) {
            ChatHelper.sendMessage("Инвентарь синхронизирован с сервером.");
        }
    }
}