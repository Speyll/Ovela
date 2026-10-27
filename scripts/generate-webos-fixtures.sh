#!/bin/sh
# Regenerate the synthetic media used by the webOS browser smoke tests.
# Requires ffmpeg. The generated files are tracked so a clean checkout can run
# `npm run test:webos` without any extra tooling.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
OUT="$ROOT/apps/webos/tests/fixtures/media"
mkdir -p "$OUT/hls"

ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=320x180:rate=15" -t 3 \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -an -movflags +faststart \
  "$OUT/clip.mp4"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=320x180:rate=15" -t 12 \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -an -movflags +faststart \
  "$OUT/clip-long.mp4"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=320x180:rate=15" -t 3 \
  -c:v libvpx-vp9 -b:v 180k -an "$OUT/clip.webm"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "testsrc2=size=320x180:rate=15" -t 4 \
  -c:v libx264 -preset veryfast -pix_fmt yuv420p -an \
  -f hls -hls_time 1 -hls_list_size 0 -hls_segment_filename "$OUT/hls/segment-%03d.ts" \
  "$OUT/hls/index.m3u8"
ffmpeg -hide_banner -loglevel error -y -ss 1 -i "$OUT/clip.mp4" -frames:v 1 "$OUT/poster.png"

echo "Regenerated media fixtures in apps/webos/tests/fixtures/media"
