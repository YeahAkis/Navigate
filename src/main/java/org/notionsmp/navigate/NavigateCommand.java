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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@CommandAlias("navigate")
@CommandPermission("navigate.use")
public class NavigateCommand extends BaseCommand {
    MiniMessage miniMessage = MiniMessage.miniMessage();

    @Subcommand("start")
    @Syntax("[x] [y] [z] (safeMode) [materials]")
    @CommandCompletion("@nothing @nothing @nothing true|false @nothing")
    @Description("Start navigating to target location")
    public void onStart(Player player, int x, int y, int z, @Optional String[] args) {
        boolean safeMode = true;
        Set<Material> materialSet = null;

        if (args != null && args.length > 0) {
            try {
                safeMode = !args[0].equalsIgnoreCase("false");
            } catch (Exception ignored) {}

            if (args.length > 1) {
                String materials = String.join("", Arrays.copyOfRange(args, 1, args.length));
                materialSet = Arrays.stream(materials.split(","))
                        .map(String::toUpperCase)
                        .map(Material::valueOf)
                        .collect(Collectors.toSet());
            }
        }

        if (Navigate.getInstance().getNavigationService().stop(player)) {
            player.sendMessage("Your previous navigation has been stopped.");
        }

        Location targetLocation = new Location(player.getWorld(), x, y, z);
        Navigate.getInstance().getNavigationService().start(player, targetLocation, safeMode, materialSet);
        player.sendMessage("Navigation started to X:" + x + " Y:" + y + " Z:" + z + " with safe mode: " + safeMode);
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
    @Syntax("<player> <x> <y> <z> (safeMode)")
    @CommandCompletion("@players @nothing @nothing @nothing true|false")
    @Description("Start navigating player to target location")
    public void onStartFor(CommandSender sender, String playerName, int x, int y, int z, @Optional String[] args) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage("Player not found.");
            return;
        }

        boolean safeMode = true;
        Set<Material> materialSet = null;

        if (args != null && args.length > 0) {
            try {
                safeMode = !args[0].equalsIgnoreCase("false");
            } catch (Exception ignored) {}

            if (args.length > 1) {
                String materials = String.join("", Arrays.copyOfRange(args, 1, args.length));
                materialSet = Arrays.stream(materials.split(","))
                        .map(String::toUpperCase)
                        .map(Material::valueOf)
                        .collect(Collectors.toSet());
            }
        }

        if (Navigate.getInstance().getNavigationService().stop(player)) {
            sender.sendMessage("Previous navigation for " + player.getName() + " has been stopped.");
        }

        Location targetLocation = new Location(player.getWorld(), x, y, z);
        Navigate.getInstance().getNavigationService().start(player, targetLocation, safeMode, materialSet);
        sender.sendMessage("Navigation started for " + player.getName() + " to X:" + x + " Y:" + y + " Z:" + z + " with safe mode: " + safeMode);
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
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate start <x> <y> <z> [safeMode(true)] [MATERIAL1,MATERIAL2]</yellow> <gray>-</gray> Start navigating to target location"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate stop</yellow> <gray>-</gray> Stop navigating"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate startfor <player> <x> <y> <z> [safeMode(true)] [MATERIAL1,MATERIAL2]</yellow> <gray>-</gray> Start navigating player to target location"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate stopfor <player></yellow> <gray>-</gray> Stop navigating player"));
        sender.sendMessage(miniMessage.deserialize("<yellow>/navigate neofetch</yellow> <gray>-</gray> Fun info about the plugin and server"));
    }

    public static String formatMillis(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        return String.format("%02d Hours, %02d Minutes, %02d Seconds", hours, minutes, remainingSeconds);
    }
}