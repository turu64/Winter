# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

**Prerequisites:**
- Java 21 (JDK 21)
- Maven
- Foundation library from github.com/kangarko/Foundation (see Compiling below)
- Library binaries described in pom.xml (must obtain yourself)

**Compiling:**
1. Obtain Foundation from github.com/kangarko/Foundation
2. Create `library/` folder in `Winter/` root and obtain binaries described in pom.xml
3. Compile Foundation first using Maven
4. Compile Winter using:
   ```bash
   mvn clean install
   ```

**Maven Goals:**
- `mvn clean install` - Full clean build and install to local repository
- `mvn package` - Build JAR without tests
- `mvn clean package` - Clean and build JAR

**Notes:**
- Uses maven-shade-plugin for dependency bundling
- Final artifact is a shaded JAR suitable for Paper servers
- Java 21 preview features are enabled (`--enable-preview`)
- Target platform: Paper 1.21.4 (uses Paper API, not Spigot)

## High-Level Architecture

### Plugin Lifecycle

The plugin follows a three-tier initialization pattern:

1. **WinterPlugin (Base Class)** - Modern replacement for Foundation's SimplePlugin
   - `onLoad()` - Registers WorldGuard custom flags (MUST happen before WorldGuard enables)
   - `onEnable()` - Calls `onPluginStart()` hook
   - `onDisable()` - Calls `onPluginStop()` hook

2. **Winter (Main Class)** - Extends WinterPlugin
   - `onPluginStart()` - Initialize config, data managers, listeners, commands, tasks
   - `onPluginStop()` - Stop tasks, save data
   - `onPluginReload()` - Reload config and restart tasks

### Configuration System (Java 21 Records)

The plugin uses **immutable Java 21 records** for type-safe configuration instead of Foundation's Settings system:

- **WinterConfig.java** - Modern config loader using records
- Configuration is loaded from `settings.yml` (not `config.yml`)
- All config is immutable - reload creates new record instances
- Nested records: `GiftChestConfig`, `TerrainConfig`, `SnowGenerationConfig`, `SnowmanConfig`, etc.
- Access pattern: `WinterConfig.get().terrain().snowGeneration().enabled()`

### Data Management

**PlayerDataManager:**
- Manages per-player data (snow particle preferences, chest access times)
- Uses Java records: `PlayerData(UUID, boolean snowEnabled, long lastChestOpen)`
- Thread-safe with `ConcurrentHashMap` cache
- YAML persistence in `playerdata.yml`

**ChestDataManager:**
- Manages gift chest and dated chest data
- Tracks which players have opened which chests
- Similar pattern to PlayerDataManager

### Snow Metadata Tracking (PDC)

**SnowMetadataManager** (Critical for melt functionality):
- Uses Paper's **Persistent Data Container (PDC)** API to mark plugin-placed snow
- Stores metadata in chunk PDC (survives server restarts)
- Key format: `winter:snow_X_Y_Z` (relative chunk coordinates)
- **Purpose:** Distinguish plugin-placed snow from player-built snow structures
- **Melt mode dependency:** When `Only_Melt_Plugin_Snow: true`, only snow with PDC metadata is melted
- All snow placement MUST call `SnowMetadataManager.markAsPluginPlaced(block)` immediately after placement

### Task System (Scheduler)

Three main background tasks run on Bukkit scheduler:

1. **ParticleSnowTask** - Spawns snow particles around players
2. **AsyncTerrainTask** - Places/melts snow with async optimization (see below)
3. **WeatherTask** - Controls weather conditions

Tasks are started in `startTasks()` and stopped in `stopTasks()` with proper ID tracking.

### Async Terrain Processing (Performance Critical)

**AsyncTerrainTask** implements high-performance snow generation/melting:

- **Dual mode:** Sync (legacy) or async processing (default)
- **Async mode:** Uses `CompletableFuture` to move calculations off main thread
- **Batch processing:** Processes blocks in configurable batches (default 100)
- **Thread-safe:** Uses `ConcurrentHashMap` for chunk caching
- **Main thread sync:** Block changes must happen on main thread (Paper requirement)
- **PDC marking:** All placed snow is marked via SnowMetadataManager

Configuration:
- `Use_Async_Processing: true` - Enable async mode
- `Batch_Size: 100` - Blocks per async batch

### Multi-Block Snow Height System

The plugin supports **realistic deep snow** (up to 3+ blocks high):

1. **Snow layers (SNOW)** - 8 layers per block (vanilla)
2. **Snow blocks (SNOW_BLOCK)** - When layers reach 8, converts to snow block
3. **Stacking** - Snow blocks can stack vertically up to `Max_Height` (default 3)
4. **Growth algorithm:**
   - Counts neighboring snow at same/higher level
   - Requires `Required_Neighbors_To_Grow` (default 2) to grow
   - Checks `Max_Height` before growing

**Important:** `getSnowHeight(block)` calculates total height by counting stacked snow blocks below.

### WorldGuard Integration

**WorldGuardHook** provides two-phase integration:

**Phase 1 - onLoad() (in WinterPlugin):**
```java
WorldGuardHook.registerCustomFlags(this)
```
- Registers custom `winter-snowboost` IntegerFlag
- MUST happen during plugin onLoad() before WorldGuard enables

