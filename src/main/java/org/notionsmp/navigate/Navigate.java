package org.notionsmp.navigate;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.notionsmp.navigate.util.NavigatePlaceholder;

import java.util.Map;
import java.util.UUID;

/**
 * Main plugin entry point and access to public API.
 */
public final class Navigate extends JavaPlugin {

    private static Navigate instance;
    private NavigationService navigationService;
    private Messages messages;

    @Override
    public void onEnable() {
        instance = this;
        navigationService = new NavigationService(this);
        saveDefaultConfig();
        messages = new Messages(this);
        messages.load();

        NavigateCommands commands = new NavigateCommands(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands.build(), "Show the way to a position"));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI").isEnabled()) {
            new NavigatePlaceholder().register();
        }
    }

    @Override
    public void onDisable() {
        navigationService.stopAll();
    }

    /**
     * Returns the navigation service API.
     *
     * @return navigation service
     */
    public NavigationService getNavigationService() {
        return navigationService;
    }

    public Messages getMessages() {
        return messages;
    }

    void reloadAll() {
        reloadConfig();
        messages.load();
    }

    /**
     * Returns a read-only map of running navigations keyed by player UUID.
     *
     * @return unmodifiable map of running navigations
     */
    public Map<UUID, Navigation> getActiveNavigations() {
        return navigationService.getRunningMap();
    }

    /**
     * Returns the active plugin instance.
     *
     * @return plugin instance
     */
    public static Navigate getInstance() { return instance; }
}
