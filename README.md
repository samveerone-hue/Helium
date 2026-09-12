# Helium

Lightweight client-side performance mod for Minecraft 1.21.11.

Helium is focused on **real, measurable client-side rendering and runtime optimizations**. Features that cannot be implemented safely are not exposed as active toggles, and GPU paths fall back to vanilla when their ABI cannot reproduce an entity or renderer state exactly.

## 1.21.11 feature status

### Rendering and culling

| Feature | Status | Notes |
|---|---|---|
| Entity culling | Working | Uses the renderer-provided render/camera position for visibility and distance tests. |
| Block-entity culling | Working | Independently configurable. |
| Particle culling / limiting / priority | Working | Separate controls for distance, count and prioritization. |
| Particle batching / LOD | Working | Optional, with conservative fallback behavior. |
| Leaf/sign/rain/beacon/painting/item-frame culling | Working | Individually switchable. |
| Item-frame LOD | Working | Optional long-range reduction. |
| Animation throttling | Working | Reduces animation/update work when enabled. |
| Render pipeline | Working | Owns the chunk scheduling/batching path and frame-budget controls. |
| Fast framebuffer / DSA / renderbuffer paths | Working | Compatibility-sensitive low-level options. |
| Temporal reprojection | Working | Optional; visual behavior is renderer/shader dependent. |

### Rentities / GPU entity batching

Rentities is the high-value GPU entity path. It packs compatible entity models into shared GPU buffers and animates them in shaders instead of issuing the full vanilla model path for every instance.

The current architecture includes:

- exact render-state pose capture for supported models;
- authoritative entity facing from render state;
- baked per-entity bone pivots;
- expanded exact-pose instance slots;
- GPU frustum/visibility culling;
- async visibility caching with refresh, age and distance controls;
- async render preparation;
- per-entity whitelist/blacklist safety controls;
- dedicated GPU equipment/held-item handling with conservative material fallbacks;
- shader-side animation categories for supported model families;
- fallback to vanilla when the common ABI cannot reproduce the visual state exactly;
- SIMD-backed CPU batch math in the mesh-baking hot path where it is beneficial.

The invariant is: **an entity is either representable by the GPU ABI or it stays on the vanilla path.**

### Deliberate Rentities fallbacks

Complex or state-heavy rigs remain vanilla until their full renderer state can be represented safely. Current examples include Blaze, Breeze, Squid/Glow Squid, segmented Silverfish/Endermite models, specialized Warden/Wither-style rigs, and other families not yet registered with exact pose metadata.

## Engine, memory, threading and world loading

| Feature | Status | Notes |
|---|---|---|
| Memory optimizations | Working | Object/buffer pooling and allocation reduction. |
| Thread optimizations | Working | Thread-priority/event-polling helpers and background work where safe. |
| Fast world loading | Working | Configurable. |
| Reduced allocations | Working | Configurable. |
| Object deduplication | Working | Configurable. |
| Idle pause / FPS limiting | Working | Configurable. |
| Native memory | Working | Configurable pool. |
| Fast pack reload | Working | Resource-pack reload path can run asynchronously. |
| Async light updates | **Removed from active surface** | The old implementation only hooked light-update batching; it did not safely move the actual lighting computation off-thread. Legacy config values are forced off. |
| Packet batching | **Removed from active surface** | The old implementation queued byte arrays but was not integrated with Minecraft's real packet framing/send path. Legacy config values are forced off. |

## Math, GPU and OpenGL

| Feature | Status | Notes |
|---|---|---|
| Fast math | Working | Explicit opt-in. |
| SIMD math | Working | Java Vector API backend with scalar fallback; now connected to a real Rentities mesh-baking batch. |
| JOML fast math | Working | Optional JVM/JOML optimization toggle. |
| Fast random | Working | Explicit opt-in after config initialization. |
| GPU compute / GPU LOS | Working | Optional OpenCL visibility path with CPU fallback. |
| GPU pathfinding | Experimental / opt-in | Does not replace Minecraft's authoritative multiplayer navigation; kept as an explicitly experimental compute feature. |
| Adaptive / display sync | Working | Driver-dependent. |
| NVIDIA / AMD / Intel optimizations | Working | Vendor-specific controls are isolated. |
| Reflex | Working | Enable/offset/debug controls. |
| Global GL state cache | **Removed from active surface** | Helium does not currently have renderer-owned invalidation strong enough for a global state cache. Legacy config values are forced off. |

