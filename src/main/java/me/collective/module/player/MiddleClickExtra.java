package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public class MiddleClickExtra extends Module {
    public final BooleanSetting pearl = add(new BooleanSetting("Pearl", true));
    public final BooleanSetting friend = add(new BooleanSetting("Friend", true));

    private boolean wasPressed = false;

    public MiddleClickExtra() {
        super("MiddleClickExtra", "Бросает жемчуг Края или добавляет игрока в друзья по клику колёсика мыши.", Category.PLAYER);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gui.screen() != null) return;

        boolean isPressed = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (isPressed && !wasPressed) {
            if (friend.getValue() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                String name = player.getName().getString();
                if (me.collective.manager.FriendManager.getInstance().isFriend(name)) {
                    me.collective.manager.FriendManager.getInstance().removeFriend(name);
                    me.collective.command.ChatHelper.sendMessage("Игрок §c" + name + " §fудалён из друзей.");
                } else {
                    me.collective.manager.FriendManager.getInstance().addFriend(name);
                    me.collective.command.ChatHelper.sendMessage("Игрок §a" + name + " §fдобавлен в друзья.");
                }
            } else if (pearl.getValue()) {
                throwPearl();
            }
        }

        wasPressed = isPressed;
    }

    private void throwPearl() {
        FindItemResult pearlResult = InventoryUtil.findInHotbarOrOffhand(Items.ENDER_PEARL);
        if (!pearlResult.found()) return;

        InteractionHand hand = pearlResult.getHand();
        int prevSlot = mc.player.getInventory().getSelectedSlot();

        if (!pearlResult.isOffhand()) {
            InventoryUtil.swap(pearlResult.slot());
        }

        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundUseItemPacket(hand, me.collective.util.GrimUtils.nextSequence(), mc.player.getYRot(), mc.player.getXRot()));
            mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
        }

        if (!pearlResult.isOffhand()) {
            InventoryUtil.swap(prevSlot);
        }
    }
}