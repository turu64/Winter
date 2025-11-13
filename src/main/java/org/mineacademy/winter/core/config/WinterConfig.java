package org.mineacademy.winter.core.config;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Modern configuration system using Java 21 Records
 * Replaces Foundation's Settings system
 */
public final class WinterConfig {

    private static FileConfiguration config;

    // Gift Chest Configuration
    public record GiftChestConfig(
        boolean enabled,
        String title,
        boolean publicAllow,
        List<String> publicFormat,
        List<String> privateFormat
    ) {}

    // Dated Chest Configuration
    public record DatedChestConfig(
        boolean preview,
        int defaultYear
    ) {}

    // Weather Configuration
    public record WeatherConfig(
        boolean disable,
        boolean snowStorm
    ) {}

    // Snow Generation Configuration
    public record SnowGenerationConfig(
        boolean enabled,
        boolean melt,
        boolean onlyMeltUnnaturalSnow,
        boolean onlyMeltPluginSnow,
        boolean freezeWater,
        boolean destroyCrops,
        int periodTicks,
        int radius,
        boolean multiLayer,
        int requiredNeighborsToGrow,
        Set<Material> doNotPlaceOn,
        Set<Biome> ignoreBiomes,
        Map<Set<Material>, Set<Material>> freezeIgnore,
        boolean useAsyncProcessing,
        int batchSize
    ) {}

    // Terrain Configuration
    public record TerrainConfig(
        Set<Material> preventMelting,
        SnowGenerationConfig snowGeneration,
        BiomeDisguiseConfig biomeDisguise
    ) {}

    // Biome Disguise Configuration
    public record BiomeDisguiseConfig(
        boolean enabled,
        Biome biome
    ) {}

    // Snow Particle Configuration
    public record SnowConfig(
        boolean enabled,
        int periodTicks,
        int amount,
        double chaos,
        boolean realistic,
        boolean requireSnowBiomes,
        boolean ignoreVanished,
        int rangeHorizontal,
        int rangeVertical
    ) {}

    // Snowman Configuration
    public record SnowmanConfig(
        boolean disableMeltDamage,
        boolean preventTarget,
        PsychoConfig psycho,
        DamageConfig damage,
        TransformConfig transform
    ) {}

    // Psycho Snowman Configuration
    public record PsychoConfig(
        boolean convertNew,
        boolean convertExisting,
        boolean pumpkinHead,
        boolean despawn
    ) {}

    // Damage Configuration
    public record DamageConfig(
        double snowball
    ) {}

    // Transform Configuration
    public record TransformConfig(
        boolean enabled,
        int chancePercent,
        Set<EntityType> applicable
    ) {}

    // Main Configuration Holder
    public record Config(
        GiftChestConfig giftChest,
        DatedChestConfig datedChest,
        WeatherConfig weather,
        TerrainConfig terrain,
        SnowConfig snow,
        SnowmanConfig snowman,
        List<String> worlds,
        List<String> commandAliases,
        String prefix,
        String locale,
        boolean notifyUpdates,
        boolean notifyPromotions,
        int logLagOverMillis,
        List<String> debug
    ) {
        public boolean isWorldAllowed(String worldName) {
            return worlds.contains("*") || worlds.contains(worldName);
        }
    }

    private static Config currentConfig;

    /**
     * Load configuration from file
     */
    public static void load(@NotNull Winter plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        try {
            currentConfig = new Config(
                loadGiftChest(),
                loadDatedChest(),
                loadWeather(),
                loadTerrain(),
                loadSnow(),
                loadSnowman(),
                config.getStringList("Worlds"),
                config.getStringList("Command_Aliases"),
                config.getString("Prefix", "&f&lWinter &8//&7"),
                config.getString("Locale", "en"),
                config.getBoolean("Notify_Updates", true),
                config.getBoolean("Notify_Promotions", true),
                config.getInt("Log_Lag_Over_Milis", 100),
                config.getStringList("Debug")
            );

            plugin.log(Level.INFO, "Configuration loaded successfully!");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load configuration", e);
            throw new RuntimeException("Configuration load failed", e);
        }
    }

