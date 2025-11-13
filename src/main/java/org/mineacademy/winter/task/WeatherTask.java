package org.mineacademy.winter.task;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

/**
 * Modern weather control task
 * Manages weather conditions in allowed worlds
 */
public final class WeatherTask implements Runnable {

    private final Winter plugin;

    public WeatherTask(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = WinterConfig.get();

        for (World world : plugin.getServer().getWorlds()) {
            // Check if world is allowed
            if (!config.isWorldAllowed(world.getName())) {
                continue;
            }

            // Disable weather if configured
            if (config.weather().disable()) {
                if (world.hasStorm() || world.isThundering()) {
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setWeatherDuration(0);
                    world.setThunderDuration(0);
                }
            }

            // Snow storm mode is handled by particle task (increases chaos during storm)
        }
    }
}
