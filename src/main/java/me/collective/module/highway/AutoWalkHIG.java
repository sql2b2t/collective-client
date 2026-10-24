package me.collective.module.highway;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.InventoryUtil;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/**
 * Порт AutoWalkHIG: идёт вперёд, стопается при лагах/падении/конце кирок и гапов.
 */
public class AutoWalkHIG extends Module {
    public final BooleanSetting pauseOnLag = add(new BooleanSetting("PauseOnLag", true));
    public final NumberSetting resumeTps = add(new NumberSetting("ResumeTps", 16, 1, 19, 1));
    public final BooleanSetting keepY = add(new BooleanSetting("KeepY", true));
    public final BooleanSetting pickToggle = add(new BooleanSetting("PickToggle", true));
    public final BooleanSetting gapToggle = add(new BooleanSetting("GapToggle", true));

    private double originY;
    private boolean sentLagMessage;

    /** Оценка TPS сервера по WorldTimeUpdate (как Meteor TickRate). */
    private long lastTickMs = 0L;
    private float tickRate = 20f;

    public AutoWalkHIG() {
        super("AutoWalkHIG", "Автоматически идёт вперёд (оптимизировано для копания хайвеев).", Category.HIGHWAY);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        originY = mc.player.getY();
        sentLagMessage = false;
        lastTickMs = 0L;
        tickRate = 20f;
    }

    @Override
    public void onDisable() {
        unpress();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof WorldTimeUpdateS2CPacket)) return;
        long now = System.currentTimeMillis();
        if (lastTickMs > 0) {
            float delta = Math.max(1, now - lastTickMs);
            tickRate = Math.max(0f, Math.min(20f, tickRate * 0.9f + (20000f / delta) * 0.1f));
        }
        lastTickMs = now;
    }

    private float timeSinceLastTick() {
        if (lastTickMs == 0) return 0f;
        return (System.currentTimeMillis() - lastTickMs) / 1000f;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (pauseOnLag.getValue()) {
            if (timeSinceLastTick() > 1.4f) {
                if (!sentLagMessage) {
                    error("Server isn't responding, pausing.");
                }
                sentLagMessage = true;
                unpress();
                return;
            }

            if (sentLagMessage) {
                if (tickRate > resumeTps.getIntValue()) {
                    sentLagMessage = false;
                } else return;
            }
        }

        mc.options.forwardKey.setPressed(true);

        if (keepY.getValue()) {
            // -0.125 чтобы можно было идти по соулсенду и похожим блокам
            if (mc.player.getY() < originY - 0.125) {
                info("Fell below original height, disabling.");
                setToggled(false);
                return;
            }
        }

        if (pickToggle.getValue()) {
            boolean hasPick = InventoryUtil.find(stack ->
                stack.isOf(Items.DIAMOND_PICKAXE) || stack.isOf(Items.NETHERITE_PICKAXE)).found();
            if (!hasPick) {
                error("No pickaxe found, disabling.");
                setToggled(false);
                return;
            }
        }

        if (gapToggle.getValue()) {
            boolean hasGap = InventoryUtil.find(stack ->
                stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE)).found();
            if (!hasGap) {
                error("No gap found, disabling.");
                setToggled(false);
            }
        }
    }

    private void unpress() {
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
    }
}