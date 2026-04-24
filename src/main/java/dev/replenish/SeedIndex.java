package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedIndex implements Listener {

    private static final int NO_SLOT = -1;
    private static final Map<Player, Map<Material, Integer>> cacheByPlayer = new ConcurrentHashMap<>();

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
        Map<Material, Integer> playerCache = cacheByPlayer.computeIfAbsent(player, p -> buildIndex(inventory));

        Integer slotIndex = playerCache.get(seedMaterial);
        if (slotIndex != null && slotIndex >= 0) {
            ItemStack stack = inventory.getItem(slotIndex);
            if (stack == null || stack.getType() != seedMaterial || stack.getAmount() <= 0) {
                playerCache.remove(seedMaterial);
            } else {
                if (consumeAt(inventory, slotIndex, seedMaterial)) {
                    ItemStack postConsume = inventory.getItem(slotIndex);
                    if (postConsume == null || postConsume.getType() != seedMaterial || postConsume.getAmount() <= 0) {
                        playerCache.put(seedMaterial, findNextSlot(inventory, seedMaterial));
                    }
                    return true;
                }
            }
        }

        Map<Material, Integer> freshIndex = buildIndex(inventory);
        cacheByPlayer.put(player, freshIndex);
        playerCache = freshIndex;
        slotIndex = playerCache.get(seedMaterial);
        if (slotIndex != null && slotIndex >= 0 && consumeAt(inventory, slotIndex, seedMaterial)) {
            ItemStack postConsume = inventory.getItem(slotIndex);
            if (postConsume == null || postConsume.getType() != seedMaterial || postConsume.getAmount() <= 0) {
                playerCache.put(seedMaterial, findNextSlot(inventory, seedMaterial));
            }
            return true;
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() == seedMaterial && offHand.getAmount() > 0) {
            int amount = offHand.getAmount();
            if (amount > 1) {
                offHand.setAmount(amount - 1);
                inventory.setItemInOffHand(offHand);
            } else {
                inventory.setItemInOffHand(new ItemStack(Material.AIR));
            }
            playerCache.put(seedMaterial, NO_SLOT);
            return true;
        }

        return false;
    }

    private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
        Map<Material, Integer> index = new ConcurrentHashMap<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getAmount() <= 0) continue;
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

    private static boolean consumeAt(PlayerInventory inventory, int slotIndex, Material material) {
        if (slotIndex < 0 || slotIndex >= inventory.getSize()) return false;
        ItemStack stack = inventory.getItem(slotIndex);
        if (stack == null || stack.getType() != material || stack.getAmount() <= 0) return false;
        int amount = stack.getAmount();
        if (amount > 1) {
            stack.setAmount(amount - 1);
            inventory.setItem(slotIndex, stack);
        } else {
            inventory.clear(slotIndex);
        }
        return true;
    }
}
