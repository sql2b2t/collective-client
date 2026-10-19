package me.collective.mixin;

import me.collective.module.combat.HitBox;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Часть порта HitBoxes из ThunderHack.
 *
 * <p>Расширяет bounding box живых сущностей (кроме себя), пока модуль включён.
 */
@Mixin(Entity.class)
public abstract class MixinEntityHitbox {

    @Shadow
    public abstract EntityPose getPose();

    @Shadow
    public abstract EntityDimensions getDimensions(EntityPose pose);

    @Shadow
    public abstract Vec3d getEntityPos();

    /** Модуль кэшируется один раз: getBoundingBox вызывается тысячи раз за кадр. */
    private HitBox collective$module;

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void expandHitbox(CallbackInfoReturnable<Box> cir) {
        HitBox mod = this.collective$module;
        if (mod == null) {
            this.collective$module = mod = HitBox.INSTANCE;
            if (mod == null) return; // модули ещё не зарегистрированы — попробуем в следующий вызов
        }

        if (!mod.isToggled()) return;

        Entity self = (Entity) (Object) this;
        if (self == MinecraftClient.getInstance().player) return;
        if (!(self instanceof LivingEntity)) return;

        float xz = mod.xzExpand.compute();
        float y = mod.yExpand.compute();
        if (xz <= 0 && y <= 0) return;

        Box base = getDimensions(getPose()).getBoxAt(getEntityPos());
        cir.setReturnValue(base.expand(xz, y, xz));
    }
}
