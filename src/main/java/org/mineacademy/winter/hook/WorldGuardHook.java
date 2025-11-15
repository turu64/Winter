package org.mineacademy.winter.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
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
 */
public final class WorldGuardHook {

    private static WorldGuardHook instance;
    private final Winter plugin;
    private boolean enabled = false;
    private StateFlag snowFallFlag;

    private WorldGuardHook(@NotNull Winter plugin) {
        this.plugin = plugin;
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
        return String.format("WorldGuard Integration: enabled=%s, snowFallFlag=%s",
            enabled, snowFallFlag != null ? "present" : "null");
    }
}
