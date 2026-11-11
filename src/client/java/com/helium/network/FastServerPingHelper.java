package com.helium.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded server-ping executor derived from the scheduling strategy used by
 * Fast Server Pings, adapted to Helium's existing vanilla-pinger hook.
 */
public final class FastServerPingHelper {

    public static final int MAX_THREADS = 32;
    public static final int MAX_QUEUE = 256;
    private static final int MIN_THREADS = 4;

    private FastServerPingHelper() {}

    public static ThreadPoolExecutor createExecutor(int serverCount) {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int desired = Math.max(MIN_THREADS, Math.max(processors, Math.min(serverCount, MAX_THREADS)));
        int threads = Math.min(MAX_THREADS, desired);

        return new PingExecutor(
                threads,
                new LinkedBlockingQueue<>(MAX_QUEUE),
                new ThreadFactoryBuilder()
                        .setNameFormat("Helium-ServerPinger-%d")
                        .setDaemon(true)
                        .build()
        );
    }

    public static void cancelQueuedTasks(ThreadPoolExecutor executor) {
        if (executor == null) return;
        List<Runnable> queued = new ArrayList<>();
        executor.getQueue().drainTo(queued);
        for (Runnable task : queued) {
            if (task instanceof Future<?> future) future.cancel(false);
        }
    }

    public static int getQueueLength(ThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getQueue().size();
    }

    public static long getAveragePingDurationMillis(ThreadPoolExecutor executor) {
        if (!(executor instanceof PingExecutor pingExecutor)) return 0;
        long completed = pingExecutor.completedTasks.get();
        return completed == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(pingExecutor.totalDurationNanos.get() / completed);
    }

    public static long getRejectedTaskCount(ThreadPoolExecutor executor) {
        return executor instanceof PingExecutor pingExecutor ? pingExecutor.rejectedTasks.get() : 0;
    }

    public static int threadCount(int serverCount) {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int desired = Math.max(MIN_THREADS, Math.max(processors, Math.min(serverCount, MAX_THREADS)));
        return Math.min(MAX_THREADS, desired);
    }

    private static final class PingExecutor extends ThreadPoolExecutor {
        private final AtomicLong totalDurationNanos = new AtomicLong();
        private final AtomicLong completedTasks = new AtomicLong();
        private final AtomicLong rejectedTasks = new AtomicLong();
        private final ThreadLocal<Long> taskStart = new ThreadLocal<>();

        private PingExecutor(int threads, LinkedBlockingQueue<Runnable> queue,
                             java.util.concurrent.ThreadFactory threadFactory) {
            super(threads, threads, 0L, TimeUnit.MILLISECONDS, queue, threadFactory,
                    rejectionHandler());
        }

        private static RejectedExecutionHandler rejectionHandler() {
            return (task, executor) -> {
                ((PingExecutor) executor).rejectedTasks.incrementAndGet();
                if (task instanceof Future<?> future) future.cancel(false);
            };
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable task) {
            taskStart.set(System.nanoTime());
            super.beforeExecute(thread, task);
        }

        @Override
        protected void afterExecute(Runnable task, Throwable failure) {
            Long started = taskStart.get();
            if (started != null) {
                totalDurationNanos.addAndGet(System.nanoTime() - started);
                completedTasks.incrementAndGet();
                taskStart.remove();
            }
            super.afterExecute(task, failure);
        }
    }
}
