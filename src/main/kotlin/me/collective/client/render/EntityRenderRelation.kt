package me.collective.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.util.function.BiConsumer

enum class EntityRenderRelation {
    DEFAULT,
    FRIEND,
    ENEMY;

    val meshBuffer: MeshBuffer = MeshBuffer.create()
        .setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_TEX)

    companion object {
        @JvmStatic
        fun forEachMesh(consumer: BiConsumer<EntityRenderRelation, MeshBuffer>) {
            for (relation in entries) {
                consumer.accept(relation, relation.meshBuffer)
            }
        }
    }
}
