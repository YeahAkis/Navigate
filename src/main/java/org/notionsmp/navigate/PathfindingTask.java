package org.notionsmp.navigate;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;

public class PathfindingTask extends BukkitRunnable {

    private final Player player;
    private final Location targetLocation;
    private final AStarPathfinder pathfinder;

    private List<Location> currentPath;
    private int lastChunkX;
    private int lastChunkZ;

    public PathfindingTask(Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials) {
        this.player = player;
        this.targetLocation = targetLocation;
        this.pathfinder = new AStarPathfinder(safeMode, allowedMaterials);

        this.lastChunkX = player.getLocation().getChunk().getX();
        this.lastChunkZ = player.getLocation().getChunk().getZ();
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancelNavigation();
            return;
        }

        if (player.getLocation().distanceSquared(targetLocation) < 4) {
            player.sendMessage("You have reached your destination!");
            cancelNavigation();
            return;
        }

        int cx = player.getLocation().getChunk().getX();
        int cz = player.getLocation().getChunk().getZ();

        if (currentPath != null && !currentPath.isEmpty() && cx == lastChunkX && cz == lastChunkZ) {
            displayPathParticles();
            return;
        }

        lastChunkX = cx;
        lastChunkZ = cz;

        Location playerBlockLoc = player.getLocation().getBlock().getLocation();
        Location targetBlockLoc = targetLocation.getBlock().getLocation();

        Navigate.getInstance().foliaLib.getScheduler().runAsync(task -> {
            List<Location> foundPath = pathfinder.findPath(playerBlockLoc, targetBlockLoc);
            Navigate.getInstance().foliaLib.getScheduler().runNextTick(nextTask -> {
                if (!player.isOnline()) {
                    cancelNavigation();
                    return;
                }
                currentPath = foundPath;
                displayPathParticles();
            });
        });
    }

    private void displayPathParticles() {
        if (currentPath == null || currentPath.isEmpty()) return;

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

    private void cancelNavigation() {
        this.cancel();
        Navigate.getInstance().getActiveNavigations().remove(player.getUniqueId());
    }

    public Location getTargetLocation() {
        return targetLocation;
    }
}
