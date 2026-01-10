package org.mineacademy.winter.model.task;

import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.winter.model.SnowRegistry;
import org.mineacademy.winter.settings.Settings;
import org.mineacademy.winter.util.FreezeIgnore;
import org.mineacademy.winter.util.WinterUtil;

public class TaskTerrain implements Runnable {

	private final StrictList<Chunk> activeChunks = new StrictList<>();

	/**
	 * Check if we should use the modern BlockData API (1.13+)
	 */
	private static final boolean USE_BLOCK_DATA = MinecraftVersion.atLeast(V.v1_13);

	@Override
	public final void run() {
		loadChunks();

		final boolean melt = Settings.Terrain.SnowGeneration.MELT;
		final boolean useRegistry = Settings.Terrain.SnowGeneration.USE_REGISTRY;
		final SnowRegistry registry = useRegistry ? SnowRegistry.getInstance() : null;

		for (final Chunk chunk : activeChunks) {
			final Location loc = WinterUtil.nextLocationNoSnow(chunk);

			if (loc == null)
				continue;

			final Block block = loc.getBlock();

			if (Settings.Terrain.SnowGeneration.IGNORE_BIOMES.contains(block.getBiome()))
				continue;

			final Block ground = block.getRelative(BlockFace.DOWN);
			final boolean canMelt = WinterUtil.canMelt(loc);

			if (Settings.Terrain.SnowGeneration.DESTROY_CROPS) {
				final Block ground2 = ground.getRelative(BlockFace.DOWN);

				if (ground2.getType() == CompMaterial.FARMLAND.getMaterial()) {
					ground.setType(Material.SNOW);

					// Register plugin-placed snow
					if (useRegistry)
						registry.register(ground);

					ground2.setType(Material.DIRT);

					continue;
				}
			}

			if (block.getType() == Material.SNOW) {
				final int layers = getSnowLayers(block);

				if (melt && canMelt && layers <= 1) {
					// Remove snow completely
					block.setType(Material.AIR);

					// Unregister from registry
					if (useRegistry)
						registry.unregister(block);

					continue;
				}

				final int near = getNeighboorsSameLevel(block);
				boolean manipulate = melt ? layers > 1 : layers < 8;

				if (!melt && manipulate)
					manipulate = near >= Settings.Terrain.SnowGeneration.NEIGHBOR_MIN && Settings.Terrain.SnowGeneration.MULTI_LAYER;

				if (manipulate) {
					setSnowLayers(block, layers + (melt ? -1 : 1));
					continue;
				}
			}

			if (!melt && WinterUtil.canPlace(ground)) {
				block.setType(Material.SNOW);

				// Register plugin-placed snow
				if (useRegistry)
					registry.register(block);

				continue;
			}

			if (Settings.Terrain.SnowGeneration.FREEZE_WATER)
				if (melt) {
					if (canMelt && ground.getType() == Material.ICE)
						ground.setType(Material.WATER);

				} else if (canFreeze(ground) && (ground.getType() == Material.WATER || ground.getType().toString().equals("STATIONARY_WATER")))
					ground.setType(Material.ICE);
		}
	}

	/**
	 * Get the number of snow layers on a block.
	 * Uses BlockData API for 1.13+, legacy getData() for older versions.
	 */
	private int getSnowLayers(Block block) {
		if (USE_BLOCK_DATA) {
			final BlockData data = block.getBlockData();
			if (data instanceof Snow)
				return ((Snow) data).getLayers();
			return 1;
		}

		// Legacy: getData() returns 0-7, but layers are 1-8
		return block.getData() + 1;
	}

	/**
	 * Set the number of snow layers on a block.
	 * Uses BlockData API for 1.13+, legacy Remain.setData() for older versions.
	 */
	private void setSnowLayers(Block block, int layers) {
		if (layers < 1)
			layers = 1;
		if (layers > 8)
			layers = 8;

		if (USE_BLOCK_DATA) {
			final BlockData data = block.getBlockData();
			if (data instanceof Snow) {
				final Snow snow = (Snow) data;
				snow.setLayers(layers);
				block.setBlockData(snow);
			}
		} else {
			// Legacy: data value is layers - 1 (0-7)
			Remain.setData(block, layers - 1);
		}
	}

	private final boolean canFreeze(Block block) {
		for (final FreezeIgnore freeze : Settings.Terrain.SnowGeneration.IGNORE_FREEZE)
			if (!freeze.canFreeze(block))
				return false;

		return true;
	}

	private final int getNeighboorsSameLevel(Block block) {
		int count = 0;
		final int blockLayers = getSnowLayers(block);

		for (final BlockFace face : Arrays.asList(BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH)) {
			final Block rel = block.getRelative(face);

			if (rel.getType() == Material.SNOW && getSnowLayers(rel) == blockLayers)
				count++;
		}

		return count;
	}

	private final void loadChunks() {
		activeChunks.clear();

		final int radius = Settings.Terrain.SnowGeneration.RADIUS;
		int chunkX, chunkZ;

		for (final World world : Bukkit.getWorlds()) {
			if (!Settings.ALLOWED_WORLDS.contains(world.getName()))
				continue;

			for (final Player worldPlayers : world.getPlayers()) {
				chunkX = worldPlayers.getLocation().getBlockX() >> 4;
				chunkZ = worldPlayers.getLocation().getBlockZ() >> 4;

				for (int x = chunkX - radius; x <= chunkX + radius; x++)
					for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
						final Chunk ch = world.getChunkAt(x, z);

						if (!activeChunks.contains(ch))
							activeChunks.add(ch);
					}
			}
		}
	}
}