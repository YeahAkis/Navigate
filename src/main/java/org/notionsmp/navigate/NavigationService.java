package org.notionsmp.navigate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public API for starting, stopping, and querying navigation sessions.
 */
public final class NavigationService {
    private final Navigate plugin;
    private final Map<UUID, Navigation> navigations;

    NavigationService(Navigate plugin) {
        this.plugin = plugin;
        this.navigations = new HashMap<>();
    }

    /**
     * Starts a navigation for the given player, replacing any existing one.
     *
     * @param player player to navigate
     * @param targetLocation destination location
     * @param safeMode whether safe mode is enabled
     * @param allowedMaterials allowed ground materials, or null for no restriction
     * @return the newly started navigation
     */
    public Navigation start(Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials) {
        return start(player, targetLocation, safeMode, allowedMaterials, false);
    }

    /**
     * Starts a navigation for the given player, replacing any existing one.
     *
     * @param player player to navigate
     * @param targetLocation destination location
     * @param safeMode whether safe mode is enabled
     * @param allowedMaterials allowed ground materials, or null for no restriction
     * @param follow whether the player is pushed along the path
     * @return the newly started navigation
     */
    public Navigation start(Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials, boolean follow) {
        stop(player.getUniqueId());
        Navigation navigation = new Navigation(plugin, player, targetLocation, safeMode, allowedMaterials, follow);
        navigations.put(player.getUniqueId(), navigation);
        navigation.start();
        return navigation;
    }

    /**
     * Stops the navigation for the given player.
     *
     * @param player player to stop navigation for
     * @return true if a navigation was running and is now stopped
     */
    public boolean stop(Player player) {
        return stop(player.getUniqueId());
    }

    /**
     * Stops the navigation for the given player UUID.
     *
     * @param playerId player UUID to stop navigation for
     * @return true if a navigation was running and is now stopped
     */
    public boolean stop(UUID playerId) {
        Navigation navigation = navigations.remove(playerId);
        if (navigation == null) {
            return false;
        }
        navigation.stop();
        return true;
    }

    /**
     * Stops all running navigations.
     */
    public void stopAll() {
        for (Navigation navigation : navigations.values()) {
            navigation.stop();
        }
        navigations.clear();
    }

    /**
     * Gets the navigation for the given player UUID.
     *
     * @param playerId player UUID to query
     * @return navigation instance, or null if none running
     */
    public Navigation getNavigation(UUID playerId) {
        return navigations.get(playerId);
    }

    /**
     * Returns a read-only collection of all running navigations.
     *
     * @return unmodifiable collection of running navigations
     */
    public Collection<Navigation> getRunning() {
        return Collections.unmodifiableCollection(navigations.values());
    }

    /**
     * Returns a read-only map of running navigations keyed by player UUID.
     *
     * @return unmodifiable map of running navigations
     */
    public Map<UUID, Navigation> getRunningMap() {
        return Collections.unmodifiableMap(navigations);
    }
}