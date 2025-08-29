package dev.replenish;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;

import java.util.*;

@SuppressWarnings("NullableProblems")
public class ReplenishCommand implements CommandExecutor, TabCompleter {
    private final ReplenishPlugin plugin;
    private static final List<String> SUBS = Arrays.asList("status", "toggle", "reload");

    public ReplenishCommand(ReplenishPlugin plugin) { this.plugin = plugin; }

    private static String bar() {
        return ChatColor.DARK_GRAY + "────────────";
    }

    private static String title(String subtitle) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "Replenish"
                + ChatColor.GRAY + "  " + ChatColor.DARK_GRAY + "•" + ChatColor.GRAY + "  " + subtitle;
    }

    private static String k(String key) {
        return ChatColor.GRAY + key + ChatColor.DARK_GRAY + ": " + ChatColor.RESET;
    }

    private static String onOff(boolean v) {
        return v ? ChatColor.GREEN + "ON" + ChatColor.RESET : ChatColor.RED + "OFF" + ChatColor.RESET;
    }

    private static String chip(boolean v) {
        return v
                ? ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "✓" + ChatColor.DARK_GRAY + "]" + ChatColor.RESET
                : ChatColor.DARK_GRAY + "[" + ChatColor.RED + "✗" + ChatColor.DARK_GRAY + "]" + ChatColor.RESET;
    }

    private static String tintMaxPerTick(int value) {
        ChatColor c = (value >= 32) ? ChatColor.GREEN : (value >= 8 ? ChatColor.YELLOW : ChatColor.RED);
        return c + String.valueOf(value) + ChatColor.RESET;
    }

    private static String tintDelay(int ticks) {
        ChatColor c = (ticks <= 2) ? ChatColor.GREEN : (ticks <= 10 ? ChatColor.YELLOW : ChatColor.RED);
        return c + String.valueOf(ticks) + ChatColor.RESET;
    }

    private static String cropItem(String name, boolean enabled) {
        return (enabled ? ChatColor.GREEN : ChatColor.RED) + name + ChatColor.RESET + " " + chip(enabled);
    }

    private static void send(CommandSender s, String msg) { s.sendMessage(msg); }

    private static String usage(String base) {
        return ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/" + base + ChatColor.WHITE + " status|toggle|reload";
    }

    private static void noPerm(CommandSender s) {
        send(s, ChatColor.RED + "You don’t have permission for that.");
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
                if (!sender.hasPermission("replenish.toggle")) { noPerm(sender); return true; }
                boolean now = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                plugin.reloadLocalConfig();
                send(sender, ChatColor.GRAY + "Global: " + onOff(now));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("replenish.reload")) { noPerm(sender); return true; }
                plugin.reloadLocalConfig();
                send(sender, ChatColor.GREEN + "Config reloaded.");
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("replenish.status")) { noPerm(sender); return true; }

                var cfg = plugin.cfg();
                String ver = plugin.getDescription().getVersion();

                send(sender, bar());
                send(sender, title("v" + ver));
                send(sender, bar());

                send(sender, ChatColor.GOLD + "Core");
                send(sender, "  " + k("Enabled") + onOff(cfg.enabled) + ChatColor.DARK_GRAY + "  " + chip(cfg.enabled));
                send(sender, "  " + k("QoL mode") + chip(cfg.qolMode));
                send(sender, "  " + k("Require seed") + onOff(cfg.requirePlayerSeed));
                send(sender, "  " + k("Restrict tools") + chip(cfg.restrictToHoesAndAxes));
                send(sender, "  " + k("Direct pickup") + onOff(cfg.directPickup));
                send(sender, "  " + k("Immature drops") + chip(cfg.allowImmatureDrops));

                send(sender, "");
                send(sender, ChatColor.GOLD + "Tuning");
                send(sender, "  " + k("Replant delay (ticks)") + tintDelay(cfg.replantDelayTicks) + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + "lower = faster");
                send(sender, "  " + k("Max replants/tick") + tintMaxPerTick(cfg.maxReplantsPerTick) + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + "higher = more");

                boolean wheat = plugin.isCropEnabled(Material.WHEAT);
                boolean carrots = plugin.isCropEnabled(Material.CARROTS);
                boolean potatoes = plugin.isCropEnabled(Material.POTATOES);
                boolean wart = plugin.isCropEnabled(Material.NETHER_WART);
                boolean cocoa = plugin.isCropEnabled(Material.COCOA);

                send(sender, "");
                send(sender, ChatColor.GOLD + "Crops");
                send(sender, "  " + cropItem("Wheat", wheat) + ChatColor.DARK_GRAY + "  •  "
                        + cropItem("Carrots", carrots) + ChatColor.DARK_GRAY + "  •  "
                        + cropItem("Potatoes", potatoes));
                send(sender, "  " + cropItem("Nether Wart", wart) + ChatColor.DARK_GRAY + "  •  "
                        + cropItem("Cocoa", cocoa));

                send(sender, "");
                send(sender, ChatColor.GRAY + "ON/OFF are states; "
                        + ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "✓" + ChatColor.DARK_GRAY + "]"
                        + ChatColor.GRAY + " enabled, "
                        + ChatColor.DARK_GRAY + "[" + ChatColor.RED + "✗" + ChatColor.DARK_GRAY + "]"
                        + ChatColor.GRAY + " disabled");

                send(sender, bar());
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
