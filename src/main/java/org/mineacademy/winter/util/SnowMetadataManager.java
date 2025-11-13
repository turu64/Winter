package org.mineacademy.winter.util;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;

/**
 * Manages persistent metadata for snow blocks using Paper's PDC system.
 * This allows tracking which snow was placed by the plugin vs. by players.
 *
 * Performance optimized:
 * - PDC operations are extremely fast (in-memory)
 * - No file I/O on each block
 * - Thread-safe by design
 */
public final class SnowMetadataManager {

    private static final NamespacedKey PLUGIN_PLACED_KEY;

    static {
        PLUGIN_PLACED_KEY = new NamespacedKey(Winter.getInstance(), "plugin_placed");
    }

    /**
     * Mark a snow block as placed by this plugin
     *
     * @param block The snow block to mark
     */
    public static void markAsPluginPlaced(@NotNull Block block) {
        try {
            // Get chunk's persistent data container
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();

            // Create a unique key for this block within the chunk
            String blockKey = getBlockKey(block);
            NamespacedKey key = new NamespacedKey(Winter.getInstance(), blockKey);

            // Mark as plugin-placed (value doesn't matter, just presence)
            pdc.set(key, PersistentDataType.BYTE, (byte) 1);

        } catch (Exception e) {
            // Fail silently - not critical
            // (PDC might not be available on very old Paper versions)
        }
    }

    /**
     * Check if a snow block was placed by this plugin
     *
     * @param block The snow block to check
     * @return true if placed by plugin, false if placed by player or unknown
     */
    public static boolean isPluginPlaced(@NotNull Block block) {
        try {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            String blockKey = getBlockKey(block);
            NamespacedKey key = new NamespacedKey(Winter.getInstance(), blockKey);

            return pdc.has(key, PersistentDataType.BYTE);

        } catch (Exception e) {
            // On error, assume it's player-placed (safer)
            return false;
        }
    }

    /**
     * Remove metadata for a snow block (when it's removed)
     *
     * @param block The snow block
     */
    public static void unmarkBlock(@NotNull Block block) {
        try {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            String blockKey = getBlockKey(block);
            NamespacedKey key = new NamespacedKey(Winter.getInstance(), blockKey);

            pdc.remove(key);

        } catch (Exception e) {
            // Fail silently
        }
    }

    /**
     * Create a unique key for a block within its chunk
     * Format: "snow_X_Y_Z" (relative to chunk)
     *
     * @param block The block
     * @return Unique key string
     */
    private static String getBlockKey(@NotNull Block block) {
        int relX = block.getX() & 0xF; // Modulo 16 (chunk coordinate)
        int y = block.getY();
        int relZ = block.getZ() & 0xF; // Modulo 16

        return "snow_" + relX + "_" + y + "_" + relZ;
    }

    /**
     * Batch mark multiple blocks (for performance)
     *
     * @param blocks Array of blocks to mark
     */
    public static void markBatch(@NotNull Block[] blocks) {
        for (Block block : blocks) {
            markAsPluginPlaced(block);
        }
    }

    /**
     * Clear all metadata from a chunk (for cleanup)
     *
     * @param block Any block in the chunk
     */
    public static void clearChunkMetadata(@NotNull Block block) {
        try {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();

            // Remove all snow-related keys
            for (var key : pdc.getKeys()) {
                if (key.getNamespace().equals("winter") && key.getKey().startsWith("snow_")) {
                    pdc.remove(key);
                }
            }

        } catch (Exception e) {
            // Fail silently
        }
    }
}
