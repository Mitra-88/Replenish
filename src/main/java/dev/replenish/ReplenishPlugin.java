package dev.replenish;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ReplenishPlugin extends JavaPlugin {

    private static final int DEFAULT_REPLANT_DELAY_TICKS = 1;
    private static final int DEFAULT_MAX_REPLANTS = 4096;
    private static final int MIN_REPLANTS_PER_TICK = 256;

    private final AtomicReference<ConfigCache> configCacheRef = new AtomicReference<>(new ConfigCache());
    private volatile ReplantQueue replantQueue;
    private AgeMetaRegistry ageMetaRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ageMetaRegistry = new AgeMetaRegistry(this);
        reloadLocalConfig();

        replantQueue = new ReplantQueue(this, getConfigCache().maxReplantsPerTick, ageMetaRegistry);
        replantQueue.start();

        getServer().getPluginManager().registerEvents(new ReplenishListener(this, ageMetaRegistry), this);

        ReplenishCommand replenishCommand = new ReplenishCommand(this);
        if (getCommand("replenish") != null) {
            Objects.requireNonNull(getCommand("replenish")).setExecutor(replenishCommand);
            Objects.requireNonNull(getCommand("replenish")).setTabCompleter(replenishCommand);
        }
    }

    @Override
    public void onDisable() {
        if (replantQueue != null) replantQueue.stop();
    }

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        int delayTicks = Math.max(
                1,
                config.getInt("replantDelayTicks", DEFAULT_REPLANT_DELAY_TICKS)
        );

        int maxPerTick = Math.max(
                MIN_REPLANTS_PER_TICK,
                config.getInt("maxReplantsPerTick", DEFAULT_MAX_REPLANTS)
        );

        ConfigCache newCache = ConfigCache.from(config, delayTicks, maxPerTick);
        configCacheRef.set(newCache);

        if (replantQueue != null) {
            replantQueue.stop();
        }

        replantQueue = new ReplantQueue(this, maxPerTick, ageMetaRegistry);
        replantQueue.start();
    }

    public boolean isEnabledGlobally() {
        return getConfigCache().enabled;
    }

    public boolean isCropEnabled(Material crop) {
        return crop != null && getConfigCache().cropEnabled.getOrDefault(crop, true);
    }

    public ConfigCache getConfigCache() {
        return configCacheRef.get();
    }

    public void enqueueReplant(org.bukkit.block.Block block,
                               org.bukkit.Material plantMaterial,
                               int delayTicks,
                               int targetAge,
                               org.bukkit.block.BlockFace cocoaFacingDirection) {
        ReplantQueue queue = this.replantQueue;
        if (queue != null) {
            queue.enqueue(block, plantMaterial, delayTicks, targetAge, cocoaFacingDirection);
        }
    }

    public static final class ConfigCache {
        final boolean enabled;
        final boolean requirePlayerSeed;
        final boolean directPickup;
        final int replantDelayTicks;
        final int maxReplantsPerTick;
        final Map<Material, Boolean> cropEnabled;

        private ConfigCache() {
            this.enabled = true;
            this.requirePlayerSeed = true;
            this.directPickup = true;
            this.replantDelayTicks = DEFAULT_REPLANT_DELAY_TICKS;
            this.maxReplantsPerTick = DEFAULT_MAX_REPLANTS;
            this.cropEnabled = defaultCrops();
        }

        static ConfigCache from(FileConfiguration config, int delayTicks, int maxPerTick) {
            return new ConfigCache(
                    config.getBoolean("enabled", true),
                    config.getBoolean("requirePlayerSeed", true),
                    config.getBoolean("directPickup", true),
                    delayTicks,
                    maxPerTick,
                    readCrops(config)
            );
        }

        private ConfigCache(boolean enabled, boolean requirePlayerSeed, boolean directPickup,
                            int replantDelayTicks, int maxReplantsPerTick, Map<Material, Boolean> cropEnabled) {
            this.enabled = enabled;
            this.requirePlayerSeed = requirePlayerSeed;
            this.directPickup = directPickup;
            this.replantDelayTicks = replantDelayTicks;
            this.maxReplantsPerTick = maxReplantsPerTick;
            this.cropEnabled = cropEnabled;
        }

        private static Map<Material, Boolean> readCrops(FileConfiguration config) {
            Map<Material, Boolean> map = defaultCrops();
            map.put(Material.WHEAT, config.getBoolean("crops.wheat", true));
            map.put(Material.CARROTS, config.getBoolean("crops.carrots", true));
            map.put(Material.POTATOES, config.getBoolean("crops.potatoes", true));
            map.put(Material.NETHER_WART, config.getBoolean("crops.nether_wart", true));
            map.put(Material.COCOA, config.getBoolean("crops.cocoa", true));
            return map;
        }

        private static Map<Material, Boolean> defaultCrops() {
            Map<Material, Boolean> map = new EnumMap<>(Material.class);
            map.put(Material.WHEAT, true);
            map.put(Material.CARROTS, true);
            map.put(Material.POTATOES, true);
            map.put(Material.NETHER_WART, true);
            map.put(Material.COCOA, true);
            return map;
        }
    }
}
