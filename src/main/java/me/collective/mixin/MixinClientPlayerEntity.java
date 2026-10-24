package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.collective.util.BaritoneBridge;
import me.collective.util.RotationUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity {

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float collective$modifyGetYaw(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating() || rotUtils.isWireFresh()) {
            return rotUtils.getSentYaw();
        }
        rotUtils.setServerYaw(original);
        return original;
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float collective$modifyGetPitch(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentPitch();
    }

    @ModifyExpressionValue(method = "sendChanges", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/ClientInput;keyPresses:Lnet/minecraft/world/entity/player/Input;"))
    private Input collective$modifyDeclaredInput(Input original) {
        if (me.collective.module.render.Freecam.INSTANCE != null && me.collective.module.render.Freecam.INSTANCE.isToggled()) {
            // Пока баритон ведёт игрока (PlayerMovementInput), его принудительные
            // вводы не глушим — иначе фрикам ломает путь. Глушим только живую
            // клавиатуру, когда баритон не пиктов.
            if (!BaritoneBridge.isPathing()) {
                return new Input(false, false, false, false, false, false, false);
            }
        }
        me.collective.module.highway.HighwayBuilderHIG hb = me.collective.module.highway.HighwayBuilderHIG.INSTANCE;
        Input in = original;
        if (hb != null && hb.isToggled() && hb.isDrivingInput()) {
            in = new Input(
                original.forward() || hb.inFwd, original.backward() || hb.inBack,
                original.left() || hb.inLeft, original.right() || hb.inRight,
                original.jump() || hb.inJump, original.shift() || hb.inSneak, original.sprint());
        }
        me.collective.module.movement.AirPlaceScaffold scaffold = me.collective.module.movement.AirPlaceScaffold.INSTANCE;
        if (scaffold != null && scaffold.isSafeWalking() && scaffold.isAtEdge()) {
            in = new Input(in.forward(), in.backward(), in.left(), in.right(),
                in.jump(), true, in.sprint());
        }
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating()) {
            return rotUtils.rotateDeclaredInput(in);
        }
        return in;
    }

    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void collective$onShouldStopSprinting(CallbackInfoReturnable<Boolean> cir) {
        if (RotationUtils.getInstance().isSprintBlocked()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyExpressionValue(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;getMoveVector()Lnet/minecraft/world/phys/Vec2;"))
    private net.minecraft.world.phys.Vec2 collective$modifyMovementInput(net.minecraft.world.phys.Vec2 original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating() && !rotUtils.isSyncMovement()) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            if (player.input != null && player.input.keyPresses != null) {
                Input in = player.input.keyPresses;
                float s = (in.left() ? 1f : 0f) - (in.right() ? 1f : 0f);
                float f = (in.forward() ? 1f : 0f) - (in.backward() ? 1f : 0f);
                if (s != 0f || f != 0f) {
                    return new net.minecraft.world.phys.Vec2(s, f).normalized();
                }
                return net.minecraft.world.phys.Vec2.ZERO;
            }
        }
        return original;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 collective$onMove(Vec3 movement, net.minecraft.world.entity.MoverType type) {
        if (me.collective.module.render.Freecam.INSTANCE != null && me.collective.module.render.Freecam.INSTANCE.isToggled()) {
            return Vec3.ZERO;
        }
        me.collective.event.events.PlayerMoveEvent event = new me.collective.event.events.PlayerMoveEvent(type, movement);
        me.collective.event.EventBus.INSTANCE.post(event);
        return event.movement;
    }
}