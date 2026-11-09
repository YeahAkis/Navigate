package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public class PathfindingTask implements Runnable {

    private static final int SEARCH_RADIUS = 96;
    private static final int SEARCH_MARGIN = 24;
    private static final long RETRY_DELAY_MS = 3000;

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
            if (navigation.isArrivalMessage()) {
                plugin.getMessages().send(player, "navigation.reached");
            }
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

        WorldSnapshot snapshot = WorldSnapshot.capture(world, minX, maxX, minZ, maxZ, navigation.getChunkCache(), System.currentTimeMillis());
        AStarPathfinder pathfinder = navigation.getPathfinder();
        Location playerBlockLoc = new Location(world, px, playerLocation.getBlockY(), pz);
        Location targetBlockLoc = new Location(world, tx, targetLocation.getBlockY(), tz);

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            List<Location> foundPath;
            try {
                foundPath = pathfinder.findPath(snapshot, playerBlockLoc, targetBlockLoc);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Pathfinding failed: " + e);
                foundPath = List.of();
            }
            List<Location> result = foundPath;
            if (!plugin.isEnabled()) {
                return;
            }
            player.getScheduler().run(plugin, nextTask -> {
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
            }, null);
        });
    }

    private void displayPathParticles(Player player, List<Location> path) {
        ParticleSettings settings = navigation.getParticles();
        if (!settings.enabled()) {
            return;
        }
        Location playerLocation = player.getLocation();
        double ox = playerLocation.getX();
        double oy = playerLocation.getY();
        double oz = playerLocation.getZ();
        for (Location block : path) {
            double dx = block.getX() - ox;
            double dy = block.getY() - oy;
            double dz = block.getZ() - oz;
            if (dx * dx + dy * dy + dz * dz > settings.rangeSquared()) {
                continue;
            }
            player.spawnParticle(settings.particle(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5, settings.amount(), 0, 0, 0, 0, settings.data());
        }
    }
}
