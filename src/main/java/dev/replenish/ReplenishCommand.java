package dev.replenish;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("toggle", "reload", "status");

    public ReplenishCommand(ReplenishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " <toggle|reload|status>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (!sender.hasPermission("replenish.toggle")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                boolean now = !plugin.getConfig().getBoolean("enabled", true);
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "[Replenish] Global enabled is now " + now);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("replenish.reload")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                plugin.reloadLocalConfig();
                sender.sendMessage(ChatColor.GREEN + "[Replenish] Config reloaded.");
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("replenish.status")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
                boolean en = plugin.isEnabledGlobally();
                boolean qol = plugin.isQolMode();
                boolean reqSeed = plugin.isRequirePlayerSeed();
                boolean restrictTools = plugin.isRestrictToHoesAndAxes();
                int ticks = plugin.getReplantDelayTicks();
                int ms = Math.max(1, Math.round(ticks * 50f));

                sender.sendMessage(ChatColor.GOLD + "—— Replenish Status ——");
                sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + en);
                sender.sendMessage(ChatColor.YELLOW + "QoL Mode: " + ChatColor.WHITE + qol);
                sender.sendMessage(ChatColor.YELLOW + "Require Player Seed: " + ChatColor.WHITE + reqSeed);
                sender.sendMessage(ChatColor.YELLOW + "Restrict to Hoes/Axes: " + ChatColor.WHITE + restrictTools);
                sender.sendMessage(ChatColor.YELLOW + "Replant Delay: " + ChatColor.WHITE + ticks + " ticks (" + ms + " ms)");
                sender.sendMessage(ChatColor.YELLOW + "Crops:");
                sender.sendMessage(ChatColor.GRAY + "  - Wheat: " + plugin.isCropEnabled(Material.WHEAT));
                sender.sendMessage(ChatColor.GRAY + "  - Carrots: " + plugin.isCropEnabled(Material.CARROTS));
                sender.sendMessage(ChatColor.GRAY + "  - Potatoes: " + plugin.isCropEnabled(Material.POTATOES));
                sender.sendMessage(ChatColor.GRAY + "  - Beetroots: " + plugin.isCropEnabled(Material.BEETROOTS));
                sender.sendMessage(ChatColor.GRAY + "  - Nether Wart: " + plugin.isCropEnabled(Material.NETHER_WART));
                sender.sendMessage(ChatColor.GRAY + "  - Cocoa: " + plugin.isCropEnabled(Material.COCOA));
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
