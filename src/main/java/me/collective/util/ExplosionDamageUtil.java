package me.collective.util;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Точная реплика серверного расчёта урона от взрыва якоря возрождения (1.21.11).
 *
 * <p>Серверная цепочка (проверено по байткоду {@code ExplosionImpl} и {@code ExplosionBehavior}):
 * <pre>
 *   float exposure = ExplosionImpl.calculateReceivedDamage(center, entity);
 *   float radius   = explosion.getPower() * 2.0f;            // power 5.0 → 10.0
 *   double d       = Math.sqrt(entity.distanceToSqr(center)) / radius;
 *   double impact  = (1.0 - d) * exposure;
 *   float raw      = (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0);
 * </pre>
 *
 * <p>Дальше клиент применяет те же редукции, что и сервер, в том же порядке:
 * броня ({@code DamageUtil.getDamageLeft}) → Blast Protection → сопротивление зельям.
 *
 * <p>Важные отличия от наивной реализации, из-за которых оценка урона
 * систематически завышалась:
 * <ul>
 *   <li>расстояние берётся до <b>позиции ног</b> сущности, а не до ближайшей
 *       точки хитбокса;</li>
 *   <li>лучи бьют по точке внутри блока-источника и блок-источник
 *       <b>не исключается</b> — он реально перекрывает обзор;</li>
 *   <li>сетка сэмплов: шаг {@code 1/(len*2+1)} по каждой оси с половинными
 *       оффсетами, а не фиксированные 4 точки;</li>
 *   <li>Blast Protection режет максимум на {@code min(level*2, 20)/25} (80%),
 *       а не {@code min(level*2, 80)/100}.</li>
 * </ul>
 *
 * <p>Сырой урон кэшируется по (сущность, позиция) на один тик — иначе подбор
 * позиции размещения выполнял бы тысячи мировых raycast'ов за тик.
 */
public final class ExplosionDamageUtil {

    /** Мощность взрыва заряженного якоря возрождения. */
    public static final float ANCHOR_POWER = 5.0f;

    /** Мощность взрыва кристалла Энда. */
    public static final float CRYSTAL_POWER = 6.0f;

    /** Радиус поражения: {@code power * 2}. */
    public static final float ANCHOR_RADIUS = ANCHOR_POWER * 2.0f;
    public static final float CRYSTAL_RADIUS = CRYSTAL_POWER * 2.0f;

    /** Минимальный «полезный» урон — формула не может дать меньше единицы. */
    private static final float MIN_RAW_DAMAGE = 1.0f;

    private static final Map<Long, Float> RAW_CACHE = new HashMap<>();
    private static final int CACHE_LIMIT = 512;

    private ExplosionDamageUtil() {}

    // ──────────────────────────────────────────────────────
    // Кэш
    // ──────────────────────────────────────────────────────

    /** Сбрасывается раз в тик из вызывающего модуля. */
    public static void clearCache() {
        if (!RAW_CACHE.isEmpty()) RAW_CACHE.clear();
    }

    private static float cachedRaw(Level world, Vec3 center, float power, LivingEntity entity,
                                   BlockPos transparentBlock) {
        long key = key(center, power, entity, transparentBlock);
        Float hit = RAW_CACHE.get(key);
        if (hit != null) return hit;

        float value = calcRaw(world, center, power, entity, transparentBlock);

        if (RAW_CACHE.size() >= CACHE_LIMIT) RAW_CACHE.clear();
        RAW_CACHE.put(key, value);
        return value;
    }

    private static long key(Vec3 center, float power, LivingEntity entity, BlockPos transparentBlock) {
        long h = BlockPos.containing(center).asLong() * 31L + entity.getId();
        h = h * 31L + (long) (power * 8.0f);          // 6.0 и 5.0 разводятся по разным битам
        if (transparentBlock != null) h = h * 31L + transparentBlock.asLong();
        return h;
    }

    // ──────────────────────────────────────────────────────
    // Публичный API
    // ──────────────────────────────────────────────────────

    /** Полный расчёт урона с учётом брони и Blast Protection. */
    public static float getDamage(Level world, BlockPos anchorPos, LivingEntity entity) {
        return getDamage(world, net.minecraft.world.phys.Vec3.atCenterOf(anchorPos), ANCHOR_POWER, entity, null);
    }

    public static float getDamage(Level world, Vec3 center, float power, LivingEntity entity) {
        return getDamage(world, center, power, entity, null);
    }

    /**
     * @param transparentBlock блок, который не должен перекрывать обзор при
     *                        подсчёте видимости: обсидиан, который мы ставим
     *                        (сейчас там воздух), либо блок, который мы ломаем
     *                        прямо перед взрывом. Может быть {@code null}.
     */
    public static float getDamage(Level world, Vec3 center, float power, LivingEntity entity,
                                  BlockPos transparentBlock) {
        if (world == null || entity == null || entity.isDeadOrDying()) return 0.0f;

        float raw = cachedRaw(world, center, power, entity, transparentBlock);
        if (raw <= 0.0f) return 0.0f;

        float afterArmor = applyArmor(entity, raw);
        float afterBlast = applyBlastProtection(world, entity, afterArmor);

        return Mth.clamp(afterBlast, 0.0f, raw);
    }

    /**
     * Сырой урон без редукций — для сравнения позиций в цикле подбора.
     * Значение кэшируется на текущий тик.
     */
    public static float getDamageRaw(Level world, BlockPos anchorPos, LivingEntity entity) {
        return getDamageRaw(world, anchorPos, entity, null);
    }

