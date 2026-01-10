package org.mineacademy.winter.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.mineacademy.winter.model.SnowRegistry;
import org.mineacademy.winter.settings.Settings;

/**
 * Handles snow registry operations:
 * - Tracks player-placed snow (to NOT melt it)
 * - Loads/saves registry data with chunks
 * - Removes registry entries when snow is broken
 */
public class SnowRegistryListener implements Listener {

	/**
	 * Remove snow from registry when broken
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return;

		final Block block = event.getBlock();
		if (isSnowBlock(block))
			SnowRegistry.getInstance().unregister(block);
	}

	/**
	 * Track player-placed snow so it won't be melted by the plugin.
	 * Player-placed snow is NOT registered, so it won't be melted.
	 * We need to make sure plugin-placed snow that gets broken and
	 * replaced by a player stays unregistered.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return;

		final Block block = event.getBlock();
		if (isSnowBlock(block)) {
			// Player placed snow - make sure it's NOT in registry
			// This ensures player-placed snow won't be melted
			SnowRegistry.getInstance().unregister(block);
		}
	}

	/**
	 * Load registry data when a chunk loads
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return;

		if (!Settings.ALLOWED_WORLDS.contains(event.getWorld().getName()))
			return;

		SnowRegistry.getInstance().loadChunk(event.getChunk());
	}

	/**
	 * Save and unload registry data when a chunk unloads
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return;

		if (!Settings.ALLOWED_WORLDS.contains(event.getWorld().getName()))
			return;

		SnowRegistry.getInstance().unloadChunk(event.getChunk());
	}

	/**
	 * Save all registry data when world saves
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldSave(WorldSaveEvent event) {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return;

		SnowRegistry.getInstance().saveAll();
	}

	/**
	 * Check if a block is a snow-related block
	 */
	private boolean isSnowBlock(Block block) {
		final Material type = block.getType();
		return type == Material.SNOW || type.name().equals("SNOW_BLOCK");
	}
}
