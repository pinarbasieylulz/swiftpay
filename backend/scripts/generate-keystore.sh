#!/usr/bin/env bash
# Generates a local self-signed TLS keystore for development HTTPS.
# NOT for production use — in production, use a certificate issued by a real CA
# (e.g. via a reverse proxy / load balancer that terminates TLS).
set -euo pipefail
cd "$(dirname "$0")/.."
KEYSTORE_PATH="src/main/resources/keystore.p12"
STORE_PASS="${SSL_KEYSTORE_PASSWORD:-changeit}"

if [ -f "$KEYSTORE_PATH" ]; then
  echo "Keystore already exists at $KEYSTORE_PATH — delete it first to regenerate."
  exit 0
fi

keytool -genkeypair -alias swiftpay -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -keystore "$KEYSTORE_PATH" \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=localhost, OU=SwiftPay, O=SwiftPay, L=Istanbul, ST=Istanbul, C=TR"

echo "Generated $KEYSTORE_PATH (password: $STORE_PASS, override with SSL_KEYSTORE_PASSWORD)"