## Networking, menus and QoL

Fast server/IP ping, refresh-scroll preservation, direct-connect preview, opt-in hotbar optimization, multi-switch/smooth hotbar controls, smooth scrolling, Windows window styling, fullbright, FPS overlay controls, menu FPS limiting, async pack reload, instant language change and one-click crafting are independently configurable where supported by the current branch.

## Configuration

Helium maintains a shared option schema so the regular Helium config screen and Sodium integration expose the same feature set and dependency relationships.

Rentities controls include:

- GPU entity batching (master toggle)
- GPU frustum culling
- async render preparation
- async visibility
- visibility refresh interval
- visibility maximum age
- visibility maximum distance
- whitelist-only mode plus whitelist/blacklist data
- batching debug and solid-debug modes

GPU Compute controls include GPU compute, GPU LOS, experimental GPU pathfinding, grid size, refresh ticks and maximum batch size.

Removed/unavailable legacy features remain load-compatible in `helium.json`, but are sanitized to disabled rather than presented as working options.

## Catalyst comparison and roadmap

[Catalyst](https://modrinth.com/mod/catalysst) is another MIT-licensed client-side Fabric optimization mod for 1.21.11. Its 1.1.3 release includes an Entity AI optimizer plus RenderBatch and AsyncMeshing work. citeturn707101search1

Helium should **take ideas and, where useful, compatible implementations from Catalyst selectively**, not blindly merge the project. The first audit targets are:

1. AsyncMeshing scheduling and queue behavior where Helium's existing chunk scheduler can be made safer or faster.
2. RenderBatch behavior where it reduces CPU/GPU submission overhead without conflicting with Helium's render pipeline.
3. Entity/block-entity/particle async work only where thread-safety and client-state ownership are proven for 1.21.11.
4. Mesh-cache and resource-loading techniques where they do not duplicate or regress Helium's existing caches.
5. Any feature that would otherwise become a placebo toggle must either gain a real implementation or stay out of the active config surface.

Catalyst is a **reference/candidate source**, not a runtime dependency of Helium.

## Build and verification

Helium targets Java 21 bytecode.

```bash
git clone https://github.com/samveerone-hue/Helium.git
cd Helium
./gradlew remapJar
```

Development client:

```bash
./gradlew runClient
```

The remapped jar is written to `build/libs/`.

The latest SIMD feature commit is `9b3a016995dadcc234c258d5a02498fea7630534`. GitHub Actions build **#473** is currently running for that commit; an in-progress run must not be treated as a green verification result.

## Project structure

```text
src/
├── main/resources/          # metadata, mixins and assets
└── client/java/com/helium/
    ├── config/              # config + config screen
    ├── compat/              # Sodium/ModMenu compatibility
    ├── compute/             # optional OpenCL backend
    ├── memory/              # memory pools and allocation helpers
    ├── math/                # fast math + optional SIMD backend
    ├── render/              # rendering, GL state and culling
    ├── rentities/           # GPU entity batching, baking, culling and shaders
    └── mixin/               # targeted Minecraft hooks
```

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| Fabric API | Required | Fabric/Minecraft integration |
| Sodium | Compile-only / integration | Helium renderer/config integration |
| YACL / config library support | Build dependency | Config UI support |
| ModMenu | Optional | Mod-list integration |

## Contributing

For bug fixes, describe what was broken and why. For renderer changes, verify the vanilla model/state path before adding a new GPU approximation. Prefer targeted ports over wholesale copies of another optimizer.

## License

MIT — see [LICENSE](LICENSE).
