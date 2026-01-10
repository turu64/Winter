package org.mineacademy.winter.commands;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.command.SimpleSubCommand;
import org.mineacademy.winter.model.SnowRegistry;
import org.mineacademy.winter.settings.Settings;
import org.mineacademy.winter.util.Permissions;

/**
 * Command to migrate existing snow to the Snow Registry.
 *
 * This is essential for servers updating from older versions (e.g., 1.21.4 to 1.21.8)
 * where plugin-placed snow wasn't tracked. After migration, the plugin can
 * reliably distinguish between:
 * - Plugin-placed snow (registered, can be melted)
 * - Natural snow (not registered, protected from melting)
 * - Player-placed snow (not registered, protected from melting)
 */
public class MigrateCommand extends SimpleSubCommand {

	private boolean confirmed = false;

	public MigrateCommand() {
		super("migrate|mig");

		setDescription("Migrate existing snow to Snow Registry for reliable melting.");
		setUsage("[world] [--all]");
		setMinArguments(0);
	}

	@Override
	protected void onCommand() {
		if (!Settings.Terrain.SnowGeneration.USE_REGISTRY) {
			tell("&cSnow Registry is disabled. Enable 'Use_Snow_Registry' in settings.yml first.");
			return;
		}

		// Parse arguments
		String worldName = null;
		boolean allSnow = false;

		for (final String arg : args) {
			if (arg.equalsIgnoreCase("--all"))
				allSnow = true;
			else if (worldName == null)
				worldName = arg;
		}

		// If no world specified, try to use player's world
		World world = null;
		if (worldName != null) {
			world = Bukkit.getWorld(worldName);
			checkNotNull(world, "World '" + worldName + "' not found. Available: " + getWorldNames());
		} else if (sender instanceof Player) {
			world = ((Player) sender).getWorld();
		} else {
			tell("&cPlease specify a world: /winter migrate <world>");
			return;
		}

		// Confirmation
		if (!confirmed) {
			confirmed = true;

			final int loadedChunks = world.getLoadedChunks().length;
			final String mode = allSnow ? "ALL snow" : "only snow in WARM biomes";

			tell(
					"&6==========================================",
					"&e Snow Registry Migration",
					"&6==========================================",
					" ",
					"&fThis will register existing snow in &b" + world.getName() + "&f",
					"&fso it can be melted by the plugin.",
					" ",
					"&fMode: &a" + mode,
					"&fLoaded chunks: &a" + loadedChunks,
					" ",
					allSnow
							? "&cWarning: --all flag will register ALL snow, including"
							: "&fOnly snow in warm biomes (non-snowy) will be registered.",
					allSnow
							? "&csnow in snowy biomes. This may cause natural snow to melt!"
							: "&fNatural snow in cold biomes will be preserved.",
					" ",
					"&7Tip: After migration, new snow placed by the plugin will be",
					"&7automatically tracked. Player-placed snow won't be tracked.",
					" ",
					"&6==========================================",
					"&e> Run the command again to proceed...",
					"&6==========================================");
			return;
		}

		confirmed = false;

		// Perform migration
		final boolean onlyUnnatural = !allSnow;
		final World targetWorld = world;

		tell("&aStarting migration for " + world.getName() + "...");

		// Run async to prevent lag
		Common.runAsync(() -> {
			int totalRegistered = 0;
			int chunksProcessed = 0;

			final Chunk[] chunks = targetWorld.getLoadedChunks();
			final int totalChunks = chunks.length;

			for (final Chunk chunk : chunks) {
				final int registered = SnowRegistry.getInstance().migrateChunk(chunk, onlyUnnatural);
				totalRegistered += registered;
				chunksProcessed++;

				// Progress update every 100 chunks
				if (chunksProcessed % 100 == 0) {
					final int progress = (chunksProcessed * 100) / totalChunks;
					Common.runLater(() -> tell("&7Progress: " + progress + "% (" + chunksProcessed + "/" + totalChunks + " chunks)"));
				}
			}

			final int finalTotal = totalRegistered;
			final int finalChunks = chunksProcessed;

			// Report results on main thread
			Common.runLater(() -> {
				tell(
						"&a==========================================",
						"&a Migration Complete!",
						"&a==========================================",
						"&f Chunks processed: &b" + finalChunks,
						"&f Snow blocks registered: &b" + finalTotal,
						"&f Total tracked blocks: &b" + SnowRegistry.getInstance().getTotalBlockCount(),
						"&a==========================================");

				// Save the registry
				SnowRegistry.getInstance().saveAll();
				tell("&7Registry saved to disk.");
			});
		});
	}

	@Override
	public List<String> tabComplete() {
		if (!PlayerUtil.hasPerm(sender, Permissions.Commands.MIGRATE))
			return null;

		if (args.length == 1)
			return completeLastWord(getWorldNames());

		if (args.length == 2)
			return completeLastWord("--all");

		return new ArrayList<>();
	}

	private List<String> getWorldNames() {
		return Common.convert(Bukkit.getWorlds(), World::getName);
	}
}
