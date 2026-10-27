# Contributing to Ovela

Thank you for helping improve an open-source television client.

## Before opening a change

- Search existing issues and pull requests.
- Use only provider accounts and media you are authorized to access.
- Never attach passwords, complete authenticated stream URLs, signing keys, or
  personal viewing history.
- Keep platform-specific behavior behind the relevant app boundary.
- Update shared contracts or design tokens when behavior should remain aligned
  across platforms.

## Development setup

```bash
npm run prepare:apps
npm run check:versions
npm run check:webos
npm run test:core
```

Platform guides:

- [Getting started](docs/getting-started.md)
- [LG webOS](docs/development/webos.md)
- [Android TV / Google TV](docs/development/android-tv.md)
- [Debugging](docs/development/debugging.md)

## Generated files

Do not directly edit generated copies in the app directories. Change the
canonical source under `packages/` and run:

```bash
npm run prepare:apps
```

`apps/webos/app/js/core.js`, `api.js`, and `store.js` are generated from
`packages/core-web/src` and must not be edited by hand.

## Pull requests

A useful pull request includes:

- Concise problem statement
- Platform(s) affected
- Screenshots or recordings for UI changes
- Test results
- Physical-TV validation when playback, focus, sizing, or codecs are involved
- Migration notes for stored settings or credentials

Keep commits focused. Do not include generated build output, local keystores,
or files under `.dev/`.

## Code style

### JavaScript

- ES5-compatible syntax in packaged webOS runtime files unless the supported TV
  baseline is deliberately raised.
- Avoid large framework runtimes in webOS.
- Handle malformed provider responses defensively.

### Kotlin

- Follow the official Kotlin style.
- Prefer immutable UI state and coroutine-based I/O.
- Use Media3 rather than direct platform `MediaPlayer` APIs.
- Keep credentials and authenticated URLs out of logs.

## Tests

- Shared domain logic belongs in `packages/core-web/test`.
- Browser and webOS behavior belongs in `apps/webos/tests` as Playwright smoke
  tests.
- Android data and policy logic belongs in JVM unit tests under
  `apps/android-tv/app/src/test`.

Run the narrowest relevant suite while iterating and the full suite before
opening a pull request.

## Legal and content policy

Contributions must not include channel lists, provider credentials, pirated
media, bypasses for paid services, or branding and assets without
redistribution rights.

By submitting a pull request you agree that your contribution is licensed under
the Apache License 2.0, the same licence as the project. No separate contributor
licence agreement is required.
