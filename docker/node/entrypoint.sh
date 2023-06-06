#!/bin/sh
set -eu
for f in /vault/secrets/*.env; do
  # shellcheck disable=SC1090
  [ -f "$f" ] && . "$f"
done
# dist/main.js for Nest, dist/server.js for documents-service. Neither team wanted to rename.
if [ -f dist/main.js ]; then exec node dist/main.js; fi
exec node dist/server.js
