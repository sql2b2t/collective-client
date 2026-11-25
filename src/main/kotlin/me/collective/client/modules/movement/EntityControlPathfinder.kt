package me.collective.client.modules.movement

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.event.events.BlockChangeEvent
import baritone.api.event.events.ChunkEvent
import baritone.api.event.listener.AbstractGameEventListener
import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalComposite
import baritone.api.pathing.goals.GoalXZ
import baritone.api.pathing.goals.GoalYLevel
import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import baritone.api.utils.interfaces.IGoalRenderPos
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.stream.IntStream
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class EntityControlPathfinder(private val module: EntityControlModule) : ClientAccess, AbstractGameEventListener, IBaritoneProcess {
   private val baritone: IBaritone
   private var context: EntityControlPathContext? = null
   private var calculation: CompletableFuture<EntityControlPathContext.Route>? = null
   private var goal: Goal? = null
   private var route: MutableList<Vec3> = mutableListOf()
   private var pathFinished: Boolean = false
   private var routeIndex: Int = 0
   private var shortcutDelay: Int = 0
   private var blocksChanged: Boolean = false
   private val blockedPositions: MutableSet<BlockPos> = HashSet()
   private var retryTicks: Int = 0
   private var warnedNoRoute: Boolean = false
   private var warnedUnsupported: Boolean = false
   private var lastPosition: Vec3? = null
   private var stuckTicks: Int = 0
   private var lastRepathPosition: Vec3? = null
   private var repeatedRepaths: Int = 0
   private var motion: Vec3 = Vec3.ZERO
   private var motionTick: Int = -1

   init {
      this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone()
      this.baritone.getPathingControlManager().registerProcess(this)
      this.baritone.getGameEventHandler().registerEventListener(this)
   }

   fun getMotion(): Vec3? {
      return if (ClientAccess.collectiveMinecraft.player != null && ClientAccess.collectiveMinecraft.player!!.tickCount == this.motionTick) this.motion else null
   }

   override fun isActive(): Boolean {
      val vehicle = this.getVehicle()
      val activeGoal = this.getGoal()
      if (vehicle == null || activeGoal == null || target(activeGoal, vehicle.position()) == null) {
         return false
      }
      if (!EntityControlPathContext.isSupported()) {
         this.warnUnsupported("This Baritone version doesn't contain a supported elytra pathfinder.")
         return false
      }
      if (!isBelowNetherRoof(vehicle)) {
         this.warnUnsupported("This Baritone version pathfinder works only in Nether.")
         return false
      }
      return true
   }

   override fun onTick(calculationFailed: Boolean, safeToCancel: Boolean): PathingCommand {
      val vehicle = this.getVehicle()
      val activeGoal = this.getGoal()
      val position: Vec3? = vehicle?.position()
      val destination: Vec3? = if (position == null || activeGoal == null) null else target(activeGoal, position)
      if (destination == null) {
         return PathingCommand(null, PathingCommandType.DEFER)
      }
      // Java guarantees these are non-null here: destination != null implies
      // position != null (computed from it), vehicle != null and activeGoal != null.
      val currentGoal: Goal = activeGoal!!
      val currentVehicle: Entity = vehicle!!
      val currentPosition: Vec3 = position!!
      if (currentGoal != this.goal) {
         this.reset()
         this.goal = currentGoal
         this.message("Going to " + BlockPos.containing(destination).toShortString() + ".")
      }
      if (isAtGoal(currentGoal, currentVehicle, currentPosition, destination)) {
         this.message("Done :^)")
         return this.finish()
      }
      val pathContext = this.getContext(currentVehicle)
      if (pathContext == null) {
         this.warning("Failed to start the pathfinder.", ChatUtils.MessagePriority.HIGH)
         return this.finish()
      }
      val bounds: AABB = relativeBounds(currentVehicle)
      val pendingCalculation = this.calculation
      if (pendingCalculation != null && pendingCalculation.isDone()) {
         val result: EntityControlPathContext.Route? = pendingCalculation.getNow(null)
         this.calculation = null
         if (result == null || result.nodes().isEmpty()) {
            if (!this.warnedNoRoute) {
               this.warning("No route found yet, retrying.", ChatUtils.MessagePriority.MID)
            }
            this.warnedNoRoute = true
            this.retryTicks = 40
         } else {
            this.warnedNoRoute = false
            this.route = routePoints(result.nodes(), bounds)
            this.pathFinished = result.finished()
            this.shortcutDelay = 0
            this.stuckTicks = 0
            val last = this.route.last()
            if (this.pathFinished && last.distanceTo(destination) <= 16.0 && isLoaded(destination) &&
         canTraverse(currentVehicle, last, destination)) {
               this.route.add(destination)
            } else if (this.pathFinished && last.distanceTo(currentPosition) < 1.5) {
               this.message("Stopped " + Math.round(currentPosition.distanceTo(destination)) + "m away, impossible to reach the destination.")
               return this.finish()
            }
            this.routeIndex = this.nearestReachablePoint(currentVehicle, currentPosition)
         }
      }
      val next: Vec3? = this.nextTarget(currentVehicle, currentPosition)
      val stuck = this.updateStuck(currentPosition, next != null)
      if (this.retryTicks > 0) {
         this.retryTicks--
      }
      if (this.calculation == null && this.retryTicks == 0 &&
         (next == null || stuck || this.routeChanged(bounds) ||
         !this.pathFinished && this.route.last().distanceTo(currentPosition) < 64.0)) {
         if (stuck) {
            this.markObstruction(pathContext, currentPosition, bounds, next)
         }
         this.calculation = pathContext.findPath(BlockPos.containing(currentPosition.add(bounds.getCenter())),
            BlockPos.containing(destination.add(bounds.getCenter())))
      }
      this.motion = this.calculateMotion(currentVehicle, currentPosition, bounds, next)
      this.motionTick = ClientAccess.collectiveMinecraft.player!!.tickCount
      return PathingCommand(currentGoal, PathingCommandType.SET_GOAL_AND_PAUSE)
   }

   override fun isTemporary(): Boolean {
      return true
   }

   override fun onLostControl() {
      this.reset()
      this.goal = null
      this.motion = Vec3.ZERO
      this.motionTick = -1
   }

   override fun priority(): Double {
      return 100.0
   }

   override fun displayName0(): String {
      return "EntityControl to " + this.goal
   }

   override fun onChunkEvent(event: ChunkEvent) {
      val context = this.context
      if (context != null && context.level === ClientAccess.collectiveMinecraft.level!! && event.isPostPopulate()) {
         context.loadChunk(event.getX(), event.getZ())
         this.blocksChanged = true
      }
   }

   override fun onBlockChange(event: BlockChangeEvent) {
      val context = this.context
      if (context != null && context.level === ClientAccess.collectiveMinecraft.level!!) {
         context.onBlockChange(event)
         this.blocksChanged = true
      }
   }

   private fun message(message: String) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module))
   }

   private fun warning(message: String, priority: ChatUtils.MessagePriority) {
      ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this.module), priority)
   }

   private fun warnUnsupported(message: String) {
      if (!this.warnedUnsupported) {
         this.warning(message, ChatUtils.MessagePriority.HIGH)
      }
      this.warnedUnsupported = true
   }

   private fun finish(): PathingCommand {
      this.reset()
      this.motion = Vec3.ZERO
      this.motionTick = ClientAccess.collectiveMinecraft.player!!.tickCount
      this.baritone.getCustomGoalProcess().onLostControl()
      return PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PAUSE)
   }

   private fun reset() {
      val calculation = this.calculation
      val context = this.context
      if (calculation != null && context != null) {
         context.cancel()
      }
      this.calculation = null
      this.route = mutableListOf()
      this.routeIndex = 0
      this.blocksChanged = false
      this.blockedPositions.clear()
      this.retryTicks = 0
      this.warnedNoRoute = false
      this.lastPosition = null
      this.stuckTicks = 0
      this.lastRepathPosition = null
      this.repeatedRepaths = 0
   }

   private fun getContext(vehicle: Entity): EntityControlPathContext? {
      val height = if (isBelowNetherRoof(vehicle)) 128 else Math.min(ClientAccess.collectiveMinecraft.level!!.getHeight(), 128)
      val context = this.context
      if (context != null && (context.level !== ClientAccess.collectiveMinecraft.level!! || context.height != height)) {
         context.close()
         this.context = null
         this.calculation = null
      }
      if (this.context == null) {
         val created = EntityControlPathContext(ClientAccess.collectiveMinecraft.level!!, height)
         if (!created.isValid()) {
            created.close()
            return null
         }
         this.context = created
         this.context!!.loadChunks(vehicle.chunkPosition())
      }
      this.context!!.cullChunks(vehicle.chunkPosition())
      return this.context
   }

   private fun markObstruction(pathContext: EntityControlPathContext, position: Vec3, bounds: AABB, next: Vec3?) {
      val lastRepathPosition = this.lastRepathPosition
      this.repeatedRepaths = if (lastRepathPosition != null && position.distanceTo(lastRepathPosition) < 8.0)
         this.repeatedRepaths + 1 else 1
      this.lastRepathPosition = position
      this.stuckTicks = 0
      if (this.repeatedRepaths >= 2) {
         val obstruction = firstBlock(bounds, position, position.add(this.motionToTarget(position, next!!)),
            Predicate { block: BlockPos -> !ClientAccess.collectiveMinecraft.level!!.getBlockState(block).getCollisionShape(ClientAccess.collectiveMinecraft.level!!, block).isEmpty() })
         if (obstruction != null) {
            pathContext.markBlocked(obstruction, 1)
         }
      }
   }

   private fun routeChanged(bounds: AABB): Boolean {
      if (!this.blocksChanged || ClientAccess.collectiveMinecraft.player!!.tickCount % 5 != 0) {
         return false
      }
      this.blocksChanged = false
      val contracted: AABB = bounds.deflate(0.05)
      var index = Math.max(this.routeIndex - 1, 0)
      while (index + 1 < Math.min(this.route.size, this.routeIndex + 400)) {
         if (!isLoaded(this.route[index + 1])) {
            break
         }
         val obstruction = firstBlock(contracted, this.route[index], this.route[index + 1],
            Predicate { block: BlockPos -> !ClientAccess.collectiveMinecraft.level!!.getBlockState(block).isAir() })
         if (obstruction != null && this.blockedPositions.add(obstruction)) {
            return true
         }
         index++
      }
      return false
   }

   private fun nearestReachablePoint(vehicle: Entity, position: Vec3): Int {
      val closest: List<Int> = IntStream.range(0, this.route.size).boxed()
         .sorted(Comparator.comparingDouble { index: Int -> this.route[index].distanceToSqr(position) }).toList()
      return closest.stream().limit(12).filter { index -> canTraverse(vehicle, position, this.route[index]) }.findFirst().orElse(closest.first())
   }

   private fun nextTarget(vehicle: Entity, position: Vec3): Vec3? {
      while (this.routeIndex < this.route.size && isNear(this.route[this.routeIndex], position)) {
         this.routeIndex++
      }
      if (this.routeIndex >= this.route.size) {
         return null
      }
      if (this.shortcutDelay-- <= 0) {
         this.shortcutDelay = 4
         this.advanceTarget(vehicle, position, this.routeIndex + 1)
      }
      return this.route[this.routeIndex]
   }

   private fun advanceTarget(vehicle: Entity, position: Vec3, minimum: Int): Boolean {
      var index = Math.min(this.routeIndex + 8, this.route.size - 1)
      while (index >= minimum) {
         val point = this.route[index]
         if (isLoaded(point) && canTraverse(vehicle, position, point)) {
            this.routeIndex = index
            return true
         }
         index--
      }
      return false
   }

   private fun updateStuck(position: Vec3, hasTarget: Boolean): Boolean {
      val lastPositionSnapshot = this.lastPosition
      this.stuckTicks = if (hasTarget && lastPositionSnapshot != null && position.distanceToSqr(lastPositionSnapshot) < 0.0025)
         this.stuckTicks + 1 else 0
      this.lastPosition = position
      return this.stuckTicks >= 20
   }

   private fun calculateMotion(vehicle: Entity, position: Vec3, bounds: AABB, next: Vec3?): Vec3 {
      if (!ClientAccess.collectiveMinecraft.level!!.noBlockCollision(vehicle, bounds.move(position))) {
         return escapeMotion(vehicle, position, bounds)
      }
      val escapeHazard: Boolean = vehicle.isInLava() || isHazard(bounds.move(position))
      var result: Vec3? = this.tryMotion(vehicle, position, bounds, next, escapeHazard)
      if (result == null && next != null && this.advanceTarget(vehicle, position, Math.max(0, this.routeIndex - 8))) {
         result = this.tryMotion(vehicle, position, bounds, this.route[this.routeIndex], escapeHazard)
      }
      return result ?: Vec3.ZERO
   }

   private fun tryMotion(vehicle: Entity, position: Vec3, bounds: AABB, next: Vec3?, escapeHazard: Boolean): Vec3? {
      val desired: Vec3 = if (next == null) Vec3.ZERO else this.motionToTarget(position, next)
      val candidates = ArrayList(listOf(desired, desired.scale(0.5), desired.scale(0.25),
         Vec3(desired.x, 0.0, desired.z), Vec3(0.0, desired.y, 0.0)))
      if (escapeHazard) {
         candidates.add(0, Vec3(0.0, Mth.clamp(this.module.getEntityPathVerticalSpeed(), 0.1, 1.0), 0.0))
      }
      return candidates.stream().filter { candidate -> allowsMotion(vehicle, position, bounds, candidate, escapeHazard) }.findFirst().orElse(null)
   }

   private fun escapeMotion(vehicle: Entity, position: Vec3, bounds: AABB): Vec3 {
      for (distance in doubleArrayOf(0.25, 0.5, 1.0, 2.0)) {
         for (direction in Direction.entries) {
            val motion: Vec3 = direction.getUnitVec3().scale(distance)
            val moved: AABB = bounds.move(position.add(motion))
            if (ClientAccess.collectiveMinecraft.level!!.noBlockCollision(vehicle, moved) && !isHazard(moved)) {
               return motion
            }
         }
      }
      return Vec3.ZERO
   }

   private fun allowsMotion(vehicle: Entity, position: Vec3, bounds: AABB, motion: Vec3, escapeHazard: Boolean): Boolean {
      if (motion.lengthSqr() < 1.0E-6) {
         return true
      }
      if (!escapeHazard && motion.y < 0.0 && isHazard(bounds.move(position.add(motion)).expandTowards(0.0, -2.0, 0.0))) {
         return false
      }
      return firstCollision(vehicle, bounds, position, position.add(motion), !escapeHazard) == null
   }

   private fun canTraverse(vehicle: Entity, from: Vec3, to: Vec3): Boolean {
      return firstCollision(vehicle, relativeBounds(vehicle), from, to, true) == null
   }

   private fun firstBlock(bounds: AABB, from: Vec3, to: Vec3, predicate: Predicate<BlockPos>): BlockPos? {
      val steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.5))
      for (step in 0..steps) {
         val moved: AABB = bounds.move(from.lerp(to, step.toDouble() / steps))
         for (position in BlockPos.betweenClosed(BlockPos.containing(moved.minX, moved.minY, moved.minZ),
            BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))) {
            if (predicate.test(position)) {
               return position.immutable()
            }
         }
      }
      return null
   }

   private fun firstCollision(vehicle: Entity, bounds: AABB, from: Vec3, to: Vec3, avoidHazards: Boolean): AABB? {
      val steps = Math.max(1, Mth.ceil(from.distanceTo(to) / 0.25))
      for (step in 0..steps) {
         val moved: AABB = bounds.move(from.lerp(to, step.toDouble() / steps))
         if (!ClientAccess.collectiveMinecraft.level!!.noBlockCollision(vehicle, moved) || avoidHazards && isHazard(moved)) {
            return moved
         }
      }
      return null
   }

   private fun motionToTarget(from: Vec3, to: Vec3): Vec3 {
      val direction: Vec3 = to.subtract(from)
      val horizontal: Double = Math.hypot(direction.x, direction.z)
      val speed: Double = this.module.getEntityPathSpeed()
      var scale: Double = if (horizontal > speed) speed / horizontal else 1.0
      val vertical: Double = Math.max(this.module.getEntityPathVerticalSpeed(), 0.1)
      if (Math.abs(direction.y) * scale > vertical) {
         scale = vertical / Math.abs(direction.y)
      }
      return direction.scale(scale)
   }

   private fun routePoints(nodes: List<EntityControlPathContext.Node>, bounds: AABB): MutableList<Vec3> {
      val offset: Vec3 = bounds.getCenter()
      val points = ArrayList<Vec3>()
      var previous: AABB? = null
      for (node in nodes) {
         val box: AABB = node.bounds()
         if (previous != null) {
            addPortal(points, previous, box, bounds)
         }
         points.add(box.getCenter().subtract(offset))
         previous = box
      }
      return points
   }

   private fun addPortal(points: MutableList<Vec3>, from: AABB, to: AABB, bounds: AABB) {
      var touching: Direction.Axis? = null
      for (axis in Direction.Axis.entries) {
         val overlap = overlap(from, to, axis)
         if (overlap < -1.0E-6) {
            return
         }
         if (overlap < 1.0E-6) {
            if (touching != null) {
               return
            }
            touching = axis
         }
      }
      if (touching == null) {
         return
      }
      val half: Vec3 = Vec3(bounds.getXsize() / 2.0, bounds.getYsize() / 2.0, bounds.getZsize() / 2.0).add(0.1)
      for (axis in Direction.Axis.entries) {
         if (axis != touching && overlap(from, to, axis) < 2.0 * half.get(axis)) {
            return
         }
      }
      val width = 2.0 * half.get(touching)
      if (from.max(touching) - from.min(touching) < width || to.max(touching) - to.min(touching) < width) {
         return
      }
      val sign = Math.signum(to.getCenter().get(touching) - from.getCenter().get(touching))
      val center = from.intersect(to).getCenter()
      for (side in -1..1 step 2) {
         points.add(center.with(touching, center.get(touching) + side * sign * half.get(touching)).subtract(bounds.getCenter()))
      }
   }

   private fun overlap(first: AABB, second: AABB, axis: Direction.Axis): Double {
      return Math.min(first.max(axis), second.max(axis)) - Math.max(first.min(axis), second.min(axis))
   }

   private fun isNear(first: Vec3, second: Vec3): Boolean {
      return Math.hypot(first.x - second.x, first.z - second.z) < 0.3 && Math.abs(first.y - second.y) < 0.3
   }

   private fun isLoaded(position: Vec3): Boolean {
      return ClientAccess.collectiveMinecraft.level!!.getChunkSource().hasChunk(Mth.floor(position.x) shr 4, Mth.floor(position.z) shr 4)
   }

   private fun isHazard(bounds: AABB): Boolean {
      return ClientAccess.collectiveMinecraft.level!!.getBlockStates(bounds).anyMatch(EntityControlPathContext::isHazard)
   }

   private fun isBelowNetherRoof(vehicle: Entity): Boolean {
      return ClientAccess.collectiveMinecraft.level!!.dimension() === Level.NETHER && vehicle.getY() < 128.0
   }

   private fun relativeBounds(vehicle: Entity): AABB {
      return vehicle.getBoundingBox().minmax(ClientAccess.collectiveMinecraft.player!!.getBoundingBox()).move(vehicle.position().reverse())
   }

   private fun isAtGoal(goal: Goal, vehicle: Entity, position: Vec3, destination: Vec3): Boolean {
      if (goal is GoalXZ) {
         return Math.hypot(destination.x - position.x, destination.z - position.z) < 1.0
      }
      if (goal is GoalYLevel) {
         return Math.abs(destination.y - position.y) < 0.5
      }
      return position.distanceTo(destination) < 1.0 || goal.isInGoal(vehicle.blockPosition())
   }

   private fun target(goal: Goal, position: Vec3): Vec3? {
      if (goal is IGoalRenderPos) {
         return goal.getGoalPos().getBottomCenter()
      }
      if (goal is GoalXZ) {
         val x = goal.getX()
         val z = goal.getZ()
         val surface = !ClientAccess.collectiveMinecraft.level!!.dimensionType().hasCeiling() && ClientAccess.collectiveMinecraft.level!!.getChunkSource().hasChunk(x shr 4, z shr 4)
         return Vec3(x + 0.5, if (surface) ClientAccess.collectiveMinecraft.level!!.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z).toDouble() else position.y, z + 0.5)
      }
      if (goal is GoalYLevel) {
         return Vec3(position.x, goal.level.toDouble(), position.z)
      }
      if (goal is GoalComposite) {
         var nearest: Vec3? = null
         for (child in goal.goals()) {
            val candidate = target(child, position)
            if (candidate != null && (nearest == null || candidate.distanceToSqr(position) < nearest.distanceToSqr(position))) {
               nearest = candidate
            }
         }
         return nearest
      }
      return null
   }

   private fun getGoal(): Goal? {
      val process = this.baritone.getCustomGoalProcess()
      return if (process.isActive()) process.getGoal() else null
   }

   private fun getVehicle(): Entity? {
      val player = ClientAccess.collectiveMinecraft.player ?: return null
      if (ClientAccess.collectiveMinecraft.level == null || !this.module.isEntityPathfindingEnabled()) {
         return null
      }
      val vehicle = player.getVehicle()
      return if (vehicle != null && vehicle.getControllingPassenger() === player) vehicle else null
   }
}
