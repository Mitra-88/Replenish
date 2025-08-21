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

    private static final BlockFace[] HORIZ = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    // Faster than string endsWith checks
    private static final EnumSet<Material> ALLOWED_TOOLS = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    public ReplenishListener(ReplenishPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        final var cfg = plugin.cfg();
        if (!cfg.enabled) return;

        final Block block = e.getBlock();
        final Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.CREATIVE) return;

        final Material cropType = block.getType();
        if (!SUPPORTED.contains(cropType)) return;
        if (!plugin.isCropEnabled(cropType)) return;

        final ItemStack tool = p.getInventory().getItemInMainHand();
        final boolean restrictTools = cfg.qolMode && cfg.restrictToHoesAndAxes;
        if (restrictTools && !ALLOWED_TOOLS.contains(tool.getType())) return;

        // Capture pre-break data once
        final BlockData preData = block.getBlockData();
        int originalAge = 0, maxAge = 0;
        if (preData instanceof Ageable a) {
            originalAge = a.getAge();
            maxAge = a.getMaximumAge();
        }
        final boolean wasMature = (maxAge > 0) && (originalAge >= maxAge);
        final int replantedAge = wasMature ? 0 : originalAge;

        // Preflight checks BEFORE seed consumption
        if (cropType == Material.COCOA) {
            if (findAdjacentJungle(block) == null) return;
        } else {
            Block under = block.getRelative(BlockFace.DOWN);
            if (cropType == Material.NETHER_WART) {
                if (under.getType() != Material.SOUL_SAND) return;
            } else if (under.getType() != Material.FARMLAND) {
                return;
            }
        }

        // Seed logic: only consume when mature
        final Material seedMat = seedFor(cropType);
        if (wasMature && cfg.requirePlayerSeed) {
            if (seedMat == null) return;
            if (!playerHasSeed(p, seedMat)) return;
            if (!consumeOneFromPlayer(p, seedMat)) return;
        }

        // Drops: respect Fortune/SilkTouch; skip calc for immature if disabled
        e.setDropItems(false);
        final boolean allowImmatureDrops = cfg.allowImmatureDrops;
        final Collection<ItemStack> drops;
        if (wasMature || allowImmatureDrops) {
            drops = block.getDrops(tool, p); // no copy; collection view is fine
        } else {
            drops = List.of(); // zero-alloc empty
        }

        // Cocoa facing hints
        final BlockFace originalCocoaFacing = (cropType == Material.COCOA && preData instanceof Directional d)
                ? d.getFacing() : null;
        final BlockFace playerFacingAtBreak = p.getFacing();

        // Set to air and dispatch drops
        block.setType(Material.AIR, false);
        final Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
        if (cfg.directPickup) {
            DropPickupManager.giveToPlayerOrDrop(p, dropLoc, drops);
        } else {
            dropAll(block, drops);
        }

        final int delay = Math.max(1, cfg.replantDelayTicks);
        final int targetAge = replantedAge;

        // Sync delayed replant (world ops must be on main thread)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!block.getType().isAir()) return;

                if (cropType == Material.COCOA) {
                    BlockFace chosen = null;
                    if (originalCocoaFacing != null && isJungle(block.getRelative(originalCocoaFacing).getType())) {
                        chosen = originalCocoaFacing;
                    }
                    if (chosen == null && isJungle(block.getRelative(playerFacingAtBreak).getType())) {
                        chosen = playerFacingAtBreak;
                    }
                    if (chosen == null) chosen = findAdjacentJungle(block);
                    if (chosen == null) return;

                    block.setType(Material.COCOA, false);
                    BlockData data = block.getBlockData();
                    if (data instanceof Directional d) d.setFacing(chosen); // flip to opposite if your server needs it
                    if (data instanceof Ageable a) {
                        a.setAge(Math.max(0, Math.min(targetAge, a.getMaximumAge())));
                        block.setBlockData(a, false);
                    } else {
                        block.setBlockData(data, false);
                    }
                    return;
                }

                Block under = block.getRelative(BlockFace.DOWN);
                if (cropType == Material.NETHER_WART) {
                    if (under.getType() != Material.SOUL_SAND) return;
                    block.setType(Material.NETHER_WART, false);
                } else {
                    if (under.getType() != Material.FARMLAND) return;
                    block.setType(cropType, false);
                }

                BlockData data = block.getBlockData();
                if (data instanceof Ageable a) {
                    a.setAge(Math.max(0, Math.min(targetAge, a.getMaximumAge())));
                    block.setBlockData(a, false);
                } else {
                    block.setBlockData(data, false);
                }

            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Replant failed", t);
            }
        }, delay);
    }

    private BlockFace findAdjacentJungle(Block b) {
        for (BlockFace face : HORIZ) {
            if (isJungle(b.getRelative(face).getType())) return face;
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
                    inv.clear(i); // faster than setItem(AIR)
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
                inv.setItemInOffHand(null); // null == AIR; avoids new ItemStack
            }
            return true;
        }
        return false;
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

    private void dropAll(Block block, Collection<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) return;
        Location dropLoc = block.getLocation().add(0.5, 0.2, 0.5);
        for (ItemStack d : drops) {
            if (d == null || d.getAmount() <= 0) continue;
            block.getWorld().dropItemNaturally(dropLoc, d);
        }
    }
}
