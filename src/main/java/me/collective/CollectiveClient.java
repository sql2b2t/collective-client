package me.collective;

import me.collective.gui.ClickGuiScreen;
import me.collective.gui.hud.HudEditorScreen;
import me.collective.module.ModuleManager;
import me.collective.module.client.HUD;
import me.collective.module.hud.HudModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

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
        System.out.println("[" + MOD_NAME + "] Initializing for Minecraft 1.21.11...");

        // Initialize ModuleManager
        ModuleManager moduleManager = ModuleManager.INSTANCE;

        // Register HUD Render Callback
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();

            // Do not render normal HUD when in ClickGUI or HudEditor
            if (mc.currentScreen instanceof ClickGuiScreen
                    || mc.currentScreen instanceof HudEditorScreen) {
                return;
            }

            float delta = renderTickCounter != null ? renderTickCounter.getTickProgress(false) : 1.0f;

            if (HUD.INSTANCE != null && HUD.INSTANCE.isToggled()) {
                // Live drag поверх чата: обновляем позицию перетаскиваемого
                // HUD-модуля по текущему курсору и рисуем его рамку-подсветку.
                if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
                    double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth()
                        / mc.getWindow().getWidth();
                    double my = mc.mouse.getY() * mc.getWindow().getScaledHeight()
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
            if (client.player != null && client.world != null) {
                // Кэш расчёта урона от взрывов живёт ровно один тик: ключ содержит
                // блок и id сущности, но НЕ позицию — она меняется каждый тик.
                // Чистим здесь, до всех модулей, иначе кто-то прочитает значения,
                // посчитанные по старым координатам.
                me.collective.util.ExplosionDamageUtil.clearCache();
                me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.TickEvent.Pre());
            }
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
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

        // Register World 3D Render Callback for Modules
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.render.Camera camera = context.gameRenderer() != null ? context.gameRenderer().getCamera() : null;
            float tickDelta = MinecraftClient.getInstance().getRenderTickCounter() != null
                ? MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false)
                : 1.0f;

            // Один gizmo-скоуп на весь кадр: все Renderer3D.box/line рисуют в
            // общую сборку вместо своего try(startDrawingGizmos) на каждый бокс.
            me.collective.render.Renderer3D.beginFrameScope();
            try {
                me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.Render3DEvent(
                    context.matrices(), camera, tickDelta, context.consumers()
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT && me.collective.module.client.UI.INSTANCE != null) {
                me.collective.module.client.UI.INSTANCE.getKeybind().setValue(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                me.collective.module.client.UI.INSTANCE.toggle();
                return;
            }
            ModuleManager.INSTANCE.onKeyPressed(key);
        }
    }
}
