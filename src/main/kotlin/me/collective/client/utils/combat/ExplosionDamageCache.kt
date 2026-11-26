package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.BlockUpdateAppliedEvent
import me.collective.client.events.ClientChunkCacheReplaceWithPacketDataEvent
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MultiPlayerGameModeDestroyBlockEvent
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.EquipmentUtils
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.Difficulty
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.BlocksAttacks
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Журнал блочных чтений одного расчёта exposure: DDA-обход записывает
 * (позиция, состояние ПОСЛЕ классификатора, форма) на каждое посещение.
 * Нужен для проверки кэша [ExplosionDamageCache] на каждом чтении.
 */
class BlockReadRecorder {
    var count = 0
        private set
    var inconsistent = false
        private set

    private var positions = LongArray(512)
    private var states = arrayOfNulls<BlockState>(512)
    private var shapes = arrayOfNulls<VoxelShape>(512)
    private var indexKeys = LongArray(1024)
    private var indexValues = IntArray(1024)

    init {
        Arrays.fill(indexKeys, EMPTY_SLOT)
    }

    fun reset() {
        count = 0
        inconsistent = false
        indexKeys.fill(EMPTY_SLOT)
    }

    fun record(position: BlockPos, state: BlockState, shape: VoxelShape) {
        val key = position.asLong()
        val slot = findSlot(key)
        if (indexKeys[slot] != EMPTY_SLOT) {
            val existing = indexValues[slot]
            // Повторное посещение той же позиции: результат обязан совпасть,
            // иначе мир менялся посреди расчёта — такую запись не кэшируем.
            if (states[existing] !== state || shapes[existing] !== shape) {
                inconsistent = true
            }
            return
        }
        if (count >= positions.size) {
            val newSize = positions.size * 2
            positions = positions.copyOf(newSize)
            states = states.copyOf(newSize)
            shapes = shapes.copyOf(newSize)
        }
        positions[count] = key
        states[count] = state
        shapes[count] = shape
        indexKeys[slot] = key
        indexValues[slot] = count
        count++
        if (count * 2 >= indexKeys.size) {
            growIndex()
        }
    }

    fun positionAt(index: Int): Long = positions[index]

    fun stateAt(index: Int): BlockState = states[index]!!

    fun shapeAt(index: Int): VoxelShape = shapes[index]!!

    private fun findSlot(key: Long): Int {
        var mask = indexKeys.size - 1
        var slot = (key.hashCode()) and mask
        while (true) {
            val stored = indexKeys[slot]
            if (stored == EMPTY_SLOT || stored == key) {
                return slot
            }
            slot = (slot + 1) and mask
        }
    }

    private fun growIndex() {
        val oldKeys = indexKeys
        val oldValues = indexValues
        indexKeys = LongArray(oldKeys.size * 2)
        indexValues = IntArray(oldValues.size * 2)
        Arrays.fill(indexKeys, EMPTY_SLOT)
        for (i in oldKeys.indices) {
            val key = oldKeys[i]
            if (key == EMPTY_SLOT) {
                continue
            }
            var mask = indexKeys.size - 1
            var slot = (key.hashCode()) and mask
            while (indexKeys[slot] != EMPTY_SLOT) {
                slot = (slot + 1) and mask
            }
            indexKeys[slot] = key
            indexValues[slot] = oldValues[i]
        }
    }

    companion object {
        private const val EMPTY_SLOT = Long.MIN_VALUE
    }
}

/**
 * Кэш итогового урона взрыва для AutoCrystal (и всех остальных вызовов
 * [DamageUtils.getExplosionDamage]).
 *
 * Эквивалентность: значение — детерминированная функция результата exposure
 * (только чтения блоков) и «не-блочных» входов. Все не-блочные входы лежат
 * в точном ключе [ExplosionDamageKey] (сравнение полей, не хешей), блочные
 * входы проверяются на КАЖДОМ чтении: для всех посещённых DDA-позиций
 * сверяются состояние после классификатора (identity — BlockState каноничен)
 * и форма (identity — VoxelShape из кэша форм состояния канонична; свеже-
 * выделенные формы не совпадают по identity → всегда miss, это консервативно).
 * Смена любого блока (включая клиентские предсказанные setBlock), выгрузка
 * чанка (getBlockState возвращает воздух/пустоту → identity не совпадёт) и
 * смена мира (identity ClientLevel в записи) обнаруживаются по построению,
 * без событий.
 *
 * Дополнительно — disziplina epoch: epoch фиксируется ДО расчёта и запись
 * отбрасывается, если epoch изменился; события изменения мира/блоков/чанков
 * поднимают epoch. Кэш ограничен по размеру и чистится при дисконнекте.
 */
object ExplosionDamageCache {
    private const val MAX_ENTRIES = 4096

