package com.helium.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CountDownLatch;
            for (int i = 0; i < FastServerPingHelper.MAX_QUEUE - 1; i++) executor.execute(() -> {});
            FutureTask<Void> stalePing = new FutureTask<>(() -> null);
            executor.execute(stalePing);
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastServerPingHelperTest {

    @Test
    void saturationNeverRunsRejectedPingOnSubmittingThread() throws InterruptedException {
        ThreadPoolExecutor executor = FastServerPingHelper.createExecutor(0);
        int workers = FastServerPingHelper.threadCount(0);
        CountDownLatch workersStarted = new CountDownLatch(workers);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();

        try {
            for (int i = 0; i < workers; i++) {
                executor.execute(() -> {
                    workersStarted.countDown();
                    try {
                        releaseWorkers.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(workersStarted.await(5, TimeUnit.SECONDS));

            for (int i = 0; i < FastServerPingHelper.MAX_QUEUE; i++) executor.execute(() -> {});
            assertEquals(FastServerPingHelper.MAX_QUEUE, FastServerPingHelper.getQueueLength(executor));

            executor.execute(() -> rejectedTaskRan.set(true));
            FutureTask<Void> rejectedPing = new FutureTask<>(() -> {
                rejectedTaskRan.set(true);
                return null;
            });
            executor.execute(rejectedPing);
            assertFalse(rejectedTaskRan.get());
            assertTrue(rejectedPing.isCancelled());
            assertEquals(1, FastServerPingHelper.getRejectedTaskCount(executor));

            FastServerPingHelper.cancelQueuedTasks(executor);
            assertEquals(0, FastServerPingHelper.getQueueLength(executor));
            assertTrue(stalePing.isCancelled());
        } finally {
            releaseWorkers.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void recordsAveragePingDuration() throws InterruptedException {
        ThreadPoolExecutor executor = FastServerPingHelper.createExecutor(0);
        executor.execute(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        executor.shutdown();

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(FastServerPingHelper.getAveragePingDurationMillis(executor) >= 1);
    }
}
