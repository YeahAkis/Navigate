package org.notionsmp.navigate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class NavigateCommands {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int MAX_SUGGESTIONS = 80;

    private final Navigate plugin;

    NavigateCommands(Navigate plugin) {
        this.plugin = plugin;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("navigate")
                .requires(source -> source.getSender().hasPermission("navigate.use"))
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource().getSender())))
                .then(Commands.literal("start")
                        .then(Commands.argument("position", ArgumentTypes.blockPosition())
                                .executes(context -> start(context, ""))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .suggests(this::suggestOptions)
                                        .executes(context -> start(context, StringArgumentType.getString(context, "options"))))))
                .then(Commands.literal("stop")
                        .executes(this::stop))
                .then(Commands.literal("startfor")
                        .requires(source -> source.getSender().hasPermission("navigate.use.others"))
                        .then(Commands.argument("players", ArgumentTypes.players())
                                .then(Commands.argument("position", ArgumentTypes.blockPosition())
                                        .executes(context -> startFor(context, ""))
                                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                                .suggests(this::suggestOptions)
                                                .executes(context -> startFor(context, StringArgumentType.getString(context, "options")))))))
                .then(Commands.literal("stopfor")
                        .requires(source -> source.getSender().hasPermission("navigate.use.others"))
                        .then(Commands.argument("players", ArgumentTypes.players())
                                .executes(this::stopFor)))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("navigate.reload"))
                        .executes(this::reload))
                .then(Commands.literal("neofetch")
                        .requires(source -> source.getSender().hasPermission("navigate.use.neofetch"))
                        .executes(context -> {
                            Neofetch.send(plugin, context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private int help(CommandSender sender) {
        sender.sendMessage(MINI_MESSAGE.deserialize("<color:#006565>Navigate</color> commands:"));
        sender.sendMessage(MINI_MESSAGE.deserialize("<gray><required> [optional]</gray>"));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate start <x y z> [safe|unsafe] [follow] [materials]</yellow> <gray>-</gray> Start navigating to a position, follow moves you along the path"));
        sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate stop</yellow> <gray>-</gray> Stop navigating"));
        if (sender.hasPermission("navigate.use.others")) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate startfor <players> <x y z> [safe|unsafe] [follow] [materials]</yellow> <gray>-</gray> Start navigating other players"));
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate stopfor <players></yellow> <gray>-</gray> Stop navigating other players"));
        }
        if (sender.hasPermission("navigate.reload")) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate reload</yellow> <gray>-</gray> Reload the config"));
        }
        if (sender.hasPermission("navigate.use.neofetch")) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<yellow>/navigate neofetch</yellow> <gray>-</gray> Fun info about the plugin and server"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int start(CommandContext<CommandSourceStack> context, String input) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Object executor = source.getExecutor() != null ? source.getExecutor() : source.getSender();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage("Only players can start a navigation, use startfor from the console.");
            return 0;
        }

        NavigateOptions options = options(source.getSender(), input);
        if (options == null) {
            return 0;
        }
        if (options.follow() && !player.hasPermission("navigate.follow")) {
            source.getSender().sendMessage("You do not have permission to be moved along the path.");
            return 0;
        }

        BlockPosition position = context.getArgument("position", BlockPositionResolver.class).resolve(source);
        begin(source.getSender(), player, position, options);
        return Command.SINGLE_SUCCESS;
    }

    private int startFor(CommandContext<CommandSourceStack> context, String input) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        NavigateOptions options = options(source.getSender(), input);
        if (options == null) {
            return 0;
        }

        List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(source);
        BlockPosition position = context.getArgument("position", BlockPositionResolver.class).resolve(source);
        for (Player player : players) {
            begin(source.getSender(), player, position, options);
        }
        return players.size();
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Object executor = source.getExecutor() != null ? source.getExecutor() : source.getSender();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage("Only players can stop their navigation, use stopfor from the console.");
            return 0;
        }

        if (plugin.getNavigationService().stop(player)) {
            source.getSender().sendMessage("Navigation stopped.");
            return Command.SINGLE_SUCCESS;
        }
        source.getSender().sendMessage("You are not currently navigating.");
        return 0;
    }

    private int stopFor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        int stopped = 0;
        for (Player player : context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(source)) {
            if (plugin.getNavigationService().stop(player)) {
                sender.sendMessage("Navigation stopped for " + player.getName() + ".");
                stopped++;
            } else {
                sender.sendMessage(player.getName() + " is not currently navigating.");
            }
        }
        return stopped;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reloadConfig();
        context.getSource().getSender().sendMessage("Config reloaded.");
        return Command.SINGLE_SUCCESS;
    }

    private NavigateOptions options(CommandSender sender, String input) {
        try {
            return NavigateOptions.parse(input);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Unknown option or block: " + e.getMessage());
            return null;
        }
    }

    private void begin(CommandSender sender, Player player, BlockPosition position, NavigateOptions options) {
        plugin.foliaLib.getScheduler().runAtEntity(player, task -> {
            boolean self = sender.equals(player);
            if (plugin.getNavigationService().stop(player)) {
                sender.sendMessage(self ? "Your previous navigation has been stopped." : "Previous navigation for " + player.getName() + " has been stopped.");
            }

            Location target = new Location(player.getWorld(), position.blockX(), position.blockY(), position.blockZ());
            plugin.getNavigationService().start(player, target, options.safeMode(), options.materials(), options.follow());
            String details = "X:" + position.blockX() + " Y:" + position.blockY() + " Z:" + position.blockZ() + " with safe mode: " + options.safeMode() + ", follow: " + options.follow();
            sender.sendMessage(self ? "Navigation started to " + details : "Navigation started for " + player.getName() + " to " + details);
        });
    }

    private CompletableFuture<Suggestions> suggestOptions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int split = remaining.lastIndexOf(' ') + 1;
        String typed = remaining.substring(0, split).toLowerCase(Locale.ROOT);
        String[] used = typed.isBlank() ? new String[0] : typed.trim().split("\\s+");

        boolean hasFollow = false;
        boolean hasMode = false;
        for (String token : used) {
            switch (token) {
                case "follow" -> hasFollow = true;
                case "safe", "unsafe", "true", "false" -> hasMode = true;
                default -> {
                }
            }
        }

        SuggestionsBuilder current = builder.createOffset(builder.getStart() + split);
        String partial = current.getRemainingLowerCase();
        int comma = partial.lastIndexOf(',');
        if (comma >= 0) {
            SuggestionsBuilder materials = current.createOffset(current.getStart() + comma + 1);
            suggestMaterials(materials);
            return materials.buildFuture();
        }

        if (!hasFollow) {
            suggestIf(current, "follow");
        }
        if (!hasMode) {
            suggestIf(current, "safe");
            suggestIf(current, "unsafe");
        }
        suggestMaterials(current);
        return current.buildFuture();
    }

    private static void suggestIf(SuggestionsBuilder builder, String value) {
        if (value.startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(value);
        }
    }

    private static void suggestMaterials(SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        if (prefix.isEmpty()) {
            return;
        }
        int count = 0;
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isBlock() || material.isAir()) {
                continue;
            }
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(prefix)) {
                builder.suggest(name);
                if (++count >= MAX_SUGGESTIONS) {
                    return;
                }
            }
        }
    }
}
