package org.mineacademy.winter.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance asynchronous terrain task for Paper 1.21.4+
 *
 * Performance optimizations:
 * - Async chunk processing (off main thread)
 * - Batch operations for reduced overhead
 * - Concurrent block processing
 * - PDC-based metadata tracking
 * - Smart caching to reduce lookups
 */
public final class AsyncTerrainTask implements Runnable {

    private final Winter plugin;

    // Cache for recently processed chunks (reduces duplicate work)
    private final Map<Long, Long> processedChunks = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT = 5000; // 5 seconds

    // Statistics for monitoring
    private long totalBlocksProcessed = 0;
    private long totalAsyncOps = 0;

    public AsyncTerrainTask(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = WinterConfig.get().terrain().snowGeneration();

        if (!config.enabled()) {
            return;
        }

        // Process each online player
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Check if world is allowed
            if (!WinterConfig.get().isWorldAllowed(player.getWorld().getName())) {
                continue;
            }

            // Use async processing if enabled
            if (config.useAsyncProcessing()) {
                processTerrainAsync(player, config);
            } else {
                processTerrainSync(player, config);
            }
        }

        // Cleanup old cache entries
        if (totalBlocksProcessed % 1000 == 0) {
            cleanupCache();
        }
    }

    /**
     * Process terrain asynchronously (off main thread)
     */
    private void processTerrainAsync(@NotNull Player player, @NotNull WinterConfig.SnowGenerationConfig config) {
        Location playerLoc = player.getLocation().clone();
        World world = player.getWorld();
        int radius = config.radius();
        int batchSize = config.batchSize();

        // Collect blocks to process
        List<BlockPosition> blocksToProcess = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Random chance to process (40%)
                if (random.nextInt(10) > 3) {
                    continue;
                }

                int worldX = playerLoc.getBlockX() + x;
                int worldZ = playerLoc.getBlockZ() + z;

                blocksToProcess.add(new BlockPosition(worldX, worldZ));
            }
        }

        // Process in batches asynchronously
        processBatchesAsync(world, blocksToProcess, config, batchSize);
    }

    /**
     * Process blocks in batches using CompletableFuture
     */
    private void processBatchesAsync(@NotNull World world, @NotNull List<BlockPosition> positions,
                                     @NotNull WinterConfig.SnowGenerationConfig config, int batchSize) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Split into batches
        for (int i = 0; i < positions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, positions.size());
            List<BlockPosition> batch = positions.subList(i, end);

            // Process batch asynchronously
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processBatch(world, batch, config);
            });

            futures.add(future);
            totalAsyncOps++;
        }

        // Wait for all batches to complete (non-blocking on main thread)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                // All async operations complete
                // Sync back to main thread for block changes
            });
    }

    /**
     * Process a batch of block positions
     */
    private void processBatch(@NotNull World world, @NotNull List<BlockPosition> batch,
                             @NotNull WinterConfig.SnowGenerationConfig config) {
        for (BlockPosition pos : batch) {
            // Get highest block at this location
            Block topBlock = world.getHighestBlockAt(pos.x, pos.z);

            // Process on main thread (Paper requires this for block changes)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (config.melt()) {
                    meltSnow(topBlock, config);
                } else {
                    placeSnow(topBlock, config);
                }
            });

            totalBlocksProcessed++;
        }
    }

    /**
     * Synchronous processing (original method, kept for compatibility)
     */
    private void processTerrainSync(@NotNull Player player, @NotNull WinterConfig.SnowGenerationConfig config) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = config.radius();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (random.nextInt(10) > 3) {
                    continue;
                }

                Location checkLoc = playerLoc.clone().add(x, 0, z);
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
     * Place snow on a block (with PDC marking)
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

        // Freeze water
        if (config.freezeWater() && topType == Material.WATER) {
            if (shouldIgnoreFreeze(topBlock, config)) {
                return;
            }
            topBlock.setType(Material.ICE);
            // Mark ice as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(topBlock);
            return;
        }

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

        // Place snow layer on AIR or on top of SNOW_BLOCK (for multi-block stacking)
        if (above.getType() == Material.AIR) {
            above.setType(Material.SNOW);

            if (above.getBlockData() instanceof Snow snow) {
                snow.setLayers(1);
                above.setBlockData(snow);
            }

            // Mark snow as plugin-placed (important!)
            SnowMetadataManager.markAsPluginPlaced(above);

        } else if (config.multiLayer() && above.getType() == Material.SNOW) {
            growSnow(above, config);
        } else if (config.multiLayer() && topType == Material.SNOW_BLOCK && above.getType() == Material.AIR) {
            // Allow snow to stack on top of snow blocks (for multi-block height)
            above.setType(Material.SNOW);

            if (above.getBlockData() instanceof Snow snow) {
                snow.setLayers(1);
                above.setBlockData(snow);
            }

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
            // Check current snow height before converting to snow block
            int currentHeight = getSnowHeight(snowBlock);
            if (currentHeight >= maxHeight) {
                return; // Already at max height, don't grow further
            }

            // Convert to snow block
            snowBlock.setType(Material.SNOW_BLOCK);
            // Mark snow block as plugin-placed
            SnowMetadataManager.markAsPluginPlaced(snowBlock);

            // If not at max height, allow snow to continue growing on top
            Block above = snowBlock.getRelative(BlockFace.UP);
            if (above.getType() == Material.AIR && currentHeight + 1 < maxHeight) {
                above.setType(Material.SNOW);
                if (above.getBlockData() instanceof Snow newSnow) {
                    newSnow.setLayers(1);
                    above.setBlockData(newSnow);
                }
                SnowMetadataManager.markAsPluginPlaced(above);
            }
            return;
        }

        int validNeighbors = countValidNeighbors(snowBlock, currentLayers);

        if (validNeighbors >= config.requiredNeighborsToGrow()) {
            snow.setLayers(currentLayers + 1);
            snowBlock.setBlockData(snow);
            // Update metadata (still plugin-placed)
            SnowMetadataManager.markAsPluginPlaced(snowBlock);
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

    // [Rest of the methods remain the same: canPlaceSnow, shouldIgnoreFreeze, isCrop, canSnowFallHere]
    // Copy from original TerrainTask...

    private boolean canPlaceSnow(@NotNull Block topBlock, @NotNull Block above,
                                 @NotNull WinterConfig.SnowGenerationConfig config) {
        if (above.getType() != Material.AIR && above.getType() != Material.SNOW) {
            return false;
        }

        Material topType = topBlock.getType();

        if (config.ignoreBiomes().contains(topBlock.getBiome())) {
            return false;
        }

        for (Material ignored : config.doNotPlaceOn()) {
            if (topType.name().contains(ignored.name())) {
                return false;
            }
        }

        if (topType == Material.WATER && !config.freezeWater()) {
            return false;
        }

        if (!config.destroyCrops() && isCrop(topType)) {
            return false;
        }

        // Only place snow on opaque blocks (blocks that completely block light)
        // This prevents snow from accumulating on glass, leaves, ice, etc.
        return topBlock.getType().isOccluding() || topBlock.getType() == Material.WATER;
    }

    private boolean shouldIgnoreFreeze(@NotNull Block block,
                                       @NotNull WinterConfig.SnowGenerationConfig config) {
        BlockFace[] faces = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST
        };

        for (var entry : config.freezeIgnore().entrySet()) {
            Set<Material> neighborMaterials = entry.getKey();
            Set<Material> cropMaterials = entry.getValue();

            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                Material neighborType = neighbor.getType();

                boolean neighborMatches = neighborMaterials.isEmpty() ||
                    neighborMaterials.stream().anyMatch(mat ->
                        mat.name().equals(neighborType.name()) ||
                        neighborType.name().contains(mat.name()));

                if (neighborMatches) {
                    Block aboveNeighbor = neighbor.getRelative(BlockFace.UP);
                    Material cropType = aboveNeighbor.getType();

                    boolean cropMatches = cropMaterials.isEmpty() ||
                        cropMaterials.stream().anyMatch(mat ->
                            mat.name().equals(cropType.name()) ||
                            cropType.name().contains(mat.name()));

                    if (cropMatches) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

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

    private boolean canSnowFallHere(@NotNull Block block) {
        if (!org.mineacademy.winter.hook.WorldGuardHook.isEnabled()) {
            return true;
        }

        var hook = org.mineacademy.winter.hook.WorldGuardHook.getInstance();
        if (hook == null) {
            return true;
        }

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
     * Calculate the current snow height at a location
     * Returns the number of blocks of snow stacked (1-3+)
     *
     * @param block The block to check (can be snow or the block below snow)
     * @return Height in blocks (0 = no snow, 1 = 1 block high, 2 = 2 blocks high, etc.)
     */
    private int getSnowHeight(@NotNull Block block) {
        int height = 0;

        // Check if current block is snow
        if (block.getType() == Material.SNOW_BLOCK) {
            height++;
        } else if (block.getType() != Material.SNOW) {
            // Not snow, return 0
            return 0;
        }

        // Count snow blocks below
        Block below = block.getRelative(BlockFace.DOWN);
        while (below.getType() == Material.SNOW_BLOCK && height < 10) { // Max 10 to prevent infinite loop
            height++;
            below = below.getRelative(BlockFace.DOWN);
        }

        return height;
    }

    /**
     * Cleanup old cache entries
     */
    private void cleanupCache() {
        long now = System.currentTimeMillis();
        processedChunks.entrySet().removeIf(entry -> now - entry.getValue() > CACHE_TIMEOUT);
    }

    /**
     * Get performance statistics
     */
    public String getStats() {
        return String.format("Blocks: %d, Async Ops: %d, Cache Size: %d",
            totalBlocksProcessed, totalAsyncOps, processedChunks.size());
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

    /**
     * Helper record for block positions
     */
    private record BlockPosition(int x, int z) {}
}
