# Ovela product and parity notes

Ovela is a minimalist, content-first IPTV client for living-room televisions.
This note records the product decisions behind the current Android TV and LG
webOS shells, the deliberate platform differences, and the limits of the
verification performed in this working tree.

## Identity

- Product name: **Ovela**.
- Palette: graphite surfaces, ivory type, and a single quiet ivory accent.
- Type: Inter, with platform fallbacks where a native TV font is a better fit.
- Mark: an ivory **O** with a folded notch. The packaged launcher, banner and
  webOS artwork share that motif, but both the legacy and mockup-derived mark
  families are retained; see `docs/design/ovela-assets.md` before replacing
  artwork.
- Tokens live in `packages/design-tokens/tokens.json`; generated platform values
  are committed so Android and webOS stay in step.
- Package IDs are `io.github.speyll.ovela` on both platforms. JS globals use the
  `Ovela*` prefix; only the Android stored-preference keys keep their historical
  names so existing installs and user data keep working. They are not
  user-facing brand text.

## Home

Home is content-first. There is no featured hero, spotlight, promotional
backdrop, tagline, or auto-playing preview.

- Continue Watching / Recently watched for on-demand titles.
- Recently watched Live TV channels.
- Four direct Browse shortcuts: Live TV, Movies, Series, Favourites.
- Favourite titles/channels when they exist.
- A short empty-library hint when there is no recent or favourite content.

Android uses one mixed on-demand rail. webOS uses separate Continue Watching
Movies and Continue Watching Series rails. Both preserve the same hierarchy:
recent activity first, then Browse, then favourites.

## Catalog and Live TV

- Android TV uses a readable vertical category rail; phone layouts use category
  chips.
- webOS uses a vertical category rail sized for 10-foot reading.
- Movies and Series use a poster grid with restrained focus treatment.
- Live TV is a flat, one-column channel list. The old programme grid, category
  wheel, and media hero are not part of the current catalog.
- Category search is scoped to the active category.
- The Red key toggles the focused channel's favourite state on Android TV.
- Android catalog rows and grids are virtualized, artwork is loaded only for
  composed cards, and the per-library category cache is bounded (2–5 entries,
  selectable in Settings). Search filtering is debounced briefly so typing over
  a very large provider category does not block the UI; clearing search applies
  immediately.
- webOS renders only visible cards through its virtual grid and keeps the same
  bounded category cache. The existing performance setting controls the cache
  ceiling on both platforms.

## Details

Non-series items open a compact Details screen rather than starting playback
immediately. The screen shows artwork, title, metadata, plot, Watch/Resume, and
Favourite actions. Series keep the existing season/episode workflow.

## Category visibility

Provider categories can be hidden per media type: Live TV, Movies, and Series.

- Prefix groups are detected from the provider's `|` delimiter with counts.
  Prefixes are not assumed to be languages: `AR|`, `FR|`, `4K|` and region or
  provider labels are grouped the same way. Names without a usable prefix stay
  under **Other**.
- Search, the hidden-only filter, and the selected prefix group combine. The
  group rail stays stable while typing, and a group filter is scoped to the
  active media type.
- Actions state their scope: **Hide/Show shown** applies to the current filtered
  results, **Hide/Show all** applies to the entire section, and **Hide/Show
  group** applies to every category in the selected group even when search
  narrows the visible rows.
- Multi-select rows keep clear Hidden/Visible states. Group operations compose
  with manual selections and with categories that were already hidden.
- Staged Apply/Cancel persists one atomic bulk update. Changes larger than
  eight categories require confirmation, and the summary states how many will
  be hidden versus shown. Undo restores the previous persisted set.
- Stable ordering, stable row keys, preserved scroll position, and focus
  restoration after bulk actions.

Android uses a dedicated category-management screen. webOS uses an inline
staged settings card so the remote never loses its place in Settings.

## Playback behaviour

- On Live TV, OK/Enter and Media Play/Pause reveal the controls. They never
  pause the live stream.
