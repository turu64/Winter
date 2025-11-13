package org.mineacademy.winter.listener;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

import java.util.concurrent.ThreadLocalRandom;

public final class SnowmanTransformListener implements Listener {
    private final Winter plugin;

    public SnowmanTransformListener(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        var config = WinterConfig.get().snowman().transform();
        if (!config.enabled()) return;

        if (config.applicable().contains(event.getEntityType())) {
            int chance = ThreadLocalRandom.current().nextInt(100);
            if (chance < config.chancePercent()) {
                event.setCancelled(true);
                event.getLocation().getWorld().spawnEntity(event.getLocation(), EntityType.SNOWMAN);
            }
        }
    }
}
