package me.collective.client.render

import com.mojang.blaze3d.vertex.PoseStack.Pose
import com.mojang.blaze3d.vertex.VertexConsumer
import me.collective.client.ClientAccess
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object VertexGeometry : ClientAccess {
   private val segmentStart = Vector3f()
   private val segmentEnd = Vector3f()

   @JvmStatic
   fun appendFilledBox(
      vertices: VertexConsumer,
      poseMatrix: Matrix4f,
      bounds: AABB,
      color: Int,
   ) {
      if (ARGB.alpha(color) != 0) {
         val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position()
         val minX = (bounds.minX - cameraPosition.x()).toFloat()
         val minY = (bounds.minY - cameraPosition.y()).toFloat()
         val minZ = (bounds.minZ - cameraPosition.z()).toFloat()
         val maxX = (bounds.maxX - cameraPosition.x()).toFloat()
         val maxY = (bounds.maxY - cameraPosition.y()).toFloat()
         val maxZ = (bounds.maxZ - cameraPosition.z()).toFloat()
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, maxX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, minY, minZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, minY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, maxZ).setColor(color)
         vertices.addVertex(poseMatrix, minX, maxY, minZ).setColor(color)
      }
   }

   @JvmStatic
   fun appendBoxOutline(
      vertices: VertexConsumer,
      pose: Pose,
      bounds: AABB,
      width: Float,
      color: Int,
   ) {
      val cameraPosition = ClientAccess.collectiveMinecraft.entityRenderDispatcher.camera!!.position()
      val minX = (bounds.minX - cameraPosition.x()).toFloat()
      val minY = (bounds.minY - cameraPosition.y()).toFloat()
      val minZ = (bounds.minZ - cameraPosition.z()).toFloat()
      val maxX = (bounds.maxX - cameraPosition.x()).toFloat()
      val maxY = (bounds.maxY - cameraPosition.y()).toFloat()
      val maxZ = (bounds.maxZ - cameraPosition.z()).toFloat()
      appendLine(vertices, pose, minX, minY, minZ, maxX, minY, minZ, width, color)
      appendLine(vertices, pose, minX, minY, minZ, minX, minY, maxZ, width, color)
      appendLine(vertices, pose, minX, minY, maxZ, maxX, minY, maxZ, width, color)
      appendLine(vertices, pose, maxX, minY, maxZ, maxX, minY, minZ, width, color)
      if (abs(maxY - minY) < 0.001) {
         return
      }
      appendLine(vertices, pose, minX, minY, minZ, minX, maxY, minZ, width, color)
      appendLine(vertices, pose, maxX, minY, minZ, maxX, maxY, minZ, width, color)
      appendLine(vertices, pose, minX, minY, maxZ, minX, maxY, maxZ, width, color)
      appendLine(vertices, pose, maxX, minY, maxZ, maxX, maxY, maxZ, width, color)
      appendLine(vertices, pose, minX, maxY, minZ, maxX, maxY, minZ, width, color)
      appendLine(vertices, pose, minX, maxY, minZ, minX, maxY, maxZ, width, color)
      appendLine(vertices, pose, minX, maxY, maxZ, maxX, maxY, maxZ, width, color)
      appendLine(vertices, pose, maxX, maxY, maxZ, maxX, maxY, minZ, width, color)
   }

   @JvmStatic
   fun appendLine(
      vertices: VertexConsumer,
      pose: Pose,
      fromX: Float,
      fromY: Float,
      fromZ: Float,
      toX: Float,
      toY: Float,
      toZ: Float,
      width: Float,
      color: Int,
   ) {
      appendLine(vertices, pose, fromX, fromY, fromZ, toX, toY, toZ, width, color, color)
   }

   @JvmStatic
   fun appendLine(
      vertices: VertexConsumer,
      pose: Pose,
      fromX: Float,
      fromY: Float,
      fromZ: Float,
      toX: Float,
      toY: Float,
      toZ: Float,
      width: Float,
      startColor: Int,
      endColor: Int,
   ) {
      val deltaX = toX - fromX
      val deltaY = toY - fromY
      val deltaZ = toZ - fromZ
      val length = Mth.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
      if (length < 1.0E-6f) {
         return
      }
      val normalX = deltaX / length
      val normalY = deltaY / length
      val normalZ = deltaZ / length
      vertices.addVertex(pose, fromX, fromY, fromZ).setColor(startColor)
         .setNormal(pose, normalX, normalY, normalZ).setLineWidth(width)
      vertices.addVertex(pose, toX, toY, toZ).setColor(endColor)
         .setNormal(pose, normalX, normalY, normalZ).setLineWidth(width)
   }

   @JvmStatic
   fun forEachCircleSegment(
      radius: Float,
      segments: Int,
      consumer: CircleSegmentConsumer,
   ) {
      if (segments < 2) {
         throw IllegalArgumentException("You need at least 2 segments to iterate circle")
      }

      var previousX = radius.toDouble()
      var previousZ = 0.0

      for (segment in 1..segments) {
         val angle = (Math.PI * 2) * segment / segments
         val x = radius * cos(angle)
         val z = radius * sin(angle)
         segmentStart.set(previousX, 0.0, previousZ)
         segmentEnd.set(x, 0.0, z)
         consumer.accept(segmentStart, segmentEnd)
         previousX = x
         previousZ = z
      }
   }

   fun interface CircleSegmentConsumer {
      fun accept(start: Vector3f, end: Vector3f)
   }
}
