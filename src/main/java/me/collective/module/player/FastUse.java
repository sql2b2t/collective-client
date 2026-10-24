package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;

public class FastUse extends Module {
    public final BooleanSetting expOnly = add(new BooleanSetting("ExpOnly", false));
    public final BooleanSetting crystals = add(new BooleanSetting("Crystals", true));
    public final BooleanSetting fireworks = add(new BooleanSetting("Fireworks", true));
    public final BooleanSetting blocks = add(new BooleanSetting("Blocks", true));
    public final NumberSetting cooldown = add(new NumberSetting("Cooldown", 0.0, 0.0, 4.0, 1.0));

    public FastUse() {
        super("FastUse", "Убирает задержку использования предметов, зелий, блоков и пузырьков опыта.", Category.PLAYER);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        ItemStack offhand = mc.player.getOffhandItem();

        boolean isExp = stack.getItem() instanceof ExperienceBottleItem || offhand.getItem() instanceof ExperienceBottleItem;
        boolean isCrystal = stack.is(net.minecraft.world.item.Items.END_CRYSTAL) || offhand.is(net.minecraft.world.item.Items.END_CRYSTAL);
        boolean isFirework = stack.getItem() instanceof FireworkRocketItem || offhand.getItem() instanceof FireworkRocketItem;
        boolean isBlock = stack.getItem() instanceof BlockItem || offhand.getItem() instanceof BlockItem;

        if (expOnly.getValue()) {
            if (!isExp) return;
        } else {
            if (isCrystal && !crystals.getValue()) return;
            if (isFirework && !fireworks.getValue()) return;
            if (isBlock && !blocks.getValue()) return;
        }

        if (((me.collective.mixin.MinecraftAccessor) mc).collective$getRightClickDelay() > cooldown.getValue().intValue()) {
            ((me.collective.mixin.MinecraftAccessor) mc).collective$setRightClickDelay((int) (cooldown.getValue().intValue()));
        }
    }
}