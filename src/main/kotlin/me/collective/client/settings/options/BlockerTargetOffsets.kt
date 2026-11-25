package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

import net.minecraft.core.Vec3i

enum class BlockerTargetOffsets(private val displayName: String, vararg targetOffsets: Vec3i) : NamedEntry {
    FULL(
        "Full",
        Vec3i(1, 0, 0), Vec3i(0, 0, 1), Vec3i(-1, 0, 0), Vec3i(0, 0, -1),
        Vec3i(1, 1, 0), Vec3i(0, 1, 1), Vec3i(-1, 1, 0), Vec3i(0, 1, -1),
        Vec3i(0, 2, 0), Vec3i(0, 3, 0)
    ),
    SURROUND("Surround", Vec3i(1, 0, 0), Vec3i(0, 0, 1), Vec3i(-1, 0, 0), Vec3i(0, 0, -1)),
    CEV("Cev", Vec3i(0, 2, 0), Vec3i(0, 3, 0)),
    CEV_EXTENDED("CevExtend", Vec3i(1, 1, 0), Vec3i(0, 1, 1), Vec3i(-1, 1, 0), Vec3i(0, 1, -1));

    val offsets: List<Vec3i> = targetOffsets.toList()

    override fun getName(): String = displayName
}
