# Helium Benchmarking

Use `scripts/benchmark_report.py` to summarize captures from the same machine and Minecraft installation. Compare three separate profiles: `vanilla`, `sodium`, and `sodium+helium`. Keep Java version, GPU driver, resolution, resource packs, world save, render distance, simulation distance, and background workload fixed. Run each profile from a fresh process, warm up the scenario, and capture at least three equal-duration runs.

| Scenario | Repeatable setup | Primary measurements |
|---|---|---|
| Entity-heavy | Fixed village or mob-farm save and camera position | Frame time, entities, Rentities batch size |
| Dense entity batching | Fixed supported entity family and count | Average FPS, 1% lows, frame-time percentiles |
| Particle-heavy | Repeat the same particle-producing event | Frame time, particle count, allocation rate |
| Large world | Follow a saved route at fixed speed and view distance | Frame time, chunk queue depth, world-load time |
| Memory stress | Repeat the same allocation-heavy route for a fixed duration | Allocation rate, peak heap and native memory |
| Multiplayer screen | Refresh the same saved server list | Ping duration and queue saturation/rejections |
| Startup | Cold process launch to the main menu | Startup time |

Capture one row per rendered frame. For startup, world-load, and ping-only measurements, add a row with an empty `frame_time_ms` and fill the corresponding workload column. Optional metrics can be left empty. Values use milliseconds, bytes, or item counts as their column names indicate.
Capture one row per rendered frame. For startup, world-load, and ping-only measurements, add a row with an empty `frame_time_ms` and fill the corresponding workload column. Optional metrics can be left empty. Values use milliseconds, bytes, or item counts as their column names indicate.

```csv
scenario,variant,run,frame_time_ms,allocated_bytes,heap_used_bytes,cpu_frame_ms,gpu_frame_ms,startup_ms,world_load_ms,ping_duration_ms,entity_count,particle_count,chunk_queue_size
scenario,variant,run,frame_time_ms,allocated_bytes,heap_used_bytes,native_memory_bytes,cpu_frame_ms,gpu_frame_ms,startup_ms,world_load_ms,ping_duration_ms,entity_count,particle_count,chunk_queue_size
entity-heavy,sodium,1,16.4,2048,734003200,65536,12.1,,,,,54,8,2
```

`gpu_frame_ms` must come from an actual GPU timer/profiler measurement; the in-game overlay's GPU fence-poll value is CPU-side synchronization time and is not GPU frame time. Do not copy that value into the GPU column. The overlay's frame interval is a frame-to-frame interval, not an isolated CPU render duration.

Generate a Markdown report, including average FPS, 1% low, P50/P95/P99 frame time, allocation rate, peak heap, optional CPU/GPU timings, startup/world-load/ping times, peak workload counts, and FPS deltas against Sodium:

```bash
python3 scripts/benchmark_report.py captures.csv
```

Keep raw CSV captures with the machine/profile metadata when publishing results. Do not publish performance percentages without the corresponding repeatable capture set.
