package org.notionsmp.navigate;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CommandAlias("navigate")
@CommandPermission("navigate.use")
public class NavigateCommand extends BaseCommand {
    MiniMessage miniMessage = MiniMessage.miniMessage();

    @Subcommand("start")
    @Syntax("[x] [y] [z] (safeMode) (follow) [materials]")
    @CommandCompletion("@nothing @nothing @nothing true|false|follow follow|@nothing")
    @Description("Start navigating to target location")
    public void onStart(Player player, int x, int y, int z, @Optional String[] args) {
        Options options = parseOptions(args);

        if (options.follow() && !player.hasPermission("navigate.follow")) {
            player.sendMessage("You do not have permission to be moved along the path.");
            return;
        }

        begin(player, player, x, y, z, options);
    }

    @Subcommand("stop")
    @Description("Stop navigating")
    public void onStop(Player player) {
        if (Navigate.getInstance().getNavigationService().stop(player)) {
            player.sendMessage("Navigation stopped.");
        } else {
            player.sendMessage("You are not currently navigating.");
        }
    }

    @Subcommand("startfor")
    @CommandPermission("navigate.use.others")
    @Syntax("<player> <x> <y> <z> (safeMode) (follow) [materials]")
    @CommandCompletion("@players @nothing @nothing @nothing true|false|follow follow|@nothing")
    @Description("Start navigating player to target location")
    public void onStartFor(CommandSender sender, String playerName, int x, int y, int z, @Optional String[] args) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage("Player not found.");
            return;
        }

        begin(sender, player, x, y, z, parseOptions(args));
    }

    @Subcommand("stopfor")
    @CommandPermission("navigate.use.others")
    @Syntax("<player>")
    @CommandCompletion("@players")
    @Description("Stop navigating player")
    public void onStopFor(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage("Player not found.");
            return;
        }

        if (Navigate.getInstance().getNavigationService().stop(player)) {
            sender.sendMessage("Navigation stopped for " + player.getName() + ".");
        } else {
            sender.sendMessage(player.getName() + " is not currently navigating.");
        }
    }

    @Subcommand("neofetch")
    @CommandPermission("navigate.use.neofetch")
    @Description("Show the amazing logo of the plugin")
    public void onNeofetch(CommandSender sender) {
        Server server = sender.getServer();
        int length = (sender instanceof ConsoleCommandSender ? 4 : sender.getName().length()) + 1 + server.getName().length();

        String username = sender instanceof ConsoleCommandSender ? "root" : sender.getName();
        String serverName = server.getName();
        String minecraftVersion = server.getMinecraftVersion();
        String tps = String.format("%.1f", Math.round(server.getTPS()[1] * 10) / 10.0);
        String uptime = formatMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        String timezone = ZoneId.systemDefault().toString();
        String pluginVersion = Navigate.getInstance().getPluginMeta().getVersion();

        String message = "<br><hover:show_text:'Click to go to<br>the <color:#1bd96a>Modrinth</color> page'><click:open_url:'https://modrinth.com/project/3t39uukw'><#006565>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛  " + username + "<white>@</white>" + serverName + "<br>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛  <white>" + "-".repeat(length) + "</white><br>" +
                "⬛⬛⬛⬛⬛⬛<dark_gray>⬛</dark_gray><gray>⬛</gray>⬛⬛  Version<white>: " + minecraftVersion + "</white><br>" +
                "⬛⬛⬛⬛<dark_gray>⬛⬛</dark_gray><gray>⬛</gray><white>⬛</white>⬛⬛  TPS<white>: " + tps + "</white><br>" +
                "⬛⬛<dark_gray>⬛⬛⬛</dark_gray><gray>⬛</gray><white>⬛</white>⬛⬛⬛  Uptime<white>: " + uptime + "</white><br>" +
                "⬛<dark_gray>⬛⬛⬛</dark_gray><gray>⬛</gray><white>⬛⬛</white>⬛⬛⬛  Plugin Name<white>: Navigate</white><br>" +
                "⬛⬛⬛<gray>⬛</gray><white>⬛⬛</white>⬛⬛⬛⬛  Plugin Version<white>: " + pluginVersion + "</white><br>" +
                "⬛⬛⬛⬛<white>⬛⬛</white>⬛⬛⬛⬛  Timezone<white>: " + timezone + "</white><br>" +
                "⬛⬛⬛⬛<white>⬛</white>⬛⬛⬛⬛⬛  <black>0</black><dark_red>0</dark_red><dark_green>0</dark_green><gold>0</gold><dark_blue>0</dark_blue><dark_purple>0</dark_purple><dark_aqua>0</dark_aqua><gray>0</gray><br>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛</#006565></click></hover>  <dark_gray>0</dark_gray><red>0</red><green>0</green><yellow>0</yellow><blue>0</blue><light_purple>0</light_purple><aqua>0</aqua><white>0</white>";

        sender.sendMessage(miniMessage.deserialize(message));
    }

    @HelpCommand()
    @Default()
    public void onHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<color:#006565>Navigate</color> commands:"));
        sender.sendMessage(miniMessage.deserialize("<gray><required> [optional(default)]</gray>"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate start <x> <y> <z> [safeMode(true)] [follow] [MATERIAL1,MATERIAL2]</yellow> <gray>-</gray> Start navigating to target location, follow moves you along the path"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate stop</yellow> <gray>-</gray> Stop navigating"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate startfor <player> <x> <y> <z> [safeMode(true)] [follow] [MATERIAL1,MATERIAL2]</yellow> <gray>-</gray> Start navigating player to target location"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate stopfor <player></yellow> <gray>-</gray> Stop navigating player"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate neofetch</yellow> <gray>-</gray> Fun info about the plugin and server"));
    }

    private static void begin(CommandSender sender, Player player, int x, int y, int z, Options options) {
        Navigate plugin = Navigate.getInstance();
        plugin.foliaLib.getScheduler().runAtEntity(player, task -> {
            boolean self = sender.equals(player);
            if (plugin.getNavigationService().stop(player)) {
                sender.sendMessage(self ? "Your previous navigation has been stopped." : "Previous navigation for " + player.getName() + " has been stopped.");
            }

            Location targetLocation = new Location(player.getWorld(), x, y, z);
            plugin.getNavigationService().start(player, targetLocation, options.safeMode(), options.materials(), options.follow());
            String details = "X:" + x + " Y:" + y + " Z:" + z + " with safe mode: " + options.safeMode() + ", follow: " + options.follow();
            sender.sendMessage(self ? "Navigation started to " + details : "Navigation started for " + player.getName() + " to " + details);
        });
    }

    private record Options(boolean safeMode, boolean follow, Set<Material> materials) {}

    private static Options parseOptions(String[] args) {
        boolean safeMode = true;
        boolean follow = false;
        Set<Material> materials = null;

        if (args != null) {
            List<String> rest = new ArrayList<>();
            for (String arg : args) {
                if (arg.equalsIgnoreCase("follow")) {
                    follow = true;
                } else {
                    rest.add(arg);
                }
            }

            if (!rest.isEmpty()) {
                safeMode = !rest.get(0).equalsIgnoreCase("false");
            }

            if (rest.size() > 1) {
                String joined = String.join("", rest.subList(1, rest.size()));
                materials = Arrays.stream(joined.split(","))
                        .map(String::toUpperCase)
                        .map(Material::valueOf)
                        .collect(Collectors.toSet());
            }
        }

        return new Options(safeMode, follow, materials);
    }

    public static String formatMillis(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%02d Hours, %02d Minutes, %02d Seconds", hours, minutes, remainingSeconds);
    }
}