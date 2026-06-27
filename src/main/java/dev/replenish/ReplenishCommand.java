package dev.replenish;

import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;

    private static final String PREFIX = "&8[&eReplenish&8] &7";
    private static final String ARROW = "&8» ";
    private static final String DOT = "&8• ";
    private static final String LINE = "&8&m                                   ";

    public ReplenishCommand(ReplenishPlugin plugin) {
        this.plugin = plugin;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.color(message));
    }

    private static void sendPrefixed(CommandSender sender, String message) {
        send(sender, PREFIX + message);
    }

    private static String usage(String base) {
        return PREFIX + ARROW + "&cUsage: &7/" + base + " &f<status | toggle | reload>";
    }

    private boolean isDenied(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send(sender, PREFIX + ARROW + "&cPermission denied. &8(&7" + perm + "&8)");
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, usage(label));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (isDenied(sender, "replenish.toggle")) return true;

                boolean nowEnabled = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", nowEnabled);
                plugin.saveConfig();

                plugin.setGloballyEnabled(nowEnabled);

                String state = nowEnabled ? "&a&lENABLED" : "&c&lDISABLED";
                sendPrefixed(sender, "Global replenish is now " + state + "&7.");
                return true;
            }
            case "reload" -> {
                if (isDenied(sender, "replenish.reload")) return true;

                plugin.reloadLocalConfig();
                sendPrefixed(sender, "&aConfiguration successfully reloaded.");
                return true;
            }
            case "status" -> {
                if (isDenied(sender, "replenish.status")) return true;

                var cfg = plugin.getConfigCache();
                String version = plugin.getDescription().getVersion();

                send(sender, "");
                send(sender, "&8&m      &8[ &e&lReplenish &7v" + version + " &8]&m      &r");
                send(sender, "");

                send(sender, "&eCore Settings:");
                send(sender, "  " + DOT + "&7Status: " + (cfg.enabled ? "&a&lON" : "&c&lOFF"));
                send(sender, "  " + DOT + "&7Require Seed: " + (cfg.requirePlayerSeed ? "&aYes" : "&cNo"));
                send(sender, "  " + DOT + "&7Direct Pickup: " + (cfg.directPickup ? "&aYes" : "&cNo"));
                send(sender, "  " + DOT + "&7Tools: &aHoes (Crops) &8| &aAxes (Cocoa)");
                send(sender, "");

                send(sender, "&ePerformance:");
                send(sender, "  " + DOT + "&7Replant Delay: &f" + cfg.replantDelayTicks + "t");
                send(sender, "  " + DOT + "&7Max Load/Tick: &f" + cfg.maxReplantsPerTick);
                send(sender, "");

                send(sender, "&eCrop Support:");
                send(sender, "  " + DOT + "&7Wheat: " + formatCrop(Material.WHEAT) +
                        "  &7Carrots: " + formatCrop(Material.CARROTS));
                send(sender, "  " + DOT + "&7Potatoes: " + formatCrop(Material.POTATOES) +
                        "  &7Nether Wart: " + formatCrop(Material.NETHER_WART));
                send(sender, "  " + DOT + "&7Cocoa: " + formatCrop(Material.COCOA));

                send(sender, "");
                send(sender, LINE);
                return true;
            }
            default -> {
                send(sender, usage(label));
                return true;
            }
        }
    }

    private String formatCrop(Material mat) {
        return plugin.isCropEnabled(mat) ? "&a✔" : "&c✘";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> allowed = new ArrayList<>(3);

            if (sender.hasPermission("replenish.status") && "status".startsWith(prefix)) {
                allowed.add("status");
            }
            if (sender.hasPermission("replenish.toggle") && "toggle".startsWith(prefix)) {
                allowed.add("toggle");
            }
            if (sender.hasPermission("replenish.reload") && "reload".startsWith(prefix)) {
                allowed.add("reload");
            }

            return allowed;
        }
        return Collections.emptyList();
    }
}
