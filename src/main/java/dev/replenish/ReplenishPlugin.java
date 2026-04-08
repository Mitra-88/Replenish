package dev.replenish;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ReplenishPlugin extends JavaPlugin {

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

        int millis = Math.max(0, config.getInt("replantDelayMs", 15));
        int replantDelayTicks = Math.max(1, Math.round(millis / 50f));
        config.set("replantDelayTicks", replantDelayTicks);

        int maxPerTick = Math.max(256, config.getInt("maxReplantsPerTick", 2048));
        config.set("maxReplantsPerTick", maxPerTick);

        saveConfig();

        ConfigCache newCache = ConfigCache.from(config);
        configCacheRef.set(newCache);

        if (replantQueue != null) {
            replantQueue.stop();
            replantQueue = new ReplantQueue(this, newCache.maxReplantsPerTick, ageMetaRegistry);
            replantQueue.start();
        }
    }

    public boolean isEnabledGlobally() { return getConfigCache().enabled; }
    public boolean isCropEnabled(Material crop) { return crop != null && getConfigCache().cropEnabled.getOrDefault(crop, true); }
    public ConfigCache getConfigCache() { return configCacheRef.get(); }

    public void enqueueReplant(org.bukkit.block.Block block,
                               org.bukkit.Material plantMaterial,
                               int delayTicks,
                               int targetAge,
                               org.bukkit.block.BlockFace cocoaFacingDirection) {
        ReplantQueue queue = this.replantQueue;
        if (queue != null) queue.enqueue(block, plantMaterial, delayTicks, targetAge, cocoaFacingDirection);
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
            this.replantDelayTicks = 1;
            this.maxReplantsPerTick = 2048;
            this.cropEnabled = defaultCrops();
        }

        static ConfigCache from(FileConfiguration config) {
            return new ConfigCache(
                    config.getBoolean("enabled", true),
                    config.getBoolean("requirePlayerSeed", true),
                    config.getBoolean("directPickup", true),
                    Math.max(1, config.getInt("replantDelayTicks", 1)),
                    Math.max(256, config.getInt("maxReplantsPerTick", 2048)),
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