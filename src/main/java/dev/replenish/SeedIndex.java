package dev.replenish;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeedIndex {

  private static final int NO_SLOT = -1;
  private static final int STORAGE_SIZE = 36;
  // REMINDER: DON'T USE WeakHashMap BRO!
  private static final Map<UUID, Map<Material, Integer>> cacheByPlayer = new HashMap<>();

  private SeedIndex() {}

  public static synchronized void init(JavaPlugin plugin) {}

  public static void invalidate(UUID uuid) {
    cacheByPlayer.remove(uuid);
  }

  public static void invalidate(Player player) {
    if (player != null) invalidate(player.getUniqueId());
  }

  public static boolean consume(Player player, Material seedMaterial) {
    if (player == null || seedMaterial == null || !seedMaterial.isItem()) return false;

    PlayerInventory inventory = player.getInventory();
    UUID uuid = player.getUniqueId();
    Map<Material, Integer> playerCache = cacheByPlayer.get(uuid);

    if (playerCache != null) {
      Integer cachedSlot = playerCache.get(seedMaterial);
      if (cachedSlot != null) {
        if (cachedSlot == NO_SLOT) return false;
        if (tryConsume(inventory, playerCache, seedMaterial, cachedSlot)) return true;
      }
    }

    playerCache = buildIndex(inventory);
    cacheByPlayer.put(uuid, playerCache);

    Integer slotIndex = playerCache.get(seedMaterial);
    if (slotIndex != null) return tryConsume(inventory, playerCache, seedMaterial, slotIndex);

    playerCache.put(seedMaterial, NO_SLOT);
    return false;
  }

  private static boolean tryConsume(
      PlayerInventory inventory, Map<Material, Integer> cache, Material material, int slot) {
    ItemStack stack = (slot == 40) ? inventory.getItemInOffHand() : inventory.getItem(slot);

    if (stack == null || stack.getType() != material || stack.getAmount() <= 0) {
      cache.remove(material);
      return false;
    }

    if (stack.getAmount() > 1) {
      stack.setAmount(stack.getAmount() - 1);
      if (slot == 40) inventory.setItemInOffHand(stack);
      else inventory.setItem(slot, stack);
    } else {
      if (slot == 40) inventory.setItemInOffHand(null);
      else inventory.clear(slot);
      cache.put(material, findNextSlot(inventory, material));
    }
    return true;
  }

  private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
    Map<Material, Integer> index = new HashMap<>();
    for (int i = 0; i < STORAGE_SIZE; i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
        index.putIfAbsent(stack.getType(), i);
      }
    }
    ItemStack offhand = inventory.getItemInOffHand();
    if (!offhand.getType().isAir() && offhand.getAmount() > 0) {
      index.putIfAbsent(offhand.getType(), 40);
    }
    return index;
  }

  private static int findNextSlot(PlayerInventory inventory, Material material) {
    for (int i = 0; i < STORAGE_SIZE; i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack != null && stack.getType() == material && stack.getAmount() > 0) return i;
    }
    ItemStack offhand = inventory.getItemInOffHand();
    if (offhand.getType() == material && offhand.getAmount() > 0) return 40;
    return NO_SLOT;
  }
}
