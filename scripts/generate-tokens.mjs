import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tokens = JSON.parse(await readFile(path.join(root, 'packages/design-tokens/tokens.json'), 'utf8'));

const rgb = (hex) => {
  const value = parseInt(hex.slice(1), 16);
  return `${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}`;
};

/** Blend two hex colours by `amount` (0..1) toward `toward`. */
const blend = (hex, toward, amount) => {
  const channel = (offset) => {
    const from = parseInt(hex.slice(1 + offset, 3 + offset), 16);
    const to = parseInt(toward.slice(1 + offset, 3 + offset), 16);
    return Math.round(from + (to - from) * amount).toString(16).padStart(2, '0').toUpperCase();
  };
  return `#${channel(0)}${channel(2)}${channel(4)}`;
};

const lift = (hex, amount) => blend(hex, '#FFFFFF', amount);
const sink = (hex, amount) => blend(hex, '#000000', amount);

const brand = tokens.brand;
// Shades derived from the base palette. Both platforms consume these so a
// single edit to tokens.json moves the whole product, on every screen.
const bg2 = lift(brand.background, 0.022);
const bg3 = sink(brand.background, 0.055);
const panelStrong = lift(brand.surface, 0.042);
const accentInk = blend(brand.background, brand.accent, 0.06);

const css = `/* Generated from packages/design-tokens/tokens.json. Do not edit by hand. */\n:root {\n` +
  `  --brand-accent: ${brand.accent};\n` +
  `  --brand-accent-secondary: ${brand.accentSecondary};\n` +
  `  --brand-background: ${brand.background};\n` +
  `  --brand-surface: ${brand.surface};\n` +
  `  --brand-text: ${brand.text};\n` +
  `  --brand-muted: ${brand.muted};\n` +
  `  --brand-danger: ${brand.danger};\n` +
  `  --brand-background-rgb: ${rgb(brand.background)};\n` +
  `  --brand-surface-rgb: ${rgb(brand.surface)};\n` +
  `  --brand-accent-rgb: ${rgb(brand.accent)};\n` +
  `  --brand-accent-2-rgb: ${rgb(brand.accentSecondary)};\n` +
  `  --brand-bg-2: ${bg2};\n` +
  `  --brand-bg-3: ${bg3};\n` +
  `  --brand-panel-strong: ${panelStrong};\n` +
  `  --brand-accent-ink: ${accentInk};\n` +
  `  --safe-inset: ${tokens.layout.safeInset}px;\n` +
  `  --card-radius: ${tokens.layout.cardRadius}px;\n` +
  `  --focus-ring: ${tokens.layout.focusRing}px;\n` +
  `  --sidebar-width: ${tokens.layout.sidebarWidth}px;\n` +
  `  --focus-duration: ${tokens.motion.focusMs}ms;\n` +
  `}\n`;
const cssDir = path.join(root, 'apps/webos/app/css');
await mkdir(cssDir, { recursive: true });
await writeFile(path.join(cssDir, 'tokens.css'), css);

const kotlin = `// Generated from packages/design-tokens/tokens.json.\n` +
  `package io.github.speyll.ovela.ui.theme\n\n` +
  `import androidx.compose.ui.graphics.Color\n\n` +
  `/**\n` +
  ` * Brand palette generated from packages/design-tokens/tokens.json by\n` +
  ` * \`npm run tokens\`. Do not edit by hand: change tokens.json instead, then\n` +
  ` * regenerate. Every Compose palette in TvTheme is derived from these values.\n` +
  ` */\n` +
  `object BrandTokens {\n` +
  `    val Accent = Color(0xFF${brand.accent.slice(1).toUpperCase()})\n` +
  `    val AccentSecondary = Color(0xFF${brand.accentSecondary.slice(1).toUpperCase()})\n` +
  `    val Background = Color(0xFF${brand.background.slice(1).toUpperCase()})\n` +
  `    val Surface = Color(0xFF${brand.surface.slice(1).toUpperCase()})\n` +
  `    val Text = Color(0xFF${brand.text.slice(1).toUpperCase()})\n` +
  `    val Muted = Color(0xFF${brand.muted.slice(1).toUpperCase()})\n` +
  `    val Danger = Color(0xFF${brand.danger.slice(1).toUpperCase()})\n` +
  `    /** Secondary and tertiary surface shades derived from [Background]. */\n` +
  `    val Background2 = Color(0xFF${bg2.slice(1)})\n` +
  `    val Background3 = Color(0xFF${bg3.slice(1)})\n` +
  `    /** Raised surface shade derived from [Surface]. */\n` +
  `    val PanelStrong = Color(0xFF${panelStrong.slice(1)})\n` +
  `    /** Dark wash of [Accent], for type sitting on an accent fill. */\n` +
  `    val AccentInk = Color(0xFF${accentInk.slice(1)})\n` +
  `    const val CardRadius = ${tokens.layout.cardRadius}\n` +
  `}\n`;
const ktDir = path.join(root, 'apps/android-tv/app/src/main/java/io/github/speyll/ovela/ui/theme');
await mkdir(ktDir, { recursive: true });
await writeFile(path.join(ktDir, 'BrandTokens.kt'), kotlin);
console.log('Generated webOS CSS and Android Compose design tokens.');
