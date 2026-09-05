# Vault policy: audit-trail-service
# Owner: GIS Secrets Management (platform-tooling CODEOWNERS routes this to gis-appsec).
# Bound to the Kubernetes auth role "audit-trail-service" in the cswt-{dev,uat,prod} namespaces via
# auth/kubernetes-cswt. The namespace in the path comes from the service account, so one policy
# serves all three environments without a prod policy being able to read dev (GIS-STD-030 s4.2).

path "secret/data/cswt/{{identity.entity.aliases.auth_kubernetes_cswt.metadata.service_account_namespace}}/audit-trail-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/cswt/{{identity.entity.aliases.auth_kubernetes_cswt.metadata.service_account_namespace}}/audit-trail-service/*" {
  capabilities = ["list", "read"]
}

# Every service can read the shared CA bundle and the Splunk HEC token for its namespace.
path "secret/data/cswt/{{identity.entity.aliases.auth_kubernetes_cswt.metadata.service_account_namespace}}/shared/ca-bundle" {
  capabilities = ["read"]
}
path "secret/data/cswt/{{identity.entity.aliases.auth_kubernetes_cswt.metadata.service_account_namespace}}/shared/splunk-hec" {
  capabilities = ["read"]
}

path "auth/token/renew-self" {
  capabilities = ["update"]
}
path "auth/token/lookup-self" {
  capabilities = ["read"]
}

# Dynamic database credentials. Static creds were removed in GIS-2988 for the ledger services.
path "database/creds/audit-trail-service-{{identity.entity.aliases.auth_kubernetes_cswt.metadata.service_account_namespace}}" {
  capabilities = ["read"]
}
