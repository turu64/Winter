package org.mineacademy.winter.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player-specific data (snow particle preferences, etc.)
 */
public final class PlayerDataManager {

    private final Winter plugin;
    private final File dataFile;
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    /**
     * Player data record
     */
    public record PlayerData(
        UUID uuid,
        boolean snowEnabled,
        long lastChestOpen
    ) {
        public PlayerData withSnowEnabled(boolean enabled) {
            return new PlayerData(uuid, enabled, lastChestOpen);
        }

        public PlayerData withLastChestOpen(long time) {
            return new PlayerData(uuid, snowEnabled, time);
        }
    }

    public PlayerDataManager(@NotNull Winter plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    /**
     * Get player data, creating default if not exists
     */
    public PlayerData getData(@NotNull UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid,
            id -> new PlayerData(id, true, 0L));
    }

    /**
     * Get player data by player
     */
    public PlayerData getData(@NotNull Player player) {
        return getData(player.getUniqueId());
    }

    /**
     * Update player data
     */
    public void setData(@NotNull UUID uuid, @NotNull PlayerData data) {
        playerDataCache.put(uuid, data);
    }

    /**
     * Toggle snow particles for player
     */
    public boolean toggleSnow(@NotNull Player player) {
        PlayerData current = getData(player);
        boolean newValue = !current.snowEnabled();
        setData(player.getUniqueId(), current.withSnowEnabled(newValue));
        return newValue;
    }

    /**
     * Check if snow is enabled for player
     */
    public boolean isSnowEnabled(@NotNull Player player) {
        return getData(player).snowEnabled();
    }

    /**
     * Load all player data from file
     */
    public void loadAll() {
        if (!dataFile.exists()) {
            plugin.log(Level.INFO, "No player data file found, starting fresh");
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            int count = 0;

            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    boolean snowEnabled = config.getBoolean(uuidStr + ".snowEnabled", true);
                    long lastChestOpen = config.getLong(uuidStr + ".lastChestOpen", 0L);

                    playerDataCache.put(uuid, new PlayerData(uuid, snowEnabled, lastChestOpen));
                    count++;
                } catch (IllegalArgumentException e) {
                    plugin.log(Level.WARNING, "Invalid UUID in player data: " + uuidStr);
                }
            }

            plugin.log(Level.INFO, "Loaded data for " + count + " players");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load player data", e);
        }
    }

    /**
     * Save all player data to file
     */
    public void saveAll() {
        try {
            FileConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
                String uuidStr = entry.getKey().toString();
                PlayerData data = entry.getValue();

                config.set(uuidStr + ".snowEnabled", data.snowEnabled());
                config.set(uuidStr + ".lastChestOpen", data.lastChestOpen());
            }

            config.save(dataFile);
            plugin.log(Level.INFO, "Saved data for " + playerDataCache.size() + " players");
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save player data", e);
        }
    }

    /**
     * Reload data
     */
    public void reload() {
        playerDataCache.clear();
        loadAll();
    }

    /**
     * Clear cache
     */
    public void clear() {
        playerDataCache.clear();
    }
}
