# Media fixture provenance

`clip.mp4`, `clip-long.mp4`, `clip.webm`, `poster.png`, and `hls/` were
generated from ffmpeg's synthetic `testsrc2` video source. They contain no
third-party media, no provider content, and no personal data.

They are tracked so `npm run test:webos` works from a clean checkout. Regenerate
them with `scripts/generate-webos-fixtures.sh` (requires ffmpeg). The browser
smoke tests decode them in desktop Chromium only; they are not a claim about TV
firmware or device codecs.
