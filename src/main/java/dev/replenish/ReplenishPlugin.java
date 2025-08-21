package dev.replenish;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class ReplenishPlugin extends JavaPlugin {

    private volatile ConfigCache cache = new ConfigCache(); // hot-path cache

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        getServer().getPluginManager().registerEvents(new ReplenishListener(this), this);

        ReplenishCommand cmd = new ReplenishCommand(this);
        if (getCommand("replenish") != null) {
            Objects.requireNonNull(getCommand("replenish")).setExecutor(cmd);
            Objects.requireNonNull(getCommand("replenish")).setTabCompleter(cmd);
        }

        getLogger().info("[Replenish] Enabled. global enabled=" + isEnabledGlobally());
    }

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration c = getConfig();

        // derive tick delay from ms (1 tick = 50ms)
        int ms = Math.max(0, c.getInt("replantDelayMs", 15));
        int replantDelayTicks = Math.max(1, Math.round(ms / 50f));
        c.set("replantDelayTicks", replantDelayTicks); // persisted for visibility
        saveConfig();

        // refresh hot-path cache
        this.cache = ConfigCache.from(c);
    }

    // -------- Cached getters (used by listener/commands) --------
    public int getReplantDelayTicks() { return cache.replantDelayTicks; }
    public boolean isEnabledGlobally() { return cache.enabled; }
    public boolean isQolMode() { return cache.qolMode; }
    public boolean isRequirePlayerSeed() { return cache.requirePlayerSeed; }
    public boolean isRestrictToHoesAndAxes() { return cache.restrictToHoesAndAxes; }
    public boolean isDirectPickup() { return cache.directPickup; }
    public boolean isAllowImmatureDrops() { return cache.allowImmatureDrops; }
    public boolean isCropEnabled(Material crop) { return cache.cropEnabled.getOrDefault(crop, true); }

    public ConfigCache cfg() { return cache; }

    // -------- Cache container --------
    public static final class ConfigCache {
        final boolean enabled;
        final boolean qolMode;
        final boolean requirePlayerSeed;
        final boolean restrictToHoesAndAxes;
        final boolean directPickup;
        final boolean allowImmatureDrops;
        final int replantDelayTicks;
        final Map<Material, Boolean> cropEnabled;

        private ConfigCache() {
            this.enabled = true;
            this.qolMode = true;
            this.requirePlayerSeed = true;
            this.restrictToHoesAndAxes = true;
            this.directPickup = true;
            this.allowImmatureDrops = false;
            this.replantDelayTicks = 1;
            this.cropEnabled = defaultCrops();
        }

        static ConfigCache from(FileConfiguration c) {
            return new ConfigCache(
                    c.getBoolean("enabled", true),
                    c.getBoolean("qolMode", true),
                    c.getBoolean("requirePlayerSeed", true),
                    c.getBoolean("restrictToHoesAndAxes", true),
                    c.getBoolean("directPickup", true),
                    c.getBoolean("allowImmatureDrops", false),
                    c.getInt("replantDelayTicks", 1),
                    readCrops(c)
            );
        }

        private ConfigCache(boolean enabled, boolean qolMode, boolean requirePlayerSeed, boolean restrictToHoesAndAxes,
                            boolean directPickup, boolean allowImmatureDrops, int replantDelayTicks,
                            Map<Material, Boolean> cropEnabled) {
            this.enabled = enabled;
            this.qolMode = qolMode;
            this.requirePlayerSeed = requirePlayerSeed;
            this.restrictToHoesAndAxes = restrictToHoesAndAxes;
            this.directPickup = directPickup;
            this.allowImmatureDrops = allowImmatureDrops;
            this.replantDelayTicks = Math.max(1, replantDelayTicks);
            this.cropEnabled = cropEnabled;
        }

        private static Map<Material, Boolean> readCrops(FileConfiguration c) {
            Map<Material, Boolean> map = defaultCrops();
            map.put(Material.WHEAT, c.getBoolean("crops.wheat", true));
            map.put(Material.CARROTS, c.getBoolean("crops.carrots", true));
            map.put(Material.POTATOES, c.getBoolean("crops.potatoes", true));
            map.put(Material.BEETROOTS, c.getBoolean("crops.beetroots", true));
            map.put(Material.NETHER_WART, c.getBoolean("crops.nether_wart", true));
            map.put(Material.COCOA, c.getBoolean("crops.cocoa", true));
            return map;
        }

        private static Map<Material, Boolean> defaultCrops() {
            Map<Material, Boolean> map = new EnumMap<>(Material.class);
            map.put(Material.WHEAT, true);
            map.put(Material.CARROTS, true);
            map.put(Material.POTATOES, true);
            map.put(Material.BEETROOTS, true);
            map.put(Material.NETHER_WART, true);
            map.put(Material.COCOA, true);
            return map;
        }
    }
}
