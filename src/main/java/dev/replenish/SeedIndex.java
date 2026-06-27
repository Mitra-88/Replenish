package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class SeedIndex implements Listener {

    private static final int NO_SLOT = -1;
    private static final Map<Player, Map<Material, Integer>> cacheByPlayer = new HashMap<>();

    public static void init(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new SeedIndex(), plugin);
    }

    public static void invalidate(Player player) {
        cacheByPlayer.remove(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    public static boolean consume(Player player, Material seedMaterial) {
        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> playerCache = cacheByPlayer.get(player);

        if (playerCache != null) {
            Integer slotIndex = playerCache.get(seedMaterial);
            if (slotIndex != null) {
                if (slotIndex == NO_SLOT) return false;
                if (tryConsume(inventory, playerCache, seedMaterial, slotIndex)) {
                    return true;
                }
            }
        }

        playerCache = buildIndex(inventory);
        cacheByPlayer.put(player, playerCache);

        Integer slotIndex = playerCache.get(seedMaterial);
        if (slotIndex != null && slotIndex != NO_SLOT) {
            return tryConsume(inventory, playerCache, seedMaterial, slotIndex);
        }

        playerCache.put(seedMaterial, NO_SLOT);
        return false;
    }

    private static boolean tryConsume(PlayerInventory inventory, Map<Material, Integer> cache, Material material, int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() != material || stack.getAmount() <= 0) {
            cache.remove(material);
            return false;
        }

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(slot, stack);
        } else {
            inventory.clear(slot);
            cache.put(material, findNextSlot(inventory, material));
        }
        return true;
    }

    private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
        Map<Material, Integer> index = new HashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            index.putIfAbsent(stack.getType(), i);
        }
        return index;
    }

    private static int findNextSlot(PlayerInventory inventory, Material material) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material && stack.getAmount() > 0) {
                return i;
            }
        }
        return NO_SLOT;
    }
}
