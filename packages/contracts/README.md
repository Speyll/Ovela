# Shared contracts

These JSON schemas and fixtures define the platform-neutral data shapes used by the webOS and Android TV implementations. Platform clients may retain provider-specific fields, but normalized identifiers and content types should follow these contracts.

`source.schema.json` covers both Xtream credentials and URL-based M3U playlists. `playback.schema.json` defines the bounded recovery decision shared by both platform clients. M3U fixtures under `fixtures/m3u/` cover normal, malformed, and query-string playlist forms.
