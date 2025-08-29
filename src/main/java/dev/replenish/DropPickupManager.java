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
    private static final ThreadLocal<Location> TMP_LOC_1 = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));
    private static final ThreadLocal<Location> TMP_LOC_2 = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));

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
            Location soundAt = TMP_LOC_1.get();
            player.getLocation(soundAt);
            player.playSound(soundAt, Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.2f);
        }
    }

    public static Location centeredDropLocation(Location target) {
        Location out = TMP_LOC_2.get();
        out.setWorld(target.getWorld());
        out.setX(target.getBlockX() + 0.5);
        out.setY(target.getBlockY() + 0.2);
        out.setZ(target.getBlockZ() + 0.5);
        out.setYaw(0f);
        out.setPitch(0f);
        return out;
    }
}
