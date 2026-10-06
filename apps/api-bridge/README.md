# Optional Xtream metadata bridge

Some webOS providers reject browser-origin API calls because they do not return CORS headers. This small LAN service proxies Xtream metadata requests; playback remains direct from the television to the provider.

Configure `BRIDGE_TOKEN` with a long random value, list the exact provider base
URL in `ALLOWED_SERVERS`, and list permitted app origins in `ALLOWED_ORIGINS`.
The supplied Compose file contains placeholders that must be replaced.
Packaged file-based TV apps commonly send the literal origin `null`; retain it
only when the bridge is protected by a strong token and a trusted LAN firewall.

```bash
docker compose up -d --build
```

Configure the bridge in webOS Settings as:

```text
http://YOUR_LAN_HOST:8787
```

Enter the same bridge token in XtreamlyTV Settings. Requests without the token,
from an unlisted browser origin, or to an unlisted provider are rejected.

Do not expose this service directly to the public internet. Android TV normally does not need the bridge because it uses native HTTP networking.
