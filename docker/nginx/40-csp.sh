#!/bin/sh
# Entrypoint drop-in. Writes the CSP connect-src map from the CSP_CONNECT_SRC environment variable
# (set by the Deployment from the chart ConfigMap). Runs before nginx starts; /tmp is the only
# writable path in the image.
set -eu
src="${CSP_CONNECT_SRC:-'self'}"
case "$src" in
  *\;*|*\"*) echo "40-csp: refusing suspicious CSP_CONNECT_SRC" >&2; exit 1 ;;
esac
cat > /tmp/csp.conf <<CONF
map \$host \$csp_connect_src {
    default "${src}";
}
CONF
