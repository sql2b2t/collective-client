package me.collective.module;

import me.collective.category.Category;
import me.collective.module.client.*;
import me.collective.module.combat.*;
import me.collective.module.exploit.*;
import me.collective.module.hud.*;
import me.collective.module.highway.*;
import me.collective.module.movement.*;
import me.collective.module.player.*;
import me.collective.module.render.*;
import me.collective.module.misc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();
    private final List<HudModule> hudModules = new ArrayList<>();

    public ModuleManager() {
        // Client Modules
        register(new UI());
        register(new HUD());
        register(new Fonts());
        register(new Colors());
        register(new Notifications());
        register(new AntiCheat());
        register(new Discord());
        register(new Baritone());
        register(new McpModule());
        register(new AutoSaveCfg());

        // Combat Modules
        register(new GrimAnchorAura());
        register(new CrystalAura());
        register(new Criticals());
        register(new Offhand());
        register(new Surround());
        register(new KillAura());
        register(new AutoArmor());
        register(new AutoLog());
        register(new AutoGap());
        register(new AutoCity());
        register(new AutoTnt());
        register(new AutoWither());
        register(new BedAura());
        register(new AutoTrap());
        register(new WebAura());
        register(new HoleFiller());
        register(new CevBreaker());
        register(new BowAimbot());
        register(new HitBox());
        register(new Reach());
        register(new TNTAura());

        // Movement Modules
        register(new GrimVelocity());
        register(new Velocity());
        register(new GrimScaffold());
        register(new AirPlaceScaffold());
        register(new Sprint());
        register(new Speed());
        register(new Step());
        register(new SafeWalk());
        register(new Jesus());
        register(new ElytraBounce());
        register(new RocketBoost());
        register(new NoFall());
        register(new NoJumpDelay());
        register(new GrimGlide());
        register(new NoWeb());
        register(new ElytraSwap());
        register(new ElytraRecast());
        register(new Pitch40());
        register(new HoleSnap());
        register(new ControlFly());
        register(new AntiHunger());
        register(new AutoWalk());
        register(new AntiVoid());
        register(new ElytraFly());
        register(new EntityControl());
        register(new Parkour());

        // Highway Modules
        register(new HighwayBuilderHIG());
        register(new HighwayTools());
        register(new ScaffoldHIG());
        register(new LiquidFillerHIG());
        register(new HotbarManager());
        register(new OffhandManager());
        register(new AutoWalkHIG());
        register(new AutoCenter());
        register(new AxisViewer());
        register(new AfkLogout());
        register(new AxisBorer());
        register(new PosPosBorer());
        register(new PosNegBorer());
        register(new NegPosBorer());
        register(new NegNegBorer());

        // Player Modules
        register(new ChestSwap());
        register(new FastUse());
        register(new AutoEat());
        register(new AutoTool());
        register(new Replenish());
        register(new GrimAirPlace());
        register(new EchestRebreak());
        register(new SpeedMine());
        register(new AntiAFK());
        register(new AutoRespawn());
        register(new MiddleClickExtra());
        register(new Excavator());
        register(new InvFix2b2t());
        register(new AutoXp());
        register(new VillagerRoller());
        register(new LitematicaPrinter());
        register(new StashMover());
        register(new StashSorter());
        register(new AutoMend());
        register(new GhostHand());
        register(new MultiTask());
        register(new AutoCraft());
        register(new AutoDrop());
        register(new NoRotate());

        // Exploit Modules
        register(new NewChunks());
        register(new WorldEventCoords());
        register(new ShulkerDupe());
        register(new ActivatedSpawnerDetector());
        register(new WaypointCoordExploit());

        // Render Modules
        register(new Blur());
        register(new NoHurtCam());
        register(new Fullbright());
        register(new LightOverlay());
        register(new StorageESP());
        register(new Freecam());
        register(new BetterF5());
        register(new HandsShader());
        register(new TargetESP());
        register(new HoleESP());
        register(new NameTags());
        register(new ViewModel());
        register(new TotemAnimation());
        register(new ContainerIndex());
        register(new ContainerTooltips());
        register(new Tooltips());
        register(new Search());
        register(new BlockESP());
        register(new Tracers());

        // Misc Modules
        register(new AntiBot());
        register(new FakePlayer());
        register(new AutoEZ());
        register(new SkinBlinker());
        register(new AutoAuth());
        register(new Spammer());
        register(new EmotesModule());

        // HUD Modules
        register(new Watermark());
        register(new ModuleList());
        register(new Armor());
        register(new Position());
        register(new Direction());
        register(new Metrics());
        register(new Inventory());
        register(new Effects());
        register(new Totems());
        register(new PlayerModel());
        register(new TextRadar());
        register(new Welcomer());
        register(new Lag());
        register(new Graph());
        register(new Chat());
        register(new Music());
        register(new Crypto());
        register(new Bps());
        register(new ImageHUD());
    }

    public void register(Module module) {        modules.add(module);
        if (module instanceof HudModule) {
            hudModules.add((HudModule) module);
        }
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<HudModule> getHudModules() {
        return hudModules;
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (m.getClass() == clazz) {
                return (T) m;
            }
        }
        return null;
    }

    public Module getModule(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public void onKeyPressed(int key) {
        for (Module m : modules) {
            if (m.getKeybind().getValue() == key) {
                m.toggle();
            }
        }
    }

    public void onUpdate() {
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            if (m.isToggled()) {
                try {
                    m.onUpdate();
                } catch (Exception ignored) {}
            }
        }
    }
}
