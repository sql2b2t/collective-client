package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.MouseClickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Порт GhostHand из Meteor Client.
 *
 * <p>Открывает контейнеры сквозь стены: клик находит первый контейнер
 * по взгляду и шлёт интеракт напрямую.
 */
public class GhostHand extends Module {
    public final NumberSetting range = add(new NumberSetting("Радиус", 6.0, 2.0, 10.0, 0.5));
    public final BooleanSetting shulkers = add(new BooleanSetting("Шалкеры", true));

    public GhostHand() {
        super("GhostHand", "Открывает контейнеры сквозь стены.", Category.PLAYER);
    }

    @EventHandler
    public void onClick(MouseClickEvent event) {
        if (event.button != 1 || mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);

        for (double d = 1.0; d <= range.getFloatValue(); d += 0.25) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!isContainer(state)) continue;
            // Ванилла сама достала — дубль не нужен
            if (mc.hitResult instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) return;

            Vec3 hitVec = eye.add(look.scale(d));
            Direction side = Direction.getApproximateNearest(look.x, look.y, look.z).getOpposite();
            BlockHitResult hit = new BlockHitResult(hitVec, side, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
            return;
        }
    }

    private boolean isContainer(BlockState state) {
        return state.getBlock() instanceof ChestBlock
            || state.getBlock() instanceof EnderChestBlock
            || state.getBlock() instanceof BarrelBlock
            || state.getBlock() instanceof HopperBlock
            || state.getBlock() instanceof FurnaceBlock
            || state.is(net.minecraft.world.level.block.Blocks.DISPENSER)
            || state.is(net.minecraft.world.level.block.Blocks.DROPPER)
            || state.is(net.minecraft.world.level.block.Blocks.CRAFTER)
            || (shulkers.getValue() && state.getBlock() instanceof ShulkerBoxBlock);
    }
}