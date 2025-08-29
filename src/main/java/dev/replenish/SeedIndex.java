package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SeedIndex {

    private static final WeakHashMap<Player, Map<Material, Integer>> cacheByPlayer = new WeakHashMap<>();

    public static void invalidate(Player player) {
        cacheByPlayer.remove(player);
    }

    public static boolean consume(Player player, Material seedMaterial) {
        PlayerInventory inventory = player.getInventory();
        Map<Material, Integer> firstSlotByMaterial = cacheByPlayer.computeIfAbsent(player, p -> buildIndex(inventory));
        Integer slotIndex = firstSlotByMaterial.get(seedMaterial);

        if (slotIndex != null && slotIndex >= 0) {
            if (consumeAt(inventory, slotIndex, seedMaterial)) {
                ItemStack postConsume = inventory.getItem(slotIndex);
                if (postConsume == null || postConsume.getType() != seedMaterial || postConsume.getAmount() <= 0) {
                    firstSlotByMaterial.put(seedMaterial, findNextSlot(inventory, seedMaterial));
                }
                return true;
            }
            firstSlotByMaterial.clear();
            firstSlotByMaterial.putAll(buildIndex(inventory));
            slotIndex = firstSlotByMaterial.get(seedMaterial);
            if (slotIndex != null && slotIndex >= 0) return consumeAt(inventory, slotIndex, seedMaterial);
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand.getType() == seedMaterial && offHand.getAmount() > 0) {
            int amount = offHand.getAmount();
            if (amount > 1) {
                offHand.setAmount(amount - 1);
                inventory.setItemInOffHand(offHand);
            } else {
                inventory.setItemInOffHand(null);
            }
            firstSlotByMaterial.put(seedMaterial, findNextSlot(inventory, seedMaterial));
            return true;
        }

        return false;
    }

    private static Map<Material, Integer> buildIndex(PlayerInventory inventory) {
        Map<Material, Integer> index = new EnumMap<>(Material.class);
        for (int i = 0, size = inventory.getSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getAmount() <= 0) continue;
            index.putIfAbsent(stack.getType(), i);
        }
        return index;
    }

    private static int findNextSlot(PlayerInventory inventory, Material material) {
        for (int i = 0, size = inventory.getSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == material && stack.getAmount() > 0) return i;
        }
        return -1;
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
