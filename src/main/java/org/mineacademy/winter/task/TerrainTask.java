package org.mineacademy.winter.task;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern terrain task using Paper API
 * Generates or melts snow around players
 */
public final class TerrainTask implements Runnable {

    private final Winter plugin;

    public TerrainTask(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = WinterConfig.get().terrain().snowGeneration();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Check if world is allowed
            if (!WinterConfig.get().isWorldAllowed(player.getWorld().getName())) {
                continue;
            }

            processTerrainAroundPlayer(player, config);
        }
    }

    /**
     * Process terrain around a player
     */
    private void processTerrainAroundPlayer(@NotNull Player player,
                                            @NotNull WinterConfig.SnowGenerationConfig config) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int radius = config.radius();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Random chance to process this block (reduces CPU load)
                if (random.nextInt(10) > 3) {
                    continue;
                }

                Location checkLoc = playerLoc.clone().add(x, 0, z);

                // Find the highest block at this location
                Block topBlock = world.getHighestBlockAt(checkLoc);

                if (config.melt()) {
                    meltSnow(topBlock, config);
                } else {
                    placeSnow(topBlock, config);
                }
            }
        }
    }

    /**
     * Place snow on a block
     */
    private void placeSnow(@NotNull Block topBlock, @NotNull WinterConfig.SnowGenerationConfig config) {
        Block above = topBlock.getRelative(BlockFace.UP);

        // Check WorldGuard protection first
        if (!canSnowFallHere(above)) {
            return;
        }

        // Check if we can place snow here
        if (!canPlaceSnow(topBlock, above, config)) {
            return;
        }

        Material topType = topBlock.getType();

        // Check current snow height before placing
        int currentHeight = getSnowHeight(topBlock);
        if (currentHeight >= config.maxHeight()) {
            return; // Already at max height
        }

        // Freeze water
        if (config.freezeWater() && topType == Material.WATER) {
            // Check freeze ignore rules
            if (shouldIgnoreFreeze(topBlock, config)) {
                return;
            }

            topBlock.setType(Material.ICE);
            return;
        }

        // Place snow layer
        if (above.getType() == Material.AIR) {
            above.setType(Material.SNOW);

            // Set snow layer height
            if (above.getBlockData() instanceof Snow snow) {
                snow.setLayers(1);
                above.setBlockData(snow);
            }
        } else if (config.multiLayer() && above.getType() == Material.SNOW) {
            // Grow existing snow
            growSnow(above, config);
        }
        // Allow stacking on SNOW_BLOCK for multi-block height
        else if (config.multiLayer() && topType == Material.SNOW_BLOCK && above.getType() == Material.AIR) {
            above.setType(Material.SNOW);
            if (above.getBlockData() instanceof Snow snow) {
                snow.setLayers(1);
                above.setBlockData(snow);
            }
        }
    }

    /**
     * Grow snow layers
     */
    private void growSnow(@NotNull Block snowBlock, @NotNull WinterConfig.SnowGenerationConfig config) {
        if (!(snowBlock.getBlockData() instanceof Snow snow)) {
            return;
        }

        int currentLayers = snow.getLayers();
        if (currentLayers >= snow.getMaximumLayers()) {
            // Check if we can grow vertically
            int currentHeight = getSnowHeight(snowBlock);
            if (currentHeight >= config.maxHeight()) {
                return; // Already at max height
            }

            // Convert to snow block
            snowBlock.setType(Material.SNOW_BLOCK);

            // Continue growing on top if not at max height
            Block above = snowBlock.getRelative(BlockFace.UP);
            if (above.getType() == Material.AIR && currentHeight + 1 < config.maxHeight()) {
                above.setType(Material.SNOW);
                if (above.getBlockData() instanceof Snow newSnow) {
                    newSnow.setLayers(1);
                    above.setBlockData(newSnow);
                }
            }
            return;
        }

        // Check neighbors to see if we should grow
        int validNeighbors = countValidNeighbors(snowBlock, currentLayers);

        if (validNeighbors >= config.requiredNeighborsToGrow()) {
            snow.setLayers(currentLayers + 1);
            snowBlock.setBlockData(snow);
        }
    }

    /**
     * Count valid neighbors with same or higher snow level
     */
    private int countValidNeighbors(@NotNull Block snowBlock, int currentLayers) {
        int count = 0;
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (BlockFace face : faces) {
            Block neighbor = snowBlock.getRelative(face);

            if (neighbor.getType() == Material.SNOW_BLOCK) {
                count++;
            } else if (neighbor.getType() == Material.SNOW) {
                if (neighbor.getBlockData() instanceof Snow neighborSnow) {
                    if (neighborSnow.getLayers() >= currentLayers) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Melt snow at a location
     */
    private void meltSnow(@NotNull Block topBlock, @NotNull WinterConfig.SnowGenerationConfig config) {
        Block above = topBlock.getRelative(BlockFace.UP);

        // Check if we should ignore this snow (natural snow in snowy biomes)
        if (config.onlyMeltUnnaturalSnow()) {
            if (topBlock.getBiome().name().contains("SNOWY") ||
                topBlock.getBiome().name().contains("ICE") ||
                topBlock.getBiome().name().contains("FROZEN") ||
                topBlock.getY() > 90) {
                return;
            }
        }

        // Melt snow layers
        if (above.getType() == Material.SNOW) {
            if (above.getBlockData() instanceof Snow snow) {
                if (snow.getLayers() > 1) {
                    snow.setLayers(snow.getLayers() - 1);
                    above.setBlockData(snow);
                } else {
                    above.setType(Material.AIR);
                }
            }
        }

        // Melt snow blocks
        if (topBlock.getType() == Material.SNOW_BLOCK) {
            topBlock.setType(Material.AIR);
        }

        // Thaw ice
        if (config.freezeWater() && topBlock.getType() == Material.ICE) {
            topBlock.setType(Material.WATER);

            // Set water level
            if (topBlock.getBlockData() instanceof Levelled water) {
                water.setLevel(0); // Full water block
                topBlock.setBlockData(water);
            }
        }
    }

    /**
     * Check if we can place snow on this block
     */
    private boolean canPlaceSnow(@NotNull Block topBlock, @NotNull Block above,
                                 @NotNull WinterConfig.SnowGenerationConfig config) {
        // Above must be air or existing snow
        if (above.getType() != Material.AIR && above.getType() != Material.SNOW) {
            return false;
        }

        Material topType = topBlock.getType();

        // Check ignore biomes
        if (config.ignoreBiomes().contains(topBlock.getBiome())) {
            return false;
        }

        // Check Do_Not_Place_On list
        for (Material ignored : config.doNotPlaceOn()) {
            if (topType.name().contains(ignored.name())) {
                return false;
            }
        }

        // Special checks
        if (topType == Material.WATER && !config.freezeWater()) {
            return false;
        }

        // Don't place on crops unless configured
        if (!config.destroyCrops() && isCrop(topType)) {
            return false;
        }

        // Block must be solid (or water for freezing)
        return topBlock.getType().isSolid() || topBlock.getType() == Material.WATER;
    }

    /**
     * Check if should ignore freezing at this location
     */
    private boolean shouldIgnoreFreeze(@NotNull Block block,
                                       @NotNull WinterConfig.SnowGenerationConfig config) {
        BlockFace[] faces = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
        };

        for (var entry : config.freezeIgnore().entrySet()) {
            Set<Material> neighborMaterials = entry.getKey();
            Set<Material> cropMaterials = entry.getValue();

            // Check if any neighbor matches
            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                Material neighborType = neighbor.getType();

                // Check if this neighbor matches the freeze ignore rule
                boolean neighborMatches = neighborMaterials.isEmpty() || // Empty set means "*"
                    neighborMaterials.stream().anyMatch(mat ->
                        mat.name().equals(neighborType.name()) ||
                        neighborType.name().contains(mat.name()));

                if (neighborMatches) {
                    // Check if there's a crop on the neighbor
                    Block aboveNeighbor = neighbor.getRelative(BlockFace.UP);
                    Material cropType = aboveNeighbor.getType();

                    boolean cropMatches = cropMaterials.isEmpty() || // Empty set means "*"
                        cropMaterials.stream().anyMatch(mat ->
                            mat.name().equals(cropType.name()) ||
                            cropType.name().contains(mat.name()));

                    if (cropMatches) {
                        return true; // Ignore freezing
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if material is a crop
     */
    private boolean isCrop(@NotNull Material material) {
        return material.name().contains("WHEAT") ||
               material.name().contains("CARROTS") ||
               material.name().contains("POTATOES") ||
               material.name().contains("BEETROOTS") ||
               material.name().contains("MELON") ||
               material.name().contains("PUMPKIN") ||
               material.name().contains("SUGAR_CANE") ||
               material.name().contains("CACTUS") ||
               material.name().contains("BAMBOO") ||
               material == Material.FARMLAND;
    }

    /**
     * Check if snow can fall at this location (WorldGuard integration)
     *
     * @param block The block where snow would be placed
     * @return true if snow can fall (allowed or WorldGuard not present), false if denied
     */
    private boolean canSnowFallHere(@NotNull Block block) {
        // Check if WorldGuard integration is enabled
        if (!org.mineacademy.winter.hook.WorldGuardHook.isEnabled()) {
            return true; // Allow if WorldGuard is not present
        }

        // Get WorldGuard hook and check snow-fall flag
        var hook = org.mineacademy.winter.hook.WorldGuardHook.getInstance();
        if (hook == null) {
            return true; // Allow if hook is not initialized
        }

        // Check the snow-fall flag for this location
        return hook.canSnowFallFast(block);
    }

    /**
     * Calculate the current snow height at a block location
     * Counts both snow layers and snow blocks below
     *
     * @param block The block to check (can be snow, snow_block, or the block below snow)
     * @return The height in blocks (1 = 1 block high, 2 = 2 blocks high, etc.)
     */
    private int getSnowHeight(@NotNull Block block) {
        int height = 0;

        // If current block is a snow block, count it
        if (block.getType() == Material.SNOW_BLOCK) {
            height++;
        } else if (block.getType() != Material.SNOW) {
            // Not snow at all, height is 0
            return 0;
        }

        // Count snow blocks below (up to reasonable limit to prevent infinite loops)
        Block below = block.getRelative(BlockFace.DOWN);
        while (below.getType() == Material.SNOW_BLOCK && height < 10) {
            height++;
            below = below.getRelative(BlockFace.DOWN);
        }

        return height;
    }
}
