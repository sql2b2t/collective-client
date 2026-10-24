package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.RenderHeldItemEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import com.mojang.math.Axis;

public class ViewModel extends Module {
    public static ViewModel INSTANCE;

    public final BooleanSetting syncHands = add(new BooleanSetting("SyncHands", true));

    // Main hand
    public final NumberSetting posX = add(new NumberSetting("MainPosX", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting posY = add(new NumberSetting("MainPosY", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting posZ = add(new NumberSetting("MainPosZ", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting scale = add(new NumberSetting("MainScale", 1.0, 0.1, 2.0, 0.05));
    public final NumberSetting rotX = add(new NumberSetting("MainRotX", 0.0, -180.0, 180.0, 1.0));
    public final NumberSetting rotY = add(new NumberSetting("MainRotY", 0.0, -180.0, 180.0, 1.0));
    public final NumberSetting rotZ = add(new NumberSetting("MainRotZ", 0.0, -180.0, 180.0, 1.0));

    // Off hand
    public final NumberSetting offPosX = add(new NumberSetting("OffPosX", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting offPosY = add(new NumberSetting("OffPosY", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting offPosZ = add(new NumberSetting("OffPosZ", 0.0, -2.0, 2.0, 0.05));
    public final NumberSetting offScale = add(new NumberSetting("OffScale", 1.0, 0.1, 2.0, 0.05));
    public final NumberSetting offRotX = add(new NumberSetting("OffRotX", 0.0, -180.0, 180.0, 1.0));
    public final NumberSetting offRotY = add(new NumberSetting("OffRotY", 0.0, -180.0, 180.0, 1.0));
    public final NumberSetting offRotZ = add(new NumberSetting("OffRotZ", 0.0, -180.0, 180.0, 1.0));

    // Animation spin
    public final BooleanSetting spinX = add(new BooleanSetting("SpinX", false));
    public final BooleanSetting spinY = add(new BooleanSetting("SpinY", false));
    public final BooleanSetting spinZ = add(new BooleanSetting("SpinZ", false));
    public final NumberSetting spinSpeed = add(new NumberSetting("SpinSpeed", 1.0, 0.1, 5.0, 0.1));

    private float spinAngle = 0.0f;

    public ViewModel() {
        super("ViewModel", "Настраивает позицию, размер, вращение и анимацию рук от первого лица", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onUpdate() {
        if (!isToggled()) return;

        if (spinX.getValue() || spinY.getValue() || spinZ.getValue()) {
            spinAngle = (spinAngle + spinSpeed.getFloatValue() * 3.0f) % 360.0f;
        }

        if (syncHands.getValue()) {
            offPosX.setValueQuietly(posX.getValue());
            offPosY.setValueQuietly(posY.getValue());
            offPosZ.setValueQuietly(posZ.getValue());
            offScale.setValueQuietly(scale.getValue());
            offRotX.setValueQuietly(rotX.getValue());
            offRotY.setValueQuietly(rotY.getValue());
            offRotZ.setValueQuietly(rotZ.getValue());
        }
    }

    @EventHandler
    public void onRenderHeldItem(RenderHeldItemEvent event) {
        if (!isToggled() || event.getMatrices() == null) return;

        boolean isMain = event.getHand() == InteractionHand.MAIN_HAND;

        float px = isMain ? posX.getFloatValue() : (syncHands.getValue() ? -posX.getFloatValue() : -offPosX.getFloatValue());
        float py = isMain ? posY.getFloatValue() : (syncHands.getValue() ? posY.getFloatValue() : offPosY.getFloatValue());
        float pz = isMain ? posZ.getFloatValue() : (syncHands.getValue() ? posZ.getFloatValue() : offPosZ.getFloatValue());
        float sc = isMain ? scale.getFloatValue() : (syncHands.getValue() ? scale.getFloatValue() : offScale.getFloatValue());
        float rx = isMain ? rotX.getFloatValue() : (syncHands.getValue() ? rotX.getFloatValue() : offRotX.getFloatValue());
        float ry = isMain ? rotY.getFloatValue() : (syncHands.getValue() ? rotY.getFloatValue() : offRotY.getFloatValue());
        float rz = isMain ? rotZ.getFloatValue() : (syncHands.getValue() ? rotZ.getFloatValue() : offRotZ.getFloatValue());

        if (spinX.getValue()) rx += spinAngle;
        if (spinY.getValue()) ry += spinAngle;
        if (spinZ.getValue()) rz += spinAngle;

        event.getMatrices().translate(px, py, pz);
        event.getMatrices().scale(sc, sc, sc);
        if (rx != 0.0f) event.getMatrices().multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        if (ry != 0.0f) event.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
        if (rz != 0.0f) event.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
    }
}