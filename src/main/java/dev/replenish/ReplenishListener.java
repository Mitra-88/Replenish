package dev.replenish;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;
    private final ReplantQueue queue;
    private final AgeMetaRegistry ages;

    private static final Set<Material> SUPPORTED = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
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

    public ReplenishListener(ReplenishPlugin plugin, ReplantQueue queue, AgeMetaRegistry ages) {
        this.plugin = plugin;
        this.queue = queue;
        this.ages = ages;
    }

    @EventHandler public void onClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p) SeedIndex.invalidate(p); }
    @EventHandler public void onDrag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p) SeedIndex.invalidate(p); }
    @EventHandler public void onOpen(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p) SeedIndex.invalidate(p); }
    @EventHandler public void onClose(InventoryCloseEvent e) { if (e.getPlayer() instanceof Player p) SeedIndex.invalidate(p); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { SeedIndex.invalidate(e.getPlayer()); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p) SeedIndex.invalidate(p); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { SeedIndex.invalidate(e.getPlayer()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;

        ReplenishPlugin.ConfigCache cfg = plugin.cfg();
        if (!cfg.enabled) return;

        Block block = e.getBlock();
        Material cropType = block.getType();
        if (!SUPPORTED.contains(cropType) || !plugin.isCropEnabled(cropType)) return;

        if (cfg.restrictToHoesAndAxes) {
            Material toolType = p.getInventory().getItemInMainHand().getType();
            if (!ALLOWED_TOOLS.contains(toolType)) return;
        }

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

        BlockData preData = block.getBlockData();
        int originalAge = 0;
        boolean wasMature = false;
        if (preData instanceof Ageable a) {
            int maxAge = ages.get(cropType).maxAge;
            originalAge = a.getAge();
            wasMature = maxAge > 0 && originalAge >= maxAge;
        }
        int replantedAge = wasMature ? 0 : originalAge;

        Material seedMat = seedFor(cropType);
        if (wasMature && cfg.requirePlayerSeed) {
            if (seedMat == null) return;
            if (!SeedIndex.consume(p, seedMat)) return;
        }

        e.setDropItems(false);
        var tool = p.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = wasMature ? block.getDrops(tool, p) : Collections.emptyList();

        BlockFace originalCocoaFacing = (cropType == Material.COCOA && preData instanceof Directional d) ? d.getFacing() : null;
        BlockFace playerFacing = p.getFacing();

        block.setType(Material.AIR, false);

        if (!drops.isEmpty()) {
            Location dropLoc = DropPickupManager.centeredDropLocation(block.getLocation());
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
        double xp = switch (cropType) {
            case WHEAT -> 3.0;
            case CARROTS, POTATOES -> 3.5;
            case NETHER_WART -> 3.7;
            case COCOA -> 4.0;
            default -> 0;
        };
        if (xp > 0) user.addSkillXp(Skills.FARMING, xp);
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
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }
}
