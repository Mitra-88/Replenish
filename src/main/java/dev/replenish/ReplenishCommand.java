package dev.replenish;

import org.bukkit.ChatColor;
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
                boolean now = !plugin.getConfig().getBoolean("enabled", true);
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
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
                sender.sendMessage(ChatColor.GOLD + "---- Replenish Status ----");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + plugin.isEnabledGlobally());
                sender.sendMessage(ChatColor.YELLOW + "QoL mode: " + ChatColor.WHITE + plugin.isQolMode());
                sender.sendMessage(ChatColor.YELLOW + "Require seed: " + ChatColor.WHITE + plugin.isRequirePlayerSeed());
                sender.sendMessage(ChatColor.YELLOW + "Restrict to hoes/axes: " + ChatColor.WHITE + plugin.isRestrictToHoesAndAxes());
                sender.sendMessage(ChatColor.YELLOW + "Direct pickup: " + ChatColor.WHITE + plugin.isDirectPickup());
                sender.sendMessage(ChatColor.YELLOW + "Allow immature drops: " + ChatColor.WHITE + plugin.isAllowImmatureDrops());
                sender.sendMessage(ChatColor.YELLOW + "Replant delay (ticks): " + ChatColor.WHITE + plugin.getReplantDelayTicks());
                sender.sendMessage(ChatColor.YELLOW + "Crops: "
                        + ChatColor.WHITE + "wheat=" + plugin.isCropEnabled(org.bukkit.Material.WHEAT)
                        + ", carrots=" + plugin.isCropEnabled(org.bukkit.Material.CARROTS)
                        + ", potatoes=" + plugin.isCropEnabled(org.bukkit.Material.POTATOES)
                        + ", beetroots=" + plugin.isCropEnabled(org.bukkit.Material.BEETROOTS)
                        + ", nether_wart=" + plugin.isCropEnabled(org.bukkit.Material.NETHER_WART)
                        + ", cocoa=" + plugin.isCropEnabled(org.bukkit.Material.COCOA));
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
