package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.logging.Level;

public final class ReplantQueue {

    private static final int TIME_WHEEL_BITS = 13;
    private static final int TIME_WHEEL_SIZE = 1 << TIME_WHEEL_BITS;
    private static final int TIME_WHEEL_MASK = TIME_WHEEL_SIZE - 1;

    private static final class Job {
        Block block;
        Material plantMaterial;
        int targetAge;
        BlockFace cocoaFacingDirection;
        int next = -1;

        void set(Block block, Material plantMaterial, int targetAge, BlockFace cocoaFacingDirection) {
            this.block = block;
            this.plantMaterial = plantMaterial;
            this.targetAge = targetAge;
            this.cocoaFacingDirection = cocoaFacingDirection;
            this.next = -1;
        }

        void clear() {
            block = null;
            plantMaterial = null;
            cocoaFacingDirection = null;
            next = -1;
        }
    }

    private final Plugin plugin;
    private final AgeMetaRegistry ageMetaRegistry;
    private final int maxPerTick;

    private final int[] wheelHeads = new int[TIME_WHEEL_SIZE];
    private Job[] pool = new Job[2048];
    private int freeHead = -1;

    private int cursor = 0;
    private int taskId = -1;
    private boolean started = false;

    public ReplantQueue(Plugin plugin, int maxPerTick, AgeMetaRegistry ageMetaRegistry) {
        this.plugin = plugin;
        this.ageMetaRegistry = ageMetaRegistry;
        this.maxPerTick = Math.max(256, maxPerTick);

        Arrays.fill(wheelHeads, -1);
        primePool();
    }

    public void start() {
        if (started) return;
        started = true;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        Arrays.fill(wheelHeads, -1);
        cursor = 0;
    }

    public void enqueue(Block block, Material plantMaterial, int delayTicks, int targetAge, BlockFace cocoaFacingDirection) {
        int delay = delayTicks % TIME_WHEEL_SIZE;
        if (delay <= 0) delay = 1;
        int slot = (cursor + delay) & TIME_WHEEL_MASK;

        int index = acquire();
        Job job = pool[index];
        job.set(block, plantMaterial, targetAge, cocoaFacingDirection);

        job.next = wheelHeads[slot];
        wheelHeads[slot] = index;
    }

    private void tick() {
        int head = wheelHeads[cursor];
        int processed = 0;

        // Grouping by chunk reduces NMS chunk lookups during heavy replanting
        long lastChunkKey = Long.MIN_VALUE;
        boolean lastChunkLoaded = false;

        while (head != -1 && processed < maxPerTick) {
            Job job = pool[head];
            int next = job.next;

            Block b = job.block;
            if (b != null) {
                // Atomic-style chunk check: compute key and cache result for the batch
                long currentKey = ((long) (b.getX() >> 4) << 32) | ((b.getZ() >> 4) & 0xFFFFFFFFL);
                if (currentKey != lastChunkKey) {
                    lastChunkKey = currentKey;
                    lastChunkLoaded = b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
                }

                if (lastChunkLoaded) {
                    try {
                        replant(job);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to replant block at " + b.getLocation(), e);
                    }
                }
            }

            job.clear();
            release(head);
            head = next;
            processed++;
        }

        wheelHeads[cursor] = head;
        cursor = (cursor + 1) & TIME_WHEEL_MASK;
    }

    private void replant(Job job) {
        Block block = job.block;
        if (!block.getType().isAir()) return;

        Material plant = job.plantMaterial;
        int targetAge = job.targetAge;

        // Optimized AgeMeta lookup: Fetch once per replant
        int maxAge = ageMetaRegistry.get(plant).maximumAge;

        if (plant == Material.COCOA) {
            BlockFace face = job.cocoaFacingDirection;
            if (face == null) return;
            block.setType(Material.COCOA, false);
            BlockData data = block.getBlockData();
            if (data instanceof Directional directional) directional.setFacing(face);
            applyAge(block, data, targetAge, maxAge);
            return;
        }

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (plant == Material.NETHER_WART) {
            if (below != Material.SOUL_SAND) return;
            block.setType(Material.NETHER_WART, false);
            applyAge(block, block.getBlockData(), targetAge, maxAge);
            return;
        }

        if (below != Material.FARMLAND) return;
        block.setType(plant, false);
        applyAge(block, block.getBlockData(), targetAge, maxAge);
    }

    private void applyAge(Block block, BlockData data, int age, int max) {
        if (data instanceof Ageable ageable) {
            if (max > 0) ageable.setAge(Math.max(0, Math.min(max, age)));
            block.setBlockData(ageable, false);
        } else {
            block.setBlockData(data, false);
        }
    }

    private void primePool() {
        if (pool.length < 1024) pool = new Job[1024];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new Job();
            pool[i].next = freeHead;
            freeHead = i;
        }
    }

    private int acquire() {
        if (freeHead == -1) {
            int oldSize = pool.length;
            int newSize = oldSize * 2;
            pool = Arrays.copyOf(pool, newSize);
            for (int i = oldSize; i < newSize; i++) {
                pool[i] = new Job();
                pool[i].next = freeHead;
                freeHead = i;
            }
        }
        int index = freeHead;
        freeHead = pool[index].next;
        pool[index].next = -1;
        return index;
    }

    private void release(int index) {
        pool[index].next = freeHead;
        freeHead = index;
    }
}