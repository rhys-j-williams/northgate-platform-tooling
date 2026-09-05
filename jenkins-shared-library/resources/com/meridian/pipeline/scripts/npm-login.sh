#!/usr/bin/env bash
# Writes a workspace scoped .npmrc pointing at the Artifactory npm-virtual repository using the
# credentials injected by withCredentials. The file is deleted in the pipeline's post block.
# Never commit an .npmrc with an _authToken in it; GIS-2107 has opinions.
set -euo pipefail

: "${NPM_USER:?NPM_USER not set}"
: "${NPM_TOKEN:?NPM_TOKEN not set}"
: "${NPM_CONFIG_REGISTRY:?NPM_CONFIG_REGISTRY not set}"

registry_host="${NPM_CONFIG_REGISTRY#https://}"
registry_host="${registry_host#http://}"

cat > .npmrc <<NPMRC
registry=${NPM_CONFIG_REGISTRY}
//${registry_host}:_auth=$(printf '%s:%s' "${NPM_USER}" "${NPM_TOKEN}" | base64 -w0)
//${registry_host}:always-auth=true
@meridian:registry=${NPM_CONFIG_REGISTRY}
fund=false
audit=false
NPMRC
chmod 600 .npmrc
echo "npm registry configured for ${registry_host}"
