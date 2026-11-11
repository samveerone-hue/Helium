<div align="center">

# Helium

A client-side performance mod for Minecraft, with optional visual features.

Helium requires Java 25 for Minecraft 1.21.11. Sodium is an optional renderer and settings integration.

<a href="https://modrinth.com/mod/heliummc"><img alt="modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg"></a>
<a href="https://github.com/qborder/Helium"><img alt="github" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>
<a href="https://fabricmc.net/"><img alt="fabric" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg"></a>

</div>

<br>

## Performance measurements

No comparative performance percentages are published for this branch yet. The repeatable scenarios, capture format, and report tool are documented in the [benchmark protocol](https://github.com/samveerone-hue/Helium/blob/1.21.11/BENCHMARKING.md).

<br>

## ⚡ Features

**Distance Culling**
Skip rendering stuff that's too far away. Entities, block entities (chests, signs, etc), and particles all get culled based on distance. You set the range per type.

**Faster Math**
Replaces Minecraft's trig functions with lookup tables and faster approximations. Runs throughout the entire renderer every frame.

**GL State Caching**
Tracks what OpenGL state is already set and skips redundant calls. Less driver overhead, smoother frames.

**Memory Pooling**
Reuses objects instead of creating new ones. Less garbage collection, fewer stutters.

**Server List Speed**
Pings multiple servers concurrently without running network work on the client thread.

**Scroll Position**
When you refresh the server list, it remembers where you scrolled. Small thing but its nice.

<br>

## 📦 Installation

1. Get [Fabric Loader](https://fabricmc.net/) 0.16.0 or newer
2. Get [Fabric API](https://modrinth.com/mod/fabric-api)
3. Get [Cloth Config](https://modrinth.com/mod/cloth-config) 15.0.0 or newer
4. Optionally install [Sodium](https://modrinth.com/mod/sodium) 0.8.14 for renderer/settings integration
5. Drop Helium in your mods folder
6. Launch the game

Settings are available through Helium's config screen; Sodium adds its video-options integration when installed.

Optional: grab [ModMenu](https://modrinth.com/mod/modmenu) if you want a quick on/off toggle

<br>

## 🤝 Works With

| Mod | Status |
|-----|--------|
| **Sodium** | Optional. Adds renderer and settings integration |
| **Lithium** | Works great. Lithium does game logic, Helium does rendering |
| **Iris** | Works. Shader compatibility is fine |
| **ImmediatelyFast** | Works. GL cache auto disables to avoid conflicts |
| **ViaFabricPlus** | Works. No protocol issues |

<br>

## ⚠️ Notes

**Android / PojavLauncher**: Not supported. Helium needs desktop OpenGL, not OpenGL ES. The mod will detect Android and disable itself automatically.

**Minecraft version**: 1.21.11

**Java**: 25 or newer

<br>

## 📝 License

[MIT License](https://github.com/qborder/Helium/blob/HEAD/LICENSE). Do whatever you want with it.
