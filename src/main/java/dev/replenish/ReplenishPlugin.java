package dev.replenish;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ReplenishPlugin extends JavaPlugin {

    private final AtomicReference<ConfigCache> cacheRef = new AtomicReference<>(new ConfigCache());
    private ReplantQueue replantQueue;
    private AgeMetaRegistry ageMeta;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ageMeta = new AgeMetaRegistry(this);
        reloadLocalConfig();

        replantQueue = new ReplantQueue(this, cfg().maxReplantsPerTick, ageMeta);
        replantQueue.start();

        getServer().getPluginManager().registerEvents(new ReplenishListener(this, ageMeta), this);

        ReplenishCommand cmd = new ReplenishCommand(this);
        if (getCommand("replenish") != null) {
            Objects.requireNonNull(getCommand("replenish")).setExecutor(cmd);
            Objects.requireNonNull(getCommand("replenish")).setTabCompleter(cmd);
        }

        getLogger().info("[Replenish] Enabled. global enabled=" + isEnabledGlobally());
    }

    @Override
    public void onDisable() {
        if (replantQueue != null) replantQueue.stop();
    }

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration c = getConfig();

        int ms = Math.max(0, c.getInt("replantDelayMs", 15));
        int replantDelayTicks = Math.max(1, Math.round(ms / 50f));
        c.set("replantDelayTicks", replantDelayTicks);

        int maxPerTick = Math.max(256, c.getInt("maxReplantsPerTick", 2048));
        c.set("maxReplantsPerTick", maxPerTick);

        saveConfig();

        ConfigCache newCache = ConfigCache.from(c);
        cacheRef.set(newCache);

        if (replantQueue != null) {
            replantQueue.stop();
            replantQueue = new ReplantQueue(this, newCache.maxReplantsPerTick, ageMeta);
            replantQueue.start();
        }
    }

    public boolean isEnabledGlobally() { return cfg().enabled; }
    public boolean isCropEnabled(Material crop) { return cfg().cropEnabled.getOrDefault(crop, true); }
    public ConfigCache cfg() { return cacheRef.get(); }
    public AgeMetaRegistry ages() { return ageMeta; }

    public void enqueueReplant(org.bukkit.block.Block block,
                               org.bukkit.Material plantMat,
                               int delayTicks,
                               int targetAge,
                               org.bukkit.block.BlockFace cocoaFacing) {
        ReplantQueue q = this.replantQueue;
        if (q != null) q.enqueue(block, plantMat, delayTicks, targetAge, cocoaFacing);
    }

    public static final class ConfigCache {
        final boolean enabled;
        final boolean requirePlayerSeed;
        final boolean restrictToHoesAndAxes;
        final boolean directPickup;
        final int replantDelayTicks;
        final int maxReplantsPerTick;
        final Map<Material, Boolean> cropEnabled;

        private ConfigCache() {
            this.enabled = true;
            this.requirePlayerSeed = true;
            this.restrictToHoesAndAxes = true;
            this.directPickup = true;
            this.replantDelayTicks = 1;
            this.maxReplantsPerTick = 2048;
            this.cropEnabled = defaultCrops();
        }

        static ConfigCache from(FileConfiguration c) {
            return new ConfigCache(
                    c.getBoolean("enabled", true),
                    c.getBoolean("requirePlayerSeed", true),
                    c.getBoolean("restrictToHoesAndAxes", true),
                    c.getBoolean("directPickup", true),
                    Math.max(1, c.getInt("replantDelayTicks", 1)),
                    Math.max(256, c.getInt("maxReplantsPerTick", 2048)),
                    readCrops(c)
            );
        }

        private ConfigCache(boolean enabled, boolean requirePlayerSeed, boolean restrictToHoesAndAxes,
                            boolean directPickup, int replantDelayTicks, int maxReplantsPerTick,
                            Map<Material, Boolean> cropEnabled) {
            this.enabled = enabled;
            this.requirePlayerSeed = requirePlayerSeed;
            this.restrictToHoesAndAxes = restrictToHoesAndAxes;
            this.directPickup = directPickup;
            this.replantDelayTicks = replantDelayTicks;
            this.maxReplantsPerTick = maxReplantsPerTick;
            this.cropEnabled = cropEnabled;
        }

        private static Map<Material, Boolean> readCrops(FileConfiguration c) {
            Map<Material, Boolean> map = defaultCrops();
            map.put(Material.WHEAT, c.getBoolean("crops.wheat", true));
            map.put(Material.CARROTS, c.getBoolean("crops.carrots", true));
            map.put(Material.POTATOES, c.getBoolean("crops.potatoes", true));
            map.put(Material.NETHER_WART, c.getBoolean("crops.nether_wart", true));
            map.put(Material.COCOA, c.getBoolean("crops.cocoa", true));
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
