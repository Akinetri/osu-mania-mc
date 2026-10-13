# osu!mania-mc

osu!mania inside Minecraft 26.2, built on [Minestom](https://minestom.net). Join with a vanilla client, no mods required.

## Running

Requires Java 25+ and (recommended) `ffmpeg` for mp3 → ogg conversion.

```sh
./gradlew run            # working directory is run/
./gradlew build          # build/libs/osu-mania-mc-all.jar
```

Put beatmaps in `run/songs/`: either `.osz` files or extracted folders containing `.osu` files.
Only mania maps (Mode: 3) from 1K to 7K are supported. A built-in 4K demo map works without audio.

Server settings live in `run/config.json` (created on first launch). To play over a network,
set `packPublicUrl` to the external address of the HTTP server the client downloads the resource pack (with the music) from.

## How to play

The camera is fixed: the player is in spectator mode, looking through the "eyes" of an invisible entity in front of the screen,
and the mouse does not rotate it. All controls use the movement keys, since those are the only keys for which the client
sends the server both press and release events. It's convenient to rebind them in Options → Controls, for example to D F J K:

| Keys | Bindings (left to right)                                 |
|------|----------------------------------------------------------|
| 4K   | left, back, forward, right                               |
| 5K   | left, back, jump, forward, right                         |
| 7K   | sneak, left, back, jump, forward, right, sprint          |

In the menu: forward/back selects a map (hold to scroll), jump starts it, sneak switches to the next setting,
left/right changes it (scroll speed, offset, hitsounds, UI scale), sprint picks a random map.
The scale (80–200%, default 140%) is the camera's distance from the screen; how much fits in view depends on your FOV.
On-screen hints show the keys as the player has them bound. The full list of layouts is available via `/keys`.

Commands: `/menu`, `/play <number>`, `/speed <ms>`, `/offset <ms>`, `/quit`.

Player settings and high scores are stored in `run/players.json`.

## Visuals

All visuals come from a resource pack that the server builds at startup. Images are drawn in code (`pack/Skin`)
and text uses a built-in pixel font (`pack/PixelFont`), so no system fonts are needed.

- Notes, long notes, keys, column highlights, hit bursts, judgments (PERFECT…MISS), SS–D ranks.
- Song select menu and results screen rendered right in the world; map background from the `.osz` (cropped to 16:9),
  song preview from `PreviewTime` (requires ffmpeg).
- Score, combo and accuracy digits use a separate bitmap font.
- The vanilla HUD (hotbar, hearts, hunger, XP, crosshair) is hidden.

Each sprite is a flat item model (`assets/osumania/items/…`) shown by an `item_display`.

## How it works

- `beatmap/`: `.osu` parser, `.osz` loading, audio conversion, previews and backgrounds, demo map.
- `pack/`: skin and resource pack generation, plus the built-in HTTP server that serves the pack.
- `ui/`: `Canvas` (sprites and text on an in-world "screen"), menu and results.
- `game/Playfield`: the playfield. Notes are moved by client-side transformation interpolation: the server sets a single target
  for the whole fall time, so movement is smooth.
- `game/GameSession`: timing, judgments (osu!mania stable hit windows based on OD), long notes, scoring.
- The server runs at 50 TPS by default (`serverTps`) so inputs are processed more precisely.

## Tests

`./gradlew test` runs an integration test in Minestom's test environment: a fake player goes through
menu → gameplay (hitting notes on time) → results → menu.
