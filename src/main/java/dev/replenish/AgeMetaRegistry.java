package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class AgeMetaRegistry {

    public static final class AgeMeta {
        public final int maximumAge;

        private AgeMeta(int maximumAge) {
            this.maximumAge = maximumAge;
        }
    }

    private static final Set<Material> SUPPORTED_CROPS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.NETHER_WART,
            Material.COCOA
    );

    private final Map<Material, AgeMeta> metadataByMaterial;

    public AgeMetaRegistry(Plugin plugin) {
        Map<Material, AgeMeta> tempMap = new EnumMap<>(Material.class);

        for (Material material : SUPPORTED_CROPS) {
            try {
                BlockData data = Bukkit.createBlockData(material);
                if (data instanceof Ageable ageable) {
                    tempMap.put(material, new AgeMeta(ageable.getMaximumAge()));
                }
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING, "Age meta scan skipped for " + material, error);
            }
        }

        this.metadataByMaterial = Collections.unmodifiableMap(tempMap);
    }

    public AgeMeta get(Material material) {
        return metadataByMaterial.get(material);
    }
}