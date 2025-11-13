# Winter Plugin - Modern Edition (v3.0.0)

## Overview

This is a complete modernization of the Winter plugin for Minecraft (Paper) 1.21.4 using Java 21.

## Major Changes

### Technology Stack
- **Java 21**: Leveraging modern Java features
  - Records for immutable data structures
  - Pattern matching in switch expressions
  - Text blocks for better code readability
  - Sealed interfaces for type safety
  - var keyword for type inference
- **Paper 1.21.4**: Using latest Paper API
  - No NMS (Net Minecraft Server) code
  - Modern Adventure API for text components
  - Enhanced performance with Paper's optimizations

### Architecture Improvements

#### 1. Configuration System (`core/config/`)
- **WinterConfig**: Modern configuration using Java 21 Records
  - Type-safe configuration access
  - Immutable configuration objects
  - Better validation and error handling

- **Messages**: Internationalization system
  - Support for multiple languages
  - Message formatting with placeholders
  - Adventure Component integration

#### 2. Data Management (`data/`)
- **PlayerDataManager**: Player preferences and state
  - Concurrent data access with ConcurrentHashMap
  - Automatic save/load functionality
  - Thread-safe operations

- **ChestDataManager**: Special chest management
  - Sealed interface for type safety
  - Three chest types: Gift, Dated, Timed
  - Pattern matching for chest type handling

#### 3. Tasks (`task/`)
- **ParticleSnowTask**: Modern particle spawning
  - TPS-aware performance optimization
  - Realistic snow behavior
  - Biome-specific snow spawning

- **TerrainTask**: Snow generation/melting
  - Multi-layer snow support
  - Water freezing mechanics
  - Crop protection system

- **WeatherTask**: Weather control
  - World-specific weather management
  - Storm mode support

#### 4. Event Listeners (`listener/`)
- **ChestListener**: Winter chest interactions
  - Pattern matching for chest types
  - Permission-based access control
  - Preview mode for dated chests

- **SnowmanMeltListener**: Snowman protection
- **SnowmanTargetListener**: Mob targeting prevention
- **SnowmanTransformListener**: Entity transformation
- **SnowmanDamageListener**: Custom snowball damage
- **MeltingListener**: Block melting prevention

#### 5. Commands (`command/`)
- **WinterCommandHandler**: Modern command system
  - Tab completion support
  - Permission-based access
  - Clean subcommand routing

### Removed Dependencies
- **Foundation Library**: Completely removed
  - Replaced with standard Paper API
  - Custom implementation of required features
  - No external library dependencies (except Paper API)

- **ProtocolLib**: No longer required
  - Biome disguise feature removed (outdated)
  - All features use native Paper API

- **NMS Code**: Eliminated
  - Psycho Snowman feature simplified
  - Uses Paper API instead of NMS

### Features

All original features have been preserved and modernized:

1. **Snow Particles**
   - Realistic snow falling around players
   - TPS-aware performance scaling
   - Biome-specific behavior
   - Per-player toggle

2. **Terrain Snow Generation**
   - Dynamic snow placement/removal
   - Multi-layer snow support
   - Water freezing
   - Crop protection
   - **WorldGuard Integration**: Automatically respects `snow-fall: deny` flags

3. **Gift Chests**
   - Public and private chests
   - One-time loot
   - Sign-based creation

4. **Dated Chests**
   - Time-limited availability
   - Year-spanning periods
   - Preview mode

5. **Timed Chests**
   - Cooldown-based reopening
   - Infinite loot with limits

6. **Snowman Features**
   - Melt protection
   - Target prevention
   - Entity transformation
   - Custom snowball damage

7. **Weather Control**
   - Disable rain/storms
   - Snow storm mode

### Configuration

Configuration files remain largely compatible with the original version:
- `config.yml`: Main configuration (auto-generated)
- `localization/messages_en.yml`: English messages
- `playerdata.yml`: Player preferences
- `chests.yml`: Special chest data

### Commands

- `/winter snow` - Toggle snow particles
- `/winter reload` - Reload configuration
- `/winter help` - Show help

### Permissions

- `winter.use` - Use basic commands (default: true)
- `winter.reload` - Reload configuration (default: op)
- `winter.chest.break` - Break Winter chests (default: op)

### Plugin Integrations

#### WorldGuard Integration

Winter automatically integrates with WorldGuard 7.0+ to respect region protection flags:

**Snow-Fall Flag Protection**:
- Regions with `snow-fall: deny` flag will NOT have snow generated
- Works automatically - no configuration needed
- Falls back gracefully if WorldGuard is not installed

**Usage Example**:
```
/region flag spawn snow-fall deny
```
This prevents snow from accumulating in your spawn region while allowing it everywhere else.

**Technical Details**:
- Uses WorldGuard's native `snow-fall` flag
- Check happens before any snow block placement
- Zero performance impact when WorldGuard is not installed
- Fully compatible with WorldGuard 7.0.11+

**Benefits**:
- Protect builds and specific areas from snow
- Fine-grained control over where snow can accumulate
- Works seamlessly with WorldGuard's region priorities
- No additional configuration required

## Migration Notes

### From v2.5.x to v3.0.0

1. **Configuration**: Mostly compatible, but review new settings
2. **Data Files**: Player data and chest data are converted automatically
3. **Permissions**: Updated permission nodes, review your permission plugin
4. **Commands**: Command syntax unchanged
5. **Dependencies**: Remove ProtocolLib if only used for Winter

### Breaking Changes

1. **Psycho Snowman**: Simplified implementation without NMS
   - No version-specific implementations
   - Uses Paper API entity manipulation
   - May behave slightly differently

2. **Biome Disguise**: Feature removed
   - Was unstable on modern versions
   - Caused client crashes
   - Paper's own biome API is more reliable

## Building

Requirements:
- Java 21 or higher
- Maven 3.9+

```bash
mvn clean package
```

The compiled JAR will be in `target/Winter-3.0.0.jar`

## Installation

1. Ensure server is running Paper 1.21.4 or higher
2. Install Java 21 on your server
3. Place JAR in plugins folder
4. Restart server
5. Configure in `plugins/Winter/config.yml`

## Performance

The modern edition includes several performance improvements:
- TPS-aware particle spawning
- Efficient concurrent data structures
- Optimized event handling
- Reduced memory allocations with Records

## Support

- GitHub Issues: https://github.com/kangarko/Winter/issues
- Original Plugin: https://www.spigotmc.org/resources/winter.49646/

## Credits

- Original Author: kangarko (MineAcademy)
- Modern Edition: AI-assisted modernization for Java 21 + Paper 1.21.4

## License

Same as original Winter plugin
