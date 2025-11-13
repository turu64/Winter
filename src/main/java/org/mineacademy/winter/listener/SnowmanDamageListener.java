package org.mineacademy.winter.listener;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

public final class SnowmanDamageListener implements Listener {
    private final Winter plugin;

    public SnowmanDamageListener(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Snowball snowball) {
            if (snowball.getShooter() instanceof org.bukkit.entity.Snowman) {
                double damage = WinterConfig.get().snowman().damage().snowball();
                event.setDamage(damage);
            }
        }
    }
}
