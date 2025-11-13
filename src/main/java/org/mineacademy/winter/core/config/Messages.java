package org.mineacademy.winter.core.config;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Modern message system using Java 21 Records
 * Replaces Foundation's Localization system
 */
public final class Messages {

    private static FileConfiguration messages;
    private static String locale = "en";

    // Chest Messages
    public record ChestMessages(
        String noChest,
        String noPlayer,
        String invalidFormat,
        String createSuccess,
        String openOwn,
        String openPublic,
        String openPrivate,
        String openAdmin,
        String expandOwn,
        String breakOwn,
        String breakAdmin,
        String breakOwnSign,
        String breakAdminSign,
        String formatGift,
        String formatDated,
        String formatTimed,
        String timedLimit,
        String timedNotReady,
        String datedLimit,
        String datedNotReady,
        String illegalPlace,
        String illegalBreak,
        String illegalBreakSign,
        String illegalExpand,
        String illegalAccess,
        String illegalInventoryClick
    ) {}

    // Boxed Messages (multi-line messages)
    public record BoxedMessages(
        List<String> chestOpen,
        List<String> chestPreview,
        String borderColor
    ) {}

    // Command Messages
    public record CommandMessages(
        String snowEnabled,
        String snowDisabled,
        String reloadSuccess,
        String noPermission
    ) {}

    // Plural Forms (for different languages)
    public record PluralForms(
        String[] player,
        String[] level,
        String[] hour,
        String[] minute,
        String[] second,
        String[] life
    ) {}

    // Text Parts
    public record TextParts(
        String on,
        String between,
        String and,
        String at
    ) {}

    // Main Messages Holder
    public record MessageBundle(
        ChestMessages chest,
        BoxedMessages boxed,
        CommandMessages commands,
        PluralForms plurals,
        TextParts parts
    ) {}

    private static MessageBundle currentMessages;

    /**
     * Load messages from file
     */
    public static void load(@NotNull Winter plugin, @NotNull String localeCode) {
        locale = localeCode;

        // Create localization directory
        File localeDir = new File(plugin.getDataFolder(), "localization");
        if (!localeDir.exists()) {
            localeDir.mkdirs();
        }

        // Load messages file
        File localeFile = new File(localeDir, "messages_" + locale + ".yml");

        if (!localeFile.exists()) {
            // Try to save from resources
            String resourcePath = "localization/messages_" + locale + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                // Fall back to English
                plugin.log(Level.WARNING, "Locale '" + locale + "' not found, using English");
                locale = "en";
                resourcePath = "localization/messages_en.yml";
                if (plugin.getResource(resourcePath) != null) {
                    plugin.saveResource(resourcePath, false);
                }
            }
        }

        // Load YAML
        messages = YamlConfiguration.loadConfiguration(localeFile);

        // Load defaults
        try (InputStream defStream = plugin.getResource("localization/messages_en.yml")) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8)
                );
                messages.setDefaults(defConfig);
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Could not load default messages", e);
        }

        // Parse messages
        try {
            currentMessages = new MessageBundle(
                loadChestMessages(),
                loadBoxedMessages(),
                loadCommandMessages(),
                loadPluralForms(),
                loadTextParts()
            );

            plugin.log(Level.INFO, "Messages loaded for locale: " + locale);
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load messages", e);
            throw new RuntimeException("Message load failed", e);
        }
    }

    /**
     * Get current messages
     */
    public static MessageBundle get() {
        if (currentMessages == null) {
            throw new IllegalStateException("Messages not loaded yet!");
        }
        return currentMessages;
    }

    /**
     * Format a message with placeholders
     */
    public static Component format(@NotNull String message, @NotNull Object... replacements) {
        String result = message;

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                String placeholder = replacements[i].toString();
                String value = replacements[i + 1].toString();
                result = result.replace("{" + placeholder + "}", value);
            }
        }

        return Winter.getInstance().colorize(result);
    }

    // Message Loaders

    private static ChestMessages loadChestMessages() {
        String prefix = "Chest.";
        return new ChestMessages(
            messages.getString(prefix + "Lacks_Chest", "You must be looking at a chest!"),
            messages.getString(prefix + "Lacks_Players", "You must specify at least one player!"),
            messages.getString(prefix + "Invalid_Format", "Invalid chest format!"),
            messages.getString(prefix + "Create_Success", "Gift chest created successfully!"),
            messages.getString(prefix + "Open.Own", "Opening your own gift chest..."),
            messages.getString(prefix + "Open.Public", "Opening public gift chest..."),
            messages.getString(prefix + "Open.Private", "Opening private gift chest..."),
            messages.getString(prefix + "Open.Admin", "Opening chest as admin..."),
            messages.getString(prefix + "Expand.Own", "Expanding your gift chest..."),
            messages.getString(prefix + "Break.Own", "Breaking your own chest..."),
            messages.getString(prefix + "Break.Admin", "Breaking chest as admin..."),
            messages.getString(prefix + "Break.Own_Sign", "Removing your sign..."),
            messages.getString(prefix + "Break.Admin_Sign", "Removing sign as admin..."),
            messages.getString(prefix + "Format.Gift", "[Gift]"),
            messages.getString(prefix + "Format.Dated", "[Dated]"),
            messages.getString(prefix + "Format.Timed", "[Timed]"),
            messages.getString(prefix + "Timed.Exceeded_Limit", "You have already opened this chest!"),
            messages.getString(prefix + "Timed.Not_Ready", "This chest is not ready yet. Wait {time}"),
            messages.getString(prefix + "Dated.Exceeded_Limit", "You have already opened this chest!"),
            messages.getString(prefix + "Dated.Not_Ready", "This chest is only available {period}"),
            messages.getString(prefix + "Illegal.Place", "You cannot place this chest here!"),
            messages.getString(prefix + "Illegal.Break", "You cannot break this chest!"),
            messages.getString(prefix + "Illegal.Break_Sign", "You cannot break this sign!"),
            messages.getString(prefix + "Illegal.Expand", "You cannot expand this chest!"),
            messages.getString(prefix + "Illegal.Access", "You cannot access this chest!"),
            messages.getString(prefix + "Illegal.Inventory_Click", "You cannot do that!")
        );
    }

    private static BoxedMessages loadBoxedMessages() {
        return new BoxedMessages(
            messages.getStringList("Boxed.Chest_Open"),
            messages.getStringList("Boxed.Chest_Preview"),
            messages.getString("Boxed.Border_Color", "GRAY")
        );
    }

    private static CommandMessages loadCommandMessages() {
        return new CommandMessages(
            messages.getString("Commands.Snow_Enabled", "Snow particles enabled!"),
            messages.getString("Commands.Snow_Disabled", "Snow particles disabled!"),
            messages.getString("Commands.Reload_Success", "Configuration reloaded!"),
            messages.getString("Commands.No_Permission", "You don't have permission to use this command!")
        );
    }

    private static PluralForms loadPluralForms() {
        return new PluralForms(
            getStringArray("Cases.Player", new String[]{"player", "players"}),
            getStringArray("Cases.Level", new String[]{"level", "levels"}),
            getStringArray("Cases.Hour", new String[]{"hour", "hours"}),
            getStringArray("Cases.Minute", new String[]{"minute", "minutes"}),
            getStringArray("Cases.Second", new String[]{"second", "seconds"}),
            getStringArray("Cases.Life", new String[]{"life", "lives"})
        );
    }

    private static TextParts loadTextParts() {
        return new TextParts(
            messages.getString("Parts.On", "on"),
            messages.getString("Parts.Between", "between"),
            messages.getString("Parts.And", "and"),
            messages.getString("Parts.At", "at")
        );
    }

    // Helper Methods

    private static String[] getStringArray(String path, String[] defaults) {
        List<String> list = messages.getStringList(path);
        return list.isEmpty() ? defaults : list.toArray(new String[0]);
    }

    /**
     * Get plural form based on count
     */
    public static String getPlural(String[] forms, int count) {
        if (forms.length == 0) return "";
        if (forms.length == 1) return forms[0];

        // English plural rules (can be extended for other languages)
        return count == 1 ? forms[0] : forms[1];
    }
}
