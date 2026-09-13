# Helium

Helium is a lightweight client-side performance mod for Minecraft 1.21.11.

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
- `rentitiesAsyncVisibilityEnabled` — cached synchronous visibility prefilter.
- `rentitiesAsyncVisibilityRefreshFrames`, `rentitiesAsyncVisibilityMaxAgeFrames`, `rentitiesAsyncVisibilityMaxDistance` — cache tuning for the render-thread distance decision.
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