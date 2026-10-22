# Character Texture VRAM Leak Fix [ZombieBuddy]

Source code for the Project Zomboid Build 42 mod
[Character Texture VRAM Leak Fix [ZombieBuddy]](https://steamcommunity.com/sharedfiles/filedetails/?id=3821525282)
(Workshop ID `3821525282`, Mod ID `SmartTextureLeakFix`).

It fixes a vanilla video-memory leak: every time a character's clothing/body texture is
rebuilt (blood, dirt, holes, dye or hue changes - players and zombies alike) the game made
a new combined texture and never gave the old one back. Long sessions fill VRAM, spill into
system RAM and stutter or freeze.

This repository exists so you can read exactly what the Workshop JAR does and rebuild it
yourself to confirm the Workshop file was built from this source.

## What the code does

The whole mod is one source file: [`42/src/smarttextureleakfix/Fix.java`](42/src/smarttextureleakfix/Fix.java)
(about 60 lines).

- Vanilla `zombie.core.textures.SmartTexture.calculate()` does
  `result = TextureCombiner.instance.combine(commands)` without releasing the previous
  `result`. Only `destroy()` ever releases it, so each rebuild of the same SmartTexture
  leaks one GPU texture (up to 16 MB for a 2048x2048 mod texture).
- The mod adds one ZombieBuddy hook at the **start** of `SmartTexture.calculate()`:
  if `result` is not null, it sets the field to null and passes the old texture to
  `TextureCombiner.instance.releaseTexture(...)` - the same call `destroy()` already makes.
  The combine that follows then reuses that texture from the game's own pool.
- It prints `[SmartTextureLeakFix] stale combined textures returned so far: N` to
  `console.txt` at most once a minute.

That is all. Why Java: the leaked texture sits in a Java field (`SmartTexture.result`) that
Lua cannot reach, so a Lua-only fix is not possible.

## What it does NOT do

- **No game files are changed on disk.** ZombieBuddy applies the hook in memory when the
  class is loaded; `projectzomboid.jar` and every other game file stay untouched. Remove the
  mod and the next start is pure vanilla.
- **Nothing is written to your save**, items or characters. No options, no files written at all.
- **No network, file, process or reflection code.** The only imports are ZombieBuddy's
  `Patch` annotation and three game classes (`SmartTexture`, `Texture`, `TextureCombiner`).
  The only output is the console line above.
- Client-side only. The JAR sits in `media/java/client`, so dedicated servers skip it.

Note: this repository covers this mod only. ZombieBuddy itself (the Java mod loader this mod
requires) is a separate project by its own author; see its Workshop page and source for what
the loader does.

## Verify the Workshop JAR

### 1. Look inside it

The JAR is a zip file. It contains exactly:

```
smarttextureleakfix/
smarttextureleakfix/Fix$Calculate.class
smarttextureleakfix/Fix.class
```

No manifest, no other classes, no resources. You can disassemble the classes with the JDK's
`javap -c -p` and compare them with the source.

### 2. Compare the hash

| Version | File | SHA-256 |
|---|---|---|
| 1.0 | `42/media/java/client/SmartTextureLeakFix.jar` | `a836e463a5563ce2d3630cd3edf1d1f65091dd35910a638cb7ac405d90658d27` |

ZombieBuddy shows the JAR's SHA-256 in its approval dialog on the first start. If it is not
the value above, click **Deny**.

You can also hash the subscribed file yourself (Windows PowerShell):

```powershell
Get-FileHash -Algorithm SHA256 "C:\Program Files (x86)\Steam\steamapps\workshop\content\108600\3821525282\mods\SmartTextureLeakFix\42\media\java\client\SmartTextureLeakFix.jar"
```

### 3. Build it yourself

The build is reproducible: no manifest and a fixed entry date, so the JAR bytes depend only
on the compiled classes.

Requirements: Windows, Project Zomboid (Build 42), ZombieBuddy subscribed, and a JDK 21-25
(for example [Eclipse Temurin](https://adoptium.net)). The game's own `jre64` folder works
too if it contains `javac.exe`.

```powershell
git clone https://github.com/OX-nd/SmartTextureLeakFix.git
cd SmartTextureLeakFix
powershell -ExecutionPolicy Bypass -File 42\src\build.ps1
```

Optional parameters: `-Jdk <path>`, `-GameRoot <ProjectZomboid folder>`,
`-ZombieBuddyJar <path to ZombieBuddy.jar>`.

The script compiles `Fix.java` against your own `projectzomboid.jar` and `ZombieBuddy.jar`,
writes `42/media/java/client/SmartTextureLeakFix.jar` and prints its SHA-256.

- With **JDK 25** the hash is exactly the one in the table above (checked with Eclipse
  Temurin 25.0.2 and the GraalVM 25.0.4 bundled with the game).
- JDK 21-24 produce older class-file versions, so the hash differs. That JAR is still your
  own build of this source; you can use it instead of the Workshop one (copy it over the
  Workshop JAR and approve the hash it printed).

Manual build without the script (any OS, JDK 25; use `:` instead of `;` on Linux/macOS):

```
javac -nowarn -encoding UTF-8 -classpath "projectzomboid.jar;ZombieBuddy.jar" -d classes 42/src/smarttextureleakfix/Fix.java
jar --create --no-manifest --date 2026-10-17T00:00:00Z --file SmartTextureLeakFix.jar -C classes smarttextureleakfix
```

### 4. Run the test (optional)

[`tests/ApplyCheck.java`](tests/ApplyCheck.java) loads the JAR with the real ZombieBuddy
agent, creates a hidden OpenGL window and calls the real, patched `SmartTexture.calculate()`:
the old texture must end up in the combiner pool and the field must be cleared.

```
tests\test_apply.bat [path\to\SmartTextureLeakFix.jar]
```

Set `GAME` / `ZB` environment variables if your game or ZombieBuddy paths differ.
Expected last line: `ALL OK`.

## Layout

```
42/mod.info                         mod metadata (same as on the Workshop)
42/poster.png                       in-game mod list image
42/src/build.ps1                    reproducible build script
42/src/smarttextureleakfix/Fix.java the entire mod
tests/ApplyCheck.java               patch-applies-and-works test
tests/test_apply.bat                runs the test
CHANGELOG.txt
```

## Compatibility

Build 42 (checked against 42.21 classes, `versionMin=42.20`). Requires ZombieBuddy 2.3.2 or
newer. Checked together with PZ_Optimization and Project Viewpoint.

## License

MIT - see [LICENSE](LICENSE). Project Zomboid and ZombieBuddy are not included and belong to their respective owners.
