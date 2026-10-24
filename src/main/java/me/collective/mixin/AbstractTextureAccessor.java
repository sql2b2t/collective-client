package me.collective.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к сэмплеру текстуры. Нужен, чтобы выставить линейную фильтрацию
 * для растрового атласа кастомного шрифта: шрифт рисуется суперсэмплированным
 * (2x) и выводится уменьшенным, поэтому NEAREST при дробном масштабе
 * выбрасывает тексели и текст выглядит пиксельным.
 */
@Mixin(AbstractTexture.class)
public interface AbstractTextureAccessor {
    @Accessor("sampler")
    void collective$setSampler(GpuSampler sampler);
}