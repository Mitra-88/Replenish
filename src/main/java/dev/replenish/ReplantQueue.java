package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;

/**
 * Centralized, allocation-friendly queue for delayed replants using a timing wheel.
 * One repeating task drives everything; no per-break tasks are created.
 */
public final class ReplantQueue {

    private static final int WHEEL_BITS = 13;
    private static final int WHEEL_SIZE = 1 << WHEEL_BITS;
    private static final int WHEEL_MASK = WHEEL_SIZE - 1;

    private static final class Job {
        Block block;
        Material plantMat;
        int slotIndex;
        int targetAge;
        BlockFace cocoaFacing;

        void set(Block b, Material m, int age, BlockFace face, int slot) {
            block = b; plantMat = m; targetAge = age; cocoaFacing = face; slotIndex = slot;
        }
        void clear() { block = null; plantMat = null; cocoaFacing = null; }
    }

    private final Plugin plugin;
    private final int maxPerTick;
    private final ArrayDeque<Job>[] wheel;
    private final ArrayDeque<Job> pool;

    private int cursor = 0;
    private int taskId = -1;
    private boolean started = false;

    @SuppressWarnings("unchecked")
    public ReplantQueue(Plugin plugin, int maxPerTick) {
        this.plugin = plugin;
        this.maxPerTick = Math.max(256, maxPerTick);
        this.wheel = (ArrayDeque<Job>[]) new ArrayDeque[WHEEL_SIZE];
        for (int i = 0; i < WHEEL_SIZE; i++) wheel[i] = new ArrayDeque<>(64);
        this.pool = new ArrayDeque<>(1024);
        for (int i = 0; i < 1024; i++) pool.addLast(new Job());
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
        for (var q : wheel) q.clear();
        pool.clear();
        cursor = 0;
    }

    public void enqueue(Block block, Material plantMat, int delayTicks, int targetAge, BlockFace cocoaFacing) {
        final int delay = Math.max(1, delayTicks) & WHEEL_MASK;
        final int slot = (cursor + delay) & WHEEL_MASK;
        final Job j = (pool.isEmpty() ? new Job() : pool.pollFirst());
        j.set(block, plantMat, targetAge, cocoaFacing, slot);
        wheel[slot].addLast(j);
    }

    private void tick() {
        final ArrayDeque<Job> bucket = wheel[cursor];
        int processed = 0;

        while (!bucket.isEmpty() && processed < maxPerTick) {
            final Job job = bucket.pollFirst();
            try {
                replant(job);
            } catch (Throwable ignored) {
            } finally {
                job.clear();
                pool.addLast(job);
            }
            processed++;
        }
        cursor = (cursor + 1) & WHEEL_MASK;
    }

    private static void setAge(BlockData data, int age, Block block) {
        if (data instanceof Ageable a) {
            a.setAge(Math.max(0, Math.min(a.getMaximumAge(), age)));
            block.setBlockData(a, false);
        } else {
            block.setBlockData(data, false);
        }
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
            setAge(data, j.targetAge, block);
            return;
        }

        final Material under = block.getRelative(org.bukkit.block.BlockFace.DOWN).getType();
        if (plant == Material.NETHER_WART) {
            if (under != Material.SOUL_SAND) return;
            block.setType(Material.NETHER_WART, false);
            setAge(block.getBlockData(), j.targetAge, block);
            return;
        }

        if (under != Material.FARMLAND) return;
        block.setType(plant, false);
        setAge(block.getBlockData(), j.targetAge, block);
    }
}
