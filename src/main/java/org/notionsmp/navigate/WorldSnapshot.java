package org.notionsmp.navigate;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

final class WorldSnapshot {

    static final int AIR = Material.AIR.ordinal();
    static final int BARRIER = Material.BARRIER.ordinal();
    private static final Material[] MATERIALS = Material.values();

    private final World world;
    private final int minY;
    private final int maxY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minChunkX;
    private final int minChunkZ;
    private final int chunkWidth;
    private final int sections;
    private final ChunkSnapshot[] chunks;
    private final short[][] cache;

    WorldSnapshot(World world, int minY, int maxY, int minX, int maxX, int minZ, int maxZ) {
        this.world = world;
        this.minY = minY;
        this.maxY = maxY;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.minChunkX = minX >> 4;
        this.minChunkZ = minZ >> 4;
        this.chunkWidth = (maxX >> 4) - minChunkX + 1;
        int chunkDepth = (maxZ >> 4) - minChunkZ + 1;
        this.sections = (maxY - minY + 15) >> 4;
        this.chunks = new ChunkSnapshot[chunkWidth * chunkDepth];
        this.cache = new short[chunks.length * sections][];
    }

    static WorldSnapshot capture(World world, int minX, int maxX, int minZ, int maxZ, ChunkCache chunkCache, long now) {
        WorldSnapshot snapshot = new WorldSnapshot(world, world.getMinHeight(), world.getMaxHeight(), minX, maxX, minZ, maxZ);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                ChunkSnapshot chunk = chunkCache.get(cx, cz, now);
                if (chunk == null) {
                    if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz) || !world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    Chunk loaded = world.getChunkAt(cx, cz);
                    chunk = loaded.getChunkSnapshot(false, false, false);
                    chunkCache.put(cx, cz, chunk, now);
                }
                snapshot.put(cx, cz, chunk);
            }
        }
        return snapshot;
    }

    void put(int chunkX, int chunkZ, ChunkSnapshot chunk) {
        chunks[(chunkZ - minChunkZ) * chunkWidth + (chunkX - minChunkX)] = chunk;
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
        return MATERIALS[id(x, y, z)];
    }

    int id(int x, int y, int z) {
        if (y >= maxY) {
            return AIR;
        }
        if (y < minY || x < minX || x > maxX || z < minZ || z > maxZ) {
            return BARRIER;
        }
        int chunkIndex = ((z >> 4) - minChunkZ) * chunkWidth + ((x >> 4) - minChunkX);
        ChunkSnapshot chunk = chunks[chunkIndex];
        if (chunk == null) {
            return BARRIER;
        }
        int section = chunkIndex * sections + ((y - minY) >> 4);
        short[] block = cache[section];
        if (block == null) {
            block = new short[4096];
            cache[section] = block;
        }
        int local = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        int value = block[local];
        if (value == 0) {
            value = chunk.getBlockType(x & 15, y, z & 15).ordinal() + 1;
            block[local] = (short) value;
        }
        return value - 1;
    }
}
