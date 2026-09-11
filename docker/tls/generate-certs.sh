#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
password=${TLS_STORE_PASSWORD:-changeit}

openssl req -x509 -nodes -newkey rsa:3072 -sha256 -days 825 \
  -keyout "$script_dir/server.key" \
  -out "$script_dir/server.crt" \
  -subj "/C=RU/ST=Saint Petersburg/L=Saint Petersburg/O=ITMO University/OU=SOA Lab/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:payara,DNS:wildfly,DNS:client,IP:127.0.0.1" \
  -addext "basicConstraints=critical,CA:FALSE" \
  -addext "keyUsage=digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth"

openssl pkcs12 -export -name s1as \
  -inkey "$script_dir/server.key" -in "$script_dir/server.crt" \
  -out "$script_dir/server.p12" -passout "pass:$password"

if [ -f "$script_dir/truststore.p12" ]; then
  keytool -delete -alias soa-lab-server -keystore "$script_dir/truststore.p12" \
    -storetype PKCS12 -storepass "$password" >/dev/null 2>&1 || true
fi
keytool -importcert -noprompt -alias soa-lab-server \
  -file "$script_dir/server.crt" -keystore "$script_dir/truststore.p12" \
  -storetype PKCS12 -storepass "$password"

chmod 600 "$script_dir/server.key" "$script_dir/server.p12" "$script_dir/truststore.p12"
printf 'TLS files generated in %s\n' "$script_dir"
