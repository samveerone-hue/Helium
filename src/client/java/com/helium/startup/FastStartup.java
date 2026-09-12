package com.helium.startup;

import com.helium.HeliumClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Parallelizes safe, initialization-free Helium class loading during the first client tick. */
public final class FastStartup {

    private static ExecutorService startupPool;
    private static final List<Future<?>> pendingTasks = new ArrayList<>();
    private static volatile boolean started;
    private static volatile boolean prepared;

    private FastStartup() {}

    public static synchronized void init() {
        if (startupPool != null && !startupPool.isShutdown()) return;
        int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "helium-startup-" + counter.getAndIncrement());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        startupPool = Executors.newFixedThreadPool(threads, factory);
        HeliumClient.LOGGER.info("fast startup pool initialized with {} threads", threads);
    }

    /**
     * Loads class metadata without running static initializers. This removes class-linking and
     * bytecode verification spikes from the render thread while preserving normal initialization order.
     */
    public static synchronized void prepare() {
        if (prepared && startupPool != null && !startupPool.isShutdown()) return;
        init();
        pendingTasks.clear();
        started = true;
        prepared = false;
        String[] classes = {
                "com.helium.math.FastMath",
                "com.helium.math.SimdMath",
                "com.helium.render.ModelCache",
                "com.helium.render.RenderPipeline",
                "com.helium.render.AsyncChunkMeshing",
                "com.helium.render.FastWorldLoadingOptimizer",
                "com.helium.rentities.entities.EntityBatchRenderer",
                "com.heium.rentities.entities.EntityMeshBaker",
                "com.helium.network.BufferOptimizer",
                "com.helium.lighting.AsyncLightEngine",
                "com.helium.compute.GpuComputeManager"
        };
        for (String name : classes) {
            pendingTasks.add(submit(() -> {
                try {
                    Class.forName(name, false, FastStartup.class.getClassLoader());
                } catch (Throwable t) {
                    HeliumClient.LOGGER.debug("fast startup preload skipped {}: {}", name, t.toString());
                }
            }));
        }
        prepared = true;
    }

    public static <T> CompletableFuture<T> submit(Supplier<T> task) {
        if (startupPool == null || startupPool.isShutdown()) init();
        return CompletableFuture.supplyAsync(task, startupPool);
    }

    public static CompletableFuture<Void> submit(Runnable task) {
        if (startupPool == null || startupPool.isShutdown()) init();
        return CompletableFuture.runAsync(task, startupPool);
    }

    public static boolean isPrepared() {
        return prepared;
    }

    public static boolean isStarted() {
        return started;
    }

    /** Drains completed preload tasks without blocking the client thread. */
    public static synchronized void pollCompleted() {
        if (pendingTasks.isEmpty()) return;
        pendingTasks.removeIf(Future::isDone);
    }

    public static synchronized void shutdown() {
        if (startupPool != null) {
            startupPool.shutdown();
            startupPool = null;
        }
        pendingTasks.clear();
        started = false;
        prepared = false;
    }
}
