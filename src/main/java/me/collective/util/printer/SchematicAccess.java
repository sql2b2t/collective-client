package me.collective.util.printer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

/**
 * Доступ к данным Litematica через reflection (порт из BepHax NEW-SRC).
 *
 * Litematica НЕ подключена как зависимость сборки (maven maruohon содержит только
 * 1.16.1), поэтому здесь всё идёт через reflection с graceful-деградацией:
 *
 *  — имена классов/методов самой litematica (fi.dy.masa.*) НЕ ремапятся маппингами
 *    Minecraft — их можно искать по имени через Class.forName;
 *  — а вот типы Minecraft в сигнатурах litematica на рантайме — intermediary
 *    (net.minecraft.class_XXXX), поэтому методы ищем по litematica-имени + числу
 *    параметров + isAssignableFrom от runtime-классов наших аргументов
 *    (state.getClass(), pos.getClass() и т.п.), а не по фиксированным
 *    Minecraft-именам.
 *
 * Любой отказ (мод не загружен, нужного класса/метода нет, invoke упал) сводится
 * к безопасным fallback-значениям, никаких крашей и NPE.
 *
 * getMaterialList()/getMissingMaterials() из исходника намеренно НЕ реализованы:
 * Printer их не использует (проверено grep-ом по вызовам), а MaterialListBase —
 * тяжёлая сущность по спискам стопок, не нужная в рантайме принтера.
 */
public final class SchematicAccess {
    private SchematicAccess() {
    }

    private static final boolean LITEMATICA_LOADED = FabricLoader.getInstance().isModLoaded("litematica");

    // ---- кэш reflection-находок (заполняется один раз в init()) ----
    private static Method getPlacementManagerMethod;      // DataManager.getSchematicPlacementManager()
    private static Method getRenderLayerRangeMethod;      // DataManager.getRenderLayerRange()
    private static Method getSelectedPlacementMethod;     // SchematicPlacementManager.getSelectedSchematicPlacement()
    private static Method placementIsEnabledMethod;       // SchematicPlacement.isEnabled()
    private static Method getSubRegionBoxesMethod;        // SchematicPlacement.getSubRegionBoxes(RequiredEnabled)
    private static Object placementEnabledValue;          // SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED
    private static Method rangeIsWithinMethod;            // LayerRange.isPositionWithinRange(BlockPos)
    private static Method getSchematicWorldMethod;        // SchematicWorldHandler.getSchematicWorld()
    private static Method materialCacheGetInstanceMethod; // MaterialCache.getInstance()
    private static Method getRequiredItemMethod;          // MaterialCache.getRequiredBuildItemForState(BlockState, Level, BlockPos)
    private static Method worldGetBlockStateMethod;       // lazy-кэш: getBlockState(BlockPos) от runtime-класса мира схемы

    private static boolean initAttempted;
    private static boolean initOk;

