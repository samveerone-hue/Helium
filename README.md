# Helium

Lightweight client-side performance mod for Minecraft.

> This is the development repository for Helium. For normal installation, use the published Modrinth build.

## Development status — `1.21.11`

This README describes the branch's real feature surface. Features that cannot be represented correctly by a generic optimization path are allowed to fall back to vanilla rendering.

### Rendering and culling

| Feature | Status | Notes |
|---|---|---|
| Entity culling | Working | Configurable distance-based visibility path. |
| Block-entity culling | Working | Configurable and independently switchable. |
| Particle culling/limits/priority | Working | Separate controls; aggressive limits can intentionally hide effects. |
| Particle batching/LOD | Working | Optional, with conservative fallback behavior. |
| Leaf/sign/rain/beacon/painting/item-frame culling | Working | Individually switchable for compatibility. |
| Item-frame LOD | Working | Optional long-range reduction. |
| Animation throttling | Working | Intentionally reduces update frequency when enabled. |
| Render-pipeline optimizations | Working | Fast math, fast animations, enum caching, accelerated text, uniform caching and renderer-owned pipeline work are independently configurable. |
| Rentities GPU entity batching | Working | Exact-pose registry plus conservative vanilla fallback for unsupported rigs. |
| Rentities mesh SIMD preprocessing | Working | Opt-in Vector API normal normalization for sufficiently large baked meshes; scalar behavior remains available. |

### Engine, memory, threading and world loading

| Feature | Status | Notes |
|---|---|---|
| Memory optimizations | Working | Pooling and allocation reductions are configurable. |
| Thread optimizations | Working | Background helpers are configurable. |
| Fast startup | Experimental / working | Parallel class metadata preloading only; static initializers are not executed off-thread. |
| Fast world loading | Working | Optional. |
| Reduced allocations | Working | Enabled by default where Helium can safely reduce temporaries. |
| Async resource-pack reload | Working | Resource preparation is off-thread while Minecraft-owned application remains on the client thread. |
| Model cache | Experimental / working | Bounded block-model front cache; Rentities mesh caching remains separate. |
| Async light preparation | Experimental / working | Background deduplication/coalescing only; vanilla light propagation remains on its owning thread. |
| Network buffer pooling | Experimental / working | Concurrent packet/direct-buffer reuse without changing packet protocol semantics. |
| Object deduplication | Working | Enabled by default. |
| Idle pause/FPS limiting | Working | Optional. |

### Math, GPU and OpenGL

| Feature | Status | Notes |
|---|---|---|
| Fast math / SIMD / JOML math | Working / experimental | SIMD uses the Java Vector API where available and falls back safely. |
| Fast random | Working | Explicit opt-in. |
| GPU compute | Working | Optional OpenCL backend with fallback. |
| Adaptive/display sync | Working | Driver-dependent. |
| Temporal reprojection | Working | Optional; visual behavior is shader-dependent. |
| Framebuffer blit / DSA / renderbuffer paths | Working | Compatibility-sensitive low-level options. |
| OpenGL cleanup / screenshot leak fixes | Working | Dedicated resource/state cleanup paths. |
| NVIDIA / AMD / Intel optimizations | Working | Vendor-specific toggles are isolated. |
| Reflex | Working | Configurable enable/offset/debug controls. |

### Networking, menus, hotbar and QoL

Fast server/IP ping, refresh-scroll preservation, direct-connect preview, opt-in hotbar optimization, multi-switch/smooth hotbar controls, smooth scrolling, Windows window styling, fullbright, FPS overlay controls, menu FPS limiting, async pack reload, instant language change and one-click crafting are independently configurable.

Experimental network maintenance uses conservative buffer reuse and optional one-tick flush coalescing. It does not rewrite Minecraft's protocol or packet ordering.

### Deliberate vanilla fallbacks

Helium does not claim a generic off-thread sound renderer, arbitrary world-save execution on worker threads, or a forced-GC "optimizer". Those require safe, measurable implementations rather than toggles that merely sound faster.

## Rentities / GPU entity batching

Rentities is a correctness-sensitive renderer. It does not replace vanilla rendering for every entity or every state.

### Fixed in the current branch

- Armor Stand render-state poses and facing are copied into the GPU instance.
- Entity-facing uses authoritative render-state yaw rather than re-interpolating live entity fields.
- Head pivots come from the baked model pivot table.
- Supported models run Minecraft's own `EntityModel#setAngles(state)` before exact pose capture.
- The instance ABI has ten exact-pose slots.
- Horse/Camel support includes an independent tail slot.
- Quadruped-family tails get an independent pose slot when present.
- Bat wing bases/tips are supported.
- Phantom wing/tail parts are supported, including renderer size scaling.
- Spider/cave-spider front/middle/hind leg aliases are supported.
- Bee body/wings and three independent leg groups are supported.
- Frog arm/leg naming and tongue support are present.
- Ghast body plus nine tentacles fit the ten-slot ABI.
- Creeper fuse-time swell scaling is carried into the GPU instance.
- Baby/base-scale/upside-down states are blocked from GPU batching when the common ABI cannot reproduce their renderer transform exactly.
- Equipment and held items have a dedicated GPU path with conservative fallbacks for glint, trims, dyes and outlines.
- Queue suppression only cancels the exact vanilla body-model submission for the entity being batched; feature renderers are left intact.
- Culling/SSBO failures use a fallback path instead of silently dropping entities.
- Special-pose shader modification is atomic: when expected shader anchors are absent, the unmodified shader is retained.

### Deliberate entity fallbacks

| Entity family | Reason for fallback |
|---|---|
| Blaze | 12 independently animated rod parts. |
| Breeze | Dedicated wind-body/top/mid/bottom and rod pieces. |
| Squid / Glow Squid | 8 independently animated tentacles. |
| Silverfish / Endermite | Segmented model hierarchy. |
| Shulker | Separate base, head and lid state. |
| Strider | Specialized body/legs plus independently named bristles. |
| Sheep | Material/geometry variants are not represented by the common batch material ABI. |
| Warden / Wither / other CPU-specialized rigs | Specialized model/state behavior exceeds the generic ABI. |
| Other dedicated-state families not in the registry | No exact-pose mapping yet, so vanilla remains the safe path. |

The invariant is: **an entity is either visually representable by the GPU ABI or it stays vanilla.**

### Rentities controls

- `entityGpuBatching` — master GPU entity-batching toggle.
- `entityGpuFrustumCulling` — GPU visibility/culling path.
- `rentitiesAsyncRenderPreparationEnabled` — async render preparation.
- `rentitiesAsyncVisibilityEnabled` — async visibility checks.
- `rentitiesAsyncVisibilityRefreshFrames`, `rentitiesAsyncVisibilityMaxAgeFrames`, `rentitiesAsyncVisibilityMaxDistance` — cache tuning.
- `rentitiesEntityBatchWhitelistOnly`, whitelist and blacklist — per-entity safety controls.
- Rentities debug and solid-debug toggles are separate.

The config screen exposes the Rentities toggles under Rendering, while GPU compute controls live under Advanced. Experimental systems remain separately opt-in until runtime validation is complete.

## Verification

The `1.21.11` branch is continuously checked by GitHub Actions. A workflow is only considered verified after `build` completes successfully; an in-progress workflow is not a green result.

The build targets Java 21 bytecode even when CI runs on a newer JDK.

## Building from source

Helium is compiled for **Java 21**.

```bash
git clone https://github.com/samveerone-hue/Helium.git
cd Helium
./gradlew remapJar
```

For a development client:

```bash
./gradlew runClient
```

The remapped jar is written to `build/libs/`.

## Project structure

```text
src/
├── main/resources/          # metadata, mixins and assets
└── client/java/com/helium/
    ├── config/              # config + config screen
    ├── compat/              # Sodium/ModMenu compatibility
    ├── compute/             # optional OpenCL backend
    ├── memory/              # memory pools and allocation helpers
    ├── render/              # rendering, GL state and culling
    ├── rentities/           # GPU entity batching, baking, culling and shaders
    └── mixin/               # targeted Minecraft hooks
```

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| Fabric API | Required | Fabric/Minecraft integration |
| Sodium | Compile-only / integration | Helium configuration and renderer integration |
| Cloth Config | Build dependency | Config UI support |
| ModMenu | Optional | Mod-list integration |

## Contributing

For bug fixes, describe what was broken and why. For renderer changes, verify the vanilla model/state path before adding a new GPU approximation.

## License

MIT — see [LICENSE](LICENSE).
