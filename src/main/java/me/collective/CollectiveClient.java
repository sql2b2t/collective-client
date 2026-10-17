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

    @Override
    public void onInitializeClient() {
        System.out.println("[" + MOD_NAME + "] Initializing for Minecraft 1.21.11...");

        // Initialize ModuleManager
        ModuleManager moduleManager = ModuleManager.INSTANCE;

        // Register HUD Render Callback
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();

            // Do not render normal HUD when in ClickGUI or HudEditor
            if (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof HudEditorScreen) {
                return;
            }

            if (HUD.INSTANCE == null || !HUD.INSTANCE.isToggled()) {
                return;
            }

            float delta = renderTickCounter != null ? renderTickCounter.getTickProgress(false) : 1.0f;

            for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
                if (module.isToggled()) {
                    module.render(drawContext, delta);
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
            try {
                me.collective.config.ConfigManager.getInstance().tick();
            } catch (Throwable ignored) {
            }
        });

        // Register World 3D Render Callback for Modules
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.render.Camera camera = context.gameRenderer() != null ? context.gameRenderer().getCamera() : null;
            float tickDelta = MinecraftClient.getInstance().getRenderTickCounter() != null
                ? MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false)
                : 1.0f;
            me.collective.event.EventBus.INSTANCE.post(new me.collective.event.events.Render3DEvent(
                context.matrices(), camera, tickDelta, context.consumers()
            ));
        });

        // Initialize Baritone Overworld Elytra Process
        try {
            me.collective.baritone.OverworldElytraProcess.init();
        } catch (Throwable ignored) {
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
            ModuleManager.INSTANCE.onKeyPressed(key);
        }
    }
}
