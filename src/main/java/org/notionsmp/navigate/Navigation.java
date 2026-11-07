package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a single running navigation session for a player.
 */
public class Navigation {
    private final Navigate plugin;
    private final Player player;
    private final Location targetLocation;
    private final boolean safeMode;
    private final Set<Material> allowedMaterials;
    private final AStarPathfinder pathfinder;
    private final PathfindingTask task;

    private List<Location> currentPath;
    private int lastChunkX;
    private int lastChunkZ;
    private boolean searching;
    private long retryAt;

    Navigation(Navigate plugin, Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials) {
        this.plugin = plugin;
        this.player = player;
        this.targetLocation = targetLocation.clone();
        this.safeMode = safeMode;
        this.allowedMaterials = normalizeMaterials(allowedMaterials);
        this.pathfinder = new AStarPathfinder(safeMode, this.allowedMaterials);
        this.lastChunkX = player.getLocation().getChunk().getX();
        this.lastChunkZ = player.getLocation().getChunk().getZ();
        this.task = new PathfindingTask(plugin, this);
    }

    /**
     * Returns the player associated with this navigation.
     *
     * @return the navigating player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the target location for this navigation.
     *
     * @return target location (cloned)
     */
    public Location getTargetLocation() {
        return targetLocation.clone();
    }

    /**
     * Indicates whether safe mode is enabled for this navigation.
     *
     * @return true if safe mode is enabled
     */
    public boolean isSafeMode() {
        return safeMode;
    }

    /**
     * Returns the allowed ground materials for this navigation, if any.
     *
     * @return immutable set of allowed materials, or null if not restricted
     */
    public Set<Material> getAllowedMaterials() {
        return allowedMaterials == null ? null : Set.copyOf(allowedMaterials);
    }

    /**
     * Returns the most recently computed path for this navigation.
     *
     * @return immutable list of path points, or empty if none computed yet
     */
    public List<Location> getCurrentPath() {
        return currentPath == null ? List.of() : List.copyOf(currentPath);
    }

    public int getLastChunkX() {
        return lastChunkX;
    }

    public int getLastChunkZ() {
        return lastChunkZ;
    }

    AStarPathfinder getPathfinder() {
        return pathfinder;
    }

    void setCurrentPath(List<Location> currentPath) {
        this.currentPath = currentPath;
    }

    List<Location> getCurrentPathInternal() {
        return currentPath;
    }

    void setLastChunk(int lastChunkX, int lastChunkZ) {
        this.lastChunkX = lastChunkX;
        this.lastChunkZ = lastChunkZ;
    }

    boolean isSearching() {
        return searching;
    }

    void setSearching(boolean searching) {
        this.searching = searching;
    }

    long getRetryAt() {
        return retryAt;
    }

    void setRetryAt(long retryAt) {
        this.retryAt = retryAt;
    }

    void start() {
        task.runTaskTimer(plugin, 0L, 10L);
    }

    void stop() {
        task.cancel();
    }

    private static Set<Material> normalizeMaterials(Set<Material> allowedMaterials) {
        if (allowedMaterials == null || allowedMaterials.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(allowedMaterials);
    }
}