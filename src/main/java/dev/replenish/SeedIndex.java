package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SeedIndex implements Listener {

    private static final int NO_SLOT = -1;
    private static final int STORAGE_SIZE = 36;
    private static final Map<Player, Map<Material, Integer>> cacheByPlayer = new WeakHashMap<>();
    private static SeedIndex listenerInstance;

    private SeedIndex() {
    }

    public static synchronized void init(JavaPlugin plugin) {
        if (listenerInstance == null) {
            listenerInstance = new SeedIndex();
            plugin.getServer().getPluginManager().registerEvents(listenerInstance, plugin);
        }
    }

    public static void invalidate(Player player) {
        cacheByPlayer.remove(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            invalidate((Player) event.getWhoClicked());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            invalidate((Player) event.getWhoClicked());
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            invalidate((Player) event.getEntity());
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        invalidate(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        invalidate(event.getPlayer());
    }

    public static boolean consume(Player player, Material seedMaterial) {
        if (player == null || seedMaterial == null || !seedMaterial.isItem()) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> playerCache = cacheByPlayer.get(player);

        if (playerCache != null) {
            Integer cachedSlot = playerCache.get(seedMaterial);
            if (cachedSlot != null) {
                if (cachedSlot == NO_SLOT) return false;
                if (tryConsume(inventory, playerCache, seedMaterial, cachedSlot)) {
                    return true;
                }
            }
        }

        playerCache = buildIndex(inventory);
        cacheByPlayer.put(player, playerCache);

        Integer slotIndex = playerCache.get(seedMaterial);
        if (slotIndex != null) {
            return tryConsume(inventory, playerCache, seedMaterial, slotIndex);
        }

        playerCache.put(seedMaterial, NO_SLOT);
        return false;
    }

    private static boolean tryConsume(PlayerInventory inventory, Map<Material, Integer> cache,
                                      Material material, int slot) {
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
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            index.putIfAbsent(stack.getType(), i);
        }
        return index;
    }

    private static int findNextSlot(PlayerInventory inventory, Material material) {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material && stack.getAmount() > 0) {
                return i;
            }
        }
        return NO_SLOT;
    }
}
