package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class MiddleClickExtra extends Module {
    public final BooleanSetting pearl = add(new BooleanSetting("Pearl", true));

    private boolean wasPressed = false;

    public MiddleClickExtra() {
        super("MiddleClickExtra", "Middle-click to instantly throw an Ender Pearl from your hotbar.", Category.PLAYER);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.currentScreen != null) return;

        boolean isPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (isPressed && !wasPressed) {
            if (pearl.getValue()) {
                throwPearl();
            }
        }

        wasPressed = isPressed;
    }

    private void throwPearl() {
        FindItemResult pearlResult = InventoryUtil.findInHotbarOrOffhand(Items.ENDER_PEARL);
        if (!pearlResult.found()) return;

        Hand hand = pearlResult.getHand();
        int prevSlot = mc.player.getInventory().getSelectedSlot();

        if (!pearlResult.isOffhand()) {
            InventoryUtil.swap(pearlResult.slot());
        }

        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, 0, mc.player.getYaw(), mc.player.getPitch()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }

        if (!pearlResult.isOffhand()) {
            InventoryUtil.swap(prevSlot);
        }
    }
}
