package me.vermeil.replenish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Replenish extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onCropBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material cropType = block.getType();
        Crop crop = Crop.fromMaterial(cropType);
        if (crop == null) return;

        if (!isFullyGrown(block, crop)) return;

        Player player = event.getPlayer();
        Location location = block.getLocation();
        Material seedType = crop.getSeedType();

        new BukkitRunnable() {
            @Override
            public void run() {
                replantCrop(player, location, crop, seedType);
            }
        }.runTaskLater(this, 2L);
    }

    private boolean isFullyGrown(Block block, Crop crop) {
        if (block.getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) block.getBlockData();
            return ageable.getAge() == ageable.getMaximumAge();
        } else if (crop == Crop.COCOA) {
            Cocoa cocoa = (Cocoa) block.getBlockData();
            return cocoa.getAge() == cocoa.getMaximumAge();
        }
        return false;
    }

    private void replantCrop(Player player, Location loc, Crop crop, Material seedType) {
        Block block = loc.getBlock();
        if (block.getType() != Material.AIR) return;

        if (!player.getInventory().containsAtLeast(new ItemStack(seedType), 1)) return;

        player.getInventory().removeItem(new ItemStack(seedType, 1));

        block.setType(crop.getCropType());
        if (crop == Crop.COCOA) {
            BlockFace facing = getOriginalFacing(loc);
            Block stem = block.getRelative(facing.getOppositeFace());
            if (stem.getType() != Material.JUNGLE_LOG) return;

            Directional cocoaData = (Directional) block.getBlockData();
            cocoaData.setFacing(facing);
            block.setBlockData(cocoaData);
        } else {
            Ageable ageable = (Ageable) block.getBlockData();
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    private BlockFace getOriginalFacing(Location loc) {
        return BlockFace.NORTH;
    }

    private enum Crop {
        WHEAT(Material.WHEAT, Material.WHEAT_SEEDS),
        CARROTS(Material.CARROTS, Material.CARROT),
        POTATOES(Material.POTATOES, Material.POTATO),
        NETHER_WART(Material.NETHER_WART, Material.NETHER_WART),
        COCOA(Material.COCOA, Material.COCOA_BEANS);

        private final Material cropType;
        private final Material seedType;

        Crop(Material cropType, Material seedType) {
            this.cropType = cropType;
            this.seedType = seedType;
        }

        public Material getCropType() {
            return cropType;
        }

        public Material getSeedType() {
            return seedType;
        }

        public static Crop fromMaterial(Material material) {
            for (Crop crop : values()) {
                if (crop.cropType == material) {
                    return crop;
                }
            }
            return null;
        }
    }
}
