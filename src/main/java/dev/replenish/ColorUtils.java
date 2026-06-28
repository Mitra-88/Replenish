package dev.replenish;

import org.bukkit.ChatColor;

public class ColorUtils {

  public static String color(String value) {
    return ChatColor.translateAlternateColorCodes('&', value);
  }
}
