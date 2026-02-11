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

public final class NavigationService {
    private final Navigate plugin;
    private final Map<UUID, Navigation> navigations;

    NavigationService(Navigate plugin) {
        this.plugin = plugin;
        this.navigations = new HashMap<>();
    }

    public Navigation start(Player player, Location targetLocation, boolean safeMode, Set<Material> allowedMaterials) {
        stop(player.getUniqueId());
        Navigation navigation = new Navigation(plugin, player, targetLocation, safeMode, allowedMaterials);
        navigations.put(player.getUniqueId(), navigation);
        navigation.start();
        return navigation;
    }

    public boolean stop(Player player) {
        return stop(player.getUniqueId());
    }

    public boolean stop(UUID playerId) {
        Navigation navigation = navigations.remove(playerId);
        if (navigation == null) {
            return false;
        }
        navigation.stop();
        return true;
    }

    public void stopAll() {
        for (Navigation navigation : navigations.values()) {
            navigation.stop();
        }
        navigations.clear();
    }

    public Navigation getNavigation(UUID playerId) {
        return navigations.get(playerId);
    }

    public Collection<Navigation> getRunning() {
        return Collections.unmodifiableCollection(navigations.values());
    }

    public Map<UUID, Navigation> getRunningMap() {
        return Collections.unmodifiableMap(navigations);
    }
}