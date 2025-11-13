package org.mineacademy.winter;

import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.winter.command.WinterCommandHandler;
import org.mineacademy.winter.core.WinterPlugin;
import org.mineacademy.winter.core.config.Messages;
import org.mineacademy.winter.core.config.WinterConfig;
import org.mineacademy.winter.data.ChestDataManager;
import org.mineacademy.winter.data.PlayerDataManager;
import org.mineacademy.winter.listener.*;
import org.mineacademy.winter.task.ParticleSnowTask;
import org.mineacademy.winter.task.TerrainTask;
import org.mineacademy.winter.task.WeatherTask;

import java.util.logging.Level;

/**
 * Winter Plugin - Modern Edition
 * Minecraft server winter wonderland plugin for Paper 1.21.4
 *
 * @author kangarko
 * @version 3.0.0
 */
public final class Winter extends WinterPlugin implements Listener {

    @Getter
    private WinterCommandHandler commandHandler;

    @Getter
    private PlayerDataManager playerDataManager;

    @Getter
    private ChestDataManager chestDataManager;

    // Task IDs for scheduler
    private int particleTaskId = -1;
    private int terrainTaskId = -1;
    private int weatherTaskId = -1;

    @Override
    protected void onPluginStart() {
        // Load configuration
        log(Level.INFO, "Loading configuration...");
        WinterConfig.load(this);

        // Load messages
        log(Level.INFO, "Loading messages...");
        Messages.load(this, WinterConfig.get().locale());

        // Initialize data managers
        log(Level.INFO, "Initializing data managers...");
        playerDataManager = new PlayerDataManager(this);
        chestDataManager = new ChestDataManager(this);

        // Load data
        playerDataManager.loadAll();
        chestDataManager.loadAll();

        // Initialize plugin integrations
        log(Level.INFO, "Initializing plugin integrations...");
        initializeIntegrations();

        // Register listeners
        log(Level.INFO, "Registering event listeners...");
        registerListeners();

        // Register commands
        log(Level.INFO, "Registering commands...");
        commandHandler = new WinterCommandHandler(this);
        commandHandler.register();

        // Start tasks
        log(Level.INFO, "Starting background tasks...");
        startTasks();

        log(Level.INFO, "Winter plugin fully initialized!");
    }

    @Override
    protected void onPluginStop() {
        // Stop tasks
        stopTasks();

        // Save data
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (chestDataManager != null) {
            chestDataManager.saveAll();
        }

        log(Level.INFO, "Winter plugin shutdown complete!");
    }

    @Override
    protected void onPluginReload() {
        // Stop existing tasks
        stopTasks();

        // Reload configuration
        WinterConfig.load(this);
        Messages.load(this, WinterConfig.get().locale());

        // Reload data
        playerDataManager.reload();
        chestDataManager.reload();

        // Restart tasks
        startTasks();

        log(Level.INFO, "Winter plugin reloaded!");
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        var config = WinterConfig.get();
        var server = getServer();
        var pm = server.getPluginManager();

        // Core listeners (always registered)
        pm.registerEvents(this, this); // Weather listener
        pm.registerEvents(new ChestListener(this), this);

        // Conditional listeners based on configuration
        if (config.snowman().disableMeltDamage()) {
            pm.registerEvents(new SnowmanMeltListener(), this);
            log(Level.INFO, "Registered snowman melt protection listener");
        }

        if (config.snowman().preventTarget()) {
            pm.registerEvents(new SnowmanTargetListener(), this);
            log(Level.INFO, "Registered snowman target prevention listener");
        }

        if (config.snowman().transform().enabled()) {
            pm.registerEvents(new SnowmanTransformListener(this), this);
            log(Level.INFO, "Registered snowman transform listener");
        }

        if (config.snowman().damage().snowball() > 0) {
            pm.registerEvents(new SnowmanDamageListener(this), this);
            log(Level.INFO, "Registered snowman damage listener");
        }

        if (!config.terrain().preventMelting().isEmpty()) {
            pm.registerEvents(new MeltingListener(this), this);
            log(Level.INFO, "Registered melting prevention listener");
        }
    }

    /**
     * Initialize plugin integrations (WorldGuard, etc.)
     */
    private void initializeIntegrations() {
        // Initialize WorldGuard integration
        org.mineacademy.winter.hook.WorldGuardHook.initialize(this);
    }

    /**
     * Start all background tasks
     */
    private void startTasks() {
        var config = WinterConfig.get();

        // Snow particle task
        if (config.snow().enabled()) {
            var task = new ParticleSnowTask(this);
            particleTaskId = getServer().getScheduler()
                .scheduleSyncRepeatingTask(this, task, 20L, config.snow().periodTicks());
            log(Level.INFO, "Started snow particle task (period: " + config.snow().periodTicks() + " ticks)");
        }

        // Terrain task
        if (config.terrain().snowGeneration().enabled()) {
            var task = new TerrainTask(this);
            terrainTaskId = getServer().getScheduler()
                .scheduleSyncRepeatingTask(this, task, 20L, config.terrain().snowGeneration().periodTicks());
            log(Level.INFO, "Started terrain task (period: " + config.terrain().snowGeneration().periodTicks() + " ticks)");
        }

        // Weather task
        if (config.weather().disable() || config.weather().snowStorm()) {
            var task = new WeatherTask(this);
            weatherTaskId = getServer().getScheduler()
                .scheduleSyncRepeatingTask(this, task, 20L, 200L); // Every 10 seconds
            log(Level.INFO, "Started weather control task");
        }
    }

    /**
     * Stop all background tasks
     */
    private void stopTasks() {
        var scheduler = getServer().getScheduler();

        if (particleTaskId != -1) {
            scheduler.cancelTask(particleTaskId);
            particleTaskId = -1;
        }

        if (terrainTaskId != -1) {
            scheduler.cancelTask(terrainTaskId);
            terrainTaskId = -1;
        }

        if (weatherTaskId != -1) {
            scheduler.cancelTask(weatherTaskId);
            weatherTaskId = -1;
        }
    }

    /**
     * Weather change event handler
     * Prevents rain/thunderstorm when weather control is enabled
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(@NotNull WeatherChangeEvent event) {
        var config = WinterConfig.get();

        // Only cancel if weather is changing TO rain/storm
        if (!event.toWeatherState()) {
            return;
        }

        // Check if world is allowed
        if (!config.isWorldAllowed(event.getWorld().getName())) {
            return;
        }

        // Cancel weather change if disabled
        if (config.weather().disable()) {
            event.setCancelled(true);
            event.getWorld().setWeatherDuration(0);
            event.getWorld().setThundering(false);
        }
    }

    /**
     * Get the Winter instance
     */
    public static Winter getInstance() {
        return (Winter) WinterPlugin.getInstance();
    }
}
