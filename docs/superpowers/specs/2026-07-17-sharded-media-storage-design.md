# Sharded Media Storage Design

## Goal

Store each photo and video asset below a two-character UUID shard so no media root accumulates every asset directory or every tile file.

## Layout

New assets use one binary container at `<media-root>/<uuid-first-two-characters>/<uuid>.tbc`. The container stores individually GZIP-compressed 16,384-byte map tiles. SQLite records the byte offset and compressed length of each tile, making a tile directly readable without scanning the container.

## Migration

During each SQLite repository's construction, direct UUID-named child directories in its media root are first moved into their shard. Those legacy tile directories are then packed into a `.tbc` container and removed only after the container and its SQLite index rows are durable. The move uses an atomic move when the filesystem supports it, otherwise a normal move. A pre-existing destination is an error: the source is retained and plugin startup fails rather than overwriting data.

## Compatibility and verification

After a successful migration, reads use only the new layout.  Repository tests cover direct sharded persistence and migration of legacy photo and video directories.
