<div align="center">

# Helium

lightweight client-side performance mod for Minecraft

[![Modrinth](https://img.shields.io/modrinth/dt/heliummc?color=00AF5C&logo=modrinth&label=modrinth)](https://modrinth.com/mod/heliummc)
[![GitHub](https://img.shields.io/github/stars/qborder/Helium?style=flat&logo=github&label=stars)](https://github.com/qborder/Helium)
[![Build](https://img.shields.io/github/actions/workflow/status/qborder/Helium/build.yml?logo=github&label=build)](https://github.com/samveerone-hue/Helium/actions)
[![License](https://img.shields.io/github/license/qborder/Helium?cacheSeconds=36000)](https://github.com/qborder/Helium/blob/HEAD/LICENSE)

**[Download on Modrinth](https://modrinth.com/mod/heliummc)** · **[Report Issues](https://github.com/samveerone-hue/Helium/issues)**

</div>

---

> this is the **development repository** for Helium. if you're just looking to install the mod, grab it from [Modrinth](https://modrinth.com/mod/heliummc). the page there has all the feature descriptions and pretty screenshots and stuff.

---

## Rentities / GPU Entity Batching Fixes

The `1.21.11` branch contains the current Rentities integration and the following rendering fixes:

- **Armor Stand poses fixed.** The GPU instance now receives the exact head, body, left/right arm, and left/right leg `EulerAngle` poses from `ArmorStandEntityRenderState`, so `/data` custom poses are no longer discarded.
- **Armor Stand facing fixed.** Armor Stand yaw now comes from its authoritative render-state `yaw` and uses the same `180 - yaw` convention as the shader path.
- **Entity facing direction fixed.** Living entity rotation is populated from the prepared render state's `bodyYaw` instead of re-interpolating the live entity fields, avoiding the previous double-interpolation/sign mismatch path.
- **Head rotation centre fixed.** The per-instance head pivot is now populated from the baked model's actual pivot table, rather than leaving ordinary entities at an implicit zero pivot.
- **Exact vanilla animation path added.** Biped, quadruped, horse, bird, and creeper entities now use Minecraft's own `EntityModel#setAngles(state)` output for their six primary bones when the model layout is supported. Rentities no longer has to approximate their main walk/head/limb rotations in the shader for those families.
- **Render-state animation inputs improved.** Limb swing, limb amplitude, head rotation, death time, hurt state, water state, sneaking state, and hand-swing progress are taken from the already-prepared 1.21.11 render state instead of being interpolated a second time.
- **Iris compatibility made safe.** Rentities' custom GPU shader path is automatically disabled when Iris is loaded, allowing vanilla rendering instead of attempting to draw entities through an incompatible custom shader path.
- **GPU batching fallback hardened.** Standard SSBO uploads remain the default backend, while indirect/culling failures fall back to the normal ordered instanced path instead of silently dropping entities.
- **Entity error fallback retained.** Failed mesh extraction can use the magenta error renderer while the normal rendering path remains available for recovery.
- **Queue rollback/compaction hardened.** Failed direct-extraction reservations are rolled back safely and cancelled slots are compacted before drawing.

### Remaining Rentities limitations

- **Specialized animations are not all exact yet.** Models with more than six independently animated bones (for example multi-segment insects, arthropods, ghast tentacles, and other specialized rigs) still use their category-specific GPU animation path. The next extension is to feed more than six animated bones without enlarging the common instance ABI.
- **Texture/UV issues remain under investigation.** The mesh consumer preserves Minecraft's source UVs verbatim. Some less-common models can still expose model-specific texture orientation problems, so a blanket UV flip is intentionally not applied.
- **Unscanned/failed entities may show the magenta error cube.** When a mesh cannot be baked or recovered, Rentities uses its visible error fallback. Normally the missing mesh can be rebuilt by returning to a world where the entity is available so the cache can be refreshed.

These changes are intended to keep Rentities visually close to vanilla while retaining the performance benefits of GPU entity batching.

> **Verification status:** GitHub Actions is currently building the latest `1.21.11` animation commits. CI must pass before the changes are considered build-verified.

---

## Building From Source

you'll need **Java 21** installed. that's it. gradle wrapper handles everything else.

```bash
git clone https://github.com/samveerone-hue/Helium.git
cd Helium
```

**build the mod:**
```bash
./gradlew remapJar
```

output jar lands in `build/libs/`. the one *without* `-dev` in the name is the one you want.

**run a dev client** (for testing):
```bash
./gradlew runClient
```

**generate IDE sources** (IntelliJ / Eclipse):
```bash
./gradlew genSources
```

then import as a Gradle project. IntelliJ will figure it out.

---

## Project Structure

```
src/
├── main/resources/          # mod metadata, mixins config, assets
└── client/java/com/helium/
    ├── HeliumClient.java    # entrypoint
    ├── config/              # config loading/saving + YACL screen
    ├── compat/              # sodium config integration, modmenu
    ├── data/                # custom data structures
    ├── memory/              # object pools, buffer pools
    ├── render/              # GL state cache, block entity culling
    ├── rentities/            # GPU entity batching, mesh baking, culling, shaders
    └── mixin/
        ├── math/            # fast math replacements
        ├── render/          # entity/block entity culling, GL state
        ├── particle/        # particle distance culling
        ├── tick/            # client world tick optimizations
        ├── network/         # network buffer optimizations
        └── multiplayer/     # server list pinging, scroll preservation
```

---

## Dependencies

| Dependency | Type | Why |
|---|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Required | you know why |
| [Sodium](https://modrinth.com/mod/sodium) | Required | config UI lives inside sodium's settings |
| [YACL](https://modrinth.com/mod/yacl) | Embedded | powers the ModMenu config screen |
| [ModMenu](https://modrinth.com/mod/modmenu) | Optional | adds the mod toggle in the mod list |

---

## Contributing

PRs welcome. if you're fixing a bug, please describe what was broken and why. if you're adding a feature, open an issue first so we don't waste each other's time.

the codebase uses **Yarn mappings** and targets **Minecraft 1.21.x** with **Fabric Loader 0.16+**.

fair warning: the mixin naming convention uses `helium$` prefix for all injected methods. keep it consistent.

---

## License

[MIT](LICENSE) do whatever you want with it.
