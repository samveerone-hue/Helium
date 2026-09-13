package com.helium.rentities.entities;

import com.helium.HeliumClient;
import net.minecraft.entity.EntityType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Off-thread preparation of immutable entity-type metadata only.
 * Never touches Minecraft entity instances, GL objects, or render state.
 * Missing/stale results are fail-closed so unresolved work remains on the vanilla path.
 */
public final class AsyncRenderPreparation {
    private final ExecutorService executor;
    private final Map<EntityType<?>, CompletableFuture<Result>> results = new ConcurrentHashMap<>();

    public AsyncRenderPreparation() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Rentities-RenderPrep");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
            return t;
        });
        warmup();
    }

    private void warmup() {
        for (EntityType<?> type : EntityBatchRegistry.REGISTRY_TYPES()) schedule(type);
    }

    public void schedule(EntityType<?> type) {
        if (type == null || results.containsKey(type) || executor.isShutdown()) return;
        results.put(type, CompletableFuture.supplyAsync(() -> prepare(type), executor));
    }

    private Result prepare(EntityType<?> type) {
        EntityAnimationCategory category = EntityBatchRegistry.getCategory(type);
        return new Result(category, EntityBatchRegistry.isGpuBatchable(type));
    }

    public boolean allowsBatch(EntityType<?> type, boolean whitelistOnly, Set<String> whitelist, Set<String> blacklist) {
        if (type == null) return false;
        String key = type.toString();
        if (blacklist != null && blacklist.contains(key)) return false;
        if (whitelistOnly && (whitelist == null || !whitelist.contains(key))) return false;
        schedule(type);
        CompletableFuture<Result> future = results.get(type);
        if (future == null || !future.isDone()) return false;
        try {
            Result r = future.getNow(null);
            return r != null && r.gpuBatchable();
        } catch (Throwable t) {
            HeliumClient.LOGGER.debug("[Rentities] Async render prep failed for {}: {}", key, t.toString());
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        results.clear();
    }

    private record Result(EntityAnimationCategory category, boolean gpuBatchable) {}
}
