package dev.replenish;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumSet;

final class CubeBreaker {

    private static final EnumSet<Material> SUPPORTED_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.NETHER_WART, Material.COCOA
    );
    private static final EnumSet<Material> HOE_TOOLS = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );
    private static final EnumSet<Material> AXE_TOOLS = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );
    private static final EnumSet<Material> JUNGLE_ANCHOR_BLOCKS = EnumSet.of(
            Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD
    );
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private static final int[][] OFFSETS_R1;
    static {
        OFFSETS_R1 = new int[26][3];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    OFFSETS_R1[i][0] = dx;
                    OFFSETS_R1[i][1] = dy;
                    OFFSETS_R1[i][2] = dz;
                    i++;
                }
            }
        }
    }

    private CubeBreaker() {}

    static void harvestAroundCenter(
            ReplenishPlugin plugin,
            AgeMetaRegistry ageMetaRegistry,
            Player player,
            Block center,
            Material centerType,
            int replantedAgeOfCenter
    ) {
        var cfg = plugin.getConfigCache();
        if (!cfg.cubeHarvestEnabled) return;
        if (cfg.cubeHarvestRadius <= 0) return;

        final World world = center.getWorld();

        final Material tool = player.getInventory().getItemInMainHand().getType();
        final boolean hoes = HOE_TOOLS.contains(tool);
        final boolean axes = AXE_TOOLS.contains(tool);

        if (!canUseToolFor(centerType, hoes, axes)) return;

        final boolean sameTypeOnly = cfg.cubeHarvestSameTypeOnly;
        final int hardCap = Math.min(cfg.cubeHarvestHardCap, OFFSETS_R1.length);

        int broken = 0;

        for (int n = 0; n < hardCap; n++) {
            int[] o = OFFSETS_R1[n];
            Block b = center.getRelative(o[0], o[1], o[2]);

            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) continue;

            Material type = b.getType();
            if (type == Material.AIR) continue;
            if (!SUPPORTED_CROPS.contains(type) || !plugin.isCropEnabled(type)) continue;

            if (sameTypeOnly && type != centerType) continue;
            if (!canUseToolFor(type, hoes, axes)) continue;

            if (type == Material.COCOA) {
                if (findAdjacentJungle(b) == null) continue;
            } else {
                Material below = b.getRelative(BlockFace.DOWN).getType();
                if (type == Material.NETHER_WART) {
                    if (below != Material.SOUL_SAND) continue;
                } else if (below != Material.FARMLAND) {
                    continue;
                }
            }

            BlockData originalData = b.getBlockData();
            int originalAge = 0;
            boolean wasMature = false;
            if (originalData instanceof Ageable ageable) {
                int maxAge = ageMetaRegistry.get(type).maximumAge;
                originalAge = ageable.getAge();
                wasMature = maxAge > 0 && originalAge >= maxAge;
            }
            int replantedAge = wasMature ? 0 : originalAge;

            Material seed = seedFor(type);
            if (wasMature && plugin.getConfigCache().requirePlayerSeed) {
                if (seed == null) continue;
                if (!SeedIndex.consume(player, seed)) continue;
            }

            ItemStack toolStack = player.getInventory().getItemInMainHand();
            Collection<ItemStack> drops = wasMature ? b.getDrops(toolStack, player) : java.util.Collections.emptyList();

            BlockFace cocoaFacing =
                    (type == Material.COCOA && originalData instanceof Directional d) ? d.getFacing() : null;
            BlockFace playerFacing = player.getFacing();

            b.setType(Material.AIR, false);

            if (!drops.isEmpty()) {
                var dropLoc = DropPickupManager.centeredDropLocation(b.getLocation());
                if (plugin.getConfigCache().directPickup) {
                    DropPickupManager.giveToPlayerOrDrop(player, dropLoc, drops);
                } else {
                    for (ItemStack it : drops) {
                        if (it == null || it.getAmount() <= 0) continue;
                        world.dropItemNaturally(dropLoc, it);
                    }
                }
            }

            int delay = Math.max(1, plugin.getConfigCache().replantDelayTicks);
            if (type == Material.COCOA) {
                BlockFace chosen;
                if (cocoaFacing != null && isJungle(b.getRelative(cocoaFacing).getType())) {
                    chosen = cocoaFacing;
                } else if (isJungle(b.getRelative(playerFacing).getType())) {
                    chosen = playerFacing;
                } else {
                    chosen = findAdjacentJungle(b);
                }
                if (chosen != null) {
                    plugin.enqueueReplant(b, Material.COCOA, delay, replantedAge, chosen);
                }
            } else {
                plugin.enqueueReplant(b, type, delay, replantedAge, null);
            }

            if (wasMature) grantFarmingExperience(player, type);

            if (++broken >= hardCap) break;
        }
    }

    private static boolean canUseToolFor(Material crop, boolean hoes, boolean axes) {
        return switch (crop) {
            case COCOA -> axes;
            case WHEAT, CARROTS, POTATOES, NETHER_WART -> hoes;
            default -> true;
        };
    }

    private static Material seedFor(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    private static void grantFarmingExperience(Player player, Material cropType) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) return;
        var user = dev.aurelium.auraskills.api.AuraSkillsApi.get().getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return;
        double xp = switch (cropType) {
            case WHEAT -> 3.0;
            case CARROTS, POTATOES -> 3.5;
            case NETHER_WART -> 3.7;
            case COCOA -> 4.0;
            default -> 0;
        };
        if (xp > 0) user.addSkillXp(dev.aurelium.auraskills.api.skill.Skills.FARMING, xp);
    }

    private static BlockFace findAdjacentJungle(Block block) {
        for (BlockFace f : HORIZONTAL_FACES) {
            if (isJungle(block.getRelative(f).getType())) return f;
        }
        return null;
    }

    private static boolean isJungle(Material m) { return JUNGLE_ANCHOR_BLOCKS.contains(m); }
}