    private val epochCounter = AtomicLong(0L)

    private val entries = object : LinkedHashMap<ExplosionDamageKey, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ExplosionDamageKey, CacheEntry>): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private val recorderScratch = ThreadLocal.withInitial { BlockReadRecorder() }

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    fun epoch(): Long = epochCounter.get()

    fun bump() {
        epochCounter.incrementAndGet()
    }

    fun recorder(): BlockReadRecorder = recorderScratch.get()

    fun keyOf(
        explosionPosition: Vec3,
        target: LivingEntity,
        bounds: AABB,
        power: Double,
        ignoreLowResistance: Boolean,
        ignoredPositions: Collection<*>,
        solidPosition: BlockPos?
    ): ExplosionDamageKey {
        val ignored = LongArray(ignoredPositions.size)
        var index = 0
        for (value in ignoredPositions) {
            val position = value as BlockPos?
            ignored[index++] = position?.asLong() ?: NULL_POSITION
        }
        Arrays.sort(ignored)
        var distinct = 0
        for (i in ignored.indices) {
            if (i == 0 || ignored[i] != ignored[i - 1]) {
                ignored[distinct++] = ignored[i]
            }
        }
        return ExplosionDamageKey(
            explosionPosition.x, explosionPosition.y, explosionPosition.z,
            bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
            power, ignoreLowResistance,
            if (distinct == ignored.size) ignored else ignored.copyOf(distinct),
            ignoredPositions.isNotEmpty(),
            solidPosition?.asLong() ?: NULL_POSITION,
            solidPosition != null,
            target.id,
            fingerprintOf(target)
        )
    }

    /** Отпечаток всех НЕ-блочных входов итогового значения урона (см. DamageUtils). */
    fun fingerprintOf(target: LivingEntity): ExplosionFingerprint {
        val level = ClientAccess.collectiveMinecraft.level
        val player = ClientAccess.collectiveMinecraft.player
        val creativeZero = player != null && player === target && player.isCreative

        val resistanceEffect = target.getEffect(MobEffects.RESISTANCE)
        val resistanceAmplifier = resistanceEffect?.amplifier ?: Int.MIN_VALUE

        val crystalModule = ClientContext.getModuleOrNull(AutoCrystalModule::class.java)
        val assumeBestArmor = crystalModule != null && crystalModule.isToggled() && crystalModule.assumeBestArmorSetting.value

        val stackValues: IntArray
        if (target is Player) {
            val armorStacks = EquipmentUtils.getArmorStacks(target)
            stackValues = IntArray(armorStacks.size * 4)
            var slot = 0
            for (armorItem in armorStacks) {
                val enchantments = EnchantmentUtils.getLevels(armorItem)
                stackValues[slot * 4] = if (armorItem.isEmpty) 1 else 0
                stackValues[slot * 4 + 1] = if (!armorItem.isEmpty && armorItem.`is`(ItemTags.HEAD_ARMOR)) 1 else 0
                stackValues[slot * 4 + 2] = enchantments.getOrDefault(Enchantments.BLAST_PROTECTION, 0) as Int
                stackValues[slot * 4 + 3] = enchantments.getOrDefault(Enchantments.PROTECTION, 0) as Int
                slot++
            }
        } else {
            stackValues = EMPTY_INTS
        }

        // resolveBlockingDamage: активно только при использовании предмета с BLOCKS_ATTACKS;
        // иначе его входы в результат не входят и в ключ не пишутся (лучший hit-rate).
        val useItem = target.useItem
        val blocksAttacks: BlocksAttacks? = if (target.isUsingItem) useItem.get(DataComponents.BLOCKS_ATTACKS) else null
        val blocking = blocksAttacks != null

        return ExplosionFingerprint(
            creativeZero,
            level?.difficulty?.ordinal ?: -1,
            target.armorValue,
            target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat(),
            resistanceAmplifier,
            assumeBestArmor,
            stackValues,
            blocking,
            blocksAttacks,
            if (blocking) target.x else 0.0,
            if (blocking) target.y else 0.0,
            if (blocking) target.z else 0.0,
            if (blocking) target.yHeadRot else 0.0f
        )
    }

    fun get(key: ExplosionDamageKey): Float? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val entry: CacheEntry
        synchronized(entries) {
            entry = entries[key] ?: return null
        }
        return if (isValid(entry, key, level)) entry.value else null
    }

    fun put(key: ExplosionDamageKey, epochAtStart: Long, value: Float, reads: BlockReadRecorder, target: LivingEntity) {
        if (epochCounter.get() != epochAtStart) {
            return
        }
        if (reads.inconsistent || reads.count == 0) {
            return
        }
        if (fingerprintOf(target) != key.fingerprint) {
            return
        }
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val count = reads.count
        val positions = LongArray(count)
        val states = arrayOfNulls<BlockState>(count)
        val shapes = arrayOfNulls<VoxelShape>(count)
        for (i in 0 until count) {
            positions[i] = reads.positionAt(i)
            states[i] = reads.stateAt(i)
            shapes[i] = reads.shapeAt(i)
        }
        val entry = CacheEntry(value, epochAtStart, level, target, positions, states, shapes)
        synchronized(entries) {
            entries[key] = entry
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    private fun isValid(entry: CacheEntry, key: ExplosionDamageKey, level: ClientLevel): Boolean {
        if (entry.level !== level) {
            return false
        }
        val positions = entry.positions
        if (positions.isEmpty()) {
            return false
        }
        val collisionContext = CollisionContext.of(entry.target)
        val mutable = BlockPos.MutableBlockPos()
        for (i in positions.indices) {
            val position = positions[i]
            mutable.set(position)
            val rawState = level.getBlockState(mutable)
            val state = classify(key, position, rawState)
            if (state !== entry.states[i]) {
                return false
            }
            if (state.getCollisionShape(level, mutable, collisionContext) !== entry.shapes[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Повторяет выбор состояния из RaycastUtils.CLIP_BLOCK: сначала
     * ignorePositions, затем solidPosition, затем IGNORE_LOW_RESISTANCE —
     * тот же порядок, что у BlockRaycastClassifier.firstNonNone.
     */
    private fun classify(key: ExplosionDamageKey, position: Long, rawState: BlockState): BlockState {
        if (key.hasIgnored && containsPosition(key.ignored, position)) {
            return Blocks.AIR.defaultBlockState()
        }
        if (key.hasSolid && position == key.solid) {
            return Blocks.BEDROCK.defaultBlockState()
        }
        if (key.ignoreLowResistance && rawState.block.explosionResistance < 600.0f) {
            return Blocks.AIR.defaultBlockState()
        }
        return rawState
    }

    private fun containsPosition(ignored: LongArray, position: Long): Boolean {
        return Arrays.binarySearch(ignored, position) >= 0
    }

    @EventHandler
    private fun onBlockUpdateApplied(event: BlockUpdateAppliedEvent) {
        bump()
    }

    @EventHandler
    private fun onChunkDataReplaced(event: ClientChunkCacheReplaceWithPacketDataEvent) {
        bump()
    }

    @EventHandler
    private fun onExplosion(event: ClientPacketListenerHandleExplosionEvent) {
        bump()
    }

    @EventHandler
    private fun onDisconnect(event: MinecraftDisconnectEvent) {
        bump()
        clear()
    }

    @EventHandler
    private fun onPacketReceived(event: PacketEvent.Receive) {
        when (event.packet) {
            is ClientboundBlockUpdatePacket,
            is ClientboundSectionBlocksUpdatePacket,
            is ClientboundExplodePacket,
            is ClientboundForgetLevelChunkPacket,
            is ClientboundSetChunkCacheRadiusPacket,
            is ClientboundLoginPacket,
            is ClientboundRespawnPacket,
            is ClientboundChangeDifficultyPacket -> bump()
        }
    }

    @EventHandler
    private fun onPacketSent(event: PacketEvent.Send) {
        // Клиентские предсказанные setBlock (MultiPlayerGameMode) происходят
        // в момент отправки этих пакетов — поднимаем epoch и для них.
        when (event.packet) {
            is ServerboundUseItemOnPacket,
            is ServerboundPlayerActionPacket -> bump()
        }
    }

    @EventHandler
    private fun onGameModeDestroy(event: MultiPlayerGameModeDestroyBlockEvent) {
        bump()
    }

    @EventHandler
    private fun onGameModeStartDestroy(event: MultiPlayerGameModeStartDestroyBlockEvent) {
        bump()
    }

    @EventHandler
    private fun onGameModeUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
        bump()
    }

    private class CacheEntry(
        val value: Float,
        val epoch: Long,
        val level: ClientLevel,
        val target: Entity,
        val positions: LongArray,
        val states: Array<BlockState?>,
        val shapes: Array<VoxelShape?>
    )

    private const val NULL_POSITION = Long.MIN_VALUE

    private val EMPTY_INTS = IntArray(0)
}

/**
 * Точное значение всех не-блочных входов урона взрыва (см. DamageUtils:
 * креатив-проверка, difficulty, броня/стойкость/сопротивление/Protection/Blast,
 * AssumeBestArmor, блокировка щитом). Сравнение по полям — без хеш-коллизий.
 */
class ExplosionFingerprint(
    private val creativeZero: Boolean,
    private val difficulty: Int,
    private val armorValue: Int,
    private val toughness: Float,
    private val resistanceAmplifier: Int,
    private val assumeBestArmor: Boolean,
    private val stackValues: IntArray,
    private val blocking: Boolean,
    private val blocksAttacks: BlocksAttacks?,
    private val posX: Double,
    private val posY: Double,
    private val posZ: Double,
    private val yHeadRot: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExplosionFingerprint) return false
        return creativeZero == other.creativeZero &&
            difficulty == other.difficulty &&
            armorValue == other.armorValue &&
            java.lang.Float.compare(toughness, other.toughness) == 0 &&
            resistanceAmplifier == other.resistanceAmplifier &&
            assumeBestArmor == other.assumeBestArmor &&
            Arrays.equals(stackValues, other.stackValues) &&
            blocking == other.blocking &&
            blocksAttacks === other.blocksAttacks &&
            java.lang.Double.compare(posX, other.posX) == 0 &&
            java.lang.Double.compare(posY, other.posY) == 0 &&
            java.lang.Double.compare(posZ, other.posZ) == 0 &&
            java.lang.Float.compare(yHeadRot, other.yHeadRot) == 0
    }

    override fun hashCode(): Int {
        var result = if (creativeZero) 1 else 0
        result = 31 * result + difficulty
        result = 31 * result + armorValue
        result = 31 * result + java.lang.Float.hashCode(toughness)
        result = 31 * result + resistanceAmplifier
        result = 31 * result + if (assumeBestArmor) 1 else 0
        result = 31 * result + Arrays.hashCode(stackValues)
        result = 31 * result + if (blocking) 1 else 0
        result = 31 * result + System.identityHashCode(blocksAttacks)
        result = 31 * result + java.lang.Double.hashCode(posX)
        result = 31 * result + java.lang.Double.hashCode(posY)
        result = 31 * result + java.lang.Double.hashCode(posZ)
        result = 31 * result + java.lang.Float.hashCode(yHeadRot)
        return result
    }
}

/**
 * Точный ключ кэша урона взрыва: геометрия, классификатор, цель и отпечаток
 * не-блочных входов. equals сравнивает поля, не свёртки.
 */
class ExplosionDamageKey(
    private val explosionX: Double,
    private val explosionY: Double,
    private val explosionZ: Double,
    private val minX: Double,
    private val minY: Double,
    private val minZ: Double,
    private val maxX: Double,
    private val maxY: Double,
    private val maxZ: Double,
    private val power: Double,
    val ignoreLowResistance: Boolean,
    val ignored: LongArray,
    val hasIgnored: Boolean,
    val solid: Long,
    val hasSolid: Boolean,
    private val targetId: Int,
    val fingerprint: ExplosionFingerprint
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExplosionDamageKey) return false
        return targetId == other.targetId &&
            hasIgnored == other.hasIgnored &&
            hasSolid == other.hasSolid &&
            ignoreLowResistance == other.ignoreLowResistance &&
            solid == other.solid &&
            java.lang.Double.compare(explosionX, other.explosionX) == 0 &&
            java.lang.Double.compare(explosionY, other.explosionY) == 0 &&
            java.lang.Double.compare(explosionZ, other.explosionZ) == 0 &&
            java.lang.Double.compare(minX, other.minX) == 0 &&
            java.lang.Double.compare(minY, other.minY) == 0 &&
            java.lang.Double.compare(minZ, other.minZ) == 0 &&
            java.lang.Double.compare(maxX, other.maxX) == 0 &&
            java.lang.Double.compare(maxY, other.maxY) == 0 &&
            java.lang.Double.compare(maxZ, other.maxZ) == 0 &&
            java.lang.Double.compare(power, other.power) == 0 &&
            Arrays.equals(ignored, other.ignored) &&
            fingerprint == other.fingerprint
    }

    override fun hashCode(): Int {
        var result = java.lang.Double.hashCode(explosionX)
        result = 31 * result + java.lang.Double.hashCode(explosionY)
        result = 31 * result + java.lang.Double.hashCode(explosionZ)
        result = 31 * result + java.lang.Double.hashCode(minX)
        result = 31 * result + java.lang.Double.hashCode(minY)
        result = 31 * result + java.lang.Double.hashCode(minZ)
        result = 31 * result + java.lang.Double.hashCode(maxX)
        result = 31 * result + java.lang.Double.hashCode(maxY)
        result = 31 * result + java.lang.Double.hashCode(maxZ)
        result = 31 * result + java.lang.Double.hashCode(power)
        result = 31 * result + if (ignoreLowResistance) 1 else 0
        result = 31 * result + Arrays.hashCode(ignored)
        result = 31 * result + if (hasIgnored) 1 else 0
        result = 31 * result + java.lang.Long.hashCode(solid)
        result = 31 * result + if (hasSolid) 1 else 0
        result = 31 * result + targetId
        result = 31 * result + fingerprint.hashCode()
        return result
    }
}