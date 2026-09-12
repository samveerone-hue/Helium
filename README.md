# Helium

lightweight client-side performance mod for Minecraft

[![Modrinth](https://img.shields.io/modrinth/dt/heliummc?color=00AF5C&logo=modrinth&label=modrinth)](https://modrinth.com/mod/heliummc) · [Report Issues](https://github.com/samveerone-hue/Helium/issues)

> this is the **development repository** for Helium. if you're just looking to install the mod, grab it from [Modrinth](https://modrinth.com/mod/heliummc).

---

## Development feature audit — `1.21.11`

This section is the current engineering status rather than marketing copy. It records what each major Helium feature has already solved, and what problems are still open.

### Rendering and culling

| Feature | Solved / working | New problems / remaining |
|---|---|---|
| Entity culling | Distance-based entity culling is configurable and remains the main cheap visibility path. | Complex render-state entities still need per-entity validation when routed through Rentities. |
| Block-entity culling | Distance/visibility culling is configurable. | Modded/custom block entities are the main compatibility surface. |
| Particle culling | Distance culling is independent from particle limits. | High-volume and unusual particle effects still need stress testing. |
| Particle limiting | Global particle cap prevents runaway particle counts. | Aggressive caps can hide effects users expect to see. |
| Particle priority | Important particles can be preserved ahead of low-value particles. | Rules are heuristic rather than a semantic guarantee for every particle type. |
| Particle batching | Optional batching path exists. | Broad particle-pack validation remains. |
| Particle LOD | Optional distance reduction exists. | Far-distance effects can still look more reduced than vanilla. |
| Leaf/sign/rain/beacon/painting/item-frame culling | Individually configurable so a troublesome effect can be disabled without losing the rest. | Custom renderers, resource packs, and unusual camera cases remain compatibility targets. |
| Item-frame LOD | Optional long-range LOD exists. | Readability and transition distance still need tuning. |
| Animation throttling | Configurable CPU-saving path. | Some animated models visibly update less often by design. |
| Render pipeline toggles | Fast math, GL state caching, fast animations, enum caching, accelerated text, shader-uniform caching and model caching are individually switchable. | Interactions with external renderers/shader mods still need continued testing. |

### Engine, memory, threading, and world loading

| Feature | Solved / working | New problems / remaining |
|---|---|---|
| Memory optimizations | Pooling/reduced allocations/native-memory options are separated into toggles. | Profiling is still needed to catch the next real allocation hotspots. |
| Thread optimizations | Async/background work is configurable. | Contention and mod interaction vary by CPU topology. |
| Fast startup | Optional startup path is present. | Benefit depends on modpack size/workload. |
| Fast world loading | Dedicated optimizer exists. | Large modpacks still need chunk-generation/load-spike validation. |
| Reduced allocations | Enabled by default where Helium can safely avoid temporaries. | Remaining hot allocations are increasingly outside Helium's optimized paths. |
| Network optimizations | Optional network path exists. | Server/proxy timing must remain conservative. |
| Object deduplication | Enabled by default. | Memory win depends on object lifetime and workload shape. |
| Idle pause/FPS limiting | Optional inactive-client throttling exists. | Background automation users may not want the reduced FPS. |
| Async lighting / packet batching | Optional. | Compatibility and latency behavior still need broader testing. |
| Native memory | Configurable native pool. | Oversized pools waste memory without improving performance. |
| Model cache | Optional, size-limited cache exists. | Cache invalidation/resource-pack reload correctness remains important. |

### Math, GPU, display, and low-level OpenGL

| Feature | Solved / working | New problems / remaining |
|---|---|---|
| Fast math / SIMD / JOML fast math | Separate opt-ins prevent forcing every numerical optimization on users. | Some workloads gain little; numerical edge cases still need validation. |
| Fast random | Explicit opt-in rather than silently forced. | Alternate RNG still needs workload-specific performance/statistical validation. |
| GPU compute | Optional OpenCL line-of-sight/pathfinding backend exists. | Hardware-dependent; safe fallback remains mandatory. |
| Adaptive/display sync | Configurable synchronization path exists. | Driver-specific behavior differs. |
| Temporal reprojection | Optional and isolated. | Visual correctness is shader/workload dependent. |
| Fast framebuffer blit / DSA / depth renderbuffer | Separate low-level controls exist. | Highest compatibility risk for unusual renderers and external shader stacks. |
| OpenGL cleanup / screenshot leak fix / error suppression | Dedicated cleanup paths address known state/resource waste. | Driver-specific behavior remains a test surface. |
| NVIDIA / AMD / Intel optimizations | Vendor-specific toggles are isolated. | Each vendor path still needs hardware coverage before being considered universal. |
| Reflex | Configurable enable/offset/debug controls exist. | End-to-end latency varies by driver and display. |

### Networking, menus, hotbar, and quality-of-life

| Feature | Solved / working | New problems / remaining |
|---|---|---|
| Fast server/IP ping | Server-list and IP ping controls are separate. | Server-specific response timing varies. |
| Preserve scroll on refresh | Scroll position survives server-list refresh. | Inserted/removed rows can still make the preserved position feel different. |
| Direct-connect preview | Optional. | Unusual server-address/list states still need UI regression testing. |
| Hotbar optimizer | Explicitly opt-in. | Per-server instant-switch assumptions can interact with server-side combat timing. |
| Hotbar multi-switch / smooth hotbar | Independent controls exist. | Client/server timing remains the limiting factor. |
| Smooth scrolling | Configurable. | Feel depends on display refresh and personal preference. |
| Window style/material/corners | Windows-specific and platform-gated. | Window-manager behavior is platform dependent. |
| Fullbright | Optional strength control. | Resource packs/shaders can intentionally change the look. |
| FPS overlay | Optional FPS/min-max/avg/memory/particles/coordinates/biome display. | Every extra statistic has a small update/draw cost. |
| Menu FPS limit | Configurable. | Lower caps save menu power but do not improve gameplay rendering. |
| Async pack reload / instant language change / one-click crafting | Optional QoL paths are present. | Resource-pack, language, and recipe/UI edge cases remain the regression surface. |

---

## Rentities / GPU entity batching audit

Rentities is treated as a correctness-sensitive renderer, not a blanket replacement for vanilla entity rendering. Unsupported or ambiguous states are allowed to fall back to vanilla.

### Solved problems

- **Armor Stand poses fixed.** Exact head/body/arm/leg render-state poses are copied into the GPU instance, so custom `/data` poses are no longer discarded.
- **Armor Stand facing fixed.** Authoritative render-state yaw is used with the shader's established convention.
- **Entity facing fixed.** Prepared `bodyYaw` is used instead of re-interpolating live entity fields a second time.
- **Head pivot fixed.** The per-instance head pivot now comes from the baked model pivot table.
- **Vanilla model-angle capture added.** Supported entities call Minecraft's own `EntityModel#setAngles(state)` and copy the resulting part rotations into GPU instance data.
- **Extended exact-pose ABI added.** Ten exact pose slots now exist instead of only six.
- **Horse/camel improved.** Horse-family parts can include the extra tail pose; Camel uses its real render-state/model animation path rather than a guessed generic walk.
- **Spider/cave-spider improved.** Distinct front/middle/hind leg names are mapped explicitly.
- **Bat wing-tip support added.** Separate wing-base/tip parts can be captured.
- **Ghast capacity added.** Body plus nine tentacles fit the ten-pose ABI.
- **Frog naming compatibility added.** Vanilla arm/leg names are aliased for pose extraction.
- **Baby/scale/flip safety gate added.** States that change the common transform in unsupported ways fall back instead of rendering incorrectly.
- **Equipment capture improved.** Held items/equipment use dedicated GPU batches where supported, with glint/trims/dyes/outlines and similar material-heavy cases allowed to fall back.
- **Iris safety added.** Rentities disables its custom entity shader path when Iris is loaded instead of forcing an incompatible shader stack.
- **Batching/culling fallback hardened.** SSBO/indirect/culling failures fall back to a normal instanced path instead of silently dropping entities.
- **Queue rollback/compaction hardened.** Failed extraction reservations are rolled back and cancelled slots are compacted before draw submission.

### 1.21.11 special render-state audit

Minecraft 1.21.11 has dedicated `LivingEntityRenderState` subclasses for many specialized entities, including Armadillo, Bat, Bee, Camel, Chicken, Creeper, Frog, Ghast, Goat, Happy Ghast, Guardian, Hoglin, Horse, Llama, Mooshroom, Nautilus, Parrot, Phantom, Pig, Polar Bear, Pufferfish, Rabbit, Ravager, Salmon, Sheep, Shulker, Slime, Sniffer, Snow Golem, Squid, Strider, Tropical Fish, Turtle, Warden and Wither, among others. citeturn740277search0

| Entity/state family | Current status | Problem found |
|---|---|---|
| Biped families | **Improved / exact pose where layout matches** | Generic head/body/arm/leg mapping is good for standard biped models. citeturn603413search4 |
| Camel | **Improved / exact pose path** | Dedicated state/model animations are captured from `setAngles`. citeturn132843search3 |
| Horse / living horse | **Improved / exact pose path** | Extra tail/leg parts are carried by the extended pose map. |
| Bat | **Improved / wing-tip support** | Separate wing-base/tip parts required explicit support. |
| Bee | **Improved / specialized model support** | Separate body, wings, and grouped legs are not a generic quadruped layout. citeturn132843search2 |
| Spider / cave spider | **Improved / specialized leg support** | Multiple named leg groups need explicit mapping. citeturn603413search7 |
| Frog | **Improved / naming compatibility** | Arm/leg naming differs from the generic quadruped aliases. |
| Ghast | **Improved / ten-pose capacity** | Body plus nine tentacles fits the new exact-pose ABI. |
| Creeper | **Partial / conservative** | Limb/body pose is capturable, but swell scaling is not fully encoded in the common GPU instance state. |
| Sheep | **Fallback / not fully fixed** | Sheep has extra state such as sheared/rainbow behavior that is not represented by a plain quadruped pose. |
| Warden / Wither | **Fallback / not fully fixed** | Their specialized rigs/states exceed current generic exact-pose coverage. |
| **Phantom** | **NOT FIXED** | Phantom has its own `PhantomEntityRenderState` with `wingFlapProgress` and `size`, while `PhantomEntityModel` uses `leftWingBase`, `leftWingTip`, `rightWingBase`, `rightWingTip`, `tailBase`, and `tailTip`. The current generic floating extractor does not map that layout. citeturn132843search1turn568007view0 |
| Guardian | **Not in current Rentities batch registry** | Dedicated state plus beam/spike behavior makes generic batching unsafe for now. citeturn740277search0 |
| Happy Ghast | **Not in current Rentities batch registry** | Dedicated 1.21.11 state/model family still needs its own exact part mapping. citeturn740277search0turn132843search0 |
| Nautilus | **Not in current Rentities batch registry** | Dedicated state/model family needs a dedicated mapping rather than a generic category. citeturn740277search0turn132843search0 |
| Turtle | **Not yet fully audited for exact GPU pose** | Dedicated state/model family should receive a specific mapping before being marked solved. citeturn740277search0turn132843search0 |
| Remaining dedicated-state entities | **Case-by-case** | A dedicated render-state class does not guarantee that a generic six/ten-bone category is visually equivalent. |

### Current Rentities problems

1. **Phantom is the clearest unresolved entity.** It is registered for batching, but there is no Phantom-specific pose map yet, so its vanilla wing-flap/tail animation is not proven equivalent.
2. **Exact pose remains model-layout dependent.** State classes can exist with different or additional `ModelPart` hierarchies.
3. **Renderer transforms are not fully represented.** Baby transforms, arbitrary scale changes, flip-upside-down, riding/attachment transforms, and other renderer-specific transforms are not all in the common ABI.
4. **Material/state variants still force safe fallback.** Glint, trims, dyes, outlines and other feature-renderer/material cases are not equivalent to one static material draw.
5. **Texture/UV issues remain under investigation.** Source UVs are preserved rather than applying a blanket flip.
6. **Failed mesh extraction can still show the magenta error fallback.** It is intentionally visible so bad/unknown meshes cannot silently disappear.

### Rentities controls

- `entityGpuBatching` — master GPU entity batching toggle.
- `entityGpuFrustumCulling` — GPU frustum/culling path.
- `rentitiesAsyncRenderPreparationEnabled` — asynchronous render preparation.
- `rentitiesAsyncVisibilityEnabled` — asynchronous visibility checks.
- `rentitiesAsyncVisibilityRefreshFrames`, `rentitiesAsyncVisibilityMaxAgeFrames`, `rentitiesAsyncVisibilityMaxDistance` — visibility-cache tuning.
- `rentitiesEntityBatchWhitelistOnly`, whitelist, blacklist — per-entity safety/compatibility controls.
- Rentities debug and solid-debug toggles are available separately.

---

## Verification status

The latest GitHub Actions run for `1.21.11` is **failing**, so the branch is not currently build-verified. Run **#414** at commit `a89cdbe26d82fcfd9671b5bee3b7caa563318e68` completed with a failed Gradle build. fileciteturn37file0

The README therefore separates **implemented/audited** work from **CI-verified** work. The Phantom result above is a Minecraft 1.21.11 model/state audit, not a claim that Phantom is already fixed in a green build.

---

## Building From Source

you'll need **Java 21** installed. gradle wrapper handles everything else.

```bash
git clone https://github.com/samveerone-hue/Helium.git
cd Helium
./gradlew remapJar
```

The remapped jar lands in `build/libs/`. For a dev client use `./gradlew runClient`.

---

## Project Structure

```text
src/
├── main/resources/          # metadata, mixins, assets
└── client/java/com/helium/
    ├── config/              # config + config screen
    ├── compat/              # Sodium/ModMenu compatibility
    ├── compute/             # optional OpenCL compute backend
    ├── memory/              # pools and allocation helpers
    ├── render/              # rendering, GL state, culling
    ├── rentities/            # GPU entity batching, baking, culling, shaders
    └── mixin/               # targeted Minecraft hooks
```

---

## Dependencies

| Dependency | Type | Why |
|---|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Required | Minecraft/Fabric integration |
| [Sodium](https://modrinth.com/mod/sodium) | Required | Helium configuration integration |
| [YACL](https://modrinth.com/mod/yacl) | Embedded | Config screen support |
| [ModMenu](https://modrinth.com/mod/modmenu) | Optional | Mod list integration |

---

## Contributing

PRs welcome. For bug fixes, describe what was broken and why. For new features, open an issue first so compatibility and architecture can be checked before implementation.

The codebase uses **Yarn mappings** and targets **Minecraft 1.21.x** with **Fabric Loader 0.16+**.

---

## License

[MIT](LICENSE)
