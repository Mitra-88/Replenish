package dev.replenish;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

public final class DropPickupManager {

    private DropPickupManager() {}

    /**
     * Insert all drops into player's inventory; leftovers are dropped at dropLoc.
     * Plays a quiet pickup sound if anything was added.
     */
    public static void giveToPlayerOrDrop(Player player, Location dropLoc, List<ItemStack> drops) {
        if (player == null || dropLoc == null || drops == null || drops.isEmpty()) return;

        PlayerInventory inv = player.getInventory();
        World world = dropLoc.getWorld();
        boolean anyAdded = false;

        for (ItemStack stack : drops) {
            if (stack == null || stack.getAmount() <= 0) continue;
            Map<Integer, ItemStack> leftovers = inv.addItem(stack);
            if (leftovers.isEmpty()) {
                anyAdded = true;
                continue;
            }
            if (world != null) {
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover == null || leftover.getAmount() <= 0) continue;
                    world.dropItemNaturally(dropLoc, leftover);
                }
            }
        }

        if (anyAdded && world != null) {
            world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.2f);
        }
    }
}
