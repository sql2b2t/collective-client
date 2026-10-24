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

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/LocalPlayer;getYaw()F"))
    private float collective$modifyGetYaw(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating() || rotUtils.isWireFresh()) {
            return rotUtils.getSentYaw();
        }
        rotUtils.setServerYaw(original);
        return original;
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/LocalPlayer;getPitch()F"))
    private float collective$modifyGetPitch(float original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        return !rotUtils.isRotating() && !rotUtils.isWireFresh() ? original : rotUtils.getSentPitch();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/input/Input;playerInput:Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput collective$modifyDeclaredInput(PlayerInput original) {
        if (me.collective.module.render.Freecam.INSTANCE != null && me.collective.module.render.Freecam.INSTANCE.isToggled()) {
            // Пока баритон ведёт игрока (PlayerMovementInput), его принудительные
            // вводы не глушим — иначе фрикам ломает путь. Глушим только живую
            // клавиатуру, когда баритон не пиктов.
            if (!BaritoneBridge.isPathing()) {
                return new PlayerInput(false, false, false, false, false, false, false);
            }
        }
        me.collective.module.highway.HighwayBuilderHIG hb = me.collective.module.highway.HighwayBuilderHIG.INSTANCE;
        PlayerInput in = original;
        if (hb != null && hb.isToggled() && hb.isDrivingInput()) {
            in = new PlayerInput(
                original.forward() || hb.inFwd, original.backward() || hb.inBack,
                original.left() || hb.inLeft, original.right() || hb.inRight,
                original.jump() || hb.inJump, original.sneak() || hb.inSneak, original.sprint());
        }
        me.collective.module.movement.AirPlaceScaffold scaffold = me.collective.module.movement.AirPlaceScaffold.INSTANCE;
        if (scaffold != null && scaffold.isSafeWalking() && scaffold.isAtEdge()) {
            in = new PlayerInput(in.forward(), in.backward(), in.left(), in.right(),
                in.jump(), true, in.sprint());
        }
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating()) {
            return rotUtils.rotateDeclaredInput(in);
        }
        return in;
    }

    @Inject(method = "shouldStopSprinting", at = @At("HEAD"), cancellable = true)
    private void collective$onShouldStopSprinting(CallbackInfoReturnable<Boolean> cir) {
        if (RotationUtils.getInstance().isSprintBlocked()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyExpressionValue(method = "tickMovementInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;getMovementInput()Lnet/minecraft/util/math/Vec2f;"))
    private net.minecraft.util.math.Vec2f collective$modifyMovementInput(net.minecraft.util.math.Vec2f original) {
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (rotUtils.isRotating() && !rotUtils.isSyncMovement()) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            if (player.input != null && player.input.playerInput != null) {
                PlayerInput in = player.input.playerInput;
                float s = (in.left() ? 1f : 0f) - (in.right() ? 1f : 0f);
                float f = (in.forward() ? 1f : 0f) - (in.backward() ? 1f : 0f);
                if (s != 0f || f != 0f) {
                    return new net.minecraft.util.math.Vec2f(s, f).normalize();
                }
                return net.minecraft.util.math.Vec2f.ZERO;
            }
        }
        return original;
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 collective$onMove(Vec3 movement, net.minecraft.entity.MovementType type) {
        if (me.collective.module.render.Freecam.INSTANCE != null && me.collective.module.render.Freecam.INSTANCE.isToggled()) {
            return Vec3.ZERO;
        }
        me.collective.event.events.PlayerMoveEvent event = new me.collective.event.events.PlayerMoveEvent(type, movement);
        me.collective.event.EventBus.INSTANCE.post(event);
        return event.movement;
    }
}