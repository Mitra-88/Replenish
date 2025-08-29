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

    private static final int WHEEL_BITS = 13;
    private static final int WHEEL_SIZE = 1 << WHEEL_BITS;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;

    private static final class Job {
        Block block;
        Material plantMat;
        int targetAge;
        BlockFace cocoaFacing;
        int next = -1;

        void set(Block b, Material m, int age, BlockFace face) {
            block = b; plantMat = m; targetAge = age; cocoaFacing = face; next = -1;
        }
        void clear() { block = null; plantMat = null; cocoaFacing = null; next = -1; }
    }

    private final Plugin plugin;
    private final AgeMetaRegistry ages;
    private final int maxPerTick;

    private final int[] wheelHeads = new int[WHEEL_SIZE];

    // Pool storage
    private Job[] pool = new Job[2048];
    private int freeHead = -1;
    private int poolSize = 0;

    private int cursor = 0;
    private int taskId = -1;
    private boolean started = false;

    public ReplantQueue(Plugin plugin, int maxPerTick, AgeMetaRegistry ages) {
        this.plugin = plugin;
        this.ages = ages;
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

    public void enqueue(Block block, Material plantMat, int delayTicks, int targetAge, BlockFace cocoaFacing) {
        final int delay = Math.max(1, delayTicks) & WHEEL_MASK;
        final int slot = (cursor + delay) & WHEEL_MASK;

        int idx = acquire();
        Job j = pool[idx];
        j.set(block, plantMat, targetAge, cocoaFacing);

        j.next = wheelHeads[slot];
        wheelHeads[slot] = idx;
    }

    private void tick() {
        int head = wheelHeads[cursor];
        int processed = 0;

        while (head != -1 && processed < maxPerTick) {
            Job j = pool[head];
            int next = j.next;

            try {
                replant(j);
            } catch (Throwable ignored) {
            } finally {
                j.clear();
                release(head);
            }
            head = next;
            processed++;
        }

        wheelHeads[cursor] = head;
        cursor = (cursor + 1) & WHEEL_MASK;
    }

    private void replant(Job j) {
        final Block block = j.block;
        if (block == null || !block.getType().isAir()) return;

        final Material plant = j.plantMat;
        if (plant == Material.COCOA) {
            final BlockFace face = j.cocoaFacing;
            if (face == null) return;
            block.setType(Material.COCOA, false);
            final BlockData data = block.getBlockData();
            if (data instanceof Directional d) d.setFacing(face);
            setAgeClamped(data, j.targetAge, plant, block);
            return;
        }

        final Material under = block.getRelative(org.bukkit.block.BlockFace.DOWN).getType();
        if (plant == Material.NETHER_WART) {
            if (under != Material.SOUL_SAND) return;
            block.setType(Material.NETHER_WART, false);
            setAgeClamped(block.getBlockData(), j.targetAge, plant, block);
            return;
        }

        if (under != Material.FARMLAND) return;
        block.setType(plant, false);
        setAgeClamped(block.getBlockData(), j.targetAge, plant, block);
    }

    private void setAgeClamped(BlockData data, int age, Material plant, Block block) {
        if (data instanceof Ageable a) {
            int max = ages.get(plant).maxAge;
            if (max > 0) a.setAge(Math.max(0, Math.min(max, age)));
            block.setBlockData(a, false);
        } else {
            block.setBlockData(data, false);
        }
    }

    private void ensureCapacity(int need) {
        if (pool.length >= need) return;
        Job[] n = new Job[Math.max(need, pool.length * 2)];
        System.arraycopy(pool, 0, n, 0, pool.length);
        pool = n;
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

    private void release(int idx) {
        Job j = pool[idx];
        if (j != null) j.clear();
        else pool[idx] = new Job();
        pool[idx].next = freeHead;
        freeHead = idx;
    }

    private int acquire() {
        if (freeHead != -1) {
            int idx = freeHead;
            freeHead = pool[idx].next;
            pool[idx].next = -1;
            return idx;
        }
        ensureCapacity(poolSize + 1);
        if (pool[poolSize] == null) pool[poolSize] = new Job();
        return poolSize++;
    }
}
