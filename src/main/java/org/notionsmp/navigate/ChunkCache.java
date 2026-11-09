package org.notionsmp.navigate;

import org.bukkit.ChunkSnapshot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class ChunkCache {

    private static final long MAX_AGE_MS = 4000;

    private record Entry(ChunkSnapshot snapshot, long time) {}

    private final Map<Long, Entry> entries = new HashMap<>();

    ChunkSnapshot get(int chunkX, int chunkZ, long now) {
        Entry entry = entries.get(key(chunkX, chunkZ));
        if (entry == null || now - entry.time > MAX_AGE_MS) {
            return null;
        }
        return entry.snapshot;
    }

    void put(int chunkX, int chunkZ, ChunkSnapshot snapshot, long now) {
        if (entries.size() > 512) {
            Iterator<Entry> iterator = entries.values().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().time > MAX_AGE_MS) {
                    iterator.remove();
                }
            }
        }
        entries.put(key(chunkX, chunkZ), new Entry(snapshot, now));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