    private static boolean init() {
        if (initAttempted) {
            return initOk;
        }
        initAttempted = true;
        if (!LITEMATICA_LOADED) {
            return false;
        }
        try {
            Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
            getPlacementManagerMethod = resolveMethod(dataManager, "getSchematicPlacementManager");
            getRenderLayerRangeMethod = resolveMethod(dataManager, "getRenderLayerRange");

            Class<?> mgr = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager");
            getSelectedPlacementMethod = resolveMethod(mgr, "getSelectedSchematicPlacement");

            Class<?> placement = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
            placementIsEnabledMethod = resolveMethod(placement, "isEnabled");
            // Единственный параметр — enum RequiredEnabled; сверяем только по имени + числу параметров.
            getSubRegionBoxesMethod = resolveMethod(placement, "getSubRegionBoxes", 1);

            Class<?> requiredEnabled = Class.forName("fi.dy.masa.litematica.schematic.placement.SubRegionPlacement$RequiredEnabled");
            placementEnabledValue = enumConstant(requiredEnabled, "PLACEMENT_ENABLED");

            Class<?> range = Class.forName("fi.dy.masa.malilib.util.LayerRange");
            rangeIsWithinMethod = resolveMethod(range, "isPositionWithinRange", BlockPos.class);

            Class<?> worldHandler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            getSchematicWorldMethod = resolveMethod(worldHandler, "getSchematicWorld");

            Class<?> materialCache = Class.forName("fi.dy.masa.litematica.materials.MaterialCache");
            materialCacheGetInstanceMethod = resolveMethod(materialCache, "getInstance");
            // param0 — BlockState, param1 — мир (тип проверяем при invoke), param2 — BlockPos.
            getRequiredItemMethod = resolveMethod(materialCache, "getRequiredBuildItemForState", BlockState.class, null, BlockPos.class);

            if (getPlacementManagerMethod == null
                || getRenderLayerRangeMethod == null
                || getSelectedPlacementMethod == null
                || placementIsEnabledMethod == null
                || getSubRegionBoxesMethod == null
                || placementEnabledValue == null
                || rangeIsWithinMethod == null
                || getSchematicWorldMethod == null
                || materialCacheGetInstanceMethod == null
                || getRequiredItemMethod == null) {
                return false;
            }

            initOk = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Поиск публичного метода (включая унаследованные) по имени + количеству параметров. */
    private static Method resolveMethod(Class<?> owner, String name, int paramCount) {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }

    /** Поиск метода без параметров. */
    private static Method resolveMethod(Class<?> owner, String name) {
        return resolveMethod(owner, name, 0);
    }

    /**
     * Поиск метода по имени + количеству параметров + совместимости типов.
     * Аргументы с null-типом не проверяются (резолв им по значению при invoke).
     */
    private static Method resolveMethod(Class<?> owner, String name, Class<?>... argTypes) {
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != argTypes.length) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                if (argTypes[i] != null && !params[i].isAssignableFrom(argTypes[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        return null;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object c : enumClass.getEnumConstants()) {
            if (c instanceof Enum<?> e && e.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static Object invokeStatic(Method m) {
        try {
            return m.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object invoke(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getSchematicWorld() {
        if (!init()) {
            return null;
        }
        return invokeStatic(getSchematicWorldMethod);
    }

    private static Object selectedPlacement() {
        Object mgr = invokeStatic(getPlacementManagerMethod);
        if (mgr == null) {
            return null;
        }
        return invoke(mgr, getSelectedPlacementMethod);
    }

    public static boolean hasActivePlacement() {
        if (!init()) {
            return false;
        }
        Object placement = selectedPlacement();
        if (placement == null) {
            return false;
        }
        Object enabled = invoke(placement, placementIsEnabledMethod);
        return enabled instanceof Boolean b && b;
    }

    public static boolean isWithinRenderLayers(BlockPos pos) {
        if (!init()) {
            return true;
        }
        Object range = invokeStatic(getRenderLayerRangeMethod);
        if (range == null) {
            return true;
        }
        Object inside = invoke(range, rangeIsWithinMethod, pos);
        if (inside == null) {
            return true;
        }
        return !(inside instanceof Boolean b) || b;
    }

    public static List<PrinterRegion> getSelectedRegions() {
        if (!init()) {
            return List.of();
        }
        Object placement = selectedPlacement();
        if (placement == null) {
            return List.of();
        }
        Object raw = invoke(placement, getSubRegionBoxesMethod, placementEnabledValue);
        if (!(raw instanceof Map<?, ?> boxes)) {
            return List.of();
        }

        try {
            List<PrinterRegion> out = new ArrayList<>();
            for (Object box : boxes.values()) {
                // Класс AABB резолвим от live-объекта: какая версия litematica не важна.
                Method getPos1 = resolveMethod(box.getClass(), "getPos1");
                Method getPos2 = resolveMethod(box.getClass(), "getPos2");
                if (getPos1 == null || getPos2 == null) {
                    continue;
                }
                Object rawPos1 = invoke(box, getPos1);
                Object rawPos2 = invoke(box, getPos2);
                if (!(rawPos1 instanceof BlockPos p1) || !(rawPos2 instanceof BlockPos p2)) {
                    continue;
                }
                out.add(
                    new PrinterRegion(
                        Math.min(p1.getX(), p2.getX()),
                        Math.min(p1.getY(), p2.getY()),
                        Math.min(p1.getZ(), p2.getZ()),
                        Math.max(p1.getX(), p2.getX()),
                        Math.max(p1.getY(), p2.getY()),
                        Math.max(p1.getZ(), p2.getZ())
                    )
                );
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static BlockState getTargetState(BlockPos pos) {
        if (!init()) {
            return null;
        }
        try {
            Object world = getSchematicWorld();
            if (world == null) {
                return null;
            }
            // getBlockState резолвим от runtime-класса мира (litematica-мир реализует
            // BlockView, а не обязательно Level), метод кэшируем — класс стабилен.
            if (worldGetBlockStateMethod == null) {
                worldGetBlockStateMethod = resolveMethod(world.getClass(), "getBlockState", BlockPos.class);
                if (worldGetBlockStateMethod == null) {
                    return null;
                }
            }
            Object state = invoke(world, worldGetBlockStateMethod, pos);
            return state instanceof BlockState bs ? bs : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ItemStack getRequiredItem(BlockState state, BlockPos pos) {
        if (state == null) {
            return null;
        }
        // Fallback по умолчанию: предмет из самого блока. Возвращается при любом отказе reflection.
        ItemStack fallback = new ItemStack(state.getBlock().asItem());
        if (!init()) {
            return fallback;
        }
        try {
            Object cache = invokeStatic(materialCacheGetInstanceMethod);
            if (cache == null) {
                return fallback;
            }
            Object world = getSchematicWorld();
            if (world == null) {
                return fallback;
            }
            Object item = invoke(cache, getRequiredItemMethod, state, world, pos);
            return item instanceof ItemStack stack ? stack : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}