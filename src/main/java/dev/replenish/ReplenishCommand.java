package dev.replenish;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("toggle", "reload", "status");

    public ReplenishCommand(ReplenishPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " <toggle|reload|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (!sender.hasPermission("replenish.toggle")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                boolean now = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                // refresh cache for consistency if toggled via command
                plugin.reloadLocalConfig();
                sender.sendMessage(ChatColor.YELLOW + "[Replenish] Global enabled is now " + now);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("replenish.reload")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.reloadLocalConfig();
                sender.sendMessage(ChatColor.GREEN + "[Replenish] Config reloaded.");
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("replenish.status")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                var cfg = plugin.cfg();
                sender.sendMessage(ChatColor.GOLD + "---- Replenish Status ----");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + cfg.enabled);
                sender.sendMessage(ChatColor.YELLOW + "QoL mode: " + ChatColor.WHITE + cfg.qolMode);
                sender.sendMessage(ChatColor.YELLOW + "Require seed: " + ChatColor.WHITE + cfg.requirePlayerSeed);
                sender.sendMessage(ChatColor.YELLOW + "Restrict to hoes/axes: " + ChatColor.WHITE + cfg.restrictToHoesAndAxes);
                sender.sendMessage(ChatColor.YELLOW + "Direct pickup: " + ChatColor.WHITE + cfg.directPickup);
                sender.sendMessage(ChatColor.YELLOW + "Allow immature drops: " + ChatColor.WHITE + cfg.allowImmatureDrops);
                sender.sendMessage(ChatColor.YELLOW + "Replant delay (ticks): " + ChatColor.WHITE + cfg.replantDelayTicks);
                sender.sendMessage(ChatColor.YELLOW + "Crops: "
                        + ChatColor.WHITE + "wheat=" + plugin.isCropEnabled(Material.WHEAT)
                        + ", carrots=" + plugin.isCropEnabled(Material.CARROTS)
                        + ", potatoes=" + plugin.isCropEnabled(Material.POTATOES)
                        + ", beetroots=" + plugin.isCropEnabled(Material.BEETROOTS)
                        + ", nether_wart=" + plugin.isCropEnabled(Material.NETHER_WART)
                        + ", cocoa=" + plugin.isCropEnabled(Material.COCOA));
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " <toggle|reload|status>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}
