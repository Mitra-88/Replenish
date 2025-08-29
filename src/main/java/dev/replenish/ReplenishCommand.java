package dev.replenish;

import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("status", "toggle", "reload");

    public ReplenishCommand(ReplenishPlugin plugin) { this.plugin = plugin; }

    private static void send(CommandSender s, String msg) { s.sendMessage(ColorUtils.color(msg)); }

    private static String usage(String base) {
        return "&7Usage: &e/" + base + " &fstatus|toggle|reload";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            send(sender, usage(label));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (!sender.hasPermission("replenish.toggle")) { send(sender, "&cYou don’t have permission."); return true; }
                boolean now = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                plugin.reloadLocalConfig();
                send(sender, "&7Global: " + (now ? "&aON" : "&cOFF"));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("replenish.reload")) { send(sender, "&cYou don’t have permission."); return true; }
                plugin.reloadLocalConfig();
                send(sender, "&aConfig reloaded.");
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("replenish.status")) { send(sender, "&cYou don’t have permission."); return true; }

                var cfg = plugin.cfg();

                send(sender, "&6&lReplenish &7v" + plugin.getDescription().getVersion());
                send(sender, "&8-----------------");

                send(sender, "&eCore");
                send(sender, "  &7Enabled: " + (cfg.enabled ? "&aON" : "&cOFF"));
                send(sender, "  &7Require seed: " + (cfg.requirePlayerSeed ? "&aYes" : "&cNo"));
                send(sender, "  &7Direct pickup: " + (cfg.directPickup ? "&aYes" : "&cNo"));
                send(sender, "  &7Tool requirement: &aHoes for crops, Axes for cocoa");

                send(sender, "");
                send(sender, "&eTuning");
                send(sender, "  &7Replant delay (ticks): &f" + cfg.replantDelayTicks);
                send(sender, "  &7Max replants/tick: &f" + cfg.maxReplantsPerTick);

                send(sender, "");
                send(sender, "&eCrops");
                send(sender, "  &7Wheat: " + (plugin.isCropEnabled(Material.WHEAT) ? "&a✓" : "&c✗"));
                send(sender, "  &7Carrots: " + (plugin.isCropEnabled(Material.CARROTS) ? "&a✓" : "&c✗"));
                send(sender, "  &7Potatoes: " + (plugin.isCropEnabled(Material.POTATOES) ? "&a✓" : "&c✗"));
                send(sender, "  &7Nether Wart: " + (plugin.isCropEnabled(Material.NETHER_WART) ? "&a✓" : "&c✗"));
                send(sender, "  &7Cocoa: " + (plugin.isCropEnabled(Material.COCOA) ? "&a✓" : "&c✗"));

                send(sender, "&8-----------------");
                return true;
            }
            default -> {
                send(sender, usage(label));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(p)).toList();
        }
        return Collections.emptyList();
    }
}
