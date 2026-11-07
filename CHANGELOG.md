# Changelog

## 1.2

- Pathfinding is much faster and no longer tanks TPS, the search reads from a chunk snapshot instead of asking the world for every block, and it never loads chunks
- Only one search runs at a time per player, a failed search retries after a few seconds instead of every tick
- Searches are limited to 96 blocks around the player, a farther target gets a partial path that is extended as you move
- Path particles only show close to the player
- Navigation stops when the player changes world instead of throwing an error
- New `follow` option on `/navigate start` and `/navigate startfor` that moves the player along the path, with jumps, ladders and swimming, needs `navigate.follow`
- New `follow.speed` option in the config
- Supports Paper 1.21.11
