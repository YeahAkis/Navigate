package org.notionsmp.navigate;

import co.aikar.commands.PaperCommandManager;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.notionsmp.navigate.util.NavigatePlaceholder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Navigate extends JavaPlugin {

    private static Navigate instance;
    private Map<UUID, PathfindingTask> activeNavigations;
    public FoliaLib foliaLib;

    @Override
    public void onEnable() {
        instance = this;
        foliaLib = new FoliaLib(this);
        activeNavigations = new HashMap<>();
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.registerCommand(new NavigateCommand());

        saveDefaultConfig();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new NavigatePlaceholder().register();
        }
    }

    @Override
    public void onDisable() {
        for (PathfindingTask task : activeNavigations.values()) {
            task.cancel();
        }
        activeNavigations.clear();
    }

    public Map<UUID, PathfindingTask> getActiveNavigations() {
        return activeNavigations;
    }

    public static Navigate getInstance() { return instance; }
}
