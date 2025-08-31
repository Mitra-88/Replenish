package dev.replenish;

import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;

public final class AgeMetaRegistry {

    public static final class AgeMeta {
        public final boolean isAgeable;
        public final int maximumAge;

        private AgeMeta(boolean isAgeable, int maximumAge) {
            this.isAgeable = isAgeable;
            this.maximumAge = maximumAge;
        }
    }

    private static final AgeMeta DEFAULT = new AgeMeta(false, 0);

    private final Map<Material, AgeMeta> metadataByMaterial = new EnumMap<>(Material.class);

    public AgeMetaRegistry(Plugin plugin) {
        for (Material material : Material.values()) {
            if (!material.isBlock()) continue;
            try {
                BlockData blockData = plugin.getServer().createBlockData(material);
                if (blockData instanceof Ageable ageable) {
                    metadataByMaterial.put(material, new AgeMeta(true, ageable.getMaximumAge()));
                } else {
                    metadataByMaterial.put(material, DEFAULT);
                }
            } catch (Throwable ignored) {
                plugin.getLogger().fine("Age meta scan skipped for " + material + ": " + ignored.getMessage());
            }
        }
    }

    public AgeMeta get(Material material) {
        return metadataByMaterial.getOrDefault(material, DEFAULT);
    }
}
