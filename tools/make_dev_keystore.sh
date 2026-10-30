#!/bin/bash
# Creates a DEVELOPMENT keystore on YOUR computer and prints the values to paste into GitHub repository secrets.
# Never commit the .jks file or its password. Needs a JDK (keytool). Usage: bash tools/make_dev_keystore.sh
set -euo pipefail
OUT="${1:-autotune-dev.jks}"
[ -e "$OUT" ] && { echo "$OUT already exists; refusing to overwrite"; exit 1; }
read -r -s -p "Choose a keystore password (min 8 chars): " PW; echo
[ ${#PW} -ge 8 ] || { echo "password too short"; exit 1; }
keytool -genkeypair -v -keystore "$OUT" -storetype PKCS12 -alias autotune-dev -keyalg RSA -keysize 2048 -validity 9125 \
  -storepass "$PW" -keypass "$PW" -dname "CN=AutoTune EQ development, O=AutoTune EQ, C=PK" >/dev/null
echo; echo "Created $OUT (keep it private; back it up)."
echo "Certificate fingerprint:"; keytool -list -keystore "$OUT" -storepass "$PW" -alias autotune-dev | grep -i "SHA-256\|Certificate fingerprint" || true
echo; echo "Add these three GitHub repository secrets (Settings > Secrets and variables > Actions):"
echo "  AUTOTUNE_DEV_KEYSTORE_PASSWORD = the password you just typed"
echo "  AUTOTUNE_DEV_KEY_PASSWORD      = the same password (optional; defaults to the keystore password)"
echo "  AUTOTUNE_DEV_KEYSTORE_B64      = the single line printed below"
echo; base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'; echo
