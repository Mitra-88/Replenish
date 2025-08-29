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

    // ---------- helpers: styling ----------
    private static String line() {
        return ChatColor.DARK_GRAY + "-------------------------------------------";
    }

    private static String header(String subtitle) {
        return ChatColor.DARK_GRAY + "——— "
                + ChatColor.GOLD + ChatColor.BOLD + "Replenish Status"
                + ChatColor.DARK_GRAY + " · "
                + ChatColor.YELLOW + subtitle
                + ChatColor.DARK_GRAY + " ———";
    }

    private static String chipBool(boolean v) {
        return v
                ? ChatColor.BLACK + "[" + ChatColor.GREEN + "✓" + ChatColor.BLACK + "]"
                : ChatColor.BLACK + "[" + ChatColor.RED + "✗" + ChatColor.BLACK + "]";
    }

    private static String label(String name) {
        return ChatColor.GRAY + name + ChatColor.DARK_GRAY + ": ";
    }

    private static String onOff(boolean v) {
        return v ? (ChatColor.GREEN + "ON") : (ChatColor.RED + "OFF");
    }

    private static String numTint(int value) {
        // green is "good or better", yellow is "okay..", red otherwise
        ChatColor c = (value >= 32) ? ChatColor.GREEN : (value >= 8 ? ChatColor.YELLOW : ChatColor.RED);
        return label("Max replants/tick") + c + value;
    }

    private static String numTintLowIsGood(int value) {
        // green when <= goodMax, yellow when <= okayMax, else red
        ChatColor c = (value <= 2) ? ChatColor.GREEN : (value <= 10 ? ChatColor.YELLOW : ChatColor.RED);
        return label("Replant delay (ticks)") + c + value;
    }

    private static String crop(String emoji, String name, boolean enabled) {
        return (enabled ? ChatColor.GREEN : ChatColor.RED)
                + emoji + " "
                + (enabled ? "EN" : "OFF")
                + ChatColor.DARK_GRAY + " (" + ChatColor.GRAY + name + ChatColor.DARK_GRAY + ")";
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
                if (!sender.hasPermission("replenish.toggle")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                boolean now = !plugin.isEnabledGlobally();
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                plugin.reloadLocalConfig();
                sender.sendMessage(ChatColor.YELLOW + "[Replenish] Global enabled is now "
                        + (now ? ChatColor.GREEN + "true" : ChatColor.RED + "false"));
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
                String ver = plugin.getDescription().getVersion();

                // HEADER
                sender.sendMessage(line());
                sender.sendMessage(header("v" + ver));

                // GLOBAL
                sender.sendMessage(label("Enabled") + onOff(cfg.enabled) + ChatColor.DARK_GRAY + "  " + chipBool(cfg.enabled)
                        + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + "QoL " + chipBool(cfg.qolMode));
                sender.sendMessage(label("Require seed") + onOff(cfg.requirePlayerSeed) + ChatColor.DARK_GRAY + "  "
                        + ChatColor.GRAY + "Restrict tools " + chipBool(cfg.restrictToHoesAndAxes));
                sender.sendMessage(label("Direct pickup") + onOff(cfg.directPickup) + ChatColor.DARK_GRAY + "  "
                        + ChatColor.GRAY + "Immature drops " + chipBool(cfg.allowImmatureDrops));

                // TUNABLES
                sender.sendMessage(ChatColor.GOLD + "Tuning");
                sender.sendMessage("  " + numTintLowIsGood(cfg.replantDelayTicks)
                        + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + "→ lower is faster");
                sender.sendMessage("  " + numTint(cfg.maxReplantsPerTick)
                        + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + "→ higher = more throughput");

                // CROPS (emoji + chips)
                boolean w = plugin.isCropEnabled(Material.WHEAT);
                boolean c = plugin.isCropEnabled(Material.CARROTS);
                boolean p = plugin.isCropEnabled(Material.POTATOES);
                boolean n = plugin.isCropEnabled(Material.NETHER_WART);
                boolean co = plugin.isCropEnabled(Material.COCOA);

                sender.sendMessage(ChatColor.GOLD + "Crops");
                sender.sendMessage("  " + crop("🌾", "wheat", w) + ChatColor.GRAY + "  ·  "
                        + crop("🥕", "carrots", c) + ChatColor.GRAY + "  ·  "
                        + crop("🥔", "potatoes", p));
                sender.sendMessage("  " + crop("🔥", "nether_wart", n) + ChatColor.GRAY + "  ·  "
                        + crop("🍫", "cocoa", co));

                // LEGEND
                sender.sendMessage(ChatColor.DARK_GRAY + "—— "
                        + ChatColor.GRAY + "Legend: " + ChatColor.BLACK + "[" + ChatColor.GREEN + "✓" + ChatColor.BLACK + "]"
                        + ChatColor.GRAY + " enabled, " + ChatColor.BLACK + "[" + ChatColor.RED + "✗" + ChatColor.BLACK + "]"
                        + ChatColor.GRAY + " disabled");
                sender.sendMessage(line());
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
