package dev.replenish;

import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;

/** Caches ageability + max ages once at startup. */
public final class AgeMetaRegistry {
    public static final class AgeMeta {
        public final boolean ageable;
        public final int maxAge; // 0 if not ageable
        AgeMeta(boolean a, int m) { this.ageable = a; this.maxAge = m; }
    }

    private final Map<Material, AgeMeta> meta = new EnumMap<>(Material.class);

    public AgeMetaRegistry(Plugin plugin) {
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            try {
                BlockData bd = plugin.getServer().createBlockData(m);
                if (bd instanceof Ageable a) {
                    meta.put(m, new AgeMeta(true, a.getMaximumAge()));
                } else {
                    meta.put(m, new AgeMeta(false, 0));
                }
            } catch (Throwable ignored) {
                // not placeable or weird — skip
            }
        }
    }

    public AgeMeta get(Material m) {
        return meta.getOrDefault(m, new AgeMeta(false, 0));
    }
}
