# Architecture

## Why a monorepo

XtreamlyTV targets television platforms with very different runtime and playback capabilities. A single repository keeps product behavior, data contracts, brand assets, tests, documentation, and release automation synchronized while allowing each platform to use its native media stack.

## Shared layers

### `packages/contracts`

JSON schemas and fixtures define normalized credentials, categories, content identities, and provider-response expectations. They are the cross-platform compatibility contract.

### `packages/design-tokens`

A single JSON source generates:

- webOS CSS variables
- Android Compose color constants

This keeps branding and spacing changes deliberate and reviewable.

### `packages/core-web`

The canonical JavaScript implementation contains:

- Xtream response normalization
- M3U/M3U+ parsing, categories, and alternate-host URL rewriting
- content-type inference
- stable item IDs
- stream URL construction
- bounded playback-recovery decisions
- web-compatible provider clients
- webOS local persistence behavior

The webOS packaging tree receives generated copies through `npm run sync:core`.

### Platform-native layers

#### webOS

The webOS target is dependency-light HTML, CSS, and JavaScript. It uses category-on-demand loading, a virtualized DOM grid, and an untransformed top-level HTML video surface to cooperate with LG's hardware media plane.

#### Android TV

The Android target is written in Kotlin with Jetpack Compose and AndroidX Media3 ExoPlayer. It uses encrypted preferences for provider credentials and native Android TV launcher metadata.

The Android implementation currently mirrors the shared data contracts rather than executing the JavaScript core. This is intentional: native networking and Media3 playback provide better diagnostics and device integration. Contract fixtures and behavior tests are the compatibility boundary.

## Data flow

```text
Source selection
        ├── Xtream: player_api.php authentication
        └── M3U/M3U+: fetch and index the playlist
        │
        ▼
Category indexes only
        │
        ▼
Selected category request
        │
        ▼
Normalized catalog items
        │
        ├──► Favorites / recent / progress
        │
        ▼
Platform player adapter
        ├── webOS HTML media surface
        └── Android Media3 ExoPlayer
```

## Source and recovery model

Each platform exposes an Xtream client and an M3U client behind the same catalog interface. M3U entries carry a direct stream URL; user-configured alternate hosts are used only to rewrite the origin, preserving paths, query tokens, and media format. No host discovery is performed.

Playback failures are classified as transient or permanent and handled with a bounded policy: try another URL candidate before repeating, retry transient failures with a delay cap, and stop with an honest message when the candidate/retry budget is exhausted. A stream that never starts or stops advancing is treated as a stall rather than a permanent codec failure.

## Large-catalog rules

- No synthetic “All Channels,” “All Movies,” or “All Series” option.
- Only one selected category is requested at a time.
- webOS renders only visible cards plus a small overscan window.
- Category rails are bounded and recycled.
- Search operates on the loaded category, not the provider's full catalog.
- Passwords and authenticated playback URLs must not be written to logs.

## Versioning

The root `VERSION`, webOS `appinfo.json`, Android `versionName`, and Android `versionCode` must be updated together for a release. Release scripts and CI validate these values.
