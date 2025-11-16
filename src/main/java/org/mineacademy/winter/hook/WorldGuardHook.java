package org.mineacademy.winter.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineacademy.winter.Winter;

import java.util.logging.Level;

/**
 * WorldGuard integration for Winter plugin
 * Handles region flag checks for snow-fall protection
 *
 * Note: Uses WorldGuard's standard 'snow-fall' flag (available in WG 7.0+)
 * Custom flag: 'winter-snowboost' allows configuring max snow height per region
 */
public final class WorldGuardHook {

    private static WorldGuardHook instance;
    private static IntegerFlag winterSnowBoostFlag; // Custom flag registered during onLoad
    private final Winter plugin;
    private boolean enabled = false;
    private StateFlag snowFallFlag;

    private WorldGuardHook(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    /**
     * Register custom WorldGuard flags during onLoad() phase
     * Must be called before WorldGuard's onEnable()
     */
    public static void registerCustomFlags(@NotNull Winter plugin) {
        try {
            // Check if WorldGuard is present
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
                return;
            }

            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            // Try to get existing flag first
            winterSnowBoostFlag = (IntegerFlag) registry.get("winter-snowboost");

            if (winterSnowBoostFlag == null) {
                // Register new custom flag
                winterSnowBoostFlag = new IntegerFlag("winter-snowboost");
                registry.register(winterSnowBoostFlag);
                plugin.log(Level.INFO, "Registered custom 'winter-snowboost' flag with WorldGuard");
            } else {
                plugin.log(Level.INFO, "Found existing 'winter-snowboost' flag");
            }

        } catch (Exception e) {
            plugin.log(Level.WARNING, "Could not register custom WorldGuard flags: " + e.getMessage());
        }
    }

    /**
     * Initialize WorldGuard integration
     */
    public static void initialize(@NotNull Winter plugin) {
        if (instance != null) {
            return;
        }

        instance = new WorldGuardHook(plugin);
        instance.setup();
    }

    /**
     * Get the WorldGuard hook instance
     */
    @Nullable
    public static WorldGuardHook getInstance() {
        return instance;
    }

    /**
     * Check if WorldGuard integration is enabled
     */
    public static boolean isEnabled() {
        return instance != null && instance.enabled;
    }

    /**
     * Setup WorldGuard integration
     */
    private void setup() {
        try {
            // Check if WorldGuard is loaded
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
                plugin.log(Level.INFO, "WorldGuard not found, snow-fall flag integration disabled");
                return;
            }

            // Get WorldGuard's standard snow-fall flag
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            snowFallFlag = (StateFlag) registry.get("snow-fall");

            if (snowFallFlag == null) {
                plugin.log(Level.WARNING, "WorldGuard snow-fall flag not found. " +
                    "Make sure you're using WorldGuard 7.0.0 or higher.");
                return;
            }

            enabled = true;
            plugin.log(Level.INFO, "WorldGuard integration enabled - respecting snow-fall flags");

        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to initialize WorldGuard integration", e);
            enabled = false;
        }
    }

    /**
     * Check if snow can fall at the given block location
     *
     * @param block The block to check
     * @return true if snow can fall (flag is ALLOW or not set), false if flag is DENY
     */
    public boolean canSnowFall(@NotNull Block block) {
        if (!enabled || snowFallFlag == null) {
            return true; // Allow if WorldGuard is not enabled
        }

        try {
            // Convert Bukkit location to WorldEdit location
            Location weLocation = BukkitAdapter.adapt(block.getLocation());

            // Get region container and query
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            // Get applicable regions
            ApplicableRegionSet regions = query.getApplicableRegions(weLocation);

            // Check the snow-fall flag
            // testState returns null if not set, ALLOW if allowed, DENY if denied
            StateFlag.State state = regions.queryState(null, snowFallFlag);

            // If state is DENY, return false
            // If state is ALLOW or null (not set), return true
            return state != StateFlag.State.DENY;

        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error checking WorldGuard snow-fall flag at " +
                block.getLocation(), e);
            return true; // Default to allow on error
        }
    }

    /**
     * Check if snow can fall at the given location (batch check optimization)
     *
     * @param block The block to check
     * @return true if snow can fall, false otherwise
     */
    public boolean canSnowFallFast(@NotNull Block block) {
        // Fast path - if not enabled, always allow
        if (!enabled) {
            return true;
        }

        // Use the full check
        return canSnowFall(block);
    }

    /**
     * Get the maximum snow height for this location
     * Takes into account both snow-fall and winter-snowboost flags
     *
     * @param block The block to check
     * @param defaultMaxHeight The default max height from config
     * @return The max height for this location, or -1 if snow cannot fall here
     */
    public int getMaxSnowHeight(@NotNull Block block, int defaultMaxHeight) {
        if (!enabled || snowFallFlag == null) {
            return defaultMaxHeight; // WorldGuard not enabled, use default
        }

        try {
            Location weLocation = BukkitAdapter.adapt(block.getLocation());
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(weLocation);

            // First check snow-fall flag - if DENY, snow cannot fall at all
            StateFlag.State snowFallState = regions.queryState(null, snowFallFlag);
            if (snowFallState == StateFlag.State.DENY) {
                return -1; // snow-fall: deny takes precedence
            }

            // Check winter-snowboost flag
            if (winterSnowBoostFlag != null) {
                Integer boostHeight = regions.queryValue(null, winterSnowBoostFlag);
                if (boostHeight != null && boostHeight > 0) {
                    return boostHeight; // Use region-specific max height
                }
            }

            // No boost flag set, use default
            return defaultMaxHeight;

        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error checking WorldGuard winter-snowboost flag at " +
                block.getLocation(), e);
            return defaultMaxHeight; // Default on error
        }
    }

    /**
     * Disable the integration (for testing or reload)
     */
    public void disable() {
        enabled = false;
        snowFallFlag = null;
    }

    /**
     * Get debug information about the current state
     */
    public String getDebugInfo() {
        return String.format("WorldGuard Integration: enabled=%s, snowFallFlag=%s, winterSnowBoostFlag=%s",
            enabled, snowFallFlag != null ? "present" : "null",
            winterSnowBoostFlag != null ? "present" : "null");
    }
}
