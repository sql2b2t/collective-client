# Collective Client

Client for Minecraft 1.21.4.

# Building

```
./gradlew build
```

or for Windows:

```
.\gradlew build
```

- Build artifact can be found in build/libs/

Fabric Loader and Fabric API are required. Baritone Meteor for Minecraft 1.21.4 (pathfinding) and ViaFabricPlus (other protocol versions) are optional and are not bundled. Development runs include both integrations.

# Features

- Modules, HUD widgets and commands (prefix `.`).
- Addon system: load `.jar` addons from `collective/addons/` (`.addon` command).
- Kotlin script system: `.kts` scripts in `collective/scripts/` (`.script` command).
- Shared IRC channel between users (IRC module, `.irc` command).