- On VOD, OK/Enter and Media Play/Pause toggle playback.
- Live left/right only seeks when the stream exposes a rewind buffer; otherwise
  the player explains that no rewind buffer is available.
- Live overlays do not show a pause control.
- Recovery uses a bounded retry budget, candidate failover, a first-frame
  watchdog, and a post-start progress watchdog.
- Stale callbacks are ignored after the player or catalog session changes.
- Media resources and listeners are released when playback ends or the player
  closes.
- Android reads rendition heights from the real HLS/DASH track list. A quality
  choice appears only when the manifest exposes more than one video rendition;
  selections cap the maximum height while **Auto** leaves adaptation
  unrestricted. A fixed-bitrate URL therefore never shows a fake selector.
- webOS uses the native media element, whose adaptive behaviour is managed by
  the platform. No quality selector is added there because the page cannot
  truthfully switch renditions of a single fixed-bitrate URL.
- Android shows a small **Opening stream…** / **Buffering…** indicator until the
  first frame and during rebuffering, without treating a normal buffer as an
  error. Audio-becoming-noisy handling is enabled for headphone/source changes.
- Playback options are laid out directly above the control bar instead of using
  a fixed bottom offset, so the panel cannot collide with the transport row on
  different screen heights.

## Startup

There is no branded chime, tagline, sweep animation, or artificial startup
delay.

- Android uses the platform splash with an empty icon, followed by a minimal
  Compose startup surface while real initialization completes.
- webOS uses a minimal Ovela lockup and progress indicator, then waits for the
  app to become ready with an 8-second safety net.

## Platform parity

| Area | Android TV / phone | LG webOS |
|---|---|---|
| Shell | Jetpack Compose | HTML/CSS/JavaScript |
| Playback | AndroidX Media3 ExoPlayer | HTML media element |
| Live TV list | Flat channel rows | Flat virtualized channel rows |
| Category rail | Vertical rail on TV, chips on touch | Vertical rail |
| Category manager | Dedicated screen | Inline settings card |
| Local M3U file import | Yes, through the system picker | URL only |
| Provider access | Direct network | Direct network or optional CORS/API bridge |
| Startup | Native splash + Compose readiness screen | HTML startup surface |
| Remote model | D-pad, media keys, optional touch UI | Magic Remote, pointer and D-pad |
| Live OK | Shows controls | Shows controls |
| VOD OK | Toggle playback | Toggle playback |

Deliberate differences are the native playback engine, Android local playlist
import and touch layout, the webOS CORS bridge, and the platform-specific
startup surfaces. The category manager and Home rail grouping are presentation
differences; the underlying persistence and hiding rules are shared.

## Verification in this working tree

Verified without a physical TV, emulator, or ADB device:

- webOS JavaScript syntax: `node --check` on the changed runtime files.
- webOS release validation: `node scripts/check-webos.mjs`.
- Core web tests: 34 tests, 0 failures.
- Headless Chromium checks: every Live TV, Movies and Series category is
  reachable by D-pad; the rail and the content list scroll independently; the
  pointer commits an off-screen category; Enter moves focus into content; Back
  from a movie detail restores the poster and category; no page errors.
- Android unit tests: 118 tests, 0 failures, 0 errors, 0 skipped.
- Android Kotlin compile: `:app:compileDebugKotlin` succeeds.
- webOS package: built with `ares-package --no-minify`; all 33 packaged app
  files are byte-identical to `apps/webos/app`.
- The Python Playwright smoke scripts were not executed in this environment
  because its Playwright/greenlet install targets a different Python version;
  the new category-rail assertions were mirrored in the Chromium harness above
  and added to `apps/webos/tests/v07_release_smoke.py`.

Not verified:

- Installation or playback on a real Android TV, phone, or LG webOS device.
- Real-provider authentication, stream codecs, or live rewind behaviour.
- Magic Remote pointer/back-key behaviour on hardware.
- Genuine multi-rendition HLS/DASH adaptation and the quality menu on hardware;
  the webOS smoke environment does not expose native track lists.
- Large-change confirmation on Android on a physical device; the webOS smoke
  test covers the shared threshold and summary behaviour.
