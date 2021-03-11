# Helm charts

Owner: Platform Engineering. Each deployable in the CSWT estate has one chart here, named after the
repository or service directory it deploys. The Jenkins shared library packages the chart from
this directory at `Helm package` and runs `helm upgrade --install` at `Deploy` with the values file
for the target environment (`values-<env>.yaml`) plus `--set image.tag=<build tag>`.

Charts are versioned independently of the applications (`version` is the chart, `appVersion` is
the last known application release; the pipeline overrides it). Bump the chart version when a
template changes, not when the application does. `helm lint --strict` runs in the platform-tooling
job for every chart against all three values files.

## Layout

Every chart renders the same set of objects: Deployment, Service, Route (optional), a ConfigMap
carrying `env.json`, NetworkPolicy, HorizontalPodAutoscaler and PodDisruptionBudget. Back end
service charts add a ServiceAccount (one per service, bound to a Vault Kubernetes auth role) and
the Vault Agent injector annotations. The two document services also have a PVC.

Front end charts (retail-web, business-web, keystone-web, ledgerline-web, canopy-showcase,
iris-widget) run the unprivileged nginx image from `platform-tooling/docker/`. Their `env.json` is
mounted over `/opt/app-root/src/env.json` and is the only runtime configuration the Angular
application receives. The CSP `connect-src` list is templated from values because it differs per
environment; the rest of `nginx.conf` is baked into the image.

Back end charts render the same `env` map twice: as individual ConfigMap keys consumed through
`envFrom`, and as `env.json` mounted at `/etc/meridian/env.json` for the Node services that read a
file. Secrets are never in values. Vault Agent renders them to `/vault/secrets/*.env` and the
container sources them before exec'ing the image entrypoint. See `../vault/README.md`.

## Environments

| values file | namespace | notes |
|---|---|---|
| `values-dev.yaml` | `cswt-dev` | one replica, `pullPolicy: Always`, no PDB, relaxed network policy |
| `values-uat.yaml` | `cswt-uat` | prod topology at half scale, HPA on |
| `values-prod.yaml` | `cswt-prod` | CAB controlled, WAF allow list on routes, MQ consumers not autoscaled |

Namespace level default deny, egress firewall and the ingress policy group label are managed by
the cluster team (OCP-2201), not by these charts.

## Conventions and gotchas

- `meridian.bank/app-id` must match the CMDB record. The CMDB feed reads that label and nothing
  else (CMDB-4410). If a chart has the wrong id the service disappears from the DR inventory.
- `readOnlyRootFilesystem: true` on the nginx charts needs the three emptyDir mounts in the
  Deployment. Removing one breaks the pod on start with a confusing permission error (OCP-3318).
- The Route annotation `hsts_header` is mandatory for anything internet facing (GIS-STD-014).
- `helm.sh/chart` label uses `replace "+" "_"` because the label validator rejects `+`.
- Do not add `helm.sh/hook` jobs for Flyway. Migrations run on application start behind the
  startup probe; the hook approach was tried in 2022 and rolled back after it ran against prod
  during a failed release (INC0041377).
- `statements-api` also has a legacy DeploymentConfig template in `../openshift/`. Dev uses that,
  uat and prod use this chart. DOC-1988 is meant to retire the template.

## Testing a change

```
helm lint --strict <chart> -f <chart>/values-prod.yaml
helm template <chart> <chart> -f <chart>/values-prod.yaml --set image.tag=local | oc apply --dry-run=server -f -
```

The dry run needs a logged in `oc` against the target cluster; from a laptop that means the VPN
and the `cswt-dev` project.
