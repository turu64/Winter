package org.mineacademy.winter.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.winter.model.SnowRegistry;
import org.mineacademy.winter.settings.Settings;
import org.mineacademy.winter.util.WinterUtil;

/**
 * Prevents natural melting of snow and ice blocks.
 *
 * When Snow Registry is enabled:
 * - Only plugin-placed snow (tracked in registry) is allowed to melt via plugin task
 * - Natural snow and player-placed snow are protected from both natural and plugin melting
 *
 * When using legacy biome-based detection:
 * - Snow in naturally snowy biomes is protected
 * - Snow in warm biomes (placed by plugin) can melt
 */
public class MeltingListener implements Listener {

	@EventHandler
	public void onBlockFade(BlockFadeEvent event) {
		final Block block = event.getBlock();

		if (!Settings.ALLOWED_WORLDS.contains(block.getWorld().getName()))
			return;

		// Check if this block type should be protected from melting
		if (Settings.Terrain.PREVENT_MELTING.contains(CompMaterial.fromBlock(block))) {
			event.setCancelled(true);
			return;
		}

		// Additional protection for snow when using registry
		if (Settings.Terrain.SnowGeneration.USE_REGISTRY && isSnowBlock(block)) {
			// If snow is NOT in registry (natural or player-placed), protect it
			if (!SnowRegistry.getInstance().isPluginPlaced(block)) {
				event.setCancelled(true);
				return;
			}
		}

		// Legacy: protect naturally snowy locations
		if (Settings.Terrain.SnowGeneration.IGNORE_SNOWY && isSnowBlock(block)) {
			if (WinterUtil.isNaturallySnowy(block.getLocation())) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/**
	 * Check if a block is snow or snow-related
	 */
	private boolean isSnowBlock(Block block) {
		final Material type = block.getType();
		return type == Material.SNOW || type.name().equals("SNOW_BLOCK");
	}
}
