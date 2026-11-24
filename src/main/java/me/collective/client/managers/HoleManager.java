package me.collective.client.managers;

import me.collective.client.utils.entity.PlayerStateUtils;
import me.collective.client.utils.world.BlockStateUtils;
import me.collective.client.utils.world.HoleInfo;
import me.collective.client.managers.HoleScanTask;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.EventHandler;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.settings.options.DimensionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class HoleManager implements ClientAccess {

   private static final Direction[] horizontalDirections;

   private static final ScheduledExecutorService scanExecutor;

   private static final MutableBlockPos classificationCursor;

   private volatile List<HoleInfo> holes = new ArrayList<>();

   private boolean inHole;

   public boolean intersectsHole(BlockPos position) {
      return this.intersectsHole(new AABB(position));
   }

   public boolean intersectsHole(AABB bounds) {
      return this.holes.stream().anyMatch(
         hole -> {
            AABB holeBounds = hole.bounds();
            return holeBounds.intersects(bounds);
         }
      );
   }

   public List getHoles() {
      return this.holes;
   }

   public HoleInfo findIntersectingHole(BlockPos position) {
      Stream<HoleInfo> matchingHoles = this.holes.stream().filter(
            hole -> {
               AABB holeBounds = hole.bounds();
               return holeBounds.intersects(new AABB(position));
            }
      );
      Optional<HoleInfo> firstMatch = matchingHoles.findFirst();
      return firstMatch.orElse(null);
   }

   public boolean isInHole() {
      return this.inHole;
   }

   public HoleManager() {
      ClientAccess.eventBus.subscribe(this);
      ScheduledExecutorService executor = scanExecutor;
      HoleScanTask scanTask = new HoleScanTask(this);
      TimeUnit scanTimeUnit = TimeUnit.MILLISECONDS;
      executor.scheduleAtFixedRate(scanTask, 0L, 50L, scanTimeUnit);
   }

   static {
      Plane horizontalPlane = Plane.HORIZONTAL;
      horizontalDirections = horizontalPlane.stream().toArray((IntFunction<Direction[]>)Direction[]::new);
      scanExecutor = Executors.newSingleThreadScheduledExecutor();
      classificationCursor = new MutableBlockPos();
   }

   static HoleInfo classifyHole(
      BlockPos position, Direction fromDirection) {
      classificationCursor.set(position);
      MutableBlockPos cursorSnapshot = classificationCursor;
      if (BlockStateUtils.isBlockingExceptCarpet(cursorSnapshot)) {
         return null;
      }
      Direction down = Direction.DOWN;
      classificationCursor.move(down);
      MutableBlockPos floorPosition = classificationCursor;
      BlockState floorState = ClientAccess.collectiveMinecraft.level.getBlockState(floorPosition);
      if (!BlockStateUtils.isBlocking(floorState)) {
         return null;
      }
      if (floorState.getBlock() instanceof Portal) {
         return null;
      }
      if (floorState.getBlock() instanceof LiquidBlock) {
         return null;
      }
      cursorSnapshot = classificationCursor;
      Direction up = Direction.UP;
      cursorSnapshot.move(up, 2);
      cursorSnapshot = classificationCursor;
      if (BlockStateUtils.isBlocking(cursorSnapshot)) {
         return null;
      }
      Direction roofDirection = Direction.UP;
      MutableBlockPos roofCursor = classificationCursor;
      cursorSnapshot = roofCursor.move(roofDirection);
      boolean trapped = BlockStateUtils.isBlocking(cursorSnapshot);
      HoleInfo.Safety safety = BlockStateUtils.isBlastResistantUnbreakable(
            floorState)
         ? HoleInfo.Safety.SAFE
         : HoleInfo.Safety.UNSAFE;
      Direction doubleDirection = null;
      for (Direction direction : horizontalDirections) {
         if (fromDirection == direction.getOpposite()) {
            continue;
         }
         MutableBlockPos sideResetCursor = classificationCursor;
         cursorSnapshot = sideResetCursor.set(position);
         cursorSnapshot.move(direction);
         MutableBlockPos sidePosition = classificationCursor;
         BlockState sideState = ClientAccess.collectiveMinecraft.level.getBlockState(sidePosition);
         cursorSnapshot = classificationCursor;
         if (!BlockStateUtils.isBlocking(cursorSnapshot)) {
            if (doubleDirection != null || fromDirection != null) {
               return null;
            }
            BlockPos adjacentBlock = position.relative(direction);
            HoleInfo adjacentHole = classifyHole(adjacentBlock, direction);
            if (adjacentHole == null) {
               return null;
            }
            if (!adjacentHole.trapped()) {
               trapped = false;
            }
            if (safety == HoleInfo.Safety.SAFE) {
               safety = adjacentHole.safety();
            }
            doubleDirection = direction;
            continue;
         }
         Block anchorBlock = Blocks.RESPAWN_ANCHOR;
         if (sideState.is(anchorBlock)) {
            DimensionType anchorPolicy = ClientContext.worldStateManager.getDimension();
            if (!anchorPolicy.isNether()) {
               return null;
            }
         }
         if (BlockStateUtils.isWeakOrInstantlyBreakable(sideState)) {
            return null;
         }
         if (BlockStateUtils.isBlastResistantBreakable(sideState)) {
            safety = HoleInfo.Safety.UNSAFE;
         }
      }
      return new HoleInfo(safety, position.immutable(), createHoleBounds(position, doubleDirection), trapped);
   }

   @EventHandler
   private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
      BlockPos roundedPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
      this.inHole = this.intersectsHole(roundedPosition);
   }

   public void setHoles(List updatedHoles) {
      this.holes = updatedHoles;
   }

   private static AABB createHoleBounds(BlockPos position, Direction face) {
      if (face == null) return new AABB(position);
      AABB box = new AABB(position);
      Vec3i xVector = face.getUnitVec3i();
      double x = xVector.getX();
      Vec3i zVector = face.getUnitVec3i();
      double z = zVector.getZ();
      return box.expandTowards(x, 0.0, z);
   }
}
