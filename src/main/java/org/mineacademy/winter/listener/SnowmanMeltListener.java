package org.mineacademy.winter.listener;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class SnowmanMeltListener implements Listener {
    @EventHandler
    public void onSnowmanDamage(EntityDamageEvent event) {
        if (event.getEntity().getType() == EntityType.SNOWMAN &&
            event.getCause() == EntityDamageEvent.DamageCause.MELTING) {
            event.setCancelled(true);
        }
    }
}
