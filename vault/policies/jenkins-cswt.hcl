# Vault policy: jenkins-cswt
# The CI approle. Reads build-time secrets only: registry credentials, the Sonar and Checkmarx
# tokens, the OpenShift deployer service account tokens. It cannot read application secrets;
# the pipeline never needs them and GIS-2911 is why it was ever asked.

path "secret/data/cswt/build/artifactory/*" {
  capabilities = ["read"]
}
path "secret/data/cswt/build/sonarqube/token" {
  capabilities = ["read"]
}
path "secret/data/cswt/build/checkmarx/*" {
  capabilities = ["read"]
}
path "secret/data/cswt/build/splunk/hec-token" {
  capabilities = ["read"]
}
path "secret/data/cswt/build/openshift/deployer-*" {
  capabilities = ["read"]
}
# Release pipeline writes the CAB evidence bundle hash so the change record can be verified later.
path "secret/data/cswt/build/release-evidence/*" {
  capabilities = ["create", "update", "read"]
}
path "auth/token/renew-self" {
  capabilities = ["update"]
}
