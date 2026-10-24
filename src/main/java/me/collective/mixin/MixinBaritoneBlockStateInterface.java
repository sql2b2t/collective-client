package me.collective.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Патч известной гонки в Baritone (аналог upstream cabaletta/baritone#3917):
 * фоновый поток PackerThread читает секции чанка, пока мир его обновляет.
 * При ресайзе палитры между проверкой размера и чтением массива
 * PalettedContainer бросает ArrayIndexOutOfBoundsException, упаковка чанка
 * прерывается и логи засоряются спамом (JIT заодно прячет стек «fast throw»).
 *
 * <p>Baritone от Meteor ProGuard-обфусцирован, поэтому целевой метод
 * выбирается по дескриптору. Если имя изменится в будущих снапшотах,
 * миксин просто не применится (без поломки клиента).
 */
@Mixin(value = baritone.utils.BlockStateInterface.class, remap = false)
public class MixinBaritoneBlockStateInterface {

    @Inject(
        method = "a(Lnet/minecraft/world/chunk/LevelChunk;III)Lnet/minecraft/block/BlockState;",
        at = @At("HEAD"),
        remap = false,
        require = 0,
        cancellable = true
    )
    private static void collective$safeGetFromChunk(
        LevelChunk chunk, int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {

        LevelChunkSection[] sections = chunk.getSections();
        int index = y >> 4;
        if (index < 0 || index >= sections.length) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
            return;
        }

        try {
            cir.setReturnValue(section.getBlockState(x & 15, y & 15, z & 15));
        } catch (ArrayIndexOutOfBoundsException first) {
            // Палитру пересайзнули во время чтения: одна повторная попытка,
            // дальше признаём блок воздухом — упакуй чанк и не вали поток.
            try {
                cir.setReturnValue(section.getBlockState(x & 15, y & 15, z & 15));
            } catch (ArrayIndexOutOfBoundsException second) {
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
            }
        }
    }
}