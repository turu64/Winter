package org.mineacademy.winter.data;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.winter.Winter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages special chest data (Gift, Dated, Timed chests)
 */
public final class ChestDataManager {

    private final Winter plugin;
    private final File dataFile;
    private final Map<Location, WinterChest> chestCache = new ConcurrentHashMap<>();

    /**
     * Base interface for all Winter chests
     */
    public sealed interface WinterChest permits GiftChest, DatedChest, TimedChest {
        Location location();
        ChestType type();
    }

    /**
     * Chest type enum
     */
    public enum ChestType {
        GIFT,
        DATED,
        TIMED
    }

    /**
     * Gift chest - can be public or private (for specific players)
     */
    public record GiftChest(
        Location location,
        boolean isPublic,
        List<UUID> allowedPlayers,
        Set<UUID> openedBy
    ) implements WinterChest {
        @Override
        public ChestType type() {
            return ChestType.GIFT;
        }

        public GiftChest withOpened(UUID player) {
            Set<UUID> newOpened = new HashSet<>(openedBy);
            newOpened.add(player);
            return new GiftChest(location, isPublic, allowedPlayers, newOpened);
        }
    }

    /**
     * Dated chest - only available during specific dates
     */
    public record DatedChest(
        Location location,
        LocalDate startDate,
        LocalDate endDate,
        boolean preview,
        Set<UUID> openedBy
    ) implements WinterChest {
        @Override
        public ChestType type() {
            return ChestType.DATED;
        }

        public boolean isAvailable() {
            LocalDate now = LocalDate.now();
            // Handle year-spanning periods (e.g., 25.12 to 03.01)
            if (startDate.isAfter(endDate)) {
                return !now.isBefore(startDate) || !now.isAfter(endDate);
            }
            return !now.isBefore(startDate) && !now.isAfter(endDate);
        }

        public DatedChest withOpened(UUID player) {
            Set<UUID> newOpened = new HashSet<>(openedBy);
            newOpened.add(player);
            return new DatedChest(location, startDate, endDate, preview, newOpened);
        }
    }

    /**
     * Timed chest - reopenable after cooldown
     */
    public record TimedChest(
        Location location,
        long cooldownMinutes,
        Map<UUID, LocalDateTime> lastOpened
    ) implements WinterChest {
        @Override
        public ChestType type() {
            return ChestType.TIMED;
        }

        public boolean canOpen(UUID player) {
            LocalDateTime last = lastOpened.get(player);
            if (last == null) return true;

            LocalDateTime now = LocalDateTime.now();
            return last.plusMinutes(cooldownMinutes).isBefore(now);
        }

        public long getRemainingCooldown(UUID player) {
            LocalDateTime last = lastOpened.get(player);
            if (last == null) return 0;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime canOpenAt = last.plusMinutes(cooldownMinutes);

            if (canOpenAt.isBefore(now)) return 0;

            return java.time.Duration.between(now, canOpenAt).toMinutes();
        }

        public TimedChest withOpened(UUID player) {
            Map<UUID, LocalDateTime> newOpened = new HashMap<>(lastOpened);
            newOpened.put(player, LocalDateTime.now());
            return new TimedChest(location, cooldownMinutes, newOpened);
        }
    }

    public ChestDataManager(@NotNull Winter plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "chests.yml");
    }

    /**
     * Get chest at location
     */
    @Nullable
    public WinterChest getChest(@NotNull Location location) {
        return chestCache.get(location);
    }

    /**
     * Register a new chest
     */
    public void registerChest(@NotNull WinterChest chest) {
        chestCache.put(chest.location(), chest);
    }

    /**
     * Remove chest
     */
    public void removeChest(@NotNull Location location) {
        chestCache.remove(location);
    }

    /**
     * Check if location has a Winter chest
     */
    public boolean isWinterChest(@NotNull Location location) {
        return chestCache.containsKey(location);
    }

    /**
     * Update chest data
     */
    public void updateChest(@NotNull WinterChest chest) {
        chestCache.put(chest.location(), chest);
    }

    /**
     * Load all chest data from file
     */
    public void loadAll() {
        if (!dataFile.exists()) {
            plugin.log(Level.INFO, "No chest data file found, starting fresh");
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            int count = 0;

            // Load gift chests
            ConfigurationSection giftSection = config.getConfigurationSection("gift");
            if (giftSection != null) {
                for (String key : giftSection.getKeys(false)) {
                    try {
                        count += loadGiftChest(giftSection.getConfigurationSection(key));
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Failed to load gift chest: " + key, e);
                    }
                }
            }

            // Load dated chests
            ConfigurationSection datedSection = config.getConfigurationSection("dated");
            if (datedSection != null) {
                for (String key : datedSection.getKeys(false)) {
                    try {
                        count += loadDatedChest(datedSection.getConfigurationSection(key));
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Failed to load dated chest: " + key, e);
                    }
                }
            }

            // Load timed chests
            ConfigurationSection timedSection = config.getConfigurationSection("timed");
            if (timedSection != null) {
                for (String key : timedSection.getKeys(false)) {
                    try {
                        count += loadTimedChest(timedSection.getConfigurationSection(key));
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Failed to load timed chest: " + key, e);
                    }
                }
            }

            plugin.log(Level.INFO, "Loaded " + count + " winter chests");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load chest data", e);
        }
    }

    private int loadGiftChest(ConfigurationSection section) {
        if (section == null) return 0;

        Location loc = section.getLocation("location");
        if (loc == null) return 0;

        boolean isPublic = section.getBoolean("public", false);
        List<UUID> allowed = section.getStringList("allowed").stream()
            .map(UUID::fromString)
            .toList();
        Set<UUID> opened = section.getStringList("opened").stream()
            .map(UUID::fromString)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        registerChest(new GiftChest(loc, isPublic, allowed, opened));
        return 1;
    }

    private int loadDatedChest(ConfigurationSection section) {
        if (section == null) return 0;

        Location loc = section.getLocation("location");
        if (loc == null) return 0;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate start = LocalDate.parse(section.getString("startDate"), formatter);
        LocalDate end = LocalDate.parse(section.getString("endDate"), formatter);
        boolean preview = section.getBoolean("preview", false);
        Set<UUID> opened = section.getStringList("opened").stream()
            .map(UUID::fromString)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);

        registerChest(new DatedChest(loc, start, end, preview, opened));
        return 1;
    }

    private int loadTimedChest(ConfigurationSection section) {
        if (section == null) return 0;

        Location loc = section.getLocation("location");
        if (loc == null) return 0;

        long cooldown = section.getLong("cooldown", 60);
        Map<UUID, LocalDateTime> lastOpened = new HashMap<>();

        ConfigurationSection openedSection = section.getConfigurationSection("lastOpened");
        if (openedSection != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            for (String uuidStr : openedSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                LocalDateTime time = LocalDateTime.parse(openedSection.getString(uuidStr), formatter);
                lastOpened.put(uuid, time);
            }
        }

        registerChest(new TimedChest(loc, cooldown, lastOpened));
        return 1;
    }

    /**
     * Save all chest data to file
     */
    public void saveAll() {
        try {
            FileConfiguration config = new YamlConfiguration();
            int giftCount = 0, datedCount = 0, timedCount = 0;

            for (Map.Entry<Location, WinterChest> entry : chestCache.entrySet()) {
                WinterChest chest = entry.getValue();
                String path = switch (chest.type()) {
                    case GIFT -> {
                        saveGiftChest(config, (GiftChest) chest, giftCount);
                        yield "gift." + giftCount++;
                    }
                    case DATED -> {
                        saveDatedChest(config, (DatedChest) chest, datedCount);
                        yield "dated." + datedCount++;
                    }
                    case TIMED -> {
                        saveTimedChest(config, (TimedChest) chest, timedCount);
                        yield "timed." + timedCount++;
                    }
                };
            }

            config.save(dataFile);
            plugin.log(Level.INFO, "Saved " + chestCache.size() + " winter chests");
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save chest data", e);
        }
    }

    private void saveGiftChest(FileConfiguration config, GiftChest chest, int index) {
        String path = "gift." + index + ".";
        config.set(path + "location", chest.location());
        config.set(path + "public", chest.isPublic());
        config.set(path + "allowed", chest.allowedPlayers().stream().map(UUID::toString).toList());
        config.set(path + "opened", chest.openedBy().stream().map(UUID::toString).toList());
    }

    private void saveDatedChest(FileConfiguration config, DatedChest chest, int index) {
        String path = "dated." + index + ".";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        config.set(path + "location", chest.location());
        config.set(path + "startDate", chest.startDate().format(formatter));
        config.set(path + "endDate", chest.endDate().format(formatter));
        config.set(path + "preview", chest.preview());
        config.set(path + "opened", chest.openedBy().stream().map(UUID::toString).toList());
    }

    private void saveTimedChest(FileConfiguration config, TimedChest chest, int index) {
        String path = "timed." + index + ".";
        config.set(path + "location", chest.location());
        config.set(path + "cooldown", chest.cooldownMinutes());

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        for (Map.Entry<UUID, LocalDateTime> entry : chest.lastOpened().entrySet()) {
            config.set(path + "lastOpened." + entry.getKey().toString(),
                entry.getValue().format(formatter));
        }
    }

    /**
     * Reload data
     */
    public void reload() {
        chestCache.clear();
        loadAll();
    }

    /**
     * Clear cache
     */
    public void clear() {
        chestCache.clear();
    }

    /**
     * Get all chests
     */
    public Collection<WinterChest> getAllChests() {
        return Collections.unmodifiableCollection(chestCache.values());
    }
}
