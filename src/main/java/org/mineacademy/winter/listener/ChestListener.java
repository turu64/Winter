package org.mineacademy.winter.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.Winter;
import org.mineacademy.winter.core.config.Messages;
import org.mineacademy.winter.data.ChestDataManager;
import org.mineacademy.winter.data.ChestDataManager.*;
import org.mineacademy.winter.util.Permissions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles Winter chest interactions
 */
public final class ChestListener implements Listener {

    private final Winter plugin;

    public ChestListener(@NotNull Winter plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle player interactions with chests
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestInteract(@NotNull PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {
            return;
        }

        // Check if this is a Winter chest
        ChestDataManager manager = plugin.getChestDataManager();
        WinterChest chest = manager.getChest(block.getLocation());

        if (chest == null) {
            return;
        }

        Player player = event.getPlayer();

        // Handle based on chest type using pattern matching
        switch (chest) {
            case GiftChest giftChest -> handleGiftChest(event, player, giftChest);
            case DatedChest datedChest -> handleDatedChest(event, player, datedChest);
            case TimedChest timedChest -> handleTimedChest(event, player, timedChest);
        }
    }

    /**
     * Handle gift chest opening
     */
    private void handleGiftChest(@NotNull PlayerInteractEvent event, @NotNull Player player, @NotNull GiftChest chest) {
        // Check if player has already opened
        if (chest.openedBy().contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already opened this gift chest!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Check access permissions
        if (!chest.isPublic()) {
            if (!chest.allowedPlayers().contains(player.getUniqueId()) &&
                !player.hasPermission(Permissions.Chest.ADMIN)) {
                player.sendMessage(Component.text("This is a private gift chest!", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
        }

        // Mark as opened
        GiftChest updated = chest.withOpened(player.getUniqueId());
        plugin.getChestDataManager().updateChest(updated);

        player.sendMessage(Component.text("You opened a gift chest!", NamedTextColor.GREEN));
    }

    /**
     * Handle dated chest opening
     */
    private void handleDatedChest(@NotNull PlayerInteractEvent event, @NotNull Player player, @NotNull DatedChest chest) {
        // Check if chest is currently available
        if (!chest.isAvailable()) {
            if (chest.preview()) {
                player.sendMessage(Component.text("This chest is not yet available (preview mode)", NamedTextColor.YELLOW));
                // Allow preview but don't mark as opened
                return;
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
                player.sendMessage(Component.text(
                    "This chest is only available from " +
                    chest.startDate().format(formatter) + " to " +
                    chest.endDate().format(formatter),
                    NamedTextColor.RED
                ));
                event.setCancelled(true);
                return;
            }
        }

        // Check if player has already opened during this period
        if (chest.openedBy().contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already opened this dated chest!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Mark as opened
        DatedChest updated = chest.withOpened(player.getUniqueId());
        plugin.getChestDataManager().updateChest(updated);

        player.sendMessage(Component.text("You opened a dated chest!", NamedTextColor.GREEN));
    }

    /**
     * Handle timed chest opening
     */
    private void handleTimedChest(@NotNull PlayerInteractEvent event, @NotNull Player player, @NotNull TimedChest chest) {
        // Check cooldown
        if (!chest.canOpen(player.getUniqueId())) {
            long remaining = chest.getRemainingCooldown(player.getUniqueId());
            player.sendMessage(Component.text(
                "You can open this chest again in " + remaining + " minutes",
                NamedTextColor.RED
            ));
            event.setCancelled(true);
            return;
        }

        // Mark as opened
        TimedChest updated = chest.withOpened(player.getUniqueId());
        plugin.getChestDataManager().updateChest(updated);

        player.sendMessage(Component.text("You opened a timed chest!", NamedTextColor.GREEN));
    }

    /**
     * Handle chest breaking
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) {
            return;
        }

        ChestDataManager manager = plugin.getChestDataManager();
        if (!manager.isWinterChest(block.getLocation())) {
            return;
        }

        Player player = event.getPlayer();

        // Check admin permission
        if (!player.hasPermission(Permissions.Chest.ADMIN)) {
            player.sendMessage(Component.text("You cannot break Winter chests!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Remove from registry
        manager.removeChest(block.getLocation());
        player.sendMessage(Component.text("Winter chest removed", NamedTextColor.YELLOW));
    }

    /**
     * Handle sign creation for Winter chests
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(@NotNull SignChangeEvent event) {
        String line0 = event.line(0);
        if (line0 == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check for [gift] sign
        if (line0.equalsIgnoreCase("[gift]") || line0.equalsIgnoreCase("[giftchest]")) {
            if (!player.hasPermission(Permissions.Chest.GIFT)) {
                player.sendMessage(Component.text("You don't have permission to create gift chests!", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }

            Block signBlock = event.getBlock();
            Block chestBlock = findAttachedChest(signBlock);

            if (chestBlock == null) {
                player.sendMessage(Component.text("No chest found attached to this sign!", NamedTextColor.RED));
                return;
            }

            // Create gift chest
            boolean isPublic = event.line(1) != null && event.line(1).equalsIgnoreCase("public");
            List<UUID> allowed = isPublic ? List.of() : List.of(player.getUniqueId());

            GiftChest chest = new GiftChest(
                chestBlock.getLocation(),
                isPublic,
                allowed,
                new HashSet<>()
            );

            plugin.getChestDataManager().registerChest(chest);
            player.sendMessage(Component.text("Gift chest created!", NamedTextColor.GREEN));
        }
    }

    /**
     * Find chest attached to a sign
     */
    private Block findAttachedChest(@NotNull Block signBlock) {
        // Check block the sign is attached to
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            return null;
        }

        // Check all adjacent blocks for a chest
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Block relative = signBlock.getRelative(x, y, z);
                    if (relative.getType() == Material.CHEST) {
                        return relative;
                    }
                }
            }
        }

        return null;
    }
}
