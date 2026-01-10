package org.mineacademy.winter.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.mineacademy.fo.BlockUtil;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.settings.SimpleLocalization;
import org.mineacademy.winter.model.SnowRegistry;
import org.mineacademy.winter.settings.Localization;
import org.mineacademy.winter.settings.Settings;

public class WinterUtil {

	/**
	 * Check if snow at this location can be melted.
	 * This considers biome, temperature, and whether it was placed by the plugin.
	 */
	public static boolean canMelt(Location loc) {
		// If registry-based melting is enabled, only melt plugin-placed snow
		if (Settings.Terrain.SnowGeneration.USE_REGISTRY)
			return SnowRegistry.getInstance().isPluginPlaced(loc.getBlock());

		// Legacy biome-based detection
		if (Settings.Terrain.SnowGeneration.IGNORE_SNOWY) {
			final boolean naturallySnowy = isNaturallySnowy(loc);
			return !naturallySnowy;
		}

		return true;
	}

	/**
	 * Check if a location is naturally snowy based on biome and altitude.
	 * Compatible with all Minecraft versions including 1.18+ 3D biomes.
	 */
	public static boolean isNaturallySnowy(Location loc) {
		final World w = loc.getWorld();
		final int x = loc.getBlockX();
		final int y = loc.getBlockY();
		final int z = loc.getBlockZ();

		// Get biome - use 3D method for 1.18+
		final Biome biome = getBiomeAt(w, x, y, z);
		final String biomeName = biome.name();

		// Check biome name for snow indicators
		if (biomeName.contains("SNOW") || biomeName.contains("FROZEN") || biomeName.contains("ICE"))
			return true;

		// Check temperature - naturally snowy if below 0.15
		// Note: getTemperature(x, z) is deprecated but still works for base temperature
		try {
			if (w.getTemperature(x, z) < 0.15)
				return true;
		} catch (final Exception ignored) {
			// Some versions may not support this
		}

		// Mountain check - high altitude in mountain biomes
		if ((biomeName.contains("MOUNTAIN") || biomeName.contains("PEAKS") || biomeName.contains("GROVE"))
				&& y > getSnowLineHeight(w))
			return true;

		// 1.18+ new biomes check
		if (biomeName.contains("SNOWY") || biomeName.contains("FROZEN"))
			return true;

		return false;
	}

	/**
	 * Get biome at location, using 3D method for 1.18+
	 */
	public static Biome getBiomeAt(World world, int x, int y, int z) {
		// 1.18+ uses 3D biomes
		if (MinecraftVersion.atLeast(V.v1_18))
			return world.getBiome(x, y, z);

		// Legacy 2D biome
		return world.getBiome(x, z);
	}

	/**
	 * Get the Y level where snow starts appearing in mountains.
	 * This varies by world height (1.18+ has taller worlds).
	 */
	public static int getSnowLineHeight(World world) {
		// 1.18+ has taller worlds with snow line around Y=95-100
		if (MinecraftVersion.atLeast(V.v1_18))
			return 95;

		// Pre-1.18 snow line
		return 90;
	}

	public static final boolean canPlace(Block ground) {
		final Material m = ground.getType();
		final boolean can = m.isSolid() && !m.isTransparent() && !Valid.isInListContains(m.toString(), Settings.Terrain.SnowGeneration.IGNORE_PLACE);

		if (can && CompMaterial.isLongGrass(ground.getType()))
			ground.setType(Material.AIR);

		return can;
	}

	public static final boolean checkPerm(CommandSender pl, String permission) {
		return checkPerm(pl, permission, true);
	}

	public static final boolean checkPerm(CommandSender pl, String permission, boolean notify) {
		permission = permission.replace("{plugin_name}", SimplePlugin.getNamed().toLowerCase());

		final boolean has = PlayerUtil.hasPerm(pl, permission);

		if (!has && notify)
			Common.tell(pl, SimpleLocalization.NO_PERMISSION.replace("{permission}", permission));

		return has;
	}

	public static final String formatTime(int seconds) {
		final int second = seconds % 60;
		int minute = seconds / 60;
		String hourMsg = "";

		if (minute >= 60) {
			final int hour = seconds / 60 / 60;
			minute %= 60;

			hourMsg = Localization.Cases.HOUR.formatWithCount(hour) + " ";
		}

		return hourMsg + (minute > 0 ? Localization.Cases.MINUTE.formatWithCount(minute) + " " : "") + Localization.Cases.SECOND.formatWithCount(second);
	}

	/**
	 * Return a random location within the given chunk that is not covered by snow
	 *
	 *
	 * @param chunk
	 * @return
	 */
	public static Location nextLocationNoSnow(Chunk chunk) {
		final int x = RandomUtil.nextInt(16) + (chunk.getX() << 4)/* - 16*/;
		final int z = RandomUtil.nextInt(16) + (chunk.getZ() << 4)/* - 16*/;
		final double y = BlockUtil.findHighestBlockNoSnow(chunk.getWorld(), x, z);

		return y == -1 ? null : new Location(chunk.getWorld(), x, y, z);
	}
}
