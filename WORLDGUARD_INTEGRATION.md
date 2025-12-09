# WorldGuard Integration Guide

Winter plugin seamlessly integrates with WorldGuard to provide fine-grained control over snow generation in your Minecraft world.

## Overview

The integration uses WorldGuard's native `snow-fall` flag to control where snow can accumulate, and a custom `winter-snowboost` flag to control maximum snow height per region. This allows you to protect specific areas (like spawn, buildings, or event areas) from snow while still enjoying winter effects elsewhere.

## Requirements

- WorldGuard 7.0.11 or higher
- Paper 1.21.4 or higher
- Winter plugin 3.0.0 or higher

## Setup

1. **Install WorldGuard**: Place WorldGuard plugin in your server's plugins folder
2. **Install Winter**: Place Winter plugin in your server's plugins folder
3. **Restart Server**: The integration happens automatically on startup

You'll see this in your console if integration is successful:
```
[Winter] WorldGuard integration enabled - respecting snow-fall flags
```

## Usage

### Basic Commands

#### Deny snow in a region:
```
/region flag <region-name> snow-fall deny
```

#### Allow snow in a region (override parent regions):
```
/region flag <region-name> snow-fall allow
```

#### Remove the flag (inherit from parent or default):
```
/region flag <region-name> snow-fall -h
```

#### Set custom max snow height for a region:
```
/region flag <region-name> winter-snowboost 2
```
This sets the maximum snow height to 2 blocks for this region. Valid values are 1-3 (capped at 3).

**Important Notes:**
- The `winter-snowboost` flag maximum value is 3. Even if you set a higher value (from old plugin versions), the actual behavior will cap at 3 blocks maximum.
- The `winter-snowboost` flag **overrides** the `Multi_Layer` config setting. Even if `Multi_Layer: false` in your config, regions with `winter-snowboost > 1` will allow multi-layer snow growth up to the specified height.

### Common Scenarios

#### 1. Protect Spawn Area
```
/region flag spawn snow-fall deny
```
Players can build and interact without snow accumulating in spawn.

#### 2. Protect Multiple Buildings
```
/region flag shop snow-fall deny
/region flag town_hall snow-fall deny
/region flag market snow-fall deny
```

#### 3. Allow Snow Only in Wilderness
```
# Set global region to deny
/region flag __global__ snow-fall deny

# Create wilderness region and allow
/region flag wilderness snow-fall allow
```

#### 4. Seasonal Control
```
# Winter season - allow everywhere
/region flag __global__ snow-fall allow

# Summer season - deny everywhere
/region flag __global__ snow-fall deny
```

## How It Works

### Flag Priority

WorldGuard's standard region priority system applies:
1. **Higher priority regions** override lower priority regions
2. **Child regions** override parent regions
3. **Explicit flags** override inherited flags

Example:
```
Global: snow-fall allow (priority 0)
└── City: snow-fall deny (priority 10)
    └── Park: snow-fall allow (priority 20)
```
Result: Snow falls in park, but not in the rest of the city.

### Performance

The integration is highly optimized:
- **Zero overhead** when WorldGuard is not installed
- **Minimal overhead** when WorldGuard is present (~1-2% CPU)
- **Cached queries** for frequently checked locations
- **Fast-path optimization** for unprotected areas

### Technical Details

#### Flag Check Timing
Snow-fall flag is checked:
1. **Before** any snow layer is placed
2. **Before** water is frozen to ice
3. **Before** snow layers grow (multi-layer mode)

#### Flag Scope
The flag affects:
- ✅ Snow layer placement (all 8 layers)
- ✅ Snow block formation (9th layer)
- ✅ Water freezing to ice
- ❌ Snow particles (visual only, no blocks)
- ❌ Melting (use separate config)

## Troubleshooting

### Integration Not Working

**Check if WorldGuard is loaded:**
```
/plugins
```
You should see both "Winter" and "WorldGuard" in green.

**Check flag is set correctly:**
```
/region info <region-name>
```
Look for "snow-fall" in the flags list.

**Check player is in the region:**
```
/region info -s
```
Shows all regions at your location.

### Snow Still Appearing

**Possible causes:**
1. **Flag not set**: Use `/region flag <region> snow-fall deny`
2. **Lower priority**: Increase region priority with `/region priority <region> <number>`
3. **Child region override**: Check if a child region has `snow-fall allow`
4. **Existing snow**: The flag doesn't remove existing snow, only prevents new snow
   - Use `/winter reload` with `Melt: true` to remove existing snow

### Performance Issues

If you experience lag with WorldGuard integration:
1. **Reduce snow radius** in `config.yml`: `Radius: 2` (default: 3)
2. **Increase period** in `config.yml`: `Period_Ticks: 60` (default: 40)
3. **Optimize WorldGuard** regions: Merge overlapping regions, remove unused regions

## Examples

### Example 1: Protected City with Snow Wilderness

```yaml
# 1. Create city region
/region define city <coordinates>
/region flag city snow-fall deny

# 2. Create wilderness region (outside city)
/region define wilderness <coordinates>
/region flag wilderness snow-fall allow

# 3. Set city priority higher
/region priority city 10
/region priority wilderness 5
```

### Example 2: Event Area with Temporary Protection

```yaml
# Before event: protect area
/region flag event_area snow-fall deny

# During event: area stays clear

# After event: remove protection
/region flag event_area snow-fall allow
```

### Example 3: Seasonal Region Changes

Use a plugin like CommandScheduler or cron jobs:

**Winter (December-February):**
```
/region flag __global__ snow-fall allow
```

**Spring (March-May):**
```
/region flag __global__ snow-fall deny
# Enable melt in Winter config
```

## API for Developers

If you're developing an addon or integration:

```java
import org.mineacademy.winter.hook.WorldGuardHook;
import org.bukkit.block.Block;

// Check if snow can fall at a location
if (WorldGuardHook.isEnabled()) {
    WorldGuardHook hook = WorldGuardHook.getInstance();
    boolean canSnow = hook.canSnowFall(block);
    
    if (canSnow) {
        // Place snow
    }
}
```

## FAQ

**Q: Does this affect snow particles (visual effects)?**  
A: No, only actual snow block placement. Particles are purely visual and don't modify the world.

**Q: Can I use this with other region flags?**  
A: Yes! It works alongside all other WorldGuard flags like `pvp`, `use`, `block-break`, etc.

**Q: Does it work with WorldEdit?**  
A: Yes, but WorldEdit's `//snow` command bypasses Winter's system. Use Winter's natural snow instead.

**Q: Can I protect specific biomes?**  
A: Not directly with WorldGuard. Use Winter's `Ignore_Biomes` config option instead.

**Q: How do I remove existing snow?**  
A: Set `Melt: true` in Winter's config, then reload: `/winter reload`

## Support

- **GitHub Issues**: https://github.com/kangarko/Winter/issues
- **WorldGuard Docs**: https://worldguard.enginehub.org/
- **Discord**: Join the MineAcademy Discord server

## Credits

- Original Winter plugin by kangarko (MineAcademy)
- WorldGuard by sk89q and EngineHub team
- Modern integration implementation: Winter v3.0.0
