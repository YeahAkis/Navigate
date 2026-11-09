package org.notionsmp.navigate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final List<String> BUNDLED = List.of("en_us");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Navigate plugin;
    private volatile Map<String, FileConfiguration> bundles = Map.of();
    private volatile FileConfiguration fallback = new YamlConfiguration();
    private volatile String language = DEFAULT_LANGUAGE;
    private volatile boolean perPlayer = true;

    Messages(Navigate plugin) {
        this.plugin = plugin;
    }

    void load() {
        File folder = new File(plugin.getDataFolder(), "lang");
        for (String name : BUNDLED) {
            if (!new File(folder, name + ".yml").exists()) {
                plugin.saveResource("lang/" + name + ".yml", false);
            }
        }

        Map<String, FileConfiguration> loaded = new TreeMap<>();
        File[] files = folder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                try {
                    YamlConfiguration configuration = new YamlConfiguration();
                    configuration.load(file);
                    loaded.put(name, configuration);
                } catch (IOException | InvalidConfigurationException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not read language file " + file.getName(), e);
                }
            }
        }

        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("lang/" + DEFAULT_LANGUAGE + ".yml")) {
            if (stream != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled language file", e);
        }

        this.bundles = loaded;
        this.fallback = bundled;
        this.language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
        this.perPlayer = plugin.getConfig().getBoolean("per-player-language", true);
    }

    public String text(CommandSender sender, String key) {
        return lookup(sender, key);
    }

    public String plain(CommandSender sender, String key, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(component(sender, key, resolvers));
    }

    public Component component(CommandSender sender, String key, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(lookup(sender, key), resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(component(sender, key, resolvers));
    }

    private String lookup(CommandSender sender, String key) {
        FileConfiguration preferred = bundleFor(sender);
        String value = preferred.getString(key);
        if (value == null) {
            FileConfiguration configured = bundles.get(language);
            value = configured == null ? null : configured.getString(key);
        }
        if (value == null) {
            value = fallback.getString(key);
        }
        return value == null ? key : value;
    }

    private FileConfiguration bundleFor(CommandSender sender) {
        Map<String, FileConfiguration> current = bundles;
        if (perPlayer && sender instanceof Player player) {
            Locale locale = player.locale();
            String language = locale.getLanguage().toLowerCase(Locale.ROOT);
            FileConfiguration exact = current.get(language + "_" + locale.getCountry().toLowerCase(Locale.ROOT));
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, FileConfiguration> entry : current.entrySet()) {
                if (entry.getKey().startsWith(language + "_")) {
                    return entry.getValue();
                }
            }
        }
        FileConfiguration configured = current.get(language);
        return configured != null ? configured : fallback;
    }
}
