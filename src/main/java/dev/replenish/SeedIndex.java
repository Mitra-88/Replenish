package dev.replenish;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Per-player O(1) seed slot cache with lazy refresh + invalidation hooks. */
public final class SeedIndex {
    private static final WeakHashMap<Player, Map<Material, Integer>> INDEX = new WeakHashMap<>();

    public static void invalidate(Player p) {
        INDEX.remove(p);
    }

    public static boolean consume(Player p, Material seedMat) {
        PlayerInventory inv = p.getInventory();
        Map<Material, Integer> map = INDEX.computeIfAbsent(p, k -> buildMap(inv));
        Integer slot = map.get(seedMat);

        // Fast path
        if (slot != null && slot >= 0) {
            if (consumeAt(inv, slot, seedMat)) {
                // still same slot unless stack emptied
                ItemStack is = inv.getItem(slot);
                if (is == null || is.getType() != seedMat || is.getAmount() <= 0) {
                    map.put(seedMat, findSlot(inv, seedMat)); // re-point
                }
                return true;
            }
            // miss — rebuild fully, then try again
            map.clear();
            map.putAll(buildMap(inv));
            slot = map.get(seedMat);
            if (slot != null && slot >= 0) return consumeAt(inv, slot, seedMat);
        }

        // Offhand special case
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == seedMat && off.getAmount() > 0) {
            int amt = off.getAmount();
            if (amt > 1) { off.setAmount(amt - 1); inv.setItemInOffHand(off); }
            else { inv.setItemInOffHand(null); }
            // update index after consumption
            map.put(seedMat, findSlot(inv, seedMat));
            return true;
        }

        return false;
    }

    private static Map<Material, Integer> buildMap(PlayerInventory inv) {
        Map<Material, Integer> m = new EnumMap<>(Material.class);
        for (int i = 0, n = inv.getSize(); i < n; i++) {
            ItemStack is = inv.getItem(i);
            if (is == null || is.getAmount() <= 0) continue;
            m.putIfAbsent(is.getType(), i);
        }
        return m;
    }

    private static int findSlot(PlayerInventory inv, Material mat) {
        for (int i = 0, n = inv.getSize(); i < n; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat && is.getAmount() > 0) return i;
        }
        return -1;
    }

    private static boolean consumeAt(PlayerInventory inv, int slot, Material mat) {
        if (slot < 0 || slot >= inv.getSize()) return false;
        ItemStack is = inv.getItem(slot);
        if (is == null || is.getType() != mat || is.getAmount() <= 0) return false;
        int amt = is.getAmount();
        if (amt > 1) { is.setAmount(amt - 1); inv.setItem(slot, is); }
        else { inv.clear(slot); }
        return true;
    }
}
