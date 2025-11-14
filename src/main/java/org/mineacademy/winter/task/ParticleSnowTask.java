package org.mineacademy.winter.task;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern snow particle task using Paper API
 * Spawns realistic snow particles around players
 */
public final class ParticleSnowTask implements Runnable {

    private final Winter plugin;
    private static final Set<Biome> SNOWY_BIOMES = Set.of(
        Biome.SNOWY_PLAINS,
        Biome.ICE_SPIKES,
        Biome.SNOWY_TAIGA,
        Biome.SNOWY_BEACH,
        Biome.FROZEN_RIVER,
        Biome.FROZEN_OCEAN,
        Biome.DEEP_FROZEN_OCEAN,
        Biome.SNOWY_SLOPES,
        Biome.FROZEN_PEAKS,
        Biome.JAGGED_PEAKS,
        Biome.GROVE
    );

    public ParticleSnowTask(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = WinterConfig.get().snow();

        // Check server TPS - reduce particles if server is lagging
        double tps = plugin.getServer().getTPS()[0]; // 1-minute average
        if (tps < 15.0) {
            return; // Skip this tick if server is heavily lagged
        }

        double tpsMultiplier = Math.min(1.0, tps / 20.0);
        int adjustedAmount = (int) (config.amount() * tpsMultiplier);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Check if world is allowed
            if (!WinterConfig.get().isWorldAllowed(player.getWorld().getName())) {
                continue;
            }

            // Check if snow is enabled for this player
            if (!plugin.getPlayerDataManager().isSnowEnabled(player)) {
                continue;
            }

            // Ignore vanished players if configured
            if (config.ignoreVanished()) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                // Check metadata for vanish (supports various vanish plugins)
                if (player.hasMetadata("vanished")) {
                    continue;
                }
            }

            // Check biome requirements
            if (config.requireSnowBiomes()) {
                Biome biome = player.getLocation().getBlock().getBiome();
                if (!SNOWY_BIOMES.contains(biome) && player.getLocation().getY() < 90) {
                    continue;
                }
            }

            spawnSnowParticles(player, adjustedAmount, config);
        }
    }

    /**
     * Spawn snow particles around a player
     */
    private void spawnSnowParticles(@NotNull Player player, int amount, @NotNull WinterConfig.SnowConfig config) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < amount; i++) {
            // Random offset within horizontal range
            double rangeH = Math.max(0.1, config.rangeHorizontal()); // Ensure minimum range
            double offsetX = random.nextDouble(-rangeH, rangeH);
            double offsetZ = random.nextDouble(-rangeH, rangeH);

            // Random height within vertical range
            double rangeV = Math.max(0.1, config.rangeVertical()); // Ensure minimum range
            double offsetY = random.nextDouble(0, rangeV);

            Location particleLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);

            // Realistic mode: only spawn if above ground (not under roof)
            if (config.realistic()) {
                Location checkLoc = particleLoc.clone();
                boolean hasRoof = false;

                // Check blocks above
                for (int y = 0; y < 10; y++) {
                    checkLoc.add(0, 1, 0);
                    if (checkLoc.getBlock().getType().isSolid()) {
                        hasRoof = true;
                        break;
                    }
                }

                if (hasRoof) {
                    continue; // Don't spawn under roofs
                }
            }

            // Calculate velocity with chaos (ensure chaos is not zero)
            double chaos = Math.max(0.001, config.chaos()); // Minimum chaos to avoid error
            double velocityX = config.chaos() > 0 ? random.nextDouble(-chaos, chaos) : 0.0;
            double velocityY = -0.1; // Always fall down
            double velocityZ = config.chaos() > 0 ? random.nextDouble(-chaos, chaos) : 0.0;

            // Spawn particle with velocity (using modern Paper API)
            world.spawnParticle(
                Particle.SNOWFLAKE,
                particleLoc,
                1, // count
                velocityX, velocityY, velocityZ, // offset as velocity
                0.0 // speed (we use offset for velocity instead)
            );
        }
    }

    /**
     * Check if location is in snowy biome
     */
    private boolean isSnowyBiome(@NotNull Biome biome) {
        return SNOWY_BIOMES.contains(biome);
    }
}
