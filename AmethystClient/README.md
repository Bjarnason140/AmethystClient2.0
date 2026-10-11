# AmethystClient (Fabric, Minecraft 26.2)

Client-side only. No mixins, no packets sent.

## Features
- **Block ESP** - outlines chests, trapped chests, ender chests, barrels, shulker boxes, hoppers, spawners,
  droppers and dispensers through walls. Every type has its own on/off switch and **its own color**
  (pick in the menu; saved to `config/amethystclient.json`).
- **Growth Finder** - scans loaded chunks for fully grown blocks and flags the chunk (tall outline + HUD list + chat ping):
  - Kelp / weeping & twisting vines / cave vines at max age (25)
  - Pointed dripstone with a long column (outside dripstone-cave biomes by default)
  - Amethyst clusters (min-count per chunk so natural geodes are less noisy)
  - Mature crops (off by default - villages cause false positives)
- **Menu** - deep-purple UI, press **Right Shift** (rebindable). Two more unbound keys toggle ESP / Growth Finder.

## Build
1. Install JDK 25.
2. Create the Gradle wrapper once: `gradle wrapper --gradle-version 9.5.1` (or copy `gradlew`, `gradlew.bat`
   and `gradle/wrapper/gradle-wrapper.jar` from the official Fabric example mod).
3. `./gradlew build` -> `build/libs/amethystclient-1.0.0.jar`
4. Put the jar and Fabric API (0.161.0+26.2 or newer for 26.2) in your `mods` folder. Fabric Loader 0.19.3+.

## Heuristics worth knowing
Growth detection is a heuristic. "Fully grown" only means a player probably kept the chunk loaded; natural
generation can also produce max-age vines/kelp, geodes full of clusters, etc. Tune the per-type minimum counts
in the Growth tab (Shift-click +/- steps by 5).

## If compilation fails
26.2 renamed a lot of API and I could not compile against the real jars when writing this. The calls most likely
to need a one-line tweak: `getChunkNow` (chunk lookup), `level.getMinY()/getHeight()`, `section.maybeHas`,
`Mth.hsvToRgb`, `MouseButtonEvent.x()/y()/button()`, `KeyMapping.matches(KeyEvent)`.
Use `javap -p` on the deobfuscated jar in `~/.gradle/caches/fabric-loom/` to check names.

Many servers prohibit ESP-style mods - check the rules before using it online.
