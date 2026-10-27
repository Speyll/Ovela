# Security policy

## Supported versions

Security fixes are applied to the latest tagged release and the current `main`
branch.

## Reporting a vulnerability

Do not open a public issue containing provider credentials, authenticated
stream URLs, encryption keys, or a reproducible exploit affecting user data.

Use GitHub private vulnerability reporting on
[Speyll/Ovela](https://github.com/Speyll/Ovela/security/advisories/new). If that
is unavailable, contact the repository owner privately.

Include:

- Affected platform and version
- Device and OS version
- Reproduction steps
- Impact assessment
- Suggested mitigation, if known

## Secrets

The repository must never contain:

- Real provider usernames or passwords
- Authenticated stream URLs
- Android signing keys
- LG Seller Lounge keys
- Personal access tokens

Android release signing uses a local keystore referenced from
`apps/android-tv/keystore.properties`, which is gitignored. Never commit the
keystore or its passwords. CI release signing, when enabled, must use encrypted
GitHub Actions secrets.

The repository runs secret scanning in CI. Report any accidentally committed
secret immediately so it can be revoked and rotated.
