package dev.replenish;

import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.*;

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

    private static final Set<Material> SUPPORTED_CROPS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.NETHER_WART,
            Material.COCOA
    );

    private final Map<Material, AgeMeta> metadataByMaterial = new EnumMap<>(Material.class);

    public AgeMetaRegistry(Plugin plugin) {
        for (Material material : SUPPORTED_CROPS) {
            try {
                BlockData data = plugin.getServer().createBlockData(material);
                if (data instanceof Ageable ageable) {
                    metadataByMaterial.put(material, new AgeMeta(true, ageable.getMaximumAge()));
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
