#!/usr/bin/env bash
# Seeds a local dev-mode Vault (vault server -dev) with placeholder values so vault-agent.hcl and
# the templates render. Every value is CHANGEME-*; nothing here works against anything real.
set -euo pipefail
export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
ns="${1:-cswt-dev}"
services=(bff-retail bff-business beacon-notifications alerts-preferences-service txn-posting-service
  pii-vault-service audit-trail-service entitlements-service bedrock-adapter iris-orchestrator
  documents-service statements-api exposure-calc)

for s in "${services[@]}"; do
  vault kv put "secret/cswt/${ns}/${s}/app-secrets" \
    SESSION_SIGNING_KEY="CHANGEME-${s}-session-key" \
    INTERNAL_API_KEY="CHANGEME-${s}-internal-api-key" >/dev/null
  vault kv put "secret/cswt/${ns}/${s}/db-credentials" \
    username="${s//-/_}_app" password="CHANGEME-${s}-db-password" >/dev/null
  vault kv put "secret/cswt/${ns}/${s}/redis-credentials" \
    password="CHANGEME-${s}-redis-password" >/dev/null
  vault kv put "secret/cswt/${ns}/${s}/idp-client" \
    client_id="${s}" client_secret="CHANGEME-${s}-oidc-client-secret" >/dev/null
  echo "seeded ${s}"
done
vault kv put "secret/cswt/${ns}/shared/mq-credentials" user=cswtmq password="CHANGEME-mq-password" >/dev/null
vault kv put "secret/cswt/${ns}/shared/splunk-hec" token="CHANGEME-splunk-hec-token" >/dev/null
vault kv put "secret/cswt/${ns}/shared/ca-bundle" pem="CHANGEME-ca-bundle-pem" >/dev/null

for p in "$(dirname "$0")"/policies/*.hcl; do
  vault policy write "$(basename "$p" .hcl)" "$p" >/dev/null && echo "policy $(basename "$p" .hcl)"
done
