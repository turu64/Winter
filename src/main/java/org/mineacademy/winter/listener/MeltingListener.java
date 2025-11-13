package org.mineacademy.winter.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.WinterConfig;

public final class MeltingListener implements Listener {
    private final Winter plugin;

    public MeltingListener(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockMelt(BlockFadeEvent event) {
        if (WinterConfig.get().terrain().preventMelting().contains(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }
}