    public static float getDamageRaw(Level world, BlockPos anchorPos, LivingEntity entity,
                                     BlockPos transparentBlock) {
        if (world == null || entity == null || entity.isDeadOrDying()) return 0.0f;
        return cachedRaw(world, net.minecraft.world.phys.Vec3.atCenterOf(anchorPos), ANCHOR_POWER, entity, transparentBlock);
    }

    /**
     * Сырой урон взрыва произвольной мощности из произвольной точки.
     * Кристалл бьёт с {@link #CRYSTAL_POWER}, якорь — с {@link #ANCHOR_POWER}.
     */
    public static float getDamageRaw(Level world, Vec3 center, float power, LivingEntity entity,
                                     BlockPos transparentBlock) {
        if (world == null || entity == null || entity.isDeadOrDying()) return 0.0f;
        return cachedRaw(world, center, power, entity, transparentBlock);
    }

    /**
     * Верхняя граница урона (все лучи видны). Используется для дешёвой
     * предварительной сортировки кандидатов перед точным расчётом —
     * {@link #getExposure} для этого не вызывается.
     */
    public static float getDamageUpperBound(Vec3 center, float power, LivingEntity entity) {
        float radius = power * 2.0f;
        double d = Math.sqrt(entity.distanceToSqr(center)) / radius;
        if (d >= 2.0) return MIN_RAW_DAMAGE;

        double impact = 1.0 - d; // exposure == 1.0
        return (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + MIN_RAW_DAMAGE);
    }

    // ──────────────────────────────────────────────────────
    // Ядро расчёта — 1-в-1 как на сервере
    // ──────────────────────────────────────────────────────

    private static float calcRaw(Level world, Vec3 center, float power, LivingEntity entity,
                                 BlockPos transparentBlock) {
        float radius = power * 2.0f;

        // squaredDistanceTo использует getX/getY/getZ — это позиция НОГ сущности.
        double d = Math.sqrt(entity.distanceToSqr(center)) / radius;

        // При d >= 2 impact <= -1 и (impact^2 + impact)/2 >= 0 — результат
        // ровно 1.0 независимо от видимости. Raycast'ы можно не делать.
        if (d >= 2.0) return MIN_RAW_DAMAGE;

        float exposure = getExposure(world, center, entity, transparentBlock);
        double impact  = (1.0 - d) * exposure;

        return (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + MIN_RAW_DAMAGE);
    }

    // ──────────────────────────────────────────────────────
    // Exposure — реплика ExplosionImpl.calculateReceivedDamage
    // ──────────────────────────────────────────────────────

    /**
     * Доля сэмплов хитбокса, видимых из точки взрыва.
     *
     * <p>Сетка: по каждой оси шаг {@code 1/(длина*2 + 1)}, значения от 0 до 1
     * включительно, плюс смещение {@code (1 - floor(1/step)*step)/2} по X и Z
     * чтобы сэмплы не попадали ровно на границу блока. Для хитбокса игрока
     * (0.6 × 1.8 × 0.6) это 3 × 5 × 3 = 45 лучей.
     *
     * <p>Луч пускается <b>из сэмпла в точку взрыва</b> (как на сервере) и
     * засчитывается только при {@code MISS}. Блок-источник исключением
     * <b>не</b> является — сервер тоже считает его перекрытием.
     */
    private static float getExposure(Level world, Vec3 explosionPos, LivingEntity entity,
                                     BlockPos transparentBlock) {
        AABB box = entity.getBoundingBox();

        double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);

        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) return 0.0f;

        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

        int hits  = 0;
        int total = 0;

        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3 sample = new Vec3(
                            Mth.lerp(x, box.minX, box.maxX) + offsetX,
                            Mth.lerp(y, box.minY, box.maxY),
                            Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);

                    BlockHitResult hit = world.clip(new ClipContext(
                            sample, explosionPos,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            entity));

                    // Прозрачный блок не считается препятствием: обсидиан, который
                    // ставим прямо сейчас, там ещё воздух; блок, который ломаем
                    // перед взрывом, к моменту взрыва уже разрушен.
                    boolean clear = hit.getType() == HitResult.Type.MISS
                            || (transparentBlock != null && hit.getBlockPos().equals(transparentBlock));

                    if (clear) hits++;
                    total++;
                }
            }
        }

        return total == 0 ? 0.0f : (float) hits / (float) total;
    }

    // ──────────────────────────────────────────────────────
    // Редукции — в том же порядке, что и на сервере
    // ──────────────────────────────────────────────────────

    /** {@code DamageUtil.getDamageLeft(damage, armor, armorToughness)}. */
    private static float applyArmor(LivingEntity entity, float damage) {
        float armor     = (float) entity.getAttributeValue(Attributes.ARMOR);
        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        float f = armor - damage / (2.0f + toughness / 4.0f);
        f = Mth.clamp(f, armor * 0.2f, 20.0f);

        return damage * (1.0f - f / 25.0f);
    }

    /**
     * Blast Protection: {@code getDamageAfterEnchantment} режет на
     * {@code min(level * 2, 20) / 25} — максимум 80%.
     */
    private static float applyBlastProtection(Level world, LivingEntity entity, float damage) {
        var lookup = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        net.minecraft.core.Holder<Enchantment> blast = lookup.getOrThrow(Enchantments.BLAST_PROTECTION);

        int totalLevel = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blast, stack);
        }

        if (totalLevel <= 0) return damage;

        float reduction = Math.min(totalLevel * 2, 20);
        return damage * (1.0f - reduction / 25.0f);
    }
}