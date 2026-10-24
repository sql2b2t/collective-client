package me.collective.module.render;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Client-thread, bounded scan shared only by the block-search modules. */
final class BlockScan {
    static final int POSITIONS_PER_TICK = 8192;
    private static final int MAX_RESULTS = 1000;

    private ClientLevel world;
    private BlockPos center;
    private int radius;
    private Set<Block> targets = Set.of();
    private int cursor;
    private int volume;
    private boolean scanning;
    private long completedAt;
    private final List<BlockPos> results = new ArrayList<>();

    void clear() {
        world = null;
        center = null;
        targets = Set.of();
        results.clear();
        scanning = false;
        completedAt = 0L;
    }

    List<BlockPos> tick(ClientLevel currentWorld, BlockPos playerPos, int range, Set<Block> selected, long now) {
        boolean changed = world != currentWorld || radius != range || !targets.equals(selected);
        // A moving player must not restart a large scan every tick. Finish the
        // captured cube, but discard it immediately on a teleport outside it.
        boolean movedAway = center != null && (Math.abs((long) playerPos.getX() - center.getX()) > range
            || Math.abs((long) playerPos.getY() - center.getY()) > range
            || Math.abs((long) playerPos.getZ() - center.getZ()) > range);
        if (changed || movedAway || (!scanning && now - completedAt >= 500L)) {
            world = currentWorld;
            center = playerPos.immutable();
            radius = range;
            targets = Set.copyOf(selected);
            cursor = 0;
            int width = radius * 2 + 1;
            volume = width * width * width;
            results.clear();
            scanning = !targets.isEmpty();
            if (!scanning) completedAt = now;
        }
        if (!scanning) return results;

        int width = radius * 2 + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int work = 0; work < POSITIONS_PER_TICK && cursor < volume; work++) {
            int index = cursor++;
            int z = index % width - radius;
            int y = index / width % width - radius;
            int x = index / (width * width) - radius;
            pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
            // Never ask the world for block state in an unloaded chunk.
            if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (targets.contains(world.getBlockState(pos).getBlock())) {
                results.add(pos.immutable());
                if (results.size() >= MAX_RESULTS) break;
            }
        }
        if (cursor >= volume || results.size() >= MAX_RESULTS) {
            scanning = false;
            completedAt = now;
        }
        return results;
    }
}