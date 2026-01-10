package org.mineacademy.winter.model;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.plugin.SimplePlugin;

/**
 * Tracks snow blocks placed by the Winter plugin.
 * This allows reliable melting of plugin-placed snow even after server updates,
 * while preserving naturally generated and player-placed snow.
 *
 * Data is stored per-chunk for efficiency and saved to disk on chunk unload.
 */
public final class SnowRegistry {

	/**
	 * The singleton instance
	 */
	private static final SnowRegistry instance = new SnowRegistry();

	/**
	 * Map of world name -> chunk key -> set of block positions (packed as long)
	 * Using ConcurrentHashMap for thread safety during async chunk operations
	 */
	private final Map<String, Map<Long, Set<Long>>> worldData = new ConcurrentHashMap<>();

	/**
	 * Tracks chunks that have been modified and need saving
	 */
	private final Set<String> dirtyChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());

	/**
	 * Private constructor for singleton
	 */
	private SnowRegistry() {
	}

	/**
	 * Get the singleton instance
	 */
	public static SnowRegistry getInstance() {
		return instance;
	}

	// ------------------------------------------------------------------------
	// Registration methods
	// ------------------------------------------------------------------------

	/**
	 * Register a snow block as placed by the plugin
	 */
	public void register(Block block) {
		register(block.getLocation());
	}

	/**
	 * Register a location as having plugin-placed snow
	 */
	public void register(Location loc) {
		Valid.checkNotNull(loc.getWorld(), "World cannot be null");

		final String worldName = loc.getWorld().getName();
		final long chunkKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
		final long blockKey = getBlockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		worldData.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(chunkKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
				.add(blockKey);

		markDirty(worldName, chunkKey);
	}

	/**
	 * Unregister a snow block (when it melts or is removed)
	 */
	public void unregister(Block block) {
		unregister(block.getLocation());
	}

	/**
	 * Unregister a location
	 */
	public void unregister(Location loc) {
		if (loc.getWorld() == null)
			return;

		final String worldName = loc.getWorld().getName();
		final long chunkKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
		final long blockKey = getBlockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		final Map<Long, Set<Long>> worldChunks = worldData.get(worldName);
		if (worldChunks != null) {
			final Set<Long> blocks = worldChunks.get(chunkKey);
			if (blocks != null && blocks.remove(blockKey)) {
				markDirty(worldName, chunkKey);

				// Clean up empty sets
				if (blocks.isEmpty())
					worldChunks.remove(chunkKey);
			}
		}
	}

	/**
	 * Check if a block was placed by the plugin
	 */
	public boolean isPluginPlaced(Block block) {
		return isPluginPlaced(block.getLocation());
	}

	/**
	 * Check if a location has plugin-placed snow
	 */
	public boolean isPluginPlaced(Location loc) {
		if (loc.getWorld() == null)
			return false;

		final String worldName = loc.getWorld().getName();
		final long chunkKey = getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
		final long blockKey = getBlockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		final Map<Long, Set<Long>> worldChunks = worldData.get(worldName);
		if (worldChunks != null) {
			final Set<Long> blocks = worldChunks.get(chunkKey);
			return blocks != null && blocks.contains(blockKey);
		}

		return false;
	}

	// ------------------------------------------------------------------------
	// Chunk operations
	// ------------------------------------------------------------------------

	/**
	 * Load data for a chunk from disk
	 */
	public void loadChunk(Chunk chunk) {
		final String worldName = chunk.getWorld().getName();
		final int chunkX = chunk.getX();
		final int chunkZ = chunk.getZ();
		final long chunkKey = getChunkKey(chunkX, chunkZ);

		final File file = getChunkFile(worldName, chunkX, chunkZ);
		if (!file.exists())
			return;

		try {
			final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
			final Set<Long> blocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

			for (final String key : config.getKeys(false)) {
				try {
					blocks.add(Long.parseLong(key));
				} catch (final NumberFormatException ignored) {
				}
			}

			if (!blocks.isEmpty())
				worldData.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
						.put(chunkKey, blocks);

		} catch (final Exception e) {
			Common.error(e, "Failed to load snow registry for chunk " + chunkX + "," + chunkZ + " in " + worldName);
		}
	}

	/**
	 * Save data for a chunk to disk
	 */
	public void saveChunk(Chunk chunk) {
		saveChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
	}

	/**
	 * Save data for a chunk to disk by coordinates
	 */
	public void saveChunk(String worldName, int chunkX, int chunkZ) {
		final long chunkKey = getChunkKey(chunkX, chunkZ);
		final String dirtyKey = worldName + ":" + chunkKey;

		// Only save if dirty
		if (!dirtyChunks.remove(dirtyKey))
			return;

		final Map<Long, Set<Long>> worldChunks = worldData.get(worldName);
		final Set<Long> blocks = worldChunks != null ? worldChunks.get(chunkKey) : null;

		final File file = getChunkFile(worldName, chunkX, chunkZ);

		if (blocks == null || blocks.isEmpty()) {
			// Delete empty files
			if (file.exists())
				file.delete();
			return;
		}

		try {
			final YamlConfiguration config = new YamlConfiguration();

			for (final Long blockKey : blocks)
				config.set(String.valueOf(blockKey), true);

			config.save(file);

		} catch (final Exception e) {
			Common.error(e, "Failed to save snow registry for chunk " + chunkX + "," + chunkZ + " in " + worldName);
		}
	}

	/**
	 * Unload chunk data from memory (call after saving)
	 */
	public void unloadChunk(Chunk chunk) {
		// Save first
		saveChunk(chunk);

		// Then remove from memory to save RAM
		final String worldName = chunk.getWorld().getName();
		final long chunkKey = getChunkKey(chunk.getX(), chunk.getZ());

		final Map<Long, Set<Long>> worldChunks = worldData.get(worldName);
		if (worldChunks != null)
			worldChunks.remove(chunkKey);
	}

	// ------------------------------------------------------------------------
	// Bulk operations
	// ------------------------------------------------------------------------

	/**
	 * Register all snow blocks in a chunk as plugin-placed.
	 * Used for migration from older versions.
	 */
	public int migrateChunk(Chunk chunk, boolean onlyUnnatural) {
		int count = 0;
		final World world = chunk.getWorld();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				final int worldX = (chunk.getX() << 4) + x;
				final int worldZ = (chunk.getZ() << 4) + z;

				// Scan from max height down
				for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
					final Block block = world.getBlockAt(worldX, y, worldZ);

					if (block.getType().name().contains("SNOW")) {
						// Check if we should skip naturally snowy locations
						if (onlyUnnatural) {
							final Location loc = block.getLocation();
							if (!canMeltLocation(loc))
								continue;
						}

						register(block);
						count++;
					}
				}
			}
		}

		return count;
	}

	/**
	 * Check if a location is in a naturally snowy area
	 * (simplified check - full check in WinterUtil)
	 */
	private boolean canMeltLocation(Location loc) {
		final String biome = loc.getBlock().getBiome().name();
		return !biome.contains("SNOW") && !biome.contains("FROZEN") && !biome.contains("ICE");
	}

	/**
	 * Get count of registered blocks in a world
	 */
	public int getBlockCount(String worldName) {
		final Map<Long, Set<Long>> worldChunks = worldData.get(worldName);
		if (worldChunks == null)
			return 0;

		int count = 0;
		for (final Set<Long> blocks : worldChunks.values())
			count += blocks.size();

		return count;
	}

	/**
	 * Get count of registered blocks across all worlds
	 */
	public int getTotalBlockCount() {
		int count = 0;
		for (final String worldName : worldData.keySet())
			count += getBlockCount(worldName);

		return count;
	}

	/**
	 * Save all data to disk
	 */
	public void saveAll() {
		for (final String dirtyKey : new HashSet<>(dirtyChunks)) {
			final String[] parts = dirtyKey.split(":");
			if (parts.length != 2)
				continue;

			final String worldName = parts[0];
			try {
				final long chunkKey = Long.parseLong(parts[1]);
				final int chunkX = (int) (chunkKey >> 32);
				final int chunkZ = (int) chunkKey;

				saveChunk(worldName, chunkX, chunkZ);
			} catch (final NumberFormatException ignored) {
			}
		}
	}

	/**
	 * Load all chunks for currently loaded worlds
	 */
	public void loadAll() {
		for (final World world : Bukkit.getWorlds())
			for (final Chunk chunk : world.getLoadedChunks())
				loadChunk(chunk);
	}

	/**
	 * Clear all data (for testing/reset)
	 */
	public void clearAll() {
		worldData.clear();
		dirtyChunks.clear();

		// Delete all data files
		final File dataFolder = getDataFolder();
		if (dataFolder.exists())
			deleteRecursively(dataFolder);
	}

	/**
	 * Recursively delete a directory and all its contents
	 */
	private void deleteRecursively(File file) {
		if (file.isDirectory()) {
			final File[] children = file.listFiles();
			if (children != null)
				for (final File child : children)
					deleteRecursively(child);
		}
		file.delete();
	}

	// ------------------------------------------------------------------------
	// Helper methods
	// ------------------------------------------------------------------------

	private void markDirty(String worldName, long chunkKey) {
		dirtyChunks.add(worldName + ":" + chunkKey);
	}

	private File getChunkFile(String worldName, int chunkX, int chunkZ) {
		final File worldFolder = new File(getDataFolder(), worldName);
		worldFolder.mkdirs();

		// Use region-based folders to avoid too many files in one directory
		final int regionX = chunkX >> 5;
		final int regionZ = chunkZ >> 5;
		final File regionFolder = new File(worldFolder, "r." + regionX + "." + regionZ);
		regionFolder.mkdirs();

		return new File(regionFolder, "c." + chunkX + "." + chunkZ + ".yml");
	}

	private File getDataFolder() {
		return new File(SimplePlugin.getData(), "snow_registry");
	}

	/**
	 * Pack chunk coordinates into a single long
	 */
	private static long getChunkKey(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/**
	 * Pack block coordinates into a single long
	 * Supports Y range from -2048 to 2047 (12 bits signed)
	 * X and Z within chunk: 0-15 (4 bits each)
	 * Total: 20 bits used, fits in long easily
	 */
	private static long getBlockKey(int x, int y, int z) {
		// Store relative X/Z within chunk (0-15) and full Y
		final int relX = x & 0xF;
		final int relZ = z & 0xF;
		// Y can be negative in 1.18+, shift to positive range
		final int shiftedY = y + 2048;

		return ((long) shiftedY << 8) | (relX << 4) | relZ;
	}
}
