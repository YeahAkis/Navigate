# Navigate

Shows a player the way to a set of coordinates. It finds a walkable path with A*, draws it as red dust particles, and can optionally push the player along it. Releases are on [Modrinth](https://modrinth.com/project/3t39uukw).

Works on Paper 1.21 up to 1.21.11, and on Folia. Needs Java 21.

## How it works

When a navigation starts, the plugin copies the loaded chunks around the player and the target on the main thread, then runs the search on another thread against that copy. Chunks that are not loaded count as walls, so searching never loads anything. The path is searched again when the player enters a new chunk, leaves the path, or when the last search found nothing and a few seconds have passed.

Searches stay within 96 blocks of the player. If the target is farther than that you get a path to the closest reachable point, and it extends as you walk. The search uses a weighted heuristic, so paths can be a little longer than the shortest one in return for being quick.

On Folia the search only sees chunks owned by the player's region, so a path can stop short at a region border.

Safe mode (on by default) only allows drops of up to 3 blocks and avoids berry bushes, cobwebs, campfires, magma and the like. Turn it off to allow any fall that ends on solid ground. Lava is never walked through or over in either mode.

If the target can not be reached, you get a path to the closest point that can. Wooden and copper doors, trapdoors and fence gates count as passable, iron ones do not.

## Commands

| Command | What it does |
| --- | --- |
| `/navigate start <x y z> [options]` | Start navigating |
| `/navigate stop` | Stop navigating |
| `/navigate startfor <players> <x y z> [options]` | Start navigating other players |
| `/navigate stopfor <players>` | Stop navigating other players |
| `/navigate reload` | Reload the config and language files |
| `/navigate help` | List the commands you can use |

Coordinates take `~` and `^` like vanilla commands, and `<players>` takes selectors like `@a`. Options can be given in any order:

- `safe` or `unsafe` (`true` and `false` still work), safe is the default
- `follow` to be moved along the path
- a comma separated list of blocks, and when it is set the path only walks on those blocks

```
/navigate start 120 64 -300
/navigate start ~ ~ ~200 unsafe
/navigate start 120 64 -300 follow
/navigate start 120 64 -300 follow stone,grass_block
/navigate startfor @a[distance=..10] 0 80 0 follow
```

## Follow

With `follow` the player is moved along the path using velocity, so they walk, jump up single blocks, climb ladders and vines, and swim. The view turns towards where they are going and levels out, whatever angle they started at. Doors, trapdoors and fence gates on the path are opened as the player gets close and closed again once they are past. Sneaking pauses it. If the player gets pushed off the path or stops making progress, the path is searched again.

`follow.speed` in the config is how far the player moves per tick, 0.22 is about walking speed. `follow.open-doors` and `follow.close-doors` turn the door handling off.

Anti cheat plugins see this as server velocity, but some may still flag it. Exempt your players if that happens.

## Config

| Key | What it does |
| --- | --- |
| `particles.enabled` | Show the path as particles |
| `particles.type` | Particle name, for example `dust` or `flame`. Particles that need extra data fall back to `dust` |
| `particles.color`, `particles.size` | Hex color and size, only used by `dust` |
| `particles.amount` | Particles per path block |
| `particles.range` | Path blocks farther than this from the player are not drawn |
| `particles.interval` | Ticks between redraws, also how often the path is checked |
| `messages.arrival` | Tell the player when they reach the destination |
| `messages.notify-target` | Tell a player when someone else starts or stops their navigation |
| `language`, `per-player-language` | See Languages |
| `follow.speed`, `follow.open-doors`, `follow.close-doors` | See Follow |

Settings are read when a navigation starts, so run `/navigate reload` and start a new one to see changes.

## Permissions

| Permission | What it does |
| --- | --- |
| `navigate.use` | Use `/navigate` |
| `navigate.follow` | Use the `follow` option on yourself |
| `navigate.use.others` | Use `startfor` and `stopfor` |
| `navigate.use.neofetch` | Use `/navigate neofetch` |
| `navigate.reload` | Use `/navigate reload` |

## Languages

Messages live in `plugins/Navigate/lang`. English, German and LOLCAT (`lol_us`) are included. Players get the language of their game client when there is a file for it, otherwise `language` from the config is used. Set `per-player-language` to `false` to always use the configured one. Edit the file or add your own, a file called `it_it.yml` is picked up for Italian clients, and anything missing falls back to the English text built into the jar. Run `/navigate reload` after changing them.

## PlaceholderAPI

With PlaceholderAPI installed there is a `navigate` expansion: `direction`, `yaw`, `pitch`, `distance2d`, `distance3d`, `x`, `y`, `z` and `world`, for example `%navigate_distance2d%`.

## API

`Navigate.getInstance().getNavigationService()` starts, stops and lists navigations. The `-api` jar has only the public classes, so you can compile against it.

## Building

```
./gradlew build
```

The jars end up in `build/libs`. `./gradlew publishPlugin` uploads to Modrinth if `modrinthToken` is set, and does a dry run otherwise.

## License

See `LICENSE`.
