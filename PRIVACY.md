# Privacy

Ovela contains no advertising, analytics, or behavioral tracking. The project
does not operate a central service and does not receive user credentials or
viewing history.

## Data stored on the device

Depending on platform and enabled features, the app may store:

- Provider server URL, username, and password
- Appearance and playback settings
- Favorites and favorite groups
- Recently watched content and watch history
- Movie and episode resume positions
- Bounded movie and series metadata used to speed up detail screens

webOS stores app state in local application storage. Android TV stores provider
credentials using Android encrypted preferences. Favorites, history, resume
positions, and cached metadata are partitioned by provider account.

This data stays on the device. Uninstalling the app or clearing its data removes
it.

## Data transmitted

Ovela sends requests only to the provider configured by the user. The optional
LAN API bridge receives provider credentials only to proxy requests to that
provider. Video streams are requested directly from the provider.

Artwork and metadata are fetched from the provider or from artwork hosts named in
the provider's responses, depending on platform behavior.

## Security note

Some providers use unencrypted HTTP. In that case credentials and stream URLs
may be visible to parties able to observe the network path. Prefer HTTPS-enabled
providers where available.

## Questions

Open a non-sensitive issue on
[Speyll/Ovela](https://github.com/Speyll/Ovela/issues). For vulnerabilities, use
the process in [SECURITY.md](SECURITY.md).
