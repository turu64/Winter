package org.mineacademy.winter.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.Messages;
import org.mineacademy.winter.core.config.WinterConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Main command handler for Winter plugin
 */
public final class WinterCommandHandler implements CommandExecutor, TabCompleter {

    private final Winter plugin;

    public WinterCommandHandler(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    /**
     * Register the command
     */
    public void register() {
        var aliases = WinterConfig.get().commandAliases();
        if (aliases.isEmpty()) {
            plugin.log(java.util.logging.Level.WARNING, "No command aliases configured!");
            return;
        }

        String mainAlias = aliases.get(0);
        var command = plugin.getCommand(mainAlias);

        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
            plugin.log(java.util.logging.Level.INFO, "Registered command: /" + mainAlias);
        } else {
            plugin.log(java.util.logging.Level.WARNING, "Could not register command: /" + mainAlias);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        // No arguments - show help
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "snow" -> handleSnow(sender);
            case "reload" -> handleReload(sender);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(plugin.colorize("&cUnknown subcommand: " + subCommand));
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleSnow(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!"));
            return true;
        }

        boolean newState = plugin.getPlayerDataManager().toggleSnow(player);
        String message = newState ?
            Messages.get().commands().snowEnabled() :
            Messages.get().commands().snowDisabled();

        player.sendMessage(plugin.colorize(message));
        return true;
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("winter.reload") && !sender.isOp()) {
            sender.sendMessage(plugin.colorize(Messages.get().commands().noPermission()));
            return true;
        }

        plugin.performReload();
        sender.sendMessage(plugin.colorize(Messages.get().commands().reloadSuccess()));
        return true;
    }

    private void sendHelp(@NotNull CommandSender sender) {
        String prefix = WinterConfig.get().prefix();

        sender.sendMessage(plugin.colorize(prefix + " &fAvailable Commands:"));
        sender.sendMessage(plugin.colorize("&7/winter snow &f- Toggle snow particles"));
        sender.sendMessage(plugin.colorize("&7/winter reload &f- Reload configuration"));
        sender.sendMessage(plugin.colorize("&7/winter help &f- Show this help"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("snow");
            completions.add("reload");
            completions.add("help");
        }

        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .toList();
    }
}
