# Navigate

Shows a player the way to a set of coordinates. It finds a walkable path with A*, draws it as red dust particles, and can optionally push the player along it. Releases are on [Modrinth](https://modrinth.com/project/3t39uukw).

Works on Paper 1.21 up to 1.21.11. Needs Java 21.

## How it works

When a navigation starts, the plugin copies the loaded chunks around the player and the target on the main thread, then runs the search on another thread against that copy. Chunks that are not loaded count as walls, so searching never loads anything. The path is searched again when the player enters a new chunk, leaves the path, or when the last search found nothing and a few seconds have passed.

Searches stay within 96 blocks of the player. If the target is farther than that you get a path to the closest reachable point, and it extends as you walk. The search uses a weighted heuristic, so paths can be a little longer than the shortest one in return for being quick.

Safe mode (on by default) only allows drops of up to 3 blocks. Turn it off to allow any fall that ends on solid ground.

## Commands

| Command | What it does |
| --- | --- |
| `/navigate start <x> <y> <z> [safeMode] [follow] [materials]` | Start navigating |
| `/navigate stop` | Stop navigating |
| `/navigate startfor <player> <x> <y> <z> [safeMode] [follow] [materials]` | Start navigating for another player |
| `/navigate stopfor <player>` | Stop navigating for another player |

`safeMode` is `true` or `false`. `materials` is a comma separated list of blocks, and when it is set the path only walks on those blocks.

```
/navigate start 120 64 -300
/navigate start 120 64 -300 false
/navigate start 120 64 -300 true follow
/navigate start 120 64 -300 true STONE,GRASS_BLOCK
```

## Follow

With `follow` the player is moved along the path using velocity, so they walk, jump up single blocks, climb ladders and vines, and swim. Sneaking pauses it. If the player gets pushed off the path or stops making progress, the path is searched again.

`follow.speed` in the config is how far the player moves per tick, 0.22 is about walking speed.

Anti cheat plugins see this as server velocity, but some may still flag it. Exempt your players if that happens.

## Permissions

| Permission | What it does |
| --- | --- |
| `navigate.use` | Use `/navigate` |
| `navigate.follow` | Use the `follow` option on yourself |
| `navigate.use.others` | Use `startfor` and `stopfor` |
| `navigate.use.neofetch` | Use `/navigate neofetch` |

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
