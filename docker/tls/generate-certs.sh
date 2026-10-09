#!/usr/bin/env sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# Не перезаписываем существующий ключ без явного удаления файлов владельцем.
if [ -e "$script_dir/server.crt" ] || [ -e "$script_dir/server.key" ]; then
  printf 'Сертификат или ключ уже существует в %s; сохраните их перед перевыпуском.\n' "$script_dir" >&2
  exit 1
fi
umask 077
openssl req -x509 -nodes -newkey rsa:3072 -sha256 -days 365 \
  -keyout "$script_dir/server.key" -out "$script_dir/server.crt" \
  -subj "/C=RU/O=ITMO/OU=SOA Lab/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:client,IP:127.0.0.1" \
  -addext "basicConstraints=critical,CA:FALSE" \
  -addext "keyUsage=digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth"
chmod 644 "$script_dir/server.crt"
printf 'TLS-файлы созданы в %s\n' "$script_dir"
