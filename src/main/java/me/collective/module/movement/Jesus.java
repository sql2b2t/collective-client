package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;

/**
 * Ходьба по поверхности жидкостей.
 *
 * <p>Подъём применяется только когда ноги находятся на уровне поверхности
 * жидкости (над головой — воздух/не жидкость): в толще воды/лавы модуль
 * не поднимает, чтобы нельзя было «выпрыгнуть» из глубины.
 * Горизонтальная скорость сохраняется, поэтому поверх жидкости
 * можно идти / бегать, а клавиша sneak по-прежнему топит игрока.
 */
public class Jesus extends Module {
    public final BooleanSetting water = add(new BooleanSetting("Water", true));
    public final BooleanSetting lava = add(new BooleanSetting("Lava", true));
    public final NumberSetting lift = add(new NumberSetting("Lift", 0.11, 0.05, 0.30, 0.01));

    public Jesus() {
        super("Jesus", "Позволяет ходить и прыгать по поверхности воды и лавы.", Category.MOVEMENT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.options.keyShift.isDown()) return;

        boolean touchingWater = water.getValue() && mc.player.isInWater();
        boolean touchingLava = lava.getValue() && mc.player.isInLava();
        if (!touchingWater && !touchingLava) return;

        BlockPos feet = mc.player.blockPosition();
        BlockPos above = feet.above();
        FluidState aboveFluid = mc.level.getFluidState(above);
        boolean surfaceOfWater = touchingWater && !aboveFluid.is(FluidTags.WATER);
        boolean surfaceOfLava = touchingLava && !aboveFluid.is(FluidTags.LAVA);

        if (!surfaceOfWater && !surfaceOfLava) return;
        if (aboveFluid.is(FluidTags.WATER) || aboveFluid.is(FluidTags.LAVA)) return;

        // Гасим вертикальное падение и даём постоянный подъём —
        // так игрок «стоит» на поверхности, а не тонет.
        double vy = Math.max(mc.player.getDeltaMovement().y, lift.getFloatValue());
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, vy, mc.player.getDeltaMovement().z);
        // Сбрасываем дистанцию падения: сервер уже увидел посадку на жидкость.
        mc.player.fallDistance = 0;
    }
}