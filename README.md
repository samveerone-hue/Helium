# Helium

Lightweight client-side performance mod for Minecraft.

> This is the development repository for Helium. For normal installation, use the published Modrinth build.

## Development status — `1.21.11`

This README is an engineering audit of the branch. Features that cannot be represented correctly by a generic optimization path are deliberately allowed to fall back to vanilla rendering.

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
| Render-pipeline optimizations | Working | Fast math, GL-state caching, fast animations, enum caching, accelerated text, uniform caching and model caching are independently configurable. |

### Engine, memory, threading and world loading

| Feature | Status | Notes |
|---|---|---|
| Memory optimizations | Working | Pooling and allocation reductions are configurable. |
| Thread optimizations | Working | Async/background work is configurable. |
| Fast startup | Working | Optional. |
| Fast world loading | Working | Optional. |
| Reduced allocations | Working | Enabled by default where Helium can safely reduce temporaries. |
| Network optimizations | Working | Optional and conservative around server timing. |
| Object deduplication | Working | Enabled by default. |
| Idle pause/FPS limiting | Working | Optional. |
| Async lighting / packet batching | Working | Optional. |
| Native memory | Working | Configurable pool. |
| Model cache | Working | Optional and size-limited. |

### Math, GPU and OpenGL

| Feature | Status | Notes |
|---|---|---|
| Fast math / SIMD / JOML math | Working | Separate opt-ins. |
| Fast random | Working | Explicit opt-in. |
| GPU compute | Working | Optional OpenCL backend with fallback. |
| Adaptive/display sync | Working | Driver-dependent. |
| Temporal reprojection | Working | Optional; visual behavior is shader-dependent. |
| Framebuffer blit / DSA / renderbuffer paths | Working | Compatibility-sensitive low-level options. |
| OpenGL cleanup / screenshot leak fixes | Working | Dedicated resource/state cleanup paths. |
| NVIDIA / AMD / Intel optimizations | Working | Vendor-specific toggles are isolated. |
| Reflex | Working | Configurable enable/offset/debug controls. |

### Networking, menus, hotbar and QoL

Fast server/IP ping, refresh-scroll preservation, direct-connect preview, opt-in hotbar optimization, multi-switch/smooth hotbar controls, smooth scrolling, Windows window styling, fullbright, FPS overlay controls, menu FPS limiting, async pack reload, instant language change and one-click crafting are all independently configurable.

## Rentities / GPU entity batching audit

Rentities is a correctness-sensitive renderer. It does not replace vanilla rendering for every entity or every state.

### Fixed in the current branch

- Armor Stand render-state poses and facing are copied into the GPU instance.
- Entity-facing uses authoritative render-state yaw rather than re-interpolating live entity fields.
- Head pivots come from the baked model pivot table.
- Supported models run Minecraft's own `EntityModel#setAngles(state)` before exact pose capture.
- The instance ABI now has ten exact-pose slots instead of six.
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
- Equipment and held items have a dedicated GPU path with conservative fallbacks for material-heavy feature cases such as glint, trims, dyes and outlines.
- Queue suppression only cancels the exact vanilla body-model submission for the entity being batched; feature renderers are left intact.
- Culling/SSBO failures use a fallback path instead of silently dropping entities.
- Special-pose shader modification is atomic: when expected shader anchors are absent, the unmodified shader is retained.

### Deliberate vanilla fallbacks

These are not bugs. Their current model hierarchies do not fit the generic Rentities bone ABI without losing visible animation state:

| Entity family | Reason for fallback |
|---|---|
| Blaze | 12 independently animated rod parts. |
| Breeze | Dedicated wind-body/top/mid/bottom and rod pieces. |
| Squid / Glow Squid | 8 independently animated tentacles. |
| Silverfish / Endermite | Segmented model hierarchy. |
| Shulker | Separate base, head and lid state. |
| Strider | Specialized body/legs plus six independently named bristle parts. |
| Sheep | Sheared/rainbow material/geometry state is not represented by the common batch material ABI. |
| Warden / Wither / other CPU-specialized rigs | Specialized model/state behavior exceeds the generic ten-bone path. |
| Guardian, Happy Ghast, Nautilus, Turtle and other dedicated-state families not in the registry | No dedicated exact-pose mapping yet, so vanilla remains the safe path. |

The important invariant is: **an entity is either visually representable by the GPU ABI or it stays vanilla.**

### Rentities controls

- `entityGpuBatching` — master GPU entity-batching toggle.
- `entityGpuFrustumCulling` — GPU visibility/culling path.
- `rentitiesAsyncRenderPreparationEnabled` — async render preparation.
- `rentitiesAsyncVisibilityEnabled` — async visibility checks.
- `rentitiesAsyncVisibilityRefreshFrames`, `rentitiesAsyncVisibilityMaxAgeFrames`, `rentitiesAsyncVisibilityMaxDistance` — cache tuning.
- `rentitiesEntityBatchWhitelistOnly`, whitelist and blacklist — per-entity safety controls.
- Rentities debug and solid-debug toggles are separate.

The config screen exposes the Rentities toggles under the Rendering page, while GPU compute controls live under Advanced.

## Verification

A previous full build on the special-state work completed successfully, including artifact upload, at run **#414 successor run #418** on an intermediate head. The current branch has continued changing after that build.

The latest branch run at the time of this audit is **#432**, commit `d89ba20e98684ffeade70bad9b80ed9722111668`, and is **still running**. The immediately previous run **#430** on the earlier pose/tail state also completed successfully.

Do not treat an in-progress GitHub Actions run as a green verification result.

## Building from source

Helium is compiled for **Java 21**. The GitHub Actions runner may use a newer JDK, but the Gradle build explicitly targets Java 21 bytecode.

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
| YACL / config library support | Build dependency | Config UI support |
| ModMenu | Optional | Mod-list integration |

## Contributing

For bug fixes, describe what was broken and why. For renderer changes, verify the vanilla model/state path before adding a new GPU approximation.

## License

MIT — see [LICENSE](LICENSE).
