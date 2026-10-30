#!/usr/bin/env bash
set -euo pipefail

# Generates a production RSA-2048 release keystore matching app/build.gradle.kts signingConfigs.release:
#   File: my-upload-key.jks
#   Key Alias: upload

KEYSTORE_FILE="${KEYSTORE_PATH:-my-upload-key.jks}"
STORE_PASS="${STORE_PASSWORD:-voiceshield2026}"
KEY_PASS="${KEY_PASSWORD:-voiceshield2026}"
KEY_ALIAS="upload"

if [ -f "$KEYSTORE_FILE" ]; then
  echo "Release keystore already exists at: $KEYSTORE_FILE"
else
  echo "Generating release keystore at: $KEYSTORE_FILE (alias: $KEY_ALIAS)..."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -storetype PKCS12 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=VoiceShield AI, OU=Mobile Security, O=VoiceShield, L=Kolkata, ST=WB, C=IN"
  echo "Successfully generated $KEYSTORE_FILE"
fi

echo ""
echo "=== Certificate Fingerprints (SHA-1 & SHA-256) ==="
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASS" -alias "$KEY_ALIAS" | grep -E "Alias name:|Owner:|Valid from:|SHA1:|SHA256:" || true
