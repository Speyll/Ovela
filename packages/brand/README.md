# Ovela brand assets

This directory holds the Ovela master artwork and the mockup-derived source pack.
It does **not** hold the assets that ship inside the applications — those live in
the platform trees and are listed below.

## Canonical assets by role

| Role | Path | Notes |
|---|---|---|
| Historical master mark | `assets/ovela-logo.png` | 1254×1254 PNG retained from the pre-1.0 artwork. Tracked in Git. **Not** the source of the current platform icon exports (different artwork family — see "Unresolved"). |
| Mockup-derived source pack | `ovela-source-pack/` | 116 files, 11 MB. Original reference mockups, extracted logo/wordmark, platform exports, store artwork, UI glyphs, tokens, and integration notes. Untracked; see the pack's own `README.md`, `SOURCE_FIDELITY.md`, `AGENT_INTEGRATION.md`, and `MANIFEST.json`. |
| Android runtime icons | `../../apps/android-tv/app/src/main/res/mipmap-*` | `ic_launcher.png` (48/72/96/144/192), `ic_launcher_foreground.png` (108/162/216/324/432), `tv_banner.png` (160×90 → 640×360). Referenced by `AndroidManifest.xml` and `mipmap-anydpi-v26/ic_launcher.xml`. |
| Android runtime palette | `../../apps/android-tv/app/src/main/res/values/colors.xml` | Window/theme-layer colours; the Compose palette is generated from `../design-tokens/tokens.json`. |
| webOS runtime icons | `../../apps/webos/app/assets/icon.png`, `largeIcon.png` | 80×80 and 130×130. Referenced by `appinfo.json`. Byte-identical to the pack's `webos/` exports. |
| webOS UI artwork | `../../apps/webos/app/assets/ovela-wordmark.png`, `ovela-mark-flat.svg` | Used by `css/ovela.css` and the JS shell. Byte-identical to `ovela-source-pack/brand/logo/`. |
| UI colour + layout tokens | `../design-tokens/tokens.json` | **Authoritative for UI colours, radii, spacing, and motion.** Generated into `apps/android-tv/.../theme/BrandTokens.kt` and `apps/webos/app/css/tokens.css` by `npm run tokens`. |
| Documentation icons/wordmarks | — | The old `docs/assets/` tree has been removed. Documentation now uses the runtime wordmarks and this repository's source pack. |

## Palette: which one is canonical?

Two palettes exist. They are **not** merged here, because doing so would be a
brand decision rather than a cleanup:

| Source | Background | Surface | Text | Accent |
|---|---|---|---|---|
| `../design-tokens/tokens.json` (used by app UI code) | `#0C0C0E` | `#17171A` | `#F4F1EA` | `#E8E2D6` |
| `ovela-source-pack/brand/tokens/colors.json` (mockup-derived artwork) | `#0B0E10` | `#1E2328` | `#FAF4EB` | — (gray `#A7A9AC`) |

The application **UI** uses `tokens.json`; the **packaged icon/banner
artwork** is mockup-derived. `apps/android-tv/app/src/main/res/values/colors.xml`
currently mixes both (it defines `ovela_deep_charcoal`/`ovela_slate` from the
mockup palette while its comment says it mirrors `tokens.json`).

Until a single palette is chosen, treat `tokens.json` as canonical for code and
the pack's tokens as the palette baked into artwork.

## Unresolved artwork questions (preserved, not guessed)

1. **Two mark families.** The tracked 1254×1254 master is the historical mark;
   the mockup-derived pack carries a re-extracted mark, wordmark, and icon tile.
   Both are retained. No asset in the repository states which one supersedes the
   other.
2. **Android adaptive foreground drift.** `ovela-source-pack/brand/reference/
   ovela-adaptive-foreground-master-432.png` and the packaged
   `mipmap-xxxhdpi/ic_launcher_foreground.png` are 432×432 but differ by 16,294
   pixels (~8.7%). The packaged variant is what ships; the pack master is kept.
3. **webOS source icon.** `apps/webos/app/assets/logo-source.png` has been
   removed. The packaged `icon.png` and `largeIcon.png` are mockup-derived and no
   editable webOS source is retained, so re-exporting needs the source pack.
4. **Dimension documentation.** `../../docs/design/ovela-assets.md` now records
   the shipped webOS sizes (80×80 and 130×130) and the packaged adaptive
   foreground (`@mipmap/ic_launcher_foreground`). The earlier 256/512 claims and
   the reference to the unreferenced vector drawable have been corrected.

## Duplicate relationships (verified with SHA-256)

These are byte-identical. They are kept because `ovela-source-pack/MANIFEST.json`
documents every file in the pack, and because density aliases (`tv_launcher`) are
exported for launchers that request that resource name.

- `ovela-source-pack/android/res/mipmap-{m,h,xh,xxh,xxxh}dpi/tv_launcher.png`
  = same-density `ic_launcher.png`
- `ovela-source-pack/android/tv/tv_banner_xhdpi_320x180.png`
  = `android/res/mipmap-xhdpi/tv_banner.png`
- `ovela-source-pack/android/tv/tv_banner_preview_1920x1080.png`
  = `brand/backgrounds/ovela-tv-banner-source-derived-1920x1080.png`
- `ovela-source-pack/common/backgrounds/ovela-backdrop-4k.png`
  = `brand/backgrounds/ovela-backdrop-source-derived-3840x2160.png`
- `ovela-source-pack/webos/{icon,largeIcon}.png` = the packaged webOS assets
- `ovela-source-pack/brand/logo/ovela-mark-extracted-source.png`
  = `apps/android-tv/.../drawable-nodpi/ovela_mark.png`
- `ovela-source-pack/brand/logo/ovela-wordmark-horizontal.png`
  = both apps' `ovela_wordmark.png` / `ovela-wordmark.png`
- `ovela-source-pack/brand/logo/ovela-mark-flat.svg`
  = `apps/webos/app/assets/ovela-mark-flat.svg`

## Rules for future changes

- Change UI colours in `../design-tokens/tokens.json`, then run `npm run tokens`.
- Do not add a third copy of an icon. Extend the runtime trees in `apps/` and, if
  the change is source-level, update `ovela-source-pack/` and its `MANIFEST.json`.
- Do not put a wordmark or tagline inside a launcher icon, TV banner, or splash.
- The pack is raster-derived (see `ovela-source-pack/SOURCE_FIDELITY.md`); if the
  original vector/PSD/Figma source appears, treat that as the new canonical master.
