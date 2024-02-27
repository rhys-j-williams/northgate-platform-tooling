# Vault policy: gis-secrets-admin
# Break glass and rotation. Held by the GIS Secrets Management on-call via the PAM tool; the
# approle for it is not in Jenkins. Every use is alerted (GIS-STD-030 s7).
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "sys/policies/acl/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "auth/kubernetes-cswt/role/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "database/roles/*" {
  capabilities = ["read", "list"]
}
path "database/rotate-root/*" {
  capabilities = ["update"]
}
path "sys/leases/revoke-prefix/*" {
  capabilities = ["update", "sudo"]
}
