package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.MouseScrollEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Кинематографический зум камеры с плавной интерполяцией и регулировкой колёсиком мыши.
 */
public class Zoom extends Module {
    public static Zoom INSTANCE;

    public enum ZoomMode {
        HOLD, TOGGLE
    }

    public final EnumSetting<ZoomMode> mode = add(new EnumSetting<>("Mode", ZoomMode.HOLD));
    public final NumberSetting factor = add(new NumberSetting("Factor", 3.0, 1.0, 10.0, 0.5));
    public final BooleanSetting smooth = add(new BooleanSetting("Smooth", true));
    public final NumberSetting smoothSpeed = add(new NumberSetting("Speed", 15.0, 1.0, 30.0, 1.0));
    public final BooleanSetting scroll = add(new BooleanSetting("Scroll", true));
    public final BooleanSetting cinematic = add(new BooleanSetting("Cinematic", true));

    private float currentFovModifier = 1.0f;
    private boolean wasZooming = false;
    private boolean savedSmoothCamera = false;
    private long lastTime = System.currentTimeMillis();

    public Zoom() {
        super("Zoom", "Плавный зум камеры с регулировкой колесиком мыши и кинематографическим режимом.", Category.RENDER, GLFW.GLFW_KEY_C);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        resetCinematicCamera();
        this.currentFovModifier = 1.0f;
        this.wasZooming = false;
    }

    public boolean isZooming() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return false;
        }

        if (mode.getValue() == ZoomMode.TOGGLE) {
            return isToggled();
        }

        int key = getKeybind().getValue();
        if (key != GLFW.GLFW_KEY_UNKNOWN && mc.getWindow() != null) {
            return InputUtil.isKeyPressed(mc.getWindow(), key);
        }

        return isToggled();
    }

    public float modifyFov(float vanillaFov, float tickDelta) {
        boolean zooming = isZooming();
        updateCinematicCamera(zooming);

        float target = zooming ? factor.getValue().floatValue() : 1.0f;

        if (!smooth.getValue()) {
            currentFovModifier = target;
        } else {
            interpolateFov(target);
        }

        if (Math.abs(currentFovModifier - 1.0f) < 0.005f && !zooming) {
            currentFovModifier = 1.0f;
            return vanillaFov;
        }

        return vanillaFov / currentFovModifier;
    }

    private void updateCinematicCamera(boolean zooming) {
        if (cinematic.getValue() && mc.options != null) {
            if (zooming && !wasZooming) {
                savedSmoothCamera = mc.options.smoothCameraEnabled;
                mc.options.smoothCameraEnabled = true;
            } else if (!zooming && wasZooming) {
                mc.options.smoothCameraEnabled = savedSmoothCamera;
            }
        }
        wasZooming = zooming;
    }

    private void resetCinematicCamera() {
        if (wasZooming && cinematic.getValue() && mc.options != null) {
            mc.options.smoothCameraEnabled = savedSmoothCamera;
        }
    }

    private void interpolateFov(float target) {
        long now = System.currentTimeMillis();
        float dt = MathHelper.clamp((now - lastTime) / 1000.0f, 0.001f, 0.1f);
        lastTime = now;

        float speed = smoothSpeed.getValue().floatValue();
        currentFovModifier += (target - currentFovModifier) * Math.min(1.0f, speed * dt);
    }

    @EventHandler
    public void onMouseScroll(MouseScrollEvent event) {
        if (!isZooming() || !scroll.getValue()) return;

        double delta = event.getAmount();
        if (delta > 0) {
            factor.setValue(Math.min(10.0, factor.getValue() + 0.5));
        } else if (delta < 0) {
            factor.setValue(Math.max(1.0, factor.getValue() - 0.5));
        }
        event.cancel();
    }
}