package dev.replenish;

import org.bukkit.*;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

// NOT a record: this class contains behaviour (event handling, scheduling) and is registered as a listener.
// Records are for immutable data carriers; converting here would be semantically wrong.
public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;

    private static final Set<Material> SUPPORTED = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA
    );

    private static final BlockFace[] HORIZ = new BlockFace[]{
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public ReplenishListener(JavaPlugin plugin) {
        this.plugin = (ReplenishPlugin) plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.isEnabledGlobally()) return;

        Block block = e.getBlock();
        Player p = e.getPlayer();

        Material cropType = block.getType();
        if (!SUPPORTED.contains(cropType)) return;

        // NEW: single unified crop toggle check using Material-based method to avoid inverted calls
        if (!plugin.isCropEnabled(cropType)) return;

        // decide whether tool qualifies (QoL)
        ItemStack tool = p.getInventory().getItemInMainHand();

        if (plugin.isQolMode() && plugin.isRestrictToHoesAndAxes() && !isHoeOrAxe(tool)) return;

        // REQUIRE that the player has the proper "seed-equivalent" in their inventory, if configured
        Material seedMat = seedFor(cropType);

        if (plugin.isRequirePlayerSeed()) {
            if (seedMat != null) {
                if (!playerHasSeed(p, seedMat)) {
                    // player doesn't have seed -> do nothing (vanilla behaviour)
                    return;
                }
                // consume 1 seed from player inventory now
                boolean consumed = consumeOneFromPlayer(p, seedMat);
                if (!consumed) {
                    // failed to consume for some reason
                    return;
                }
            } else {
                // Crop has no seed mapping (shouldn't happen), abort to be safe
                return;
            }
        }

        // Compute drops using the exact held tool so Fortune works
        e.setDropItems(false);
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool, p));

        // Since we consumed seed from player's inventory already, we don't remove seed from drops
        Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
        block.setType(Material.AIR, false);
        for (ItemStack d : drops) {
            if (d == null || d.getAmount() <= 0) continue;
            block.getWorld().dropItemNaturally(dropLoc, d);
        }

        final Block bRef = block;
        final Material mRef = cropType;
        final int delay = plugin.getReplantDelayTicks();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!bRef.getType().isAir()) return;

                if (mRef == Material.COCOA) {
                    BlockFace attachFace = findAdjacentJungle(bRef);
                    if (attachFace == null) return;
                    bRef.setType(Material.COCOA, false);
                    BlockData data = bRef.getBlockData();
                    if (data instanceof Directional d) d.setFacing(attachFace);
                    if (data instanceof Ageable a) a.setAge(0);
                    bRef.setBlockData(data, false);
                    return;
                }

                if (mRef == Material.NETHER_WART) {
                    Block under = bRef.getRelative(BlockFace.DOWN);
                    if (under.getType() != Material.SOUL_SAND) return;
                    bRef.setType(Material.NETHER_WART, false);
                    BlockData data = bRef.getBlockData();
                    if (data instanceof Ageable a) { a.setAge(0); bRef.setBlockData(a, false); }
                    return;
                }

                Block under = bRef.getRelative(BlockFace.DOWN);
                if (under.getType() != Material.FARMLAND) return;

                bRef.setType(mRef, false);
                BlockData data = bRef.getBlockData();
                if (data instanceof Ageable a) { a.setAge(0); bRef.setBlockData(a, false); }

            } catch (Throwable ignored) {
            }
        }, delay);
    }

    private BlockFace findAdjacentJungle(Block b) {
        for (BlockFace face : HORIZ) {
            Block neighbor = b.getRelative(face);
            Material t = neighbor.getType();
            if (isJungle(t)) return face;
        }
        return null;
    }

    // check player has at least one seed-equivalent in inventory (including offhand and main inventory)
    private boolean playerHasSeed(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        // main inventory
        for (ItemStack is : inv.getContents()) {
            if (is == null) continue;
            if (is.getType() == seedMat && is.getAmount() > 0) return true;
        }
        ItemStack off = inv.getItemInOffHand();
        return off.getType() == seedMat && off.getAmount() > 0;
    }

    // consume one seed-equivalent from player's inventory (returns true if consumed)
    // IMPORTANT: we removed Player#updateInventory() (internal), and rely on setItem / setItemInOffHand.
    private boolean consumeOneFromPlayer(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        // iterate main contents
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (is.getType() == seedMat && is.getAmount() > 0) {
                if (is.getAmount() > 1) {
                    is.setAmount(is.getAmount() - 1);
                    inv.setItem(i, is);
                } else {
                    inv.setItem(i, null);
                }
                // DO NOT call p.updateInventory(); it's unstable/internal.
                return true;
            }
        }
        // check offhand
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == seedMat && off.getAmount() > 0) {
            if (off.getAmount() > 1) {
                off.setAmount(off.getAmount() - 1);
                inv.setItemInOffHand(off);
            } else {
                inv.setItemInOffHand(null);
            }
            // DO NOT call p.updateInventory(); it's unstable/internal.
            return true;
        }
        return false;
    }

    private boolean isHoeOrAxe(ItemStack tool) {
        String n = tool.getType().name();
        return n.endsWith("_HOE") || n.endsWith("_AXE");
    }

    private boolean isJungle(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.contains("JUNGLE_LOG") || n.contains("JUNGLE_WOOD") || n.contains("STRIPPED_JUNGLE");
    }

    private Material seedFor(Material crop) {
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

    private void dropAll(Block block, List<ItemStack> drops) {
        Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
        for (ItemStack d : drops) {
            if (d == null || d.getAmount() <= 0) continue;
            block.getWorld().dropItemNaturally(dropLoc, d);
        }
    }
}
