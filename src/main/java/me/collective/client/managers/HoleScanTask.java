package me.collective.client.managers;

import me.collective.client.utils.world.HoleInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.phys.AABB;

public final class HoleScanTask implements Runnable {
   private static final MutableBlockPos scanCursor;
   private final HoleManager holeManager;

   private void scanNearbyHoles() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null || minecraft.player == null || minecraft.level == null) {
         return;
      }

      ArrayList<HoleInfo> holes = new ArrayList<>();
      HashSet<Long> coveredPositions = new HashSet<>();
      LocalPlayer player = minecraft.player;
      BlockPos origin = player.blockPosition();
      for (int xOffset = -16; xOffset < 16; xOffset++) {
         for (int yOffset = -16; yOffset < 16; yOffset++) {
            for (int zOffset = -16; zOffset < 16; zOffset++) {
               MutableBlockPos cursor = scanCursor;
               int x = origin.getX() + xOffset;
               int y = origin.getY() + yOffset;
               int z = origin.getZ() + zOffset;
               cursor.set(x, y, z);
               long positionKey = scanCursor.asLong();
               if (!coveredPositions.contains(positionKey)) {
                  MutableBlockPos cursorForClassification = scanCursor;
                  HoleInfo hole = HoleManager.classifyHole(cursorForClassification, null);
                  if (hole != null) {
                     holes.add(hole);
                     AABB bounds = hole.bounds();
                     this.addCoveredPositions(coveredPositions, bounds);
                  }
               }
            }
         }
      }

      this.holeManager.setHoles(holes);
   }

   public HoleScanTask(HoleManager holeManager) {
      this.holeManager = holeManager;
   }

   static {
      scanCursor = new MutableBlockPos();
   }

   @Override
   public void run() {
      try {
         this.scanNearbyHoles();
      } catch (Throwable ignored) {
      }
   }

   private void addCoveredPositions(Set<Long> positions, AABB bounds) {
      for (int x = (int)bounds.minX; x < (int)bounds.maxX; x++) {
         for (int z = (int)bounds.minZ; z < (int)bounds.maxZ; z++) {
            int y = (int)bounds.minY;
            long floorPosition = BlockPos.asLong(x, y, z);
            positions.add(floorPosition);
            positions.add(BlockPos.asLong(x, (int)bounds.minY + 1, z));
            int topY = (int)bounds.minY;
            topY += 2;
            positions.add(BlockPos.asLong(x, topY, z));
         }
      }
   }
}
