package dev.replenish;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collection;
import java.util.Map;

public final class DropPickupManager {
    private DropPickupManager() {}

    public static void giveToPlayerOrDrop(Player player, Location dropLocation, Collection<ItemStack> drops) {
        if (player == null || dropLocation == null || drops == null || drops.isEmpty()) return;

        PlayerInventory inventory = player.getInventory();
        World world = dropLocation.getWorld();
        boolean anyAdded = false;

        for (ItemStack stack : drops) {
            if (stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) continue;

            Map<Integer, ItemStack> leftovers = inventory.addItem(stack);

            if (leftovers.isEmpty()) {
                anyAdded = true;
            } else if (world != null) {
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover == null || leftover.getAmount() <= 0) continue;
                    if (leftover.getAmount() < stack.getAmount()) {
                        anyAdded = true;
                    }
                    world.dropItemNaturally(dropLocation, leftover);
                }
            }
        }
        if (anyAdded) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.2f);
        }
    }

    public static Location centeredDropLocation(Location target) {
        if (target == null) return null;
        World world = target.getWorld();
        Location out = new Location(
                world,
                target.getBlockX() + 0.5,
                target.getBlockY() + 0.5,
                target.getBlockZ() + 0.5
        );
        out.setYaw(0f);
        out.setPitch(0f);
        return out;
    }
}