**Phase 2 - onEnable() (in Winter):**
```java
WorldGuardHook.initialize(this)
```
- Gets WorldGuard's standard `snow-fall` StateFlag
- Enables flag checking

**Flag behavior:**
- `snow-fall: deny` - No snow placement at all (returns -1 from getMaxSnowHeight)
- `snow-fall: allow` - Use default or winter-snowboost height
- `winter-snowboost: <number>` - Custom max height for region (overrides config, capped at 3)

**Usage in terrain code:**
- Always call `canSnowFallHere(block)` before placing snow
- Use `getMaxSnowHeightForLocation(block, defaultHeight)` to respect winter-snowboost
- `winter-snowboost` values are capped at 3 (even if old versions set higher values)
- `winter-snowboost` overrides `Multi_Layer: false` - if flag allows height > 1, multi-layer is enabled for that region

### Fragile Block Protection

**AsyncTerrainTask.hasFragileEntitiesOrBlocks()** prevents snow from destroying:

**Entities:**
- ITEM_FRAME, GLOW_ITEM_FRAME, PAINTING (wall-mounted)
- ARMOR_STAND (ground)

**Blocks:**
- Torches, lanterns, levers, buttons, pressure plates
- Signs, banners, rails
- Flowers, carpets
- Interactive: chests, furnaces, barrels, hoppers, dispensers, droppers
- Crafting stations: tables, anvils, looms, grindstones
- Functional: beacons, brewing stands, lecterns, composters, campfires, beehives

This is automatically checked before snow placement - do not place snow if this returns true.

### Listener Architecture

Listeners are **conditionally registered** based on configuration:

- Always registered: `ChestListener`, weather handler
- Conditional: `SnowmanMeltListener`, `SnowmanTargetListener`, `SnowmanTransformListener`, `SnowmanDamageListener`, `MeltingListener`
- Check in `registerListeners()` for registration logic

### Command System

**WinterCommandHandler** manages all commands:
- Main command defined by first alias in `Command_Aliases` (default: `/winter`)
- Subcommands: reload, toggle snow, etc.

### Messages and Localization

**Messages.java** loads localized messages from `localization/messages_<locale>.yml`:
- Locale configured in `settings.yml` (default: `en`)
- Available: en, es, it, ru, de
- Uses Adventure API Components (not legacy chat)

### Melt Mode vs Snow Mode

The plugin has two operational modes (mutually exclusive):

**Snow Mode (default):**
- `Terrain.Snow_Generation.Melt: false`
- Gradually covers terrain with snow
- Places snow layers → snow blocks → stacks blocks

**Melt Mode:**
- `Terrain.Snow_Generation.Melt: true`
- Gradually removes snow from terrain
- Melts from top down: stacked blocks → snow block → layers → air
- Respects `Only_Melt_Unnatural_Snow` (preserves snow in snowy biomes/high altitude)
- Respects `Only_Melt_Plugin_Snow` (preserves player-built snow via PDC check)
- Can convert ice back to water if `Freeze_Water: true`

## Important Technical Details

### Paper API (Not Spigot)

This plugin targets **Paper 1.21.4**, not Spigot:
- Uses Adventure API for text components (not legacy strings)
- Uses Paper's enhanced scheduler and async APIs
- DO NOT use deprecated Spigot APIs

### Persistent Data Container (PDC)

PDC is used for chunk-level metadata storage:
- Key namespace: `winter`
- Format: `snow_X_Y_Z` (chunk-relative coordinates)
- Persists across server restarts (stored in chunk data)
- Thread-safe, fast (in-memory lookups)

### WorldGuard Flag Registration Timing

**CRITICAL:** Custom flags MUST be registered during `onLoad()` phase:
- WinterPlugin.onLoad() → WorldGuardHook.registerCustomFlags()
- If registered during onEnable(), WorldGuard will ignore them

### Configuration File Naming

The plugin uses **settings.yml**, not config.yml:
- Bukkit's default `config.yml` is unused
- All configuration in `settings.yml`
- Messages in `localization/messages_<locale>.yml`

### Foundation Library

Original plugin depended on Foundation (kangarko's utility library):
- **WinterPlugin** class replaces Foundation's SimplePlugin
- **WinterConfig** replaces Foundation's Settings
- Still compatible with Foundation patterns (same initialization flow)

### Performance Considerations

When working on terrain generation/melting:
- Keep `Radius` low (2-5) for production
- Use `Use_Async_Processing: true` for better TPS
- Batch size should be 50-200 depending on hardware
- PDC operations are fast - no performance concerns
- WorldGuard flag checks are cached internally by WG

## Coding Patterns to Follow

**Configuration access:**
```java
var config = WinterConfig.get().terrain().snowGeneration();
if (config.enabled()) { ... }
```

**Snow placement:**
```java
block.setType(Material.SNOW);
SnowMetadataManager.markAsPluginPlaced(block); // Always mark!
```

**WorldGuard checks:**
```java
if (!canSnowFallHere(block)) return;
int maxHeight = getMaxSnowHeightForLocation(block, config.maxHeight());
if (maxHeight < 0) return; // snow-fall: deny
```

**Logging:**
```java
plugin.log(Level.INFO, "Message");
plugin.log(Level.WARNING, "Error message", exception);
```

**Adventure Components:**
```java
Component message = plugin.colorize("&aGreen text");
player.sendMessage(message);
```
