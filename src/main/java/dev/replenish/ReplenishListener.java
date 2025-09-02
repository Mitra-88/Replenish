package dev.replenish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class ReplenishListener implements Listener {

    private final ReplenishPlugin plugin;
    private final AgeMetaRegistry ageMetaRegistry;

    private static final Set<Material> SUPPORTED_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.NETHER_WART, Material.COCOA
    );

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private static final EnumSet<Material> HOE_TOOLS = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );
    private static final EnumSet<Material> AXE_TOOLS = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private static final EnumSet<Material> JUNGLE_ANCHOR_BLOCKS = EnumSet.of(
            Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD
    );

    public ReplenishListener(ReplenishPlugin plugin, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
    }

    @EventHandler public void onClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) SeedIndex.invalidate(player); }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) SeedIndex.invalidate(player); }
    @EventHandler public void onOpen(InventoryOpenEvent event) { if (event.getPlayer() instanceof Player player) SeedIndex.invalidate(player); }
    @EventHandler public void onClose(InventoryCloseEvent event) { if (event.getPlayer() instanceof Player player) SeedIndex.invalidate(player); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { SeedIndex.invalidate(event.getPlayer()); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) SeedIndex.invalidate(player); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { SeedIndex.invalidate(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ReplenishPlugin.ConfigCache config = plugin.getConfigCache();
        if (!config.enabled) return;

        Block block = event.getBlock();
        Material cropType = block.getType();
        if (!SUPPORTED_CROPS.contains(cropType) || !plugin.isCropEnabled(cropType)) return;

        Material toolInHandType = player.getInventory().getItemInMainHand().getType();
        boolean hasRequiredTool = switch (cropType) {
            case COCOA -> AXE_TOOLS.contains(toolInHandType);
            case WHEAT, CARROTS, POTATOES, NETHER_WART -> HOE_TOOLS.contains(toolInHandType);
            default -> true;
        };
        if (!hasRequiredTool) return;

        if (cropType == Material.COCOA) {
            if (findAdjacentJungle(block) == null) return;
        } else {
            Material belowType = block.getRelative(BlockFace.DOWN).getType();
            if (cropType == Material.NETHER_WART) {
                if (belowType != Material.SOUL_SAND) return;
            } else if (belowType != Material.FARMLAND) {
                return;
            }
        }

        BlockData originalBlockData = block.getBlockData();
        int originalAge = 0;
        boolean wasMature = false;
        if (originalBlockData instanceof Ageable ageable) {
            int maxAge = ageMetaRegistry.get(cropType).maximumAge;
            originalAge = ageable.getAge();
            wasMature = maxAge > 0 && originalAge >= maxAge;
        }
        int replantedAge = wasMature ? 0 : originalAge;

        Material seedMaterial = seedFor(cropType);
        if (wasMature && config.requirePlayerSeed) {
            if (seedMaterial == null) return;
            if (!SeedIndex.consume(player, seedMaterial)) return;
        }

        event.setDropItems(false);
        var toolInHand = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = wasMature ? block.getDrops(toolInHand, player) : Collections.emptyList();

        BlockFace originalCocoaFacing = (cropType == Material.COCOA && originalBlockData instanceof Directional directional) ? directional.getFacing() : null;
        BlockFace playerFacing = player.getFacing();

        block.setType(Material.AIR, false);

        if (!drops.isEmpty()) {
            Location dropLocation = DropPickupManager.centeredDropLocation(block.getLocation());
            if (config.directPickup) {
                DropPickupManager.giveToPlayerOrDrop(player, dropLocation, drops);
            } else {
                World world = block.getWorld();
                for (ItemStack drop : drops) {
                    if (drop == null || drop.getAmount() <= 0) continue;
                    world.dropItemNaturally(dropLocation, drop);
                }
            }
        }

        int delay = Math.max(1, config.replantDelayTicks);
        if (cropType == Material.COCOA) {
            BlockFace chosenFacing;
            if (originalCocoaFacing != null && isJungle(block.getRelative(originalCocoaFacing).getType())) {
                chosenFacing = originalCocoaFacing;
            } else if (isJungle(block.getRelative(playerFacing).getType())) {
                chosenFacing = playerFacing;
            } else {
                chosenFacing = findAdjacentJungle(block);
            }
            if (chosenFacing != null) {
                plugin.enqueueReplant(block, Material.COCOA, delay, replantedAge, chosenFacing);
            }
        } else {
            plugin.enqueueReplant(block, cropType, delay, replantedAge, null);
        }

        if (wasMature) {
            double xp = switch (cropType) {
                case WHEAT -> 3.0;
                case CARROTS, POTATOES -> 3.5;
                case NETHER_WART -> 3.7;
                case COCOA -> 4.0;
                default -> 0;
            };
            AuraSkillsCompat.grantFarmingXp(player, xp);
        }

        CubeBreaker.harvestAroundCenter(plugin, ageMetaRegistry, player, block, cropType);
    }

    private BlockFace findAdjacentJungle(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (isJungle(block.getRelative(face).getType())) return face;
        }
        return null;
    }

    private boolean isJungle(Material material) { return JUNGLE_ANCHOR_BLOCKS.contains(material); }

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
