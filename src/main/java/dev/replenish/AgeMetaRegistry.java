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

        AgeMeta(boolean isAgeable, int maximumAge) {
            this.isAgeable = isAgeable;
            this.maximumAge = maximumAge;
        }
    }

    private final Map<Material, AgeMeta> metadataByMaterial = new EnumMap<>(Material.class);

    public AgeMetaRegistry(Plugin plugin) {
        for (Material material : Material.values()) {
            if (!material.isBlock()) continue;
            try {
                BlockData blockData = plugin.getServer().createBlockData(material);
                if (blockData instanceof Ageable ageable) {
                    metadataByMaterial.put(material, new AgeMeta(true, ageable.getMaximumAge()));
                } else {
                    metadataByMaterial.put(material, new AgeMeta(false, 0));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public AgeMeta get(Material material) {
        return metadataByMaterial.getOrDefault(material, new AgeMeta(false, 0));
    }
}
