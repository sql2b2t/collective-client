package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.module.movement.AirPlaceScaffold;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Порт OffhandManager: держит тотем/геп в левой руке, авто-ест гепы.
 * Без проверки AutoTotem.isLocked (такого API в клиенте нет).
 */
public class OffhandManager extends Module {
    public final BooleanSetting hotbar = add(new BooleanSetting("Hotbar", true));
    public final BooleanSetting warningMsg = add(new BooleanSetting("Notify", false));
    public final NumberSetting hungerThreshold = add(new NumberSetting("Hunger", 16, 1, 19, 1));
    public final BooleanSetting autoFireRes = add(new BooleanSetting("FireResistance", true));
    public final NumberSetting healthThreshold = add(new NumberSetting("MinHealth", 6, 0, 35, 1));
    public final BooleanSetting fallDamage = add(new BooleanSetting("FallDamage", true));

    public enum Item {
        EGap(Items.ENCHANTED_GOLDEN_APPLE),
        Totem(Items.TOTEM_OF_UNDYING);

        final net.minecraft.world.item.Item item;

        Item(net.minecraft.world.item.Item item) {
            this.item = item;
        }
    }

    private Item currentItem;
    private boolean eating;
    private boolean justStarted;
    private boolean moved;
    private boolean sentMsg;
    private boolean swapped;

    public OffhandManager() {
        super("OffhandManager", "Управляет предметами во второй руке для непрерывного строительства дорог.", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        eating = false;
        justStarted = true;
        moved = false;
        sentMsg = false;
        swapped = true;
        currentItem = Item.Totem;
    }

    @Override
    public void onDisable() {
        if (eating) stopEating();
    }

    /**
     * Обслуживание офхенда переведено из Render3DEvent в тик: раньше каждый
     * кадр (60-300 раз/с) делался линейный поиск модуля и при недостающем
     * офхенде — полный скан инвентаря с отправкой кликов. На тике — тоже
     * быстро, но в 3-15 раз реже и без фризов отрисовки.
     */
    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || !isToggled()) return;

        AirPlaceScaffold scaffold = ModuleManager.INSTANCE.getModule(AirPlaceScaffold.class);
        boolean scaffolding = scaffold != null && scaffold.isBusy();
        if (scaffolding) return;

        // Switch back if swap key has been used
        if (mc.options.keySwapOffhand.isDown() && !swapped
            && mc.player.getOffhandItem().is(currentItem.item)) swapped = true;
        else if (mc.options.keySwapOffhand.isDown() && swapped
            && mc.player.getMainHandItem().is(currentItem.item)) swapped = false;
        else swapped = false;

        // Checking offhand item
        if (!mc.player.getOffhandItem().is(currentItem.item) && !swapped) {
            FindItemResult item = InventoryUtil.find(stack -> stack.is(currentItem.item),
                hotbar.getValue() ? 0 : 9, 35);

            if (!item.found() && currentItem == Item.EGap) {
                item = InventoryUtil.find(stack -> stack.is(Items.GOLDEN_APPLE),
                    hotbar.getValue() ? 0 : 9, 35);
            }

            if (!item.found()) {
                if (!sentMsg) {
                    if (warningMsg.getValue()) warning("Chosen item not found.");
                    sentMsg = true;
                    moved = false;
                }
            } else if (!item.isOffhand()) {
                InventoryUtil.moveToOffhand(item.slot());
                sentMsg = false;
                moved = true;
            }
        }

        // ── Авто-гепы ─────────────────────────────────────────────────
        if (eating) {
            if (shouldEat(scaffolding)) {
                if (mc.player.getOffhandItem().get(DataComponents.FOOD) == null) {
                    stopEating();
                } else {
                    startEating();
                }
            } else {
                stopEating();
            }
        } else if (shouldEat(scaffolding)
                && mc.player.getOffhandItem().get(DataComponents.FOOD) != null) {
            startEating();
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.player.getHealth() <= healthThreshold.getValue()
            || (fallDamage.getValue() && !mc.player.isInWater() && mc.player.fallDistance > 3)) {
            currentItem = Item.Totem;
        } else if (autoFireRes.getValue() && !justStarted
            && !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            currentItem = Item.EGap;
        } else if (mc.player.getFoodData().getFoodLevel() < hungerThreshold.getIntValue() + 1) {
            currentItem = Item.EGap;
        } else {
            currentItem = Item.Totem;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundUseItemOnPacket) stopEating();
    }

    private boolean shouldEat(boolean scaffolding) {
        return (mc.player.getFoodData().getFoodLevel() <= hungerThreshold.getIntValue()
            || (autoFireRes.getValue() && !mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)))
            && !scaffolding;
    }

    private void startEating() {
        if (mc.player == null || mc.gameMode == null) return;

        eating = true;
        justStarted = false;

        if (mc.player.isUsingItem()) return;

        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
    }

    private void stopEating() {
        if (mc.player == null) return;

        mc.options.keyUse.setDown(false);
        mc.player.stopUsingItem();
        if (mc.getConnection() != null) {
            BlockPos pos = mc.player.blockPosition();
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, pos, Direction.DOWN));
        }
        eating = false;
    }

    private void dropCursor() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.containerMenu.getCarried().isEmpty()) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
            -999, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
    }

    public boolean isEating() {
        return isToggled() && eating;
    }
}