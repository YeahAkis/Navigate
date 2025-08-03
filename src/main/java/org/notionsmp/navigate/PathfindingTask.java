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
    private List<Location> currentPath;
    private final AStarPathfinder pathfinder;
    private boolean pathFailed = false;

    public PathfindingTask(Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials) {
        this.player = player;
        this.targetLocation = targetLocation;
        this.pathfinder = new AStarPathfinder(safeMode, allowedMaterials);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancelNavigation();
            return;
        }

        if (pathFailed) {
            if (Navigate.getInstance().getActiveNavigations().get(player.getUniqueId()) == null) {
                this.cancel();
            }
            return;
        }

        if (player.getLocation().distanceSquared(targetLocation) < 4) {
            player.sendMessage("You have reached your destination!");
            cancelNavigation();
            return;
        }

        Navigate.getInstance().foliaLib.getScheduler().runAsync(task -> {
            Location playerBlockLoc = player.getLocation().getBlock().getLocation();
            Location targetBlockLoc = targetLocation.getBlock().getLocation();
            List<Location> foundPath = pathfinder.findPath(playerBlockLoc, targetBlockLoc);
            Navigate.getInstance().foliaLib.getScheduler().runNextTick(nextTask -> {
                if (!player.isOnline()) {
                    cancelNavigation();
                    return;
                }
                if (foundPath.isEmpty()) {
                    if (!pathFailed) {
                        player.sendMessage("Destination is inaccessible!");
                    }
                    pathFailed = true;
                    cancelNavigation();
                    return;
                }
                currentPath = foundPath;
                displayPathParticles();
            });
        });
    }

    private void displayPathParticles() {
        for (Location pathBlock : currentPath) {
            Particle.DustOptions dustOptions = new Particle.DustOptions(
                    Color.fromRGB(220, 20, 60),
                    1.0f
            );
            player.spawnParticle(Particle.DUST, pathBlock.clone().add(0.5, 0.5, 0.5), 5, 0, 0, 0, 0, dustOptions);
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