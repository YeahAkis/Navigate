package org.notionsmp.navigate;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;

final class WorldSnapshot {

    private final Map<Long, ChunkSnapshot> chunks = new HashMap<>();
    private final World world;
    private final int minY;
    private final int maxY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    private WorldSnapshot(World world, int minX, int maxX, int minZ, int maxZ) {
        this.world = world;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    static WorldSnapshot capture(World world, int minX, int maxX, int minZ, int maxZ) {
        WorldSnapshot snapshot = new WorldSnapshot(world, minX, maxX, minZ, maxZ);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    snapshot.chunks.put(key(cx, cz), chunk.getChunkSnapshot(false, false, false));
                }
            }
        }
        return snapshot;
    }

    World getWorld() {
        return world;
    }

    int getMinY() {
        return minY;
    }

    boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    Material get(int x, int y, int z) {
        if (y >= maxY) {
            return Material.AIR;
        }
        if (y < minY) {
            return Material.BARRIER;
        }
        ChunkSnapshot chunk = chunks.get(key(x >> 4, z >> 4));
        if (chunk == null) {
            return Material.BARRIER;
        }
        return chunk.getBlockType(x & 15, y, z & 15);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
