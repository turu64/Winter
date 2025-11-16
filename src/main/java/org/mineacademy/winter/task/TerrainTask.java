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
import org.mineacademy.winter.util.SnowMetadataManager;

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

        // Check for fragile entities/blocks that would be destroyed by snow
        if (hasFragileEntitiesOrBlocks(above, topBlock)) {
            return;
        }

        Material topType = topBlock.getType();

        // Get max snow height for this location (respects WorldGuard winter-snowboost flag)
        int maxHeight = getMaxSnowHeightForLocation(above, config.maxHeight());
        if (maxHeight < 0) {
            return; // snow-fall: deny, cannot place snow at all
        }

        // Check current snow height before placing
        int currentHeight = getSnowHeight(topBlock);
        if (currentHeight >= maxHeight) {
            return; // Already at max height
        }

        // Freeze water
        if (config.freezeWater() && topType == Material.WATER) {
            // Check freeze ignore rules
            if (shouldIgnoreFreeze(topBlock, config)) {
                return;
            }

            topBlock.setType(Material.ICE);
            // Mark ice as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(topBlock);
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

            // Mark snow as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(above);
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

            // Mark snow as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(above);
        }
    }

    /**
     * Grow snow layers
     */
    private void growSnow(@NotNull Block snowBlock, @NotNull WinterConfig.SnowGenerationConfig config) {
        if (!(snowBlock.getBlockData() instanceof Snow snow)) {
            return;
        }

        // Get max snow height for this location (respects WorldGuard winter-snowboost flag)
        int maxHeight = getMaxSnowHeightForLocation(snowBlock, config.maxHeight());
        if (maxHeight < 0) {
            return; // snow-fall: deny, cannot grow snow
        }

        int currentLayers = snow.getLayers();
        if (currentLayers >= snow.getMaximumLayers()) {
            // Check if we can grow vertically
            int currentHeight = getSnowHeight(snowBlock);
            if (currentHeight >= maxHeight) {
                return; // Already at max height
            }

            // Convert to snow block
            snowBlock.setType(Material.SNOW_BLOCK);
            // Mark snow block as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(snowBlock);

            // Continue growing on top if not at max height
            Block above = snowBlock.getRelative(BlockFace.UP);
            if (above.getType() == Material.AIR && currentHeight + 1 < maxHeight) {
                above.setType(Material.SNOW);
                if (above.getBlockData() instanceof Snow newSnow) {
                    newSnow.setLayers(1);
                    above.setBlockData(newSnow);
                }
                // Mark new snow as plugin-placed
                SnowMetadataManager.markAsPluginPlaced(above);
            }
            return;
        }

        // Check neighbors to see if we should grow
        int validNeighbors = countValidNeighbors(snowBlock, currentLayers);

        if (validNeighbors >= config.requiredNeighborsToGrow()) {
            snow.setLayers(currentLayers + 1);
            snowBlock.setBlockData(snow);
            // Snow layer is growing, keep it marked as plugin-placed
            // (it was already marked when first placed)
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
     * Melt snow at a location (respects PDC metadata)
     * Melts from the topmost snow layer for multi-block stacks
     */
    private void meltSnow(@NotNull Block topBlock, @NotNull WinterConfig.SnowGenerationConfig config) {
        // Check if we should ignore this snow (natural snow in snowy biomes)
        if (config.onlyMeltUnnaturalSnow()) {
            if (topBlock.getBiome().name().contains("SNOWY") ||
                topBlock.getBiome().name().contains("ICE") ||
                topBlock.getBiome().name().contains("FROZEN") ||
                topBlock.getY() > 90) {
                return;
            }
        }

        // Get max snow height for this location to search for snow blocks
        // Use absolute value to handle snow-fall: deny case (we can still melt existing snow)
        int maxHeight = Math.abs(getMaxSnowHeightForLocation(topBlock, config.maxHeight()));

        // Find the topmost snow block/layer above this block
        Block topmostSnow = null;
        Block currentBlock = topBlock.getRelative(BlockFace.UP);

        // Search upward for snow (up to maxHeight blocks)
        for (int i = 0; i < maxHeight; i++) {
            Material type = currentBlock.getType();
            if (type == Material.SNOW || type == Material.SNOW_BLOCK) {
                topmostSnow = currentBlock;
                currentBlock = currentBlock.getRelative(BlockFace.UP);
            } else {
                break; // Hit non-snow block or air
            }
        }

        // If no snow found above, nothing to melt
        if (topmostSnow == null) {
            // Check if topBlock itself is ice that needs thawing
            if (config.freezeWater() && topBlock.getType() == Material.ICE) {
                if (config.onlyMeltPluginSnow() && !SnowMetadataManager.isPluginPlaced(topBlock)) {
                    return; // Skip player-placed ice
                }

                topBlock.setType(Material.WATER);
                if (topBlock.getBlockData() instanceof Levelled water) {
                    water.setLevel(0);
                    topBlock.setBlockData(water);
                }
                if (config.onlyMeltPluginSnow()) {
                    SnowMetadataManager.unmarkBlock(topBlock);
                }
            }
            return;
        }

        // Check metadata if onlyMeltPluginSnow is enabled
        if (config.onlyMeltPluginSnow()) {
            if (!SnowMetadataManager.isPluginPlaced(topmostSnow)) {
                return; // Skip player-placed snow
            }
        }

        // Melt the topmost snow
        if (topmostSnow.getType() == Material.SNOW) {
            // Reduce snow layers
            if (topmostSnow.getBlockData() instanceof Snow snow) {
                if (snow.getLayers() > 1) {
                    snow.setLayers(snow.getLayers() - 1);
                    topmostSnow.setBlockData(snow);
                    // Keep metadata
                    if (config.onlyMeltPluginSnow()) {
                        SnowMetadataManager.markAsPluginPlaced(topmostSnow);
                    }
                } else {
                    topmostSnow.setType(Material.AIR);
                    // Remove metadata
                    if (config.onlyMeltPluginSnow()) {
                        SnowMetadataManager.unmarkBlock(topmostSnow);
                    }
                }
            }
        } else if (topmostSnow.getType() == Material.SNOW_BLOCK) {
            // Convert snow block to 8 layers (gradual melting)
            topmostSnow.setType(Material.SNOW);
            if (topmostSnow.getBlockData() instanceof Snow snow) {
                snow.setLayers(8);
                topmostSnow.setBlockData(snow);
            }
            // Keep metadata
            if (config.onlyMeltPluginSnow()) {
                SnowMetadataManager.markAsPluginPlaced(topmostSnow);
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

        // Only place snow on opaque blocks (blocks that completely block light)
        // This prevents snow from accumulating on glass, leaves, ice, etc.
        return topBlock.getType().isOccluding() || topBlock.getType() == Material.WATER;
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
     * Get the maximum snow height for a location, respecting WorldGuard's winter-snowboost flag
     *
     * @param block The block to check
     * @param defaultMaxHeight The default max height from config
     * @return Max height for this location, or -1 if snow-fall: deny
     */
    private int getMaxSnowHeightForLocation(@NotNull Block block, int defaultMaxHeight) {
        if (!org.mineacademy.winter.hook.WorldGuardHook.isEnabled()) {
            return defaultMaxHeight;
        }

        var hook = org.mineacademy.winter.hook.WorldGuardHook.getInstance();
        if (hook == null) {
            return defaultMaxHeight;
        }

        return hook.getMaxSnowHeight(block, defaultMaxHeight);
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

    /**
     * Check if there are fragile entities or blocks that would be destroyed by snow placement
     * This includes item frames, paintings, armor stands, torches, levers, buttons, etc.
     *
     * @param snowLocation Where snow would be placed
     * @param baseBlock The block below where snow would be placed
     * @return true if there are fragile entities/blocks (don't place snow), false otherwise
     */
    private boolean hasFragileEntitiesOrBlocks(@NotNull Block snowLocation, @NotNull Block baseBlock) {
        // Check for entities at the snow location and on the base block
        // Entities like item frames, paintings can be on walls adjacent to these blocks
        for (org.bukkit.entity.Entity entity : snowLocation.getWorld().getNearbyEntities(
            snowLocation.getLocation().add(0.5, 0.5, 0.5), 1.0, 1.0, 1.0)) {

            switch (entity.getType()) {
                // Wall-mounted entities
                case ITEM_FRAME, GLOW_ITEM_FRAME, PAINTING -> {
                    return true; // Don't place snow near these
                }
                // Ground entities
                case ARMOR_STAND -> {
                    // Check if armor stand is at or near the snow location
                    if (entity.getLocation().getBlockY() >= baseBlock.getY() &&
                        entity.getLocation().getBlockY() <= snowLocation.getY()) {
                        return true;
                    }
                }
            }
        }

        // Check for fragile blocks on the base block and adjacent blocks
        // These blocks can be destroyed when snow is placed
        if (isFragileBlock(baseBlock)) {
            return true;
        }

        // Check adjacent blocks for wall-mounted fragile blocks
        for (BlockFace face : new BlockFace[]{
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        }) {
            Block adjacent = baseBlock.getRelative(face);
            if (isFragileBlock(adjacent)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a block is fragile and could be destroyed by snow placement
     * Also includes interactive blocks that would become non-functional
     *
     * @param block The block to check
     * @return true if the block is fragile or interactive
     */
    private boolean isFragileBlock(@NotNull Block block) {
        Material type = block.getType();
        String name = type.name();

        // Torch variants
        if (name.contains("TORCH") || name.contains("LANTERN")) {
            return true;
        }

        // Redstone components
        if (name.contains("LEVER") || name.contains("BUTTON") ||
            name.contains("PRESSURE_PLATE") || name.contains("TRIPWIRE")) {
            return true;
        }

        // Signs and banners
        if (name.contains("SIGN") || name.contains("BANNER")) {
            return true;
        }

        // Rails
        if (name.contains("RAIL")) {
            return true;
        }

        // Flowers and plants
        if (name.contains("FLOWER") || name.contains("TULIP") ||
            name.contains("ORCHID") || name.contains("ALLIUM") ||
            name.contains("DANDELION") || name.contains("POPPY") ||
            name.contains("ROSE") || name.contains("LILY")) {
            return true;
        }

        // Carpets
        if (name.contains("CARPET")) {
            return true;
        }

        // Interactive blocks that need access (chest, furnace, etc.)
        if (name.contains("CHEST") || name.contains("BARREL") ||
            name.contains("SHULKER_BOX") || name.contains("FURNACE") ||
            name.contains("SMOKER") || name.contains("BLAST_FURNACE") ||
            name.contains("HOPPER") || name.contains("DROPPER") ||
            name.contains("DISPENSER")) {
            return true;
        }

        // Crafting/Work stations
        if (name.contains("TABLE") || name.contains("ANVIL") ||
            name.contains("LOOM") || name.contains("GRINDSTONE") ||
            name.contains("STONECUTTER")) {
            return true;
        }

        // Other specific fragile/interactive blocks
        return switch (type) {
            // Fragile blocks
            case DEAD_BUSH, GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT,
                 WHEAT, CARROTS, POTATOES, BEETROOTS,
                 SWEET_BERRY_BUSH, CAKE, CANDLE,
                 REDSTONE_WIRE, REPEATER, COMPARATOR,
                 SCAFFOLDING, TURTLE_EGG, SNOW,
                 // Interactive blocks
                 BEACON, BREWING_STAND, LECTERN, COMPOSTER,
                 CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON,
                 RESPAWN_ANCHOR, LODESTONE, FLOWER_POT,
                 DAYLIGHT_DETECTOR, CAMPFIRE, SOUL_CAMPFIRE,
                 BEE_NEST, BEEHIVE, BELL -> true;
            default -> false;
        };
    }
}
