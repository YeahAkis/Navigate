package org.notionsmp.navigate;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final boolean follow;
    private final PathfindingTask task;
    private final FollowTask followTask;
    private final ChunkCache chunkCache = new ChunkCache();
    private final ParticleSettings particles;
    private final boolean arrivalMessage;
    private ScheduledTask pathTask;
    private ScheduledTask moveTask;

    private volatile List<Location> currentPath;
    private volatile int lastChunkX = Integer.MIN_VALUE;
    private volatile int lastChunkZ = Integer.MIN_VALUE;
    private volatile boolean searching;
    private volatile long retryAt;

    Navigation(Navigate plugin, Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials, boolean follow) {
        this.plugin = plugin;
        this.player = player;
        this.targetLocation = targetLocation.clone();
        this.safeMode = safeMode;
        this.allowedMaterials = normalizeMaterials(allowedMaterials);
        this.pathfinder = new AStarPathfinder(safeMode, this.allowedMaterials);
        this.follow = follow;
        this.particles = ParticleSettings.load(plugin.getConfig());
        this.arrivalMessage = plugin.getConfig().getBoolean("messages.arrival", true);
        this.task = new PathfindingTask(plugin, this);
        this.followTask = follow ? new FollowTask(plugin, this) : null;
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
     * Indicates whether the player is moved along the path.
     *
     * @return true if the player is pushed along the path
     */
    public boolean isFollow() {
        return follow;
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

    ParticleSettings getParticles() {
        return particles;
    }

    boolean isArrivalMessage() {
        return arrivalMessage;
    }

    ChunkCache getChunkCache() {
        return chunkCache;
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

    void requestReplan() {
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        retryAt = 0;
    }

    void start() {
        UUID playerId = player.getUniqueId();
        pathTask = player.getScheduler().runAtFixedRate(plugin, scheduled -> task.run(), () -> plugin.getNavigationService().stop(playerId), 1L, particles.interval());
        if (followTask != null) {
            moveTask = player.getScheduler().runAtFixedRate(plugin, scheduled -> followTask.run(), null, 1L, 1L);
        }
    }

    void stop() {
        if (pathTask != null) {
            pathTask.cancel();
        }
        if (moveTask != null) {
            moveTask.cancel();
        }
        if (followTask != null) {
            followTask.release();
        }
    }

    private static Set<Material> normalizeMaterials(Set<Material> allowedMaterials) {
        if (allowedMaterials == null || allowedMaterials.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(allowedMaterials);
    }
}