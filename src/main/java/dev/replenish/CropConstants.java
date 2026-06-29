package dev.replenish;

import java.util.EnumSet;
import org.bukkit.Material;

public final class CropConstants {

  public static final EnumSet<Material> JUNGLE_ANCHOR_BLOCKS =
      EnumSet.of(
          Material.JUNGLE_LOG,
          Material.STRIPPED_JUNGLE_LOG,
          Material.JUNGLE_WOOD,
          Material.STRIPPED_JUNGLE_WOOD);

  private CropConstants() {
  }
}