    /**
     * Get current configuration
     */
    public static Config get() {
        if (currentConfig == null) {
            throw new IllegalStateException("Configuration not loaded yet!");
        }
        return currentConfig;
    }

    // Configuration Loaders

    private static GiftChestConfig loadGiftChest() {
        ConfigurationSection section = config.getConfigurationSection("Gift_Chest");
        if (section == null) {
            return new GiftChestConfig(true, "&8[&fGift&8]", true,
                List.of("Click for", "free gifts!"),
                List.of("{receiver_1}", "{receiver_2}", "{receiver_3}"));
        }

        return new GiftChestConfig(
            section.getBoolean("Enabled", true),
            section.getString("Title", "&8[&fGift&8]"),
            section.getBoolean("Public.Allow", true),
            section.getStringList("Public.Format"),
            section.getStringList("Private.Format")
        );
    }

    private static DatedChestConfig loadDatedChest() {
        ConfigurationSection section = config.getConfigurationSection("Dated_Chest");
        if (section == null) {
            return new DatedChestConfig(true, Calendar.getInstance().get(Calendar.YEAR));
        }

        int defaultYear = section.getInt("Default_Year", Calendar.getInstance().get(Calendar.YEAR));

        // Warning for old year
        if (defaultYear != Calendar.getInstance().get(Calendar.YEAR)) {
            Winter.getInstance().log(Level.WARNING,
                "Dated_Chest.Default_Year is set to " + defaultYear + ". " +
                "Update it if your chests are not overlapping years.");
        }

        return new DatedChestConfig(
            section.getBoolean("Preview", true),
            defaultYear
        );
    }

    private static WeatherConfig loadWeather() {
        ConfigurationSection section = config.getConfigurationSection("Weather");
        if (section == null) {
            return new WeatherConfig(false, true);
        }

        return new WeatherConfig(
            section.getBoolean("Disable", false),
            section.getBoolean("Snow_Storm", true)
        );
    }

