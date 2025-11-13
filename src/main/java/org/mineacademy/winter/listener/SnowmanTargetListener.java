package org.mineacademy.winter.listener;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

public final class SnowmanTargetListener implements Listener {
    @EventHandler
    public void onSnowmanTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && event.getTarget().getType() == EntityType.SNOWMAN) {
            event.setCancelled(true);
        }
    }
}
