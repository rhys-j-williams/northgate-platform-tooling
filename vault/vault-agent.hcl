# Vault Agent configuration for a CSWT service pod. The Helm charts inject the equivalent through
# the Vault injector annotations; this file is the sidecar-less variant used by the two VM hosted
# jobs (statements batch, exposure-calc nightly) and by developers running a service locally
# against the dev Vault namespace.
#
#   VAULT_ADDR=https://vault.meridian.internal SERVICE=bff-retail NAMESPACE=cswt-dev \
#     vault agent -config=platform-tooling/vault/vault-agent.hcl
#
# Owner: GIS Secrets Management. Template changes go through a TOOL ticket with a GIS reviewer.
# Environment variables are expanded by the wrapper script (envsubst); vault agent does not do it.

pid_file = "/tmp/vault-agent.pid"

vault {
  address         = "https://vault.meridian.internal"
  namespace       = "cswt"
  ca_cert         = "/etc/pki/tls/certs/meridian-root-ca.pem"
  tls_skip_verify = false
  retry {
    num_retries = 5
  }
}

auto_auth {
  # Kubernetes auth in the cluster. For the VM hosted jobs and laptops the wrapper swaps this for
  # the approle block below by setting AUTH_METHOD=approle.
  method "kubernetes" {
    mount_path = "auth/kubernetes-cswt"
    config = {
      role       = "${SERVICE}"
      token_path = "/var/run/secrets/kubernetes.io/serviceaccount/token"
    }
  }

  # method "approle" {
  #   mount_path = "auth/approle"
  #   config = {
  #     role_id_file_path                   = "/etc/vault/role-id"
  #     secret_id_file_path                 = "/etc/vault/secret-id"
  #     remove_secret_id_file_after_reading = true
  #   }
  # }

  sink "file" {
    config = {
      path = "/vault/token"
      mode = 0640
    }
  }
}

cache {
  use_auto_auth_token = true
}

listener "unix" {
  address     = "/vault/agent.sock"
  tls_disable = true
}

# One template per secret. The output is a shell env file that the container entrypoint sources
# before exec (see docker/*/entrypoint.sh). File mode 0640, owned by the app user.
# Adding a new secret means a new template block, a new path in the service policy, and a chart
# values change; there is no wildcard on purpose.

template {
  source      = "/etc/vault/templates/app-secrets.env.ctmpl"
  destination = "/vault/secrets/app-secrets.env"
  perms       = "0640"
  error_on_missing_key = true
  command     = "sh -c 'kill -HUP $(cat /tmp/app.pid 2>/dev/null) 2>/dev/null || true'"
}

template {
  source      = "/etc/vault/templates/db-credentials.env.ctmpl"
  destination = "/vault/secrets/db-credentials.env"
  perms       = "0640"
  error_on_missing_key = true
}

template {
  source      = "/etc/vault/templates/redis-credentials.env.ctmpl"
  destination = "/vault/secrets/redis-credentials.env"
  perms       = "0640"
}

template {
  source      = "/etc/vault/templates/mq-credentials.env.ctmpl"
  destination = "/vault/secrets/mq-credentials.env"
  perms       = "0640"
}

template {
  source      = "/etc/vault/templates/idp-client.env.ctmpl"
  destination = "/vault/secrets/idp-client.env"
  perms       = "0640"
}

template_config {
  exit_on_retry_failure = true
  static_secret_render_interval = "5m"
}