    private static TerrainConfig loadTerrain() {
        ConfigurationSection section = config.getConfigurationSection("Terrain");
        if (section == null) {
            return new TerrainConfig(
                Set.of(Material.ICE, Material.SNOW_BLOCK, Material.SNOW),
                loadSnowGeneration(),
                loadBiomeDisguise()
            );
        }

        Set<Material> preventMelting = section.getStringList("Prevent_Melting").stream()
            .map(name -> {
                try {
                    return Material.valueOf(name.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return new TerrainConfig(
            preventMelting,
            loadSnowGeneration(),
            loadBiomeDisguise()
        );
    }

    private static SnowGenerationConfig loadSnowGeneration() {
        ConfigurationSection section = config.getConfigurationSection("Terrain.Snow_Generation");
        if (section == null) {
            return new SnowGenerationConfig(true, false, true, true, true, false, 40, 3, true, 2,
                Set.of(), Set.of(), Map.of(), true, 100);
        }

        // Parse Do_Not_Place_On materials
        Set<Material> doNotPlaceOn = section.getStringList("Do_Not_Place_On").stream()
            .flatMap(name -> Arrays.stream(Material.values())
                .filter(mat -> mat.name().contains(name.toUpperCase())))
            .collect(Collectors.toSet());

        // Parse Ignore_Biomes
        Set<Biome> ignoreBiomes = section.getStringList("Ignore_Biomes").stream()
            .map(name -> {
                try {
                    return Biome.valueOf(name.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Parse Freeze_Ignore
        Map<Set<Material>, Set<Material>> freezeIgnore = new HashMap<>();
        ConfigurationSection freezeSection = section.getConfigurationSection("Freeze_Ignore");
        if (freezeSection != null) {
            for (String key : freezeSection.getKeys(false)) {
                Set<Material> neighbors = parseMaterialList(key);
                Set<Material> crops = parseMaterialList(freezeSection.getString(key, ""));
                freezeIgnore.put(neighbors, crops);
            }
        }

        return new SnowGenerationConfig(
            section.getBoolean("Enabled", true),
            section.getBoolean("Melt", false),
            section.getBoolean("Only_Melt_Unnatural_Snow", true),
            section.getBoolean("Only_Melt_Plugin_Snow", true),
            section.getBoolean("Freeze_Water", true),
            section.getBoolean("Destroy_Crops", false),
            section.getInt("Period_Ticks", 40),
            section.getInt("Radius", 3),
            section.getBoolean("Multi_Layer", true),
            section.getInt("Required_Neighbors_To_Grow", 2),
            doNotPlaceOn,
            ignoreBiomes,
            freezeIgnore,
            section.getBoolean("Use_Async_Processing", true),
            section.getInt("Batch_Size", 100)
        );
    }

    private static BiomeDisguiseConfig loadBiomeDisguise() {
        ConfigurationSection section = config.getConfigurationSection("Terrain.Disguise_Biomes");
        if (section == null) {
            return new BiomeDisguiseConfig(false, Biome.ICE_SPIKES);
        }

        Biome biome = Biome.ICE_SPIKES;
        try {
            String biomeName = section.getString("Biome", "ICE_SPIKES");
            biome = Biome.valueOf(biomeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Winter.getInstance().log(Level.WARNING, "Invalid biome in config, using ICE_SPIKES");
        }

        return new BiomeDisguiseConfig(
            section.getBoolean("Enabled", false),
            biome
        );
    }

    private static SnowConfig loadSnow() {
        ConfigurationSection section = config.getConfigurationSection("Snow");
        if (section == null) {
            return new SnowConfig(true, 1, 30, 0.0, true, false, true, 15, 10);
        }

        return new SnowConfig(
            section.getBoolean("Enabled", true),
            section.getInt("Period_Ticks", 1),
            section.getInt("Amount", 30),
            section.getDouble("Chaos", 0.0),
            section.getBoolean("Realistic", true),
            section.getBoolean("Require_Snow_Biomes", false),
            section.getBoolean("Ignore_Vanished", true),
            section.getInt("Range.Horizontal", 15),
            section.getInt("Range.Vertical", 10)
        );
    }

    private static SnowmanConfig loadSnowman() {
        ConfigurationSection section = config.getConfigurationSection("Snowman");
        if (section == null) {
            return new SnowmanConfig(true, true,
                new PsychoConfig(false, false, false, true),
                new DamageConfig(3.0),
                new TransformConfig(true, 15, Set.of(EntityType.ZOMBIE)));
        }

        return new SnowmanConfig(
            section.getBoolean("Disable_Melt_Damage", true),
            section.getBoolean("Prevent_Target", true),
            loadPsycho(),
            loadDamage(),
            loadTransform()
        );
    }

    private static PsychoConfig loadPsycho() {
        ConfigurationSection section = config.getConfigurationSection("Snowman.Psycho");
        if (section == null) {
            return new PsychoConfig(false, false, false, true);
        }

        return new PsychoConfig(
            section.getBoolean("Convert_New", false),
            section.getBoolean("Convert_Existing", false),
            section.getBoolean("Pumpkin_Head", false),
            section.getBoolean("Despawn", true)
        );
    }

    private static DamageConfig loadDamage() {
        ConfigurationSection section = config.getConfigurationSection("Snowman.Damage");
        if (section == null) {
            return new DamageConfig(3.0);
        }

        return new DamageConfig(
            section.getDouble("Snowball", 3.0)
        );
    }

    private static TransformConfig loadTransform() {
        ConfigurationSection section = config.getConfigurationSection("Snowman.Transform");
        if (section == null) {
            return new TransformConfig(true, 15, Set.of(EntityType.ZOMBIE));
        }

        Set<EntityType> applicable = section.getStringList("Applicable").stream()
            .map(name -> {
                try {
                    return EntityType.valueOf(name.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return new TransformConfig(
            section.getBoolean("Enabled", true),
            section.getInt("Chance_Percent", 15),
            applicable
        );
    }

    // Helper Methods

    private static Set<Material> parseMaterialList(String list) {
        if (list.equals("*")) {
            return Set.of(); // Special marker for "all"
        }

        return Arrays.stream(list.split(",\\s*"))
            .map(name -> {
                try {
                    return Material.valueOf(name.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Try partial match
                    return Arrays.stream(Material.values())
                        .filter(mat -> mat.name().contains(name.trim().toUpperCase()))
                        .findFirst()
                        .orElse(null);
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
