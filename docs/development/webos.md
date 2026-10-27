# LG webOS development

## Requirements

- Windows 10+, macOS, or Linux
- Node.js and npm
- LG webOS CLI
- LG Developer account
- LG TV and development machine on the same LAN

Install the current unified webOS CLI:

```bash
npm install -g @webos-tools/cli
ares-config --profile tv
ares -V
```

## Enable Developer Mode on the TV

1. Install **Developer Mode** from the LG Content Store.
2. Sign in with an LG Developer account.
3. Enable **Dev Mode Status** and allow the TV to reboot.
4. Reopen Developer Mode and enable **Key Server**.
5. Note the TV IP address and six-character passphrase.

Developer Mode has a limited session. Extend it from the Developer Mode app before it expires.

## Pair the TV

```bash
ares-setup-device
```

Add a device with values similar to:

```text
Name: tv1
IP: 192.168.1.123
Port: 9922
User: prisoner
Password: leave blank
Profile: tv
```

Retrieve the key and verify connectivity:

```bash
ares-novacom --device tv1 --getkey
ares-device --system-info --device tv1
```

## Build

From the repository root:

```bash
npm run prepare:apps
npm run check:webos
npm run build:webos
```

Equivalent direct command:

```bash
ares-package --no-minify -o dist/webos apps/webos/app
```

## Provider sources

Ovela accepts either an Xtream account or an M3U/M3U+ playlist URL. For M3U, the TV downloads and indexes the playlist locally; playback still goes directly to the stream host. Optional alternate hosts rewrite only the scheme/host/port, preserving the original stream path and query token.

LG webOS enforces browser CORS on provider requests. If an M3U host does not allow the TV origin, configure the optional [API bridge](../../apps/api-bridge/README.md), add the exact playlist URL to `ALLOWED_PLAYLISTS`, and enter the bridge URL and token in Settings.

## Install and launch

Developer Mode installs the unsigned IPK directly; custom package signing is not required and the abandoned signing experiments are not used.

```bash
ares-launch --device tv1 --close io.github.speyll.ovela

ares-install --device tv1 \
  dist/webos/io.github.speyll.ovela_1.0.0_all.ipk

ares-launch --device tv1 io.github.speyll.ovela
```

Windows PowerShell helper:

```powershell
.\scripts\install-webos.ps1 -Device tv1
```

Repeated installation with the same app ID updates the development app in place. Uninstalling is normally unnecessary and clears less state only if the TV removes app data.

## Inspect and debug

Launch the app, then run:

```bash
ares-inspect --device tv1 --app io.github.speyll.ovela --open
```

Useful checks:

- Console errors
- Network response status and payload shape
- CORS failures
- `<video>` events and dimensions
- Local storage
- DOM count during catalog navigation

Never paste a provider password or an authenticated stream URL into a public issue.

## Simulator and hosted testing

Static browser testing:

```bash
cd apps/webos/app
python3 -m http.server 8080
```

LG Simulator testing can be launched through webOS Studio or `ares-launch` with the installed simulator target. Final playback validation should be performed on physical hardware because codec and media-plane behavior varies by TV generation.
