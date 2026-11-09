# Changelog

## 1.3

- Follow opens doors, trapdoors and fence gates on the way and closes them behind the player, iron doors count as walls
- Follow eases the pitch back to level, starting while looking up or down no longer keeps the view stuck there
- Safe mode no longer routes through lava, and avoids berry bushes, cobwebs, campfires and magma. Lava is never walked on or through in either mode
- Pathfinding rewrite: drops are checked properly (3 blocks in safe mode), jumps need headroom, ladders and swimming work in both directions, and an unreachable target gives a path to the closest point
- Much faster searches, block lookups are cached per chunk and chunk snapshots are reused for a few seconds
- Commands use Brigadier: `~` coordinates, player selectors, tab completion for options and blocks, and the ACF library is gone from the jar
- Options are now `safe`/`unsafe`, `follow` and a block list in any order, the old `true`/`false` still works
- New `/navigate reload` and `/navigate help`, `navigate.reload` permission
- Messages come from language files in `plugins/Navigate/lang`, picked per player from the client language. English and LOLCAT are included, add your own to translate
- Particles are configurable: type, color, size, amount, range and interval, or turn them off
- `messages.arrival` and `messages.notify-target` options, players are told when someone else starts or stops their navigation
- `strings.direction` is gone from the config, direction names are in the language files now

## 1.2

- Pathfinding is much faster and no longer tanks TPS, the search reads from a chunk snapshot instead of asking the world for every block, and it never loads chunks
- Only one search runs at a time per player, a failed search retries after a few seconds instead of every tick
- Searches are limited to 96 blocks around the player, a farther target gets a partial path that is extended as you move
- Path particles only show close to the player
- Navigation stops when the player changes world instead of throwing an error
- New `follow` option on `/navigate start` and `/navigate startfor` that moves the player along the path, with jumps, ladders and swimming, needs `navigate.follow`
- New `follow.speed` option in the config
- Follow turns the player toward where they are walking
- Leaf litter no longer blocks paths
- Works on Folia, tasks now run on the player's region
- Supports Paper 1.21.11
