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

    public static void giveToPlayerOrDrop(Player player, Location dropLoc, Collection<ItemStack> drops) {
        if (player == null || drops == null || drops.isEmpty()) return;

        final PlayerInventory inv = player.getInventory();
        final World world = dropLoc.getWorld();
        boolean anyAdded = false;

        for (ItemStack stack : drops) {
            if (stack == null || stack.getAmount() <= 0) continue;
            final Map<Integer, ItemStack> leftovers = inv.addItem(stack);
            if (leftovers.isEmpty()) {
                anyAdded = true;
            } else if (world != null) {
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover == null || leftover.getAmount() <= 0) continue;
                    world.dropItemNaturally(dropLoc, leftover);
                }
            }
        }

        if (anyAdded) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.2f);
        }
    }

    public static Location centeredDropLocation(Location target) {
        World w = target.getWorld();
        Location out = new Location(
                w,
                target.getBlockX() + 0.5,
                target.getBlockY() + 0.2,
                target.getBlockZ() + 0.5
        );
        out.setYaw(0f);
        out.setPitch(0f);
        return out;
    }
}
