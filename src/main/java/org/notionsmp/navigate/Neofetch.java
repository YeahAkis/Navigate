package org.notionsmp.navigate;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.lang.management.ManagementFactory;
import java.time.ZoneId;

final class Neofetch {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Neofetch() {
    }

    static void send(Navigate plugin, CommandSender sender) {
        Server server = sender.getServer();
        int length = (sender instanceof ConsoleCommandSender ? 4 : sender.getName().length()) + 1 + server.getName().length();

        String username = sender instanceof ConsoleCommandSender ? "root" : sender.getName();
        String serverName = server.getName();
        String minecraftVersion = server.getMinecraftVersion();
        String tps = String.format("%.1f", Math.round(server.getTPS()[1] * 10) / 10.0);
        Messages messages = plugin.getMessages();
        String uptime = formatMillis(messages.text(sender, "neofetch.uptime-format"), ManagementFactory.getRuntimeMXBean().getUptime());
        String timezone = ZoneId.systemDefault().toString();
        String pluginVersion = plugin.getPluginMeta().getVersion();

        String message = "<br><hover:show_text:'" + messages.text(sender, "neofetch.hover") + "'><click:open_url:'https://modrinth.com/project/3t39uukw'><#006565>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛  " + username + "<white>@</white>" + serverName + "<br>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛  <white>" + "-".repeat(length) + "</white><br>" +
                "⬛⬛⬛⬛⬛⬛<dark_gray>⬛</dark_gray><gray>⬛</gray>⬛⬛  " + messages.text(sender, "neofetch.version") + "<white>: " + minecraftVersion + "</white><br>" +
                "⬛⬛⬛⬛<dark_gray>⬛⬛</dark_gray><gray>⬛</gray><white>⬛</white>⬛⬛  " + messages.text(sender, "neofetch.tps") + "<white>: " + tps + "</white><br>" +
                "⬛⬛<dark_gray>⬛⬛⬛</dark_gray><gray>⬛</gray><white>⬛</white>⬛⬛⬛  " + messages.text(sender, "neofetch.uptime") + "<white>: " + uptime + "</white><br>" +
                "⬛<dark_gray>⬛⬛⬛</dark_gray><gray>⬛</gray><white>⬛⬛</white>⬛⬛⬛  " + messages.text(sender, "neofetch.plugin-name") + "<white>: Navigate</white><br>" +
                "⬛⬛⬛<gray>⬛</gray><white>⬛⬛</white>⬛⬛⬛⬛  " + messages.text(sender, "neofetch.plugin-version") + "<white>: " + pluginVersion + "</white><br>" +
                "⬛⬛⬛⬛<white>⬛⬛</white>⬛⬛⬛⬛  " + messages.text(sender, "neofetch.timezone") + "<white>: " + timezone + "</white><br>" +
                "⬛⬛⬛⬛<white>⬛</white>⬛⬛⬛⬛⬛  <black>0</black><dark_red>0</dark_red><dark_green>0</dark_green><gold>0</gold><dark_blue>0</dark_blue><dark_purple>0</dark_purple><dark_aqua>0</dark_aqua><gray>0</gray><br>" +
                "⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛</#006565></click></hover>  <dark_gray>0</dark_gray><red>0</red><green>0</green><yellow>0</yellow><blue>0</blue><light_purple>0</light_purple><aqua>0</aqua><white>0</white>";

        sender.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    static String formatMillis(String format, long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        return String.format(format, hours, minutes, remainingSeconds);
    }
}
