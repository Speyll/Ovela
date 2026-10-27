# Third-party notices

Ovela is licensed under the Apache License, Version 2.0 (see `LICENSE`).
This file records the copyright notices and licences of the third-party
software and assets distributed with it.

## Upstream project

Ovela began as a fork of XtreamlyTV:

- XtreamlyTV, https://github.com/joshcoburn/XtreamlyTV
- Copyright (c) 2026 XtreamlyTV Contributors
- Licensed under the MIT License

The original MIT grant permits relicensing of derivative works under the
Apache License 2.0. The MIT notice is preserved below as required.

```text
MIT License

Copyright (c) 2026 XtreamlyTV Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Fonts

Ovela bundles the following fonts. Both are licensed under the SIL Open Font
License, Version 1.1, and are redistributed unmodified.

| Font | Copyright | Licence | Full text |
|---|---|---|---|
| Inter | Copyright (c) 2016 The Inter Project Authors | SIL OFL 1.1 | `third_party/fonts/inter/LICENSE.txt` |
| Arimo | Copyright 2026 The Arimo Project Authors | SIL OFL 1.1 | `third_party/fonts/arimo/OFL.txt` |

The same licence files are bundled with the applications under
`assets/licenses/`.

## Android libraries

The Android TV application links the following libraries at build time. They
are not vendored in this repository; their licences are declared by their
upstream artifacts.

| Component | Licence |
|---|---|
| AndroidX Activity, Core, Lifecycle, Security Crypto | Apache-2.0 |
| AndroidX Media3 (ExoPlayer, HLS, UI) | Apache-2.0 |
| Jetpack Compose (UI, Foundation, Material 3) | Apache-2.0 |
| Kotlin standard library and kotlinx.coroutines | Apache-2.0 |
| kotlinx.serialization | Apache-2.0 |
| OkHttp | Apache-2.0 |
| Coil 3 | Apache-2.0 |
| JUnit 4 (test only) | Eclipse Public License 1.0 |

## Development tooling

Node.js tooling, Playwright, Gradle, and the webOS CLI are used only to build
and test Ovela and are not redistributed in the applications.
