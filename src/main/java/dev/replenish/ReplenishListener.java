package dev.replenish;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.user.SkillsUser;
import dev.aurelium.auraskills.api.skill.Skills;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;
    private final ReplantQueue queue;

    private static final Set<Material> SUPPORTED = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA
    );

    private static final BlockFace[] HORIZ = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private static final EnumSet<Material> ALLOWED_TOOLS = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private static final EnumSet<Material> JUNGLE_ANCHORS = EnumSet.of(
            Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD
    );

    public ReplenishListener(ReplenishPlugin plugin, ReplantQueue queue) {
        this.plugin = plugin;
        this.queue = queue;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        ReplenishPlugin.ConfigCache cfg = plugin.cfg();
        if (!cfg.enabled) return;

        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Block block = e.getBlock();
        Material cropType = block.getType();
        if (!SUPPORTED.contains(cropType) || !plugin.isCropEnabled(cropType)) return;

        if (cfg.qolMode && cfg.restrictToHoesAndAxes) {
            Material toolType = p.getInventory().getItemInMainHand().getType();
            if (!ALLOWED_TOOLS.contains(toolType)) return;
        }

        BlockData preData = block.getBlockData();
        int originalAge = 0, maxAge = 0;
        if (preData instanceof Ageable a) { originalAge = a.getAge(); maxAge = a.getMaximumAge(); }
        boolean wasMature = maxAge > 0 && originalAge >= maxAge;
        int replantedAge = wasMature ? 0 : originalAge;

        if (cropType == Material.COCOA) {
            if (findAdjacentJungle(block) == null) return;
        } else {
            Material underType = block.getRelative(BlockFace.DOWN).getType();
            if (cropType == Material.NETHER_WART) {
                if (underType != Material.SOUL_SAND) return;
            } else if (underType != Material.FARMLAND) {
                return;
            }
        }

        Material seedMat = seedFor(cropType);
        if (wasMature && cfg.requirePlayerSeed) {
            if (seedMat == null) return;
            if (!consumeOneSeedIfAvailable(p, seedMat)) return;
        }

        e.setDropItems(false);
        ItemStack tool = p.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = (wasMature || cfg.allowImmatureDrops)
                ? block.getDrops(tool, p)
                : Collections.emptyList();

        BlockFace originalCocoaFacing = (cropType == Material.COCOA && preData instanceof Directional d)
                ? d.getFacing() : null;
        BlockFace playerFacing = p.getFacing();

        block.setType(Material.AIR, false);
        if (!drops.isEmpty()) {
            Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
            if (cfg.directPickup) {
                DropPickupManager.giveToPlayerOrDrop(p, dropLoc, drops);
            } else {
                World w = block.getWorld();
                for (ItemStack d : drops) {
                    if (d == null || d.getAmount() <= 0) continue;
                    w.dropItemNaturally(dropLoc, d);
                }
            }
        }

        int delay = Math.max(1, cfg.replantDelayTicks);
        if (cropType == Material.COCOA) {
            BlockFace chosen;
            if (originalCocoaFacing != null && isJungle(block.getRelative(originalCocoaFacing).getType())) {
                chosen = originalCocoaFacing;
            } else if (isJungle(block.getRelative(playerFacing).getType())) {
                chosen = playerFacing;
            } else {
                chosen = findAdjacentJungle(block);
            }
            if (chosen != null) {
                queue.enqueue(block, Material.COCOA, delay, replantedAge, chosen);
            }
        } else {
            queue.enqueue(block, cropType, delay, replantedAge, null);
        }

        if (wasMature) {
            giveFarmingXp(p, cropType);
        }
    }

    private void giveFarmingXp(Player p, Material cropType) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) return;
        SkillsUser user = AuraSkillsApi.get().getUser(p.getUniqueId());
        if (user == null || !user.isLoaded()) return;
        double xp;
        switch (cropType) {
            case WHEAT -> xp = 1.0;
            case CARROTS, POTATOES -> xp = 0.75;
            case BEETROOTS -> xp = 0.5;
            case NETHER_WART -> xp = 1.2;
            case COCOA -> xp = 0.6;
            default -> xp = 0;
        }
        if (xp > 0) {
            user.addSkillXp(Skills.FARMING, xp);
        }
    }

    private BlockFace findAdjacentJungle(Block b) {
        for (BlockFace face : HORIZ) {
            if (isJungle(b.getRelative(face).getType())) return face;
        }
        return null;
    }

    private boolean isJungle(Material m) { return JUNGLE_ANCHORS.contains(m); }

    private static Material seedFor(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    private boolean consumeOneSeedIfAvailable(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0, n = inv.getSize(); i < n; i++) {
            ItemStack is = inv.getItem(i);
            if (is == null || is.getType() != seedMat || is.getAmount() <= 0) continue;
            int amt = is.getAmount();
            if (amt > 1) { is.setAmount(amt - 1); inv.setItem(i, is); }
            else { inv.clear(i); }
            return true;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == seedMat && off.getAmount() > 0) {
            int amt = off.getAmount();
            if (amt > 1) { off.setAmount(amt - 1); inv.setItemInOffHand(off); }
            else { inv.setItemInOffHand(null); }
            return true;
        }
        return false;
    }
}
