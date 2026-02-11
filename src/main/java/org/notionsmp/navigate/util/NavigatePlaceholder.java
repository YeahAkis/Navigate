package org.notionsmp.navigate.util;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.notionsmp.navigate.AStarPathfinder;
import org.notionsmp.navigate.Navigate;
import org.notionsmp.navigate.Navigation;

public class NavigatePlaceholder extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "navigate";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Akis (discord: yeahakis)";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "No player";

        Navigation navigation = Navigate.getInstance().getNavigationService().getNavigation(player.getUniqueId());
        if (navigation == null || navigation.getTargetLocation() == null) return "No target";

        Location target = navigation.getTargetLocation();

        return switch (params.toLowerCase()) {
            case "direction" -> AStarPathfinder.getDirection(player.getLocation(), target);
            case "yaw" -> String.format("%.1f", AStarPathfinder.getYaw(player.getLocation(), target));
            case "pitch" -> String.format("%.1f", AStarPathfinder.getPitch(player.getLocation(), target));
            case "distance2d" -> String.valueOf(AStarPathfinder.getDistance2D(player.getLocation(), target));
            case "distance3d" -> String.valueOf(AStarPathfinder.getDistance3D(player.getLocation(), target));
            case "x" -> String.valueOf(target.getBlockX());
            case "y" -> String.valueOf(target.getBlockY());
            case "z" -> String.valueOf(target.getBlockZ());
            case "world" -> target.getWorld().getName();
            default -> null;
        };
    }

    @Override
    public boolean persist() {
        return true;
    }
}