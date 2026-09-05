Consul-template files used by `../vault-agent.hcl` and mirrored in the Helm charts' injector
annotations. When you change one, change the other; there is no generator (TOOL-1377 wants one).

Expected KV v2 layout, all under the `cswt` Vault namespace:

```
secret/cswt/<k8s namespace>/<service>/app-secrets       arbitrary keys, all exported
secret/cswt/<k8s namespace>/<service>/db-credentials    username, password  (static services)
database/creds/<service>-<k8s namespace>                 dynamic (txn-posting, audit-trail)
secret/cswt/<k8s namespace>/<service>/redis-credentials  password, tls_ca
secret/cswt/<k8s namespace>/<service>/idp-client         client_id, client_secret
secret/cswt/<k8s namespace>/shared/mq-credentials        user, password, cipher_suite
secret/cswt/<k8s namespace>/shared/ca-bundle             pem
secret/cswt/<k8s namespace>/shared/splunk-hec            token
```

Local dev: `../dev-seed.sh` writes CHANGEME values to a dev-mode Vault on :8200 so the templates
render. Nothing in it is a credential.
