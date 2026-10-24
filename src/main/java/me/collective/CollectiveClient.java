package me.collective;

import me.collective.gui.ClickGuiScreen;
import me.collective.gui.hud.HudEditorScreen;
import me.collective.module.ModuleManager;
import me.collective.module.client.HUD;
import me.collective.module.hud.HudModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

public class CollectiveClient implements ClientModInitializer {
    public static final String MOD_ID = "collective";
    public static final String MOD_NAME = "Collective Client";
    public static final String MOD_VERSION = "1.1";

    /**
     * Вызывается при смене игрового мира (вход на сервер, респавн, смена измерения).
     * Модули используют для сброса таймеров и выдачи только что применённых состояний.
     */
    public static void onWorldChange() {
        for (me.collective.module.Module module : ModuleManager.INSTANCE.getModules()) {
            try {
                module.onWorldChange();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[" + MOD_NAME + "] Initializing for Minecraft 26.3...");

        // Initialize ModuleManager
        ModuleManager moduleManager = ModuleManager.INSTANCE;

        // Register HUD Render Callback
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (drawContext, renderTickCounter) -> {
            Minecraft mc = Minecraft.getInstance();

            // Do not render normal HUD when in ClickGUI or HudEditor
            if (mc.gui.screen() instanceof ClickGuiScreen
                    || mc.gui.screen() instanceof HudEditorScreen) {
                return;
            }

            float delta = renderTickCounter != null ? renderTickCounter.getGameTimeDeltaPartialTick(false) : 1.0f;

            if (HUD.INSTANCE != null && HUD.INSTANCE.isToggled()) {
                // Live drag поверх чата: обновляем позицию перетаскиваемого
                // HUD-модуля по текущему курсору и рисуем его рамку-подсветку.
                if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
                    double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                        / mc.getWindow().getWidth();
                    double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                        / mc.getWindow().getHeight();
                    me.collective.gui.hud.ChatHudDrag.update(mx, my);

                    me.collective.module.hud.HudModule dragged =
                        me.collective.gui.hud.ChatHudDrag.draggingModule();
                    if (dragged != null) {
                        me.collective.render.RenderUtil.drawOutline(drawContext,
                            dragged.getX() - 2, dragged.getY() - 2,
                            Math.max(20, dragged.getWidth()) + 4,
                            Math.max(10, dragged.getHeight()) + 4,
                            1, new java.awt.Color(120, 180, 255, 255).getRGB());
                    }
                }

                for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
                    if (module.isToggled()) {
                        module.render(drawContext, delta);
                    }
                }
            }

            for (me.collective.module.Module module : ModuleManager.INSTANCE.getModules()) {
                if (module.isToggled() && !(module instanceof HudModule)) {
                    module.onRender2D(drawContext, delta);
                }
            }
        });

        // Register Client Tick Callbacks for Modules and EventBus
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> {
            me.collective.util.TickPacer.onTick();
            if (client.player != null && client.level != null) {
                // Кэш расчёта урона от взрывов живёт ровно один тик: ключ содержит
                // блок и id сущности, но НЕ позицию — она меняется каждый тик.
                // Чистим здесь, до всех модулей, иначе кто-то прочитает значения,
                // посчитанные по старым координатам.
                me.collective.util.ExplosionDamageUtil.clearCache();
                me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.TickEvent.Pre());
            }
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.TickEvent.Post());
                ModuleManager.INSTANCE.onUpdate();
            }
            me.collective.module.misc.AutoReconnect autoReconnect = ModuleManager.INSTANCE.getModule(me.collective.module.misc.AutoReconnect.class);
            if (autoReconnect != null) {
                autoReconnect.onClientTick();
            }
            if (me.collective.module.client.UI.INSTANCE != null) {
                me.collective.module.client.UI.INSTANCE.onClientTick();
            }
            try {
                me.collective.config.ConfigManager.getInstance().tick();
            } catch (Throwable ignored) {
            }
        });

        // Сброс клиентских состояний при выходе: углы поворота, серверный слот
        // и удержанные свапы не должны переезжать в следующую сессию.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> {
                me.collective.util.RotationUtils.getInstance().onDisconnect();
                me.collective.util.InventoryManager.getInstance().onDisconnect();
                me.collective.util.SwapManager.getInstance().onDisconnect();
                me.collective.util.ExplosionDamageUtil.clearCache();
            });

        // Register Level 3D Render Callback for Modules
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer != null ? Minecraft.getInstance().gameRenderer.mainCamera() : null;
            float tickDelta = Minecraft.getInstance().getDeltaTracker() != null
                ? Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)
                : 1.0f;

            // Один gizmo-скоуп на весь кадр: все Renderer3D.box/line рисуют в
            // общую сборку вместо своего try(startDrawingGizmos) на каждый бокс.
            me.collective.render.Renderer3D.beginFrameScope();
            try {
                me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.Render3DEvent(
                    context.poseStack(), camera, tickDelta
                ));
            } finally {
                me.collective.render.Renderer3D.endFrameScope();
            }
        });

        // Initialize Baritone Overworld Elytra Process
        if (me.collective.util.BaritoneHelper.isAvailable()) {
            try {
                me.collective.baritone.OverworldElytraProcess.init();
            } catch (Throwable ignored) {
            }
        }

        // Initialize Addon & Scripting Systems
        try {
            me.collective.addon.AddonManager.getInstance().init();
            me.collective.scripting.ScriptManager.getInstance().init();
        } catch (Throwable t) {
            System.err.println("[Collective] Error initializing Addons/Scripts: " + t.getMessage());
            t.printStackTrace();
        }

        // Load saved config (modules, settings, HUD, friends, prefix)
        try {
            if (me.collective.config.ConfigManager.getInstance().loadDefault()) {
                System.out.println("[Collective] Config loaded: default.json");
            }
        } catch (Throwable t) {
            System.err.println("[Collective] Error loading config: " + t.getMessage());
        }

        System.out.println("[" + MOD_NAME + "] Initialized successfully!");
    }

    public static void onKeyPressed(int key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT && me.collective.module.client.UI.INSTANCE != null) {
                me.collective.module.client.UI.INSTANCE.getKeybind().setValue(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                me.collective.module.client.UI.INSTANCE.toggle();
                return;
            }
            ModuleManager.INSTANCE.onKeyPressed(key);
        }
    }
}