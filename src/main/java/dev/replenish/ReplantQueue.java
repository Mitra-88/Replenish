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
    private int poolSize = 0;

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

        pool = new Job[2048];
        freeHead = -1;
        poolSize = 0;
        primePool();

        cursor = 0;
    }

    public void enqueue(Block block, Material plantMaterial, int delayTicks, int targetAge, BlockFace cocoaFacingDirection) {
        int delay = Math.max(1, delayTicks) & TIME_WHEEL_MASK;
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

        while (head != -1 && processed < maxPerTick) {
            Job job = pool[head];
            int next = job.next;

            try {
                replant(job);
            } catch (Throwable ignored) {
            } finally {
                job.clear();
                release(head);
            }
            head = next;
            processed++;
        }

        wheelHeads[cursor] = head;
        cursor = (cursor + 1) & TIME_WHEEL_MASK;
    }

    private void replant(Job job) {
        Block block = job.block;
        if (block == null || !block.getType().isAir()) return;

        Material plant = job.plantMaterial;
        if (plant == Material.COCOA) {
            BlockFace face = job.cocoaFacingDirection;
            if (face == null) return;
            block.setType(Material.COCOA, false);
            BlockData data = block.getBlockData();
            if (data instanceof Directional directional) directional.setFacing(face);
            setAgeClamped(data, job.targetAge, plant, block);
            return;
        }

        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (plant == Material.NETHER_WART) {
            if (below != Material.SOUL_SAND) return;
            block.setType(Material.NETHER_WART, false);
            setAgeClamped(block.getBlockData(), job.targetAge, plant, block);
            return;
        }

        if (below != Material.FARMLAND) return;
        block.setType(plant, false);
        setAgeClamped(block.getBlockData(), job.targetAge, plant, block);
    }

    private void setAgeClamped(BlockData data, int age, Material plant, Block block) {
        if (data instanceof Ageable ageable) {
            int max = ageMetaRegistry.get(plant).maximumAge;
            if (max > 0) ageable.setAge(Math.max(0, Math.min(max, age)));
            block.setBlockData(ageable, false);
        } else {
            block.setBlockData(data, false);
        }
    }

    private void ensureCapacity(int required) {
        if (pool.length >= required) return;
        Job[] expanded = new Job[Math.max(required, pool.length * 2)];
        System.arraycopy(pool, 0, expanded, 0, pool.length);
        pool = expanded;
    }

    private void primePool() {
        ensureCapacity(1024);
        int target = Math.max(poolSize, 1024);
        for (int i = poolSize; i < target; i++) {
            pool[i] = new Job();
            release(i);
        }
        poolSize = Math.max(poolSize, target);
    }

    private void release(int index) {
        Job job = pool[index];
        if (job != null) job.clear();
        else pool[index] = new Job();
        pool[index].next = freeHead;
        freeHead = index;
    }

    private int acquire() {
        if (freeHead != -1) {
            int index = freeHead;
            freeHead = pool[index].next;
            pool[index].next = -1;
            return index;
        }
        ensureCapacity(poolSize + 1);
        if (pool[poolSize] == null) pool[poolSize] = new Job();
        return poolSize++;
    }
}
