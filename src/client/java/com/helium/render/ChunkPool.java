package com.helium.render;

import java.util.ArrayDeque;

/** Thread-local pool for transient chunk scheduling records. */
public final class ChunkPool {
    private static final int MAX = 512;
    private static final ThreadLocal<ArrayDeque<Task>> POOL = ThreadLocal.withInitial(ArrayDeque::new);

    private ChunkPool() {}

    public static Task borrow(int x, int y, int z, double priority, boolean important) {
        Task task = POOL.get().pollFirst();
        if (task == null) task = new Task();
        return task.reset(x, y, z, priority, important);
    }

    public static void release(Task task) {
        if (task == null) return;
        ArrayDeque<Task> pool = POOL.get();
        if (pool.size() < MAX) pool.offerFirst(task);
    }

    public static final class Task implements Comparable<Task> {
        private int x, y, z;
        private double priority;
        private boolean important;

        private Task reset(int x, int y, int z, double priority, boolean important) {
            this.x = x; this.y = y; this.z = z;
            this.priority = priority; this.important = important;
            return this;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public double priority() { return priority; }
        public boolean important() { return important; }

        @Override
        public int compareTo(Task other) {
            int priorityCompare = Double.compare(priority, other.priority);
            return priorityCompare != 0 ? priorityCompare : Boolean.compare(!important, !other.important);
        }
    }
}
