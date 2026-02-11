package org.notionsmp.navigate;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class PathfindingTask extends BukkitRunnable {

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
        AStarPathfinder pathfinder = navigation.getPathfinder();
        List<Location> currentPath = navigation.getCurrentPathInternal();

        if (!player.isOnline()) {
            plugin.getNavigationService().stop(player.getUniqueId());
            return;
        }

        if (player.getLocation().distanceSquared(targetLocation) < 4) {
            player.sendMessage("You have reached your destination!");
            plugin.getNavigationService().stop(player.getUniqueId());
            return;
        }

        int cx = player.getLocation().getChunk().getX();
        int cz = player.getLocation().getChunk().getZ();

        if (currentPath != null && !currentPath.isEmpty()
                && cx == navigation.getLastChunkX()
                && cz == navigation.getLastChunkZ()) {
            displayPathParticles(player, currentPath);
            return;
        }

        navigation.setLastChunk(cx, cz);

        Location playerBlockLoc = player.getLocation().getBlock().getLocation();
        Location targetBlockLoc = targetLocation.getBlock().getLocation();

        plugin.foliaLib.getScheduler().runAsync(task -> {
            List<Location> foundPath = pathfinder.findPath(playerBlockLoc, targetBlockLoc);
            plugin.foliaLib.getScheduler().runNextTick(nextTask -> {
                if (!player.isOnline()) {
                    plugin.getNavigationService().stop(player.getUniqueId());
                    return;
                }
                navigation.setCurrentPath(foundPath);
                displayPathParticles(player, foundPath);
            });
        });
    }

    private void displayPathParticles(Player player, List<Location> currentPath) {
        if (currentPath == null || currentPath.isEmpty()) {
            return;
        }

        Particle.DustOptions dustOptions = new Particle.DustOptions(
                Color.fromRGB(220, 20, 60),
                1.0f
        );

        for (Location pathBlock : currentPath) {
            player.spawnParticle(
                    Particle.DUST,
                    pathBlock.clone().add(0.5, 0.5, 0.5),
                    3,
                    0,
                    0,
                    0,
                    0,
                    dustOptions
            );
        }
    }
}
