package com.helium.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUE),
                new ThreadFactoryBuilder()
                        .setNameFormat("Helium-ServerPinger-%d")
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public static int threadCount(int serverCount) {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int desired = Math.max(MIN_THREADS, Math.max(processors, Math.min(serverCount, MAX_THREADS)));
        return Math.min(MAX_THREADS, desired);
    }
}
