package org.mineacademy.winter.core;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;

import java.util.logging.Level;

/**
 * Modern base class for the Winter plugin
 * Replaces Foundation's SimplePlugin
 */
public abstract class WinterPlugin extends JavaPlugin {

    @Getter
    private static WinterPlugin instance;

    @Override
    public final void onLoad() {
        instance = this;

        try {
            // Register WorldGuard custom flags during onLoad phase
            org.mineacademy.winter.hook.WorldGuardHook.registerCustomFlags(this);
        } catch (Exception e) {
            log(Level.WARNING, "Failed to register WorldGuard custom flags", e);
        }
    }

    @Override
    public final void onEnable() {
        instance = this;

        // Display startup logo
        displayStartupLogo();

        try {
            // Initialize plugin
            onPluginStart();

            log(Level.INFO, "Plugin enabled successfully!");
            log(Level.INFO, "Get Support: https://github.com/kangarko/Winter/issues");
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public final void onDisable() {
        try {
            onPluginStop();
            log(Level.INFO, "Plugin disabled successfully!");
        } catch (Exception e) {
            log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }

    /**
     * Called when the plugin is starting
     */
    protected abstract void onPluginStart();

    /**
     * Called when the plugin is stopping
     */
    protected void onPluginStop() {
        // Override if needed
    }

    /**
     * Reload the plugin configuration
     */
    public void performReload() {
        reloadConfig();
        onPluginReload();
        log(Level.INFO, "Configuration reloaded!");
    }

    /**
     * Called when the plugin is reloaded
     */
    protected void onPluginReload() {
        // Override if needed
    }

    /**
     * Display the startup logo
     */
    private void displayStartupLogo() {
        String[] logo = {
            "",
            "&f╦ ╦╦╔╗╔╔╦╗╔═╗╦═╗",
            "&f║║║║║║║ ║ ║╣ ╠╦╝",
            "&7╚╩╝╩╝╚╝ ╩ ╚═╝╩╚═",
            "&7Version 3.0.0 - Modern Edition",
            "&7Java 21 + Paper 1.21.4",
            ""
        };

        for (String line : logo) {
            getServer().getConsoleSender().sendMessage(
                colorize(line)
            );
        }
    }

    /**
     * Log a message
     */
    public void log(@NotNull Level level, @NotNull String message) {
        getLogger().log(level, message);
    }

    /**
     * Log a message with exception
     */
    public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
        getLogger().log(level, message, throwable);
    }

    /**
     * Colorize a message using & color codes
     */
    public Component colorize(@NotNull String message) {
        if (message.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        StringBuilder current = new StringBuilder();
        NamedTextColor currentColor = NamedTextColor.WHITE;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (c == '&' && i + 1 < message.length()) {
                char code = message.charAt(i + 1);

                // Add current text with current formatting
                if (!current.isEmpty()) {
                    Component part = Component.text(current.toString(), currentColor);
                    if (bold) part = part.decorate(TextDecoration.BOLD);
                    if (italic) part = part.decorate(TextDecoration.ITALIC);
                    if (underline) part = part.decorate(TextDecoration.UNDERLINED);
                    if (strikethrough) part = part.decorate(TextDecoration.STRIKETHROUGH);
                    result = result.append(part);
                    current = new StringBuilder();
                }

                // Parse color code
                currentColor = switch (code) {
                    case '0' -> NamedTextColor.BLACK;
                    case '1' -> NamedTextColor.DARK_BLUE;
                    case '2' -> NamedTextColor.DARK_GREEN;
                    case '3' -> NamedTextColor.DARK_AQUA;
                    case '4' -> NamedTextColor.DARK_RED;
                    case '5' -> NamedTextColor.DARK_PURPLE;
                    case '6' -> NamedTextColor.GOLD;
                    case '7' -> NamedTextColor.GRAY;
                    case '8' -> NamedTextColor.DARK_GRAY;
                    case '9' -> NamedTextColor.BLUE;
                    case 'a' -> NamedTextColor.GREEN;
                    case 'b' -> NamedTextColor.AQUA;
                    case 'c' -> NamedTextColor.RED;
                    case 'd' -> NamedTextColor.LIGHT_PURPLE;
                    case 'e' -> NamedTextColor.YELLOW;
                    case 'f' -> NamedTextColor.WHITE;
                    default -> currentColor;
                };

                // Parse formatting codes
                switch (code) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> underline = true;
                    case 'm' -> strikethrough = true;
                    case 'r' -> {
                        currentColor = NamedTextColor.WHITE;
                        bold = italic = underline = strikethrough = false;
                    }
                }

                i++; // Skip next character
            } else {
                current.append(c);
            }
        }

        // Add remaining text
        if (!current.isEmpty()) {
            Component part = Component.text(current.toString(), currentColor);
            if (bold) part = part.decorate(TextDecoration.BOLD);
            if (italic) part = part.decorate(TextDecoration.ITALIC);
            if (underline) part = part.decorate(TextDecoration.UNDERLINED);
            if (strikethrough) part = part.decorate(TextDecoration.STRIKETHROUGH);
            result = result.append(part);
        }

        return result;
    }

    /**
     * Get the plugin instance
     */
    public static Winter getWinter() {
        return (Winter) getInstance();
    }
}
