package me.collective.module.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.utils.BetterBlockPos;
import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.KeyInputEvent;
import me.collective.event.events.MouseClickEvent;
import me.collective.event.events.Render3DEvent;
import me.collective.module.Module;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.KeybindSetting;
import me.collective.setting.SettingColorSetting;
import me.collective.util.SettingColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class Excavator extends Module {
    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    // Keybindings: right click by default (-101)
    public final KeybindSetting selectionBind = add(new KeybindSetting("SelectionBind", -101));

    // Logging & options
    public final BooleanSetting logSelection = add(new BooleanSetting("LogSelection", true));
    public final BooleanSetting keepActive = add(new BooleanSetting("KeepActive", false));

    // Rendering settings (exact Meteor style)
    public final EnumSetting<ShapeMode> shapeMode = add(new EnumSetting<>("ShapeMode", ShapeMode.Both));
    public final SettingColorSetting sideColor = add(new SettingColorSetting("SideColor", new SettingColor(255, 255, 255, 50)));
    public final SettingColorSetting lineColor = add(new SettingColorSetting("LineColor", new SettingColor(255, 255, 255, 255)));

    private enum Status {
        SEL_START,
        SEL_END,
        WORKING
    }

    private Status status = Status.SEL_START;
    private BetterBlockPos start, end;

    public Excavator() {
        super("Excavator", "Excavate a selection area using Baritone.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        status = Status.SEL_START;
        start = null;
        end = null;
    }

    @Override
    public void onDisable() {
        try {
            if (baritone != null) {
                var lastSel = baritone.getSelectionManager().getLastSelection();
                if (lastSel != null) {
                    baritone.getSelectionManager().removeSelection(lastSel);
                }
                if (baritone.getBuilderProcess().isActive()) {
                    baritone.getCommandManager().execute("stop");
                }
            }
        } catch (Throwable ignored) {}
        status = Status.SEL_START;
        start = null;
        end = null;
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (!isToggled() || mc.currentScreen != null) return;
        int bind = selectionBind.getValue();
        // Mouse buttons are encoded as -100 - button
        if (bind <= -100 && bind == (-100 - event.button)) {
            selectCorners();
        }
    }

    @EventHandler
    private void onKeyInput(KeyInputEvent event) {
        if (!isToggled() || mc.currentScreen != null) return;
        int bind = selectionBind.getValue();
        if (bind > 0 && bind == event.key) {
            selectCorners();
        }
    }

    private void selectCorners() {
        if (mc.crosshairTarget == null || !(mc.crosshairTarget instanceof BlockHitResult result)) return;

        if (status == Status.SEL_START) {
            start = BetterBlockPos.from(result.getBlockPos());
            status = Status.SEL_END;
            if (logSelection.getValue()) {
                info("Start corner set: (%d, %d, %d)", start.getX(), start.getY(), start.getZ());
            }
        } else if (status == Status.SEL_END) {
            end = BetterBlockPos.from(result.getBlockPos());
            status = Status.WORKING;
            if (logSelection.getValue()) {
                info("End corner set: (%d, %d, %d)", end.getX(), end.getY(), end.getZ());
            }
            try {
                baritone.getSelectionManager().addSelection(start, end);
                baritone.getBuilderProcess().clearArea(start, end);
            } catch (Throwable t) {
                error("Baritone clearArea failed: " + t.getMessage());
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled()) return;

        if (status == Status.SEL_START || status == Status.SEL_END) {
            if (mc.crosshairTarget instanceof BlockHitResult result) {
                event.renderer.box(result.getBlockPos(), sideColor.getValue(), lineColor.getValue(), shapeMode.getValue(), 0);
            }
            if (status == Status.SEL_END && start != null) {
                event.renderer.box(start, sideColor.getValue(), lineColor.getValue(), shapeMode.getValue(), 0);
            }
        } else if (status == Status.WORKING) {
            if (start != null && end != null) {
                double minX = Math.min(start.getX(), end.getX());
                double minY = Math.min(start.getY(), end.getY());
                double minZ = Math.min(start.getZ(), end.getZ());
                double maxX = Math.max(start.getX(), end.getX()) + 1;
                double maxY = Math.max(start.getY(), end.getY()) + 1;
                double maxZ = Math.max(start.getZ(), end.getZ()) + 1;

                // Skip the fill while the camera is inside the area: while Baritone
                // digs, the player usually stands inside it, and the translucent faces
                // right in front of the camera wash out the whole screen in white.
                Vec3d eye = mc.player != null ? mc.player.getEyePos() : null;
                boolean inside = eye != null
                        && eye.x >= minX && eye.x <= maxX
                        && eye.y >= minY && eye.y <= maxY
                        && eye.z >= minZ && eye.z <= maxZ;
                ShapeMode mode = inside ? ShapeMode.Lines : shapeMode.getValue();
                event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ,
                        inside ? null : sideColor.getValue(),
                        lineColor.getValue(), mode, 0);
            }

            try {
                if (!baritone.getBuilderProcess().isActive()) {
                    if (keepActive.getValue()) {
                        var lastSel = baritone.getSelectionManager().getLastSelection();
                        if (lastSel != null) {
                            baritone.getSelectionManager().removeSelection(lastSel);
                        }
                        status = Status.SEL_START;
                    } else {
                        toggle();
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

@Override
    public String getInfoString() {
        if (this.status == Status.WORKING) {
            return "dig";
        } else if (this.status == Status.SEL_END) {
            return "corner 1/2";
        } else {
            return null;
        }
    }
}
