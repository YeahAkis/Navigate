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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class NavigateCommands {

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
        messages().send(sender, "help.header");
        messages().send(sender, "help.legend");
        messages().send(sender, "help.start");
        messages().send(sender, "help.stop");
        if (sender.hasPermission("navigate.use.others")) {
            messages().send(sender, "help.startfor");
            messages().send(sender, "help.stopfor");
        }
        if (sender.hasPermission("navigate.reload")) {
            messages().send(sender, "help.reload");
        }
        if (sender.hasPermission("navigate.use.neofetch")) {
            messages().send(sender, "help.neofetch");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int start(CommandContext<CommandSourceStack> context, String input) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Object executor = source.getExecutor() != null ? source.getExecutor() : source.getSender();
        if (!(executor instanceof Player player)) {
            messages().send(source.getSender(), "error.players-only-start");
            return 0;
        }

        NavigateOptions options = options(source.getSender(), input);
        if (options == null) {
            return 0;
        }
        if (options.follow() && !player.hasPermission("navigate.follow")) {
            messages().send(source.getSender(), "error.no-follow-permission");
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
        if (players.isEmpty()) {
            messages().send(source.getSender(), "error.no-players");
            return 0;
        }
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
            messages().send(source.getSender(), "error.players-only-stop");
            return 0;
        }

        if (plugin.getNavigationService().stop(player)) {
            messages().send(source.getSender(), "navigation.stopped");
            return Command.SINGLE_SUCCESS;
        }
        messages().send(source.getSender(), "navigation.not-navigating");
        return 0;
    }

    private int stopFor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        int stopped = 0;
        List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(source);
        if (players.isEmpty()) {
            messages().send(sender, "error.no-players");
            return 0;
        }
        for (Player player : players) {
            if (plugin.getNavigationService().stop(player)) {
                messages().send(sender, "navigation.stopped-for", Placeholder.unparsed("player", player.getName()));
                if (plugin.getConfig().getBoolean("messages.notify-target", true) && !sender.equals(player)) {
                    messages().send(player, "navigation.stopped-by", Placeholder.unparsed("sender", sender.getName()));
                }
                stopped++;
            } else {
                messages().send(sender, "navigation.not-navigating-for", Placeholder.unparsed("player", player.getName()));
            }
        }
        return stopped;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reloadAll();
        plugin.getMessages().send(context.getSource().getSender(), "reload");
        return Command.SINGLE_SUCCESS;
    }

    private Messages messages() {
        return plugin.getMessages();
    }

    private NavigateOptions options(CommandSender sender, String input) {
        try {
            return NavigateOptions.parse(input);
        } catch (IllegalArgumentException e) {
            messages().send(sender, "error.unknown-option", Placeholder.unparsed("value", e.getMessage()));
            return null;
        }
    }

    private void begin(CommandSender sender, Player player, BlockPosition position, NavigateOptions options) {
        player.getScheduler().run(plugin, task -> {
            boolean self = sender.equals(player);
            if (plugin.getNavigationService().stop(player)) {
                if (self) {
                    messages().send(sender, "navigation.previous-stopped");
                } else {
                    messages().send(sender, "navigation.previous-stopped-for", Placeholder.unparsed("player", player.getName()));
                }
            }

            Location target = new Location(player.getWorld(), position.blockX(), position.blockY(), position.blockZ());
            plugin.getNavigationService().start(player, target, options.safeMode(), options.materials(), options.follow());
            TagResolver[] resolvers = {
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("x", String.valueOf(position.blockX())),
                    Placeholder.unparsed("y", String.valueOf(position.blockY())),
                    Placeholder.unparsed("z", String.valueOf(position.blockZ())),
                    Placeholder.unparsed("safe", messages().plain(sender, options.safeMode() ? "state.enabled" : "state.disabled")),
                    Placeholder.unparsed("follow", messages().plain(sender, options.follow() ? "state.enabled" : "state.disabled"))
            };
            messages().send(sender, self ? "navigation.started" : "navigation.started-for", resolvers);
            if (!self && plugin.getConfig().getBoolean("messages.notify-target", true)) {
                messages().send(player, "navigation.started-by", Placeholder.unparsed("sender", sender.getName()));
            }
       }, null);
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
