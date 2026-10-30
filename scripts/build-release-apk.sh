#!/usr/bin/env bash
set -euo pipefail

# Ensures the release keystore exists, removes any duplicate legacy .webp mipmaps,
# and builds the signed Release APK:
# Output: app/build/outputs/apk/release/app-release.apk

export KEYSTORE_PATH="${KEYSTORE_PATH:-$(pwd)/my-upload-key.jks}"
export STORE_PASSWORD="${STORE_PASSWORD:-voiceshield2026}"
export KEY_PASSWORD="${KEY_PASSWORD:-voiceshield2026}"

./scripts/generate-release-key.sh

# Clean up any duplicate .webp launcher icons when custom .png icons are present
for dir in app/src/main/res/mipmap-mdpi app/src/main/res/mipmap-hdpi app/src/main/res/mipmap-xhdpi app/src/main/res/mipmap-xxhdpi app/src/main/res/mipmap-xxxhdpi; do
  if [ -f "$dir/ic_launcher.png" ] && [ -f "$dir/ic_launcher.webp" ]; then
    rm -f "$dir/ic_launcher.webp"
  fi
  if [ -f "$dir/ic_launcher_round.png" ] && [ -f "$dir/ic_launcher_round.webp" ]; then
    rm -f "$dir/ic_launcher_round.webp"
  fi
done

echo ""
echo "=== Building Signed Release APK ==="
gradle :app:assembleRelease

echo ""
echo "=== Signed Release APK Output ==="
ls -lh app/build/outputs/apk/release/
