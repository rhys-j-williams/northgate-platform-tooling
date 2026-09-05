# Vault

Owner: GIS Secrets Management (`gis-appsec` in CODEOWNERS). Platform Engineering maintains the
agent configuration and the chart wiring; GIS owns the policies and the auth roles. A change to
anything under `policies/` needs a GIS reviewer and, for prod, a CAB record (see
`../governance/CAB_TEMPLATE.md`).

## Vault is the source of truth

HashiCorp Vault Enterprise (1.15.x, the `cswt` namespace on the shared enterprise cluster at
`vault.meridian.internal`) is the system of record for every application secret in CSWT. This is
bank standard GIS-STD-030, not a team preference. Consequences:

- Nothing is created directly in a cloud secrets manager, a Kubernetes Secret, a Jenkins
  credential store or a `.env` file. Those are all caches. When the Beacon landing zone
  (`../terraform/`) grows a real workload, AWS Secrets Manager entries will be *fed from Vault* by
  the sync job GIS runs (the same pattern the Bedrock adapter uses through `aws/creds`), and the
  Vault path stays the authoritative copy that audit reads.
- Rotation happens in Vault and propagates. Rotating a password in the consuming system and not
  in Vault is how INC0048213 started.
- Access is by identity, not by shared token. Services authenticate with their Kubernetes service
  account (`auth/kubernetes-cswt`), CI with an approle, humans through the PAM tool. The policy
  templates use the service account namespace so one policy per service covers dev, uat and prod
  without a dev token ever being able to read prod (GIS-STD-030 s4.2).
- Audit device is Splunk, index `vault_audit`. GIS reviews the `gis-secrets-admin` usage weekly.

## What is in this directory

| path | what |
|---|---|
| `policies/<service>.hcl` | one ACL policy per deployable, plus `jenkins-cswt` and `gis-secrets-admin` |
| `vault-agent.hcl` | agent config for the non-injector cases (VM jobs, local dev) |
| `templates/*.ctmpl` | consul-template files that render secrets to `/vault/secrets/*.env` |
| `dev-seed.sh` | seeds a `vault server -dev` with `CHANGEME-*` values so templates render locally |

The Helm charts carry the injector annotations that do the same job in the cluster; keep the two
in step (TOOL-1377 is the ticket for generating one from the other, unassigned since 2023).

## Secrets never appear in the repo

Every value in this directory and in the charts is a path or a `CHANGEME-<purpose>` placeholder.
The Checkmarx ruleset (`../mock-scanners/rules/checkmarx-rules.json`, CX-SEC-*) fails the build on
anything that looks like a real credential, and the `.npmrc` sample in `../registry/` shows the
approved way to reference a token. If you need a new secret:

1. TOOL ticket, tagged `secrets`. Say what it is, who reads it, what rotates it.
2. GIS adds the KV path and, if needed, a line to the service policy. Prod values are entered by
   GIS through the PAM session, never by the team.
3. Add the template block (here and in the chart values `vault.secrets` list).
4. The entrypoint sources `/vault/secrets/*.env`; nothing else to do in the application.

## Known issues and history

- `pii-vault-service` is the only policy with transit access. It has been asked for by two other
  teams and refused both times; the answer is to call pii-vault-service.
- `beacon-notifications` reads the shared MQ credential read-only after INC0048213 (a rotation
  it performed clobbered txn-posting's connection).
- The `gis-secrets-admin` policy has `sudo` on lease revocation. That was needed during the 2023
  incident and nobody has removed it. GIS-4102.
- Vault Agent's `static_secret_render_interval` is 5 minutes. Services that cache the file at
  startup (documents-service does) will not see a rotation until restart. Documented, not fixed.
- The enterprise namespace is `cswt`. Some older docs say `retail`; that namespace was merged in
  2022 (GIS-2200).
