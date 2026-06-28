package dev.replenish;

import java.util.Arrays;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

public final class ReplantQueue {

  private static final int TIME_WHEEL_BITS = 13;
  private static final int TIME_WHEEL_SIZE = 1 << TIME_WHEEL_BITS;
  private static final int TIME_WHEEL_MASK = TIME_WHEEL_SIZE - 1;
  private static final int INITIAL_POOL_SIZE = 1 << 14;

  private static final BlockFace[] FACES = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
  };

  private Block[] poolBlocks;
  private Material[] poolMaterials;
  private int[] poolMeta;
  private int[] poolNext;

  private final int[] wheelHeads = new int[TIME_WHEEL_SIZE];
  private int freeHead = -1;

  private final Plugin plugin;
  private final AgeMetaRegistry ageMetaRegistry;
  private final int maxPerTick;

  private int cursor = 0;
  private int taskId = -1;
  private volatile boolean started = false;

  public ReplantQueue(Plugin plugin, int maxPerTick, AgeMetaRegistry ageMetaRegistry) {
    this.plugin = plugin;
    this.ageMetaRegistry = ageMetaRegistry;
    this.maxPerTick = Math.max(256, maxPerTick);

    Arrays.fill(wheelHeads, -1);
    primePool();
  }

  public synchronized void start() {
    if (started) return;
    started = true;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
  }

  public synchronized void stop() {
    if (!started) return;
    started = false;
    if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    taskId = -1;

    freeHead = -1;
    for (int i = poolBlocks.length - 1; i >= 0; i--) {
      poolBlocks[i] = null;
      poolMaterials[i] = null;
      poolMeta[i] = 0;
      poolNext[i] = freeHead;
      freeHead = i;
    }
    Arrays.fill(wheelHeads, -1);
    cursor = 0;
  }

  public synchronized void enqueue(
      Block block,
      Material plantMaterial,
      int delayTicks,
      int targetAge,
      BlockFace cocoaFacingDirection) {
    int delay = Math.max(1, delayTicks);
    if (delay >= TIME_WHEEL_SIZE) {
      plugin
          .getLogger()
          .warning(
              "Replant delay of "
                  + delayTicks
                  + " ticks exceeds wheel size ("
                  + (TIME_WHEEL_SIZE - 1)
                  + "), truncating. Block at "
                  + locString(block));
      delay = TIME_WHEEL_SIZE - 1;
    }
    int slot = (cursor + delay) & TIME_WHEEL_MASK;

    int index = acquire();

    poolBlocks[index] = block;
    poolMaterials[index] = plantMaterial;

    int safeAge = Math.max(0, targetAge) & 0xFF;
    poolMeta[index] = safeAge | ((faceToOrdinal(cocoaFacingDirection) & 0x3) << 8);

    poolNext[index] = wheelHeads[slot];
    wheelHeads[slot] = index;
  }

  private synchronized void tick() {
    if (!started) return;

    int head = wheelHeads[cursor];
    if (head == -1) {
      cursor = (cursor + 1) & TIME_WHEEL_MASK;
      return;
    }

    int processed = 0;
    World lastWorld = null;
    int lastChunkX = Integer.MIN_VALUE;
    int lastChunkZ = Integer.MIN_VALUE;
    boolean lastChunkLoaded = false;

    int unprocessedHead = -1;
    int unprocessedTail = -1;

    while (head != -1) {
      if (processed >= maxPerTick) break;

      Block b = poolBlocks[head];
      int next = poolNext[head];

      if (b == null) {
        release(head);
        head = next;
        continue;
      }

      World world = b.getWorld();

      boolean shouldProcess = false;
      int chunkX = b.getX() >> 4;
      int chunkZ = b.getZ() >> 4;

      if (world != lastWorld || chunkX != lastChunkX || chunkZ != lastChunkZ) {
        lastWorld = world;
        lastChunkX = chunkX;
        lastChunkZ = chunkZ;

        try {
          lastChunkLoaded = world.isChunkLoaded(chunkX, chunkZ);
        } catch (Exception e) {
          lastChunkLoaded = false;
        }
      }

      if (lastChunkLoaded) {
        shouldProcess = true;
      }

      if (shouldProcess) {
        try {
          replant(head);
        } catch (Exception e) {
          plugin.getLogger().log(Level.WARNING, "Failed to replant crop at " + locString(b), e);
        }
        release(head);
        processed++;
      } else {
        if (unprocessedHead == -1) {
          unprocessedHead = head;
        } else {
          poolNext[unprocessedTail] = head;
        }
        poolNext[head] = -1;
        unprocessedTail = head;
      }

      head = next;
    }

    int nextSlot = (cursor + 1) & TIME_WHEEL_MASK;

    if (head != -1) {
      int tail = head;
      while (poolNext[tail] != -1) {
        tail = poolNext[tail];
      }
      poolNext[tail] = wheelHeads[nextSlot];
      wheelHeads[nextSlot] = head;
    }

    if (unprocessedHead != -1) {
      poolNext[unprocessedTail] = wheelHeads[nextSlot];
      wheelHeads[nextSlot] = unprocessedHead;
    }

    wheelHeads[cursor] = -1;
    cursor = (cursor + 1) & TIME_WHEEL_MASK;
  }

  private void replant(int index) {
    Block block = poolBlocks[index];
    if (block == null || !block.getType().isAir()) return;

    Material plant = poolMaterials[index];
    if (plant == null) return;

    int metadata = poolMeta[index];
    int targetAge = metadata & 0xFF;
    BlockFace face = ordinalToFace((metadata >> 8) & 0x3);

    if (ageMetaRegistry == null) return;

    AgeMetaRegistry.AgeMeta meta = ageMetaRegistry.get(plant);
    if (meta == null) {
      plugin
          .getLogger()
          .warning(
              "No age data found for plant: "
                  + plant
                  + ", skipping replant at "
                  + locString(block));
      return;
    }
    int maxAge = meta.maximumAge;

    BlockData data = plant.createBlockData();

    if (plant == Material.COCOA) {
      if (data instanceof Directional directional) {
        directional.setFacing(face);
        Block attached = block.getRelative(face);
        String attachedName = attached.getType().name();

        if (!attachedName.contains("JUNGLE_LOG") && !attachedName.contains("JUNGLE_WOOD")) {
          return;
        }
      } else {
        return;
      }
    } else {
      Material below = block.getRelative(BlockFace.DOWN).getType();
      if (plant == Material.NETHER_WART) {
        if (below != Material.SOUL_SAND) return;
      } else {
        if (below != Material.FARMLAND) return;
      }
    }

    if (data instanceof Ageable ageable) {
      if (maxAge > 0) {
        ageable.setAge(Math.min(maxAge, targetAge));
      }
    }

    block.setBlockData(data, false);
  }

  private String locString(Block b) {
    World w = b.getWorld();
    String wName = w.getName();
    return wName + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
  }

  private void primePool() {
    int size = INITIAL_POOL_SIZE;
    poolBlocks = new Block[size];
    poolMaterials = new Material[size];
    poolMeta = new int[size];
    poolNext = new int[size];

    for (int i = size - 1; i >= 0; i--) {
      poolNext[i] = freeHead;
      freeHead = i;
    }
  }

  private void growPool() {
    int oldSize = poolBlocks.length;
    int newSize = oldSize << 1;
    if (newSize <= oldSize) {
      throw new IllegalStateException(
          "Pool size overflow: cannot grow beyond " + oldSize + " entries");
    }

    poolBlocks = Arrays.copyOf(poolBlocks, newSize);
    poolMaterials = Arrays.copyOf(poolMaterials, newSize);
    poolMeta = Arrays.copyOf(poolMeta, newSize);
    poolNext = Arrays.copyOf(poolNext, newSize);

    for (int i = newSize - 1; i >= oldSize; i--) {
      poolBlocks[i] = null;
      poolMaterials[i] = null;
      poolMeta[i] = 0;
      poolNext[i] = freeHead;
      freeHead = i;
    }
  }

  private int acquire() {
    if (freeHead == -1) growPool();
    int index = freeHead;
    freeHead = poolNext[index];
    poolNext[index] = -1;
    return index;
  }

  private void release(int index) {
    poolBlocks[index] = null;
    poolMaterials[index] = null;
    poolMeta[index] = 0;
    poolNext[index] = freeHead;
    freeHead = index;
  }

  private static int faceToOrdinal(BlockFace face) {
    if (face == null) return 0;
    if (face == BlockFace.EAST) return 1;
    if (face == BlockFace.SOUTH) return 2;
    if (face == BlockFace.WEST) return 3;
    return 0;
  }

  private static BlockFace ordinalToFace(int ord) {
    return FACES[ord & 3];
  }
}
