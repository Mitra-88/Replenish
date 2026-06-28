package dev.replenish;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

public final class AgeMetaRegistry {

  public static final class AgeMeta {
    public final int maximumAge;

    private AgeMeta(int maximumAge) {
      this.maximumAge = maximumAge;
    }
  }

  private final AgeMeta[] metadataArray;

  public AgeMetaRegistry(Plugin plugin) {
    this.metadataArray = new AgeMeta[Material.values().length];
    Material[] supportedCrops = {
      Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.NETHER_WART, Material.COCOA
    };

    for (Material material : supportedCrops) {
      try {
        BlockData data = Bukkit.createBlockData(material);
        if (data instanceof Ageable ageable) {
          metadataArray[material.ordinal()] = new AgeMeta(ageable.getMaximumAge());
        }
      } catch (Throwable error) {
        plugin.getLogger().log(Level.WARNING, "Age meta scan skipped for " + material, error);
      }
    }
  }

  public AgeMeta get(Material material) {
    if (material == null) return null;
    int ordinal = material.ordinal();
    return ordinal < metadataArray.length ? metadataArray[ordinal] : null;
  }
}
