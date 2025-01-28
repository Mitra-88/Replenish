package me.vermeil.replenish;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;

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

        BlockFace facing = null;
        if (crop == Crop.COCOA && block.getBlockData() instanceof Directional) {
            Directional directional = (Directional) block.getBlockData();
            facing = directional.getFacing();
        }

        Player player = event.getPlayer();
        Location location = block.getLocation();
        Material seedType = crop.getSeedType();

        new ReplantTask(new ReplantData(
            player,
            location,
            crop,
            seedType,
            facing
        )).runTaskLater(this, 2L);
    }

    private boolean isFullyGrown(Block block, Crop crop) {
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        return false;
    }

    private class ReplantTask extends BukkitRunnable {
        private final ReplantData data;

        public ReplantTask(ReplantData data) {
            this.data = data;
        }

        @Override
        public void run() {
            Block block = data.location.getBlock();
            if (block.getType() != Material.AIR) return;

            if (!data.player.getInventory().containsAtLeast(new ItemStack(data.seedType), 1)) return;

            data.player.getInventory().removeItem(new ItemStack(data.seedType, 1));
            block.setType(data.crop.getCropType());

            if (data.crop == Crop.COCOA) {
                handleCocoaReplant(block, data.facing);
            } else {
                Ageable ageable = (Ageable) block.getBlockData();
                ageable.setAge(0);
                block.setBlockData(ageable);
            }
        }

        private void handleCocoaReplant(Block block, BlockFace originalFacing) {
            Directional cocoaData = (Directional) block.getBlockData();

            Block attachedTo = block.getRelative(originalFacing.getOppositeFace());
            if (attachedTo.getType() != Material.JUNGLE_LOG) return;

            cocoaData.setFacing(originalFacing);
            block.setBlockData(cocoaData);
        }
    }

    private record ReplantData(
        Player player,
        Location location,
        Crop crop,
        Material seedType,
        BlockFace facing
    ) {}

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
