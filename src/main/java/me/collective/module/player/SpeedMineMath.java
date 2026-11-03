package me.collective.module.player;

import me.collective.util.SwapManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/**
 * Математика копания для {@link SpeedMine}: ванильно-точный расчёт дельты
 * прогресса и скорости с учётом серверного слота (SwapManager), эффекта
 * спешки/усталости, воды и отсутствия земли. Инстансные зеркала Grim
 * (grimAttrEffLevel и др.) остаются в модуле — компаньон только читает их.
 *
 * <p>Выделено из тела {@link SpeedMine} по паттерну компаньонов: модуль
 * держит тонкие делегаты, логика живёт здесь.
 */
final class SpeedMineMath {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private SpeedMineMath() {
    }

    static float calcBlockBreakingDelta(SpeedMine b, BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return 0.0F;
        }

        if (b.swapConfig.get() == SpeedMine.Swap.OFF) {
            return state.calcBlockBreakingDelta(mc.player, mc.world, pos);
        }

        float f = state.getHardness(mc.world, pos);
        if (f == -1.0F) {
            return 0.0F;
        }

        int i = canHarvest(b, state) ? 30 : 100;
        return getBlockBreakingSpeed(b, state) / f / i;
    }

    static float serverHeldBreakingDelta(SpeedMine b, BlockState state, BlockPos pos) {
        float hardness = state.getHardness(mc.world, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = mc.player.getInventory().getStack(SwapManager.getInstance().getServerSlot());
        int i = canStackHarvest(stack, state) ? 30 : 100;
        return stackBreakingSpeed(stack, state, effLevel(stack)) / hardness / i;
    }

    static float grimSampledBreakingDelta(SpeedMine b, BlockState state, BlockPos pos) {
        float hardness = state.getHardness(mc.world, pos);
        if (hardness == -1.0F) {
            return 0.0F;
        }

        ItemStack stack = mc.player.getInventory().getStack(SwapManager.getInstance().getServerSlot());
        int i = canStackHarvest(stack, state) ? 30 : 100;
        return stackBreakingSpeed(stack, state, b.grimAttrEffLevel) / hardness / i;
    }

    static int effLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        ItemEnchantmentsComponent enchants = stack.getEnchantments();
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            if (entry.matchesKey(Enchantments.EFFICIENCY)) {
                return enchants.getLevel(entry);
            }
        }

        return 0;
    }

    static float getBlockBreakingSpeed(SpeedMine b, BlockState block) {
        int tool = getBestTool(b, block);
        return stackBreakingSpeed(mc.player.getInventory().getStack(tool), block);
    }

    static float stackBreakingSpeed(ItemStack stack, BlockState block) {
        return stackBreakingSpeed(stack, block, effLevel(stack));
    }

    /** 1-в-1 с ванильным расчётом скорости копания: спешка, усталость, вода, воздух. */
    static float stackBreakingSpeed(ItemStack stack, BlockState block, int efficiency) {
        float f = stack.getMiningSpeedMultiplier(block);
        if (f > 1.0F && efficiency > 0 && !stack.isEmpty()) {
            f += efficiency * efficiency + 1;
        }

        if (StatusEffectUtil.hasHaste(mc.player)) {
            f *= 1.0F + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2F;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float g = switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        f *= (float) mc.player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (mc.player.isSubmergedIn(FluidTags.WATER)) {
            f *= (float) mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        }

        if (!mc.player.isOnGround()) {
            f /= 5.0F;
        }

        return f;
    }

    static boolean canHarvest(SpeedMine b, BlockState state) {
        if (state.isToolRequired()) {
            int tool = getBestTool(b, state);
            return mc.player.getInventory().getStack(tool).isSuitableFor(state);
        } else {
            return true;
        }
    }

    static int getBestTool(SpeedMine b, BlockState state) {
        int bestSlot = bestToolSlot(b, state);
        return bestSlot == -1 ? SwapManager.getInstance().referenceSlot() : bestSlot;
    }

    static int bestToolSlot(SpeedMine b, BlockState state) {
        ItemStack held = mc.player.getInventory().getStack(SwapManager.getInstance().referenceSlot());
        float bestSpeed = held.getMiningSpeedMultiplier(state);
        boolean bestHarvest = canStackHarvest(held, state);
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            boolean harvest = canStackHarvest(stack, state);
            if (harvest && !bestHarvest || harvest == bestHarvest && speed > bestSpeed) {
                bestHarvest = harvest;
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    static boolean canStackHarvest(ItemStack stack, BlockState state) {
        return !state.isToolRequired() || stack.isSuitableFor(state);
    }
}
