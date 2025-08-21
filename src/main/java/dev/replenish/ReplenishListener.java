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

import java.util.*;
import java.util.logging.Level;

public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;

    private static final Set<Material> SUPPORTED = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA
    );

    private static final BlockFace[] HORIZ = new BlockFace[]{
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public ReplenishListener(ReplenishPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.isEnabledGlobally()) return;

        Block block = e.getBlock();
        Player p = e.getPlayer();

        // Creative players shouldn't have inventory altered or drops spawned
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Material cropType = block.getType();
        if (!SUPPORTED.contains(cropType)) return;
        if (!plugin.isCropEnabled(cropType)) return;

        // Tool restriction (QoL)
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (plugin.isQolMode() && plugin.isRestrictToHoesAndAxes() && !isHoeOrAxe(tool)) return;

        // Capture age data before any mutations
        int originalAge = 0;
        int maxAge = 0;
        BlockData preData = block.getBlockData();
        if (preData instanceof Ageable a) {
            originalAge = a.getAge();
            maxAge = a.getMaximumAge();
        }
        final boolean wasMature = (maxAge > 0) && (originalAge >= maxAge);
        final int replantedAge = wasMature ? 0 : originalAge;

        // Preflight environment checks BEFORE consuming seeds
        if (cropType == Material.COCOA) {
            if (findAdjacentJungle(block) == null) return;
        } else if (cropType == Material.NETHER_WART) {
            if (block.getRelative(BlockFace.DOWN).getType() != Material.SOUL_SAND) return;
        } else {
            if (block.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;
        }

        // Seed logic: only require/consume seed when the crop was mature
        Material seedMat = seedFor(cropType);
        if (wasMature && plugin.isRequirePlayerSeed()) {
            if (seedMat == null) return;
            if (!playerHasSeed(p, seedMat)) return;
            if (!consumeOneFromPlayer(p, seedMat)) return;
        }

        // Use Bukkit's drop calc to respect Fortune/Silk Touch
        e.setDropItems(false);
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool, p));

        // Prevent dupes/exploits: suppress drops for immature crops unless explicitly allowed
        if (!wasMature && !plugin.isAllowImmatureDrops()) {
            drops.clear();
        }

        // Capture cocoa orientation hints BEFORE we set AIR
        final BlockFace originalCocoaFacing = (cropType == Material.COCOA && preData instanceof Directional d)
                ? d.getFacing()
                : null;
        final BlockFace playerFacingAtBreak = p.getFacing();

        // Clear the block and dispatch drops
        block.setType(Material.AIR, false);
        Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
        if (plugin.isDirectPickup()) {
            DropPickupManager.giveToPlayerOrDrop(p, dropLoc, drops);
        } else {
            dropAll(block, drops);
        }

        final Block bRef = block;
        final Material mRef = cropType;
        final int delay = plugin.getReplantDelayTicks();

        // Delayed replant to mimic vanilla timings & avoid physics race conditions
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!bRef.getType().isAir()) return;

                if (mRef == Material.COCOA) {
                    // Choose facing: original -> player -> any
                    BlockFace chosen = null;
                    if (originalCocoaFacing != null && isJungle(bRef.getRelative(originalCocoaFacing).getType())) {
                        chosen = originalCocoaFacing;
                    }
                    if (chosen == null && isJungle(bRef.getRelative(playerFacingAtBreak).getType())) {
                        chosen = playerFacingAtBreak;
                    }
                    if (chosen == null) {
                        chosen = findAdjacentJungle(bRef);
                    }
                    if (chosen == null) return;

                    bRef.setType(Material.COCOA, false);
                    BlockData data = bRef.getBlockData();
                    if (data instanceof Directional d) {
                        // If your server build flips this, change to chosen.getOppositeFace()
                        d.setFacing(chosen);
                    }
                    if (data instanceof Ageable a) {
                        a.setAge(Math.max(0, Math.min(replantedAge, a.getMaximumAge())));
                        bRef.setBlockData(a, false);
                    } else {
                        bRef.setBlockData(data, false);
                    }
                    return;
                }

                if (mRef == Material.NETHER_WART) {
                    if (bRef.getRelative(BlockFace.DOWN).getType() != Material.SOUL_SAND) return;
                    bRef.setType(Material.NETHER_WART, false);
                    BlockData data = bRef.getBlockData();
                    if (data instanceof Ageable a) {
                        a.setAge(Math.max(0, Math.min(replantedAge, a.getMaximumAge())));
                        bRef.setBlockData(a, false);
                    } else {
                        bRef.setBlockData(data, false);
                    }
                    return;
                }

                if (bRef.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;

                bRef.setType(mRef, false);
                BlockData data = bRef.getBlockData();
                if (data instanceof Ageable a) {
                    a.setAge(Math.max(0, Math.min(replantedAge, a.getMaximumAge())));
                    bRef.setBlockData(a, false);
                } else {
                    bRef.setBlockData(data, false);
                }

            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Replant failed", t);
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

    private boolean playerHasSeed(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack is : inv.getContents()) {
            if (is == null) continue;
            if (is.getType() == seedMat && is.getAmount() > 0) return true;
        }
        ItemStack off = inv.getItemInOffHand();
        return off.getType() == seedMat && off.getAmount() > 0;
    }

    private boolean consumeOneFromPlayer(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (is.getType() == seedMat && is.getAmount() > 0) {
                if (is.getAmount() > 1) {
                    is.setAmount(is.getAmount() - 1);
                    inv.setItem(i, is);
                } else {
                    inv.setItem(i, new ItemStack(Material.AIR));
                }
                return true;
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == seedMat && off.getAmount() > 0) {
            if (off.getAmount() > 1) {
                off.setAmount(off.getAmount() - 1);
                inv.setItemInOffHand(off);
            } else {
                inv.setItemInOffHand(new ItemStack(Material.AIR));
            }
            return true;
        }
        return false;
    }

    private boolean isHoeOrAxe(ItemStack tool) {
        if (tool == null) return false;
        Material mt = tool.getType();
        String n = mt.name();
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
