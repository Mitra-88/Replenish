package dev.replenish;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("toggle", "reload");

    public ReplenishCommand(ReplenishPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " <toggle|reload>");
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
            default -> {
                sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " <toggle|reload>");
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
