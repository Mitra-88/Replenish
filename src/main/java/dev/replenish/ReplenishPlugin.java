package dev.replenish;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.Objects;

public class ReplenishPlugin extends JavaPlugin {

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
        int ms = Math.max(0, c.getInt("replantDelayMs", 15));
        int replantDelayTicks = Math.max(1, Math.round(ms / 50f));
        getConfig().set("replantDelayTicks", replantDelayTicks);
        saveConfig();
    }

    public int getReplantDelayTicks() { return getConfig().getInt("replantDelayTicks", 1); }
    public boolean isEnabledGlobally() { return getConfig().getBoolean("enabled", true); }
    public boolean isQolMode() { return getConfig().getBoolean("qolMode", true); }
    public boolean isRequirePlayerSeed() { return getConfig().getBoolean("requirePlayerSeed", true); }
    public boolean isRestrictToHoesAndAxes() { return getConfig().getBoolean("restrictToHoesAndAxes", true); }

    public boolean isCropEnabled(Material crop) {
        return switch (crop) {
            case WHEAT -> getConfig().getBoolean("crops.wheat", true);
            case CARROTS -> getConfig().getBoolean("crops.carrots", true);
            case POTATOES -> getConfig().getBoolean("crops.potatoes", true);
            case BEETROOTS -> getConfig().getBoolean("crops.beetroots", true);
            case NETHER_WART -> getConfig().getBoolean("crops.nether_wart", true);
            case COCOA -> getConfig().getBoolean("crops.cocoa", true);
            default -> true;
        };
    }
}
