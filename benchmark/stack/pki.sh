#!/usr/bin/env bash
# Generates the local CA, the certificate of the stub targets (valid for their IPs), and a JVM truststore that holds
# the JDK's own CA certificates plus the local CA, so Kuvasz trusts the stub targets AND the real internet.
# Idempotent: does nothing if the truststore already exists.
set -euo pipefail

out="$(cd "$(dirname "$0")/.." && pwd)/generated/pki"
image="${TRUSTSTORE_IMAGE:-eclipse-temurin:25-jre-alpine}"

if [[ -f "$out/truststore.p12" ]]; then
  exit 0
fi
mkdir -p "$out"
cd "$out"

openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=kbench local CA" \
  -keyout ca.key -out ca.crt 2>/dev/null

cat > target.ext <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = IP:172.31.89.10, IP:172.31.89.11, DNS:www.bench.test
EOF
openssl req -newkey rsa:2048 -nodes -subj "/CN=kbench target" -keyout target.key -out target.csr 2>/dev/null
openssl x509 -req -in target.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 825 \
  -extfile target.ext -out target.crt 2>/dev/null
# openresty's workers run as nobody
chmod 644 target.key

docker run --rm -v "$out:/pki" "$image" sh -c '
  cp "$JAVA_HOME/lib/security/cacerts" /pki/truststore.p12 &&
  keytool -importcert -noprompt -alias kbench-ca -file /pki/ca.crt \
    -keystore /pki/truststore.p12 -storepass changeit >/dev/null
'
echo "PKI generated under $out"
