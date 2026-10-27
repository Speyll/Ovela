# Ovela asset family

This note records the assets the Android TV and LG webOS packages actually
consume, their required dimensions and formats, and the existing source
references. No artwork is generated or substituted here. Two mark families are
retained; resolve that question before replacing shipped artwork.

## Identity rules

- Product name: **Ovela**. The launcher, TV banner, webOS icon and splash use
  the same ivory **O** with a folded notch.
- The mark is not combined with a wordmark or tagline inside the icon. Keep the
  mark inside the platform's mask/safe zone.
- Palette (from `packages/design-tokens/tokens.json`):

  | Token | Value |
  |---|---|
  | Background | `#0C0C0E` |
  | Text | `#F4F1EA` |
  | Accent | `#E8E2D6` |
  | Secondary accent | `#A8A296` |
  | Muted | `#9A958C` |
  | Danger | `#D9737E` |

- Package identity is `io.github.speyll.ovela` on both Android and webOS
  (`applicationId` in `apps/android-tv/app/build.gradle.kts` and `id` in
  `apps/webos/app/appinfo.json`). It is not user-facing brand text.
- The palette and mark families are not fully resolved. `packages/design-tokens/tokens.json`
  is authoritative for UI colour; the packaged icon/banner/splash artwork is
  mockup-derived, and both mark families are retained. See
  `packages/brand/README.md` before changing artwork.

## Android TV / phone

| Asset | Path | Current size / format | Requirement |
|---|---|---|---|
| Adaptive icon | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | XML, 108 × 108 dp viewport | Background `@color/ovela_icon_background`; foreground `@mipmap/ic_launcher_foreground`. Used on API 26+. |
| Adaptive foreground | `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png` | Raster, 108, 162, 216, 324, 432 px square | Keep artwork clear of launcher masks. The unreferenced vector `drawable/ovela_icon_foreground.xml` is not the packaged foreground. |
| Launcher fallback | `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` | 48, 72, 96, 144, 192 px square PNG | 32-bit PNG with transparency where the platform mask expects it. Used below API 26 and by some launchers. |
| TV banner | `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/tv_banner.png` | 160 × 90, 240 × 135, 320 × 180, 480 × 270, 640 × 360 px PNG | 16:9, no critical text/art near the edges. Referenced by `android:banner`; the 320 × 180 xhdpi asset is the Leanback baseline. |
| Launch background | `app/src/main/res/drawable/launch_background.xml` | XML solid `#0C0C0E` | Must stay a flat brand background; no logo is baked into the native splash. |
| Android 12 splash icon | `app/src/main/res/drawable/ic_splash_empty.xml` | Vector | Intentionally empty; the platform splash shows only the Ovela background before Compose starts. |
| Home backdrop | `app/src/main/res/drawable-nodpi/home_live_backdrop.jpg` | 1600 × 900 JPEG, ~225 KB | Opaque 16:9 image, sRGB, no text. Keep the same crop as the webOS copy. |

The style names are `Theme.Ovela` and `Theme.Ovela.Starting` in
`values/themes.xml`, `values-v31/themes.xml` and `AndroidManifest.xml`.

## LG webOS

| Asset | Path | Current size / format | Requirement |
|---|---|---|---|
| App icon | `app/assets/icon.png` | 80 × 80 PNG, fully opaque RGBA | Referenced by `appinfo.json` `"icon"`. Keep the mark centred with generous padding. |
| Large icon | `app/assets/largeIcon.png` | 130 × 130 PNG, fully opaque RGBA | Referenced by `"largeIcon"` for the launcher and store surfaces. Same artwork as `icon.png`. |
| Home backdrop | `app/assets/home-live-backdrop.jpg` | 1600 × 900 JPEG, ~225 KB | Same asset family as Android; opaque 16:9, sRGB. |
| Startup splash | CSS in `app/css/startup.css`, markup in `app/js/startup.js` | 1920 × 1080 layout, scaled to the viewport | The branded boot splash is drawn in the web runtime and removed once the shell renders. No raster `splash.png` is packaged. |

`appinfo.json` is the packaging contract: `icon` and `largeIcon` must keep
pointing at these files (or at files with the same dimensions and format) for
`ares-package` and store submission.

## Shared / documentation

| Asset | Path | Current size / format | Notes |
|---|---|---|---|
| Historical master | `packages/brand/assets/ovela-logo.png` | 1254 × 1254 PNG | Pre-1.0 artwork retained for reference. It is **not** the source of the current platform icon exports. |
| Mockup-derived source pack | `packages/brand/ovela-source-pack/` | 116 files including source marks, wordmarks, store art and tokens | Untracked reference pack. Its `MANIFEST.json`, `SOURCE_FIDELITY.md` and `AGENT_INTEGRATION.md` document provenance and unresolved questions. |
| Runtime wordmarks | `apps/webos/app/assets/ovela-wordmark.png`, `apps/android-tv/app/src/main/res/drawable-nodpi/ovela_wordmark.png` | PNG | In-app wordmarks. Do not place a wordmark inside launcher icons. |

## Replacement checklist

1. Resolve which mark family and palette supersede the other before replacing
   artwork; do not re-export from the historical master blindly.
2. Replace the chosen master, then export Android adaptive foreground, launcher
   PNGs, TV banner PNGs, and the webOS icon family.
3. Keep every Android density at the dimensions above; do not upscale a single
   PNG into other densities.
4. Re-export webOS `icon.png` (80 × 80) and `largeIcon.png` (130 × 130) without
   changing their paths. The webOS startup splash is CSS, not an exported image.
5. Verify the mark survives circular, rounded-square and TV banner masks.
6. Package both apps and confirm the APK `android:icon`/`android:banner` and
   IPK `appinfo.json` references still resolve.
