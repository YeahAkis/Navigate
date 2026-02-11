package org.notionsmp.navigate;

import co.aikar.commands.PaperCommandManager;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.notionsmp.navigate.util.NavigatePlaceholder;

import java.util.Map;
import java.util.UUID;

public final class Navigate extends JavaPlugin {

    private static Navigate instance;
    private NavigationService navigationService;
    public FoliaLib foliaLib;

    @Override
    public void onEnable() {
        instance = this;
        foliaLib = new FoliaLib(this);
        navigationService = new NavigationService(this);
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.registerCommand(new NavigateCommand());

        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI").isEnabled()) {
            new NavigatePlaceholder().register();
        }
    }

    @Override
    public void onDisable() {
        navigationService.stopAll();
    }

    public NavigationService getNavigationService() {
        return navigationService;
    }

    public Map<UUID, Navigation> getActiveNavigations() {
        return navigationService.getRunningMap();
    }

    public static Navigate getInstance() { return instance; }
}
