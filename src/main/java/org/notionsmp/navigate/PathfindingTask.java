package org.notionsmp.navigate;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class PathfindingTask extends BukkitRunnable {

    private static final int SEARCH_RADIUS = 96;
    private static final int SEARCH_MARGIN = 24;
    private static final long RETRY_DELAY_MS = 3000;
    private static final double PARTICLE_RANGE_SQ = 64 * 64;
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.0f);

    private final Navigate plugin;
    private final Navigation navigation;

    public PathfindingTask(Navigate plugin, Navigation navigation) {
        this.plugin = plugin;
        this.navigation = navigation;
    }

    @Override
    public void run() {
        Player player = navigation.getPlayer();
        Location targetLocation = navigation.getTargetLocation();
        List<Location> currentPath = navigation.getCurrentPathInternal();

        if (!player.isOnline()) {
            plugin.getNavigationService().stop(player.getUniqueId());
            return;
        }

        Location playerLocation = player.getLocation();
        World world = playerLocation.getWorld();
        if (!world.equals(targetLocation.getWorld())) {
            plugin.getNavigationService().stop(player.getUniqueId());
            return;
        }

        if (playerLocation.distanceSquared(targetLocation) < 4) {
            player.sendMessage("You have reached your destination!");
            plugin.getNavigationService().stop(player.getUniqueId());
            return;
        }

        boolean hasPath = currentPath != null && !currentPath.isEmpty();
        if (hasPath) {
            displayPathParticles(player, currentPath);
        }

        if (navigation.isSearching()) {
            return;
        }

        int cx = playerLocation.getBlockX() >> 4;
        int cz = playerLocation.getBlockZ() >> 4;
        boolean movedChunk = cx != navigation.getLastChunkX() || cz != navigation.getLastChunkZ();

        if (hasPath && !movedChunk) {
            return;
        }
        if (!hasPath && !movedChunk && System.currentTimeMillis() < navigation.getRetryAt()) {
            return;
        }

        navigation.setLastChunk(cx, cz);
        navigation.setSearching(true);

        int px = playerLocation.getBlockX();
        int pz = playerLocation.getBlockZ();
        int tx = targetLocation.getBlockX();
        int tz = targetLocation.getBlockZ();
        int minX = Math.max(Math.min(px, tx) - SEARCH_MARGIN, px - SEARCH_RADIUS);
        int maxX = Math.min(Math.max(px, tx) + SEARCH_MARGIN, px + SEARCH_RADIUS);
        int minZ = Math.max(Math.min(pz, tz) - SEARCH_MARGIN, pz - SEARCH_RADIUS);
        int maxZ = Math.min(Math.max(pz, tz) + SEARCH_MARGIN, pz + SEARCH_RADIUS);

        WorldSnapshot snapshot = WorldSnapshot.capture(world, minX, maxX, minZ, maxZ);
        AStarPathfinder pathfinder = navigation.getPathfinder();
        Location playerBlockLoc = new Location(world, px, playerLocation.getBlockY(), pz);
        Location targetBlockLoc = new Location(world, tx, targetLocation.getBlockY(), tz);

        plugin.foliaLib.getScheduler().runAsync(task -> {
            List<Location> foundPath;
            try {
                foundPath = pathfinder.findPath(snapshot, playerBlockLoc, targetBlockLoc);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Pathfinding failed: " + e);
                foundPath = List.of();
            }
            List<Location> result = foundPath;
            plugin.foliaLib.getScheduler().runNextTick(nextTask -> {
                navigation.setSearching(false);
                if (!player.isOnline()) {
                    plugin.getNavigationService().stop(player.getUniqueId());
                    return;
                }
                navigation.setCurrentPath(result);
                if (result.isEmpty()) {
                    navigation.setRetryAt(System.currentTimeMillis() + RETRY_DELAY_MS);
                    return;
                }
                displayPathParticles(player, result);
            });
        });
    }

    private void displayPathParticles(Player player, List<Location> path) {
        Location playerLocation = player.getLocation();
        double ox = playerLocation.getX();
        double oy = playerLocation.getY();
        double oz = playerLocation.getZ();
        for (Location block : path) {
            double dx = block.getX() - ox;
            double dy = block.getY() - oy;
            double dz = block.getZ() - oz;
            if (dx * dx + dy * dy + dz * dz > PARTICLE_RANGE_SQ) {
                continue;
            }
            player.spawnParticle(Particle.DUST, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, 3, 0, 0, 0, 0, DUST);
        }
    }
}
