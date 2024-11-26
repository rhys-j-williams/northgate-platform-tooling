# platform-tooling — Delivery tooling for CSWT

Owner: Platform Engineering (Jira `TOOL`, `#cswt-platform`). GIS AppSec owns the scanner rules
and the Vault policies and is a required reviewer on them (CODEOWNERS). This directory is not an
application. It is the pipeline every CSWT repository's `Jenkinsfile` calls, the scanners that
produce the findings those pipelines fail on, the deployment packaging, and the governance
documents the other repositories link to.

If you are here because a build is red: `docs/runbooks/pipeline-quality-gate-failed.md`.
If you are here because a dependency will not install: `governance/DEPENDENCY_POLICY.md` s6.
If you are here because you were told the agent label is out of support:
`jenkins-shared-library/README.md`, "Agent labels", and then MBZ-2231.

## Map

| directory | what | owner |
|---|---|---|
| `jenkins-shared-library/` | `meridianNodePipeline` and `meridianJavaPipeline`, `Jenkinsfile.release`, the classes behind the quality gates, and the agent label inventory | Platform Eng |
| `mock-scanners/` | `cx`, `sonar-scanner`, `xray` emulations the library calls when the real ones are unreachable (ADR-0012); GIS-maintained ruleset and advisory mirror | Platform Eng / GIS |
| `helm/` | one chart per deployable, values for dev, uat, prod | Platform Eng, charts co-owned by the service team |
| `openshift/` | the one remaining `DeploymentConfig` template (statements-api, dev only) | Documents team, reluctantly |
| `docker/` | the shared Dockerfiles (ADR-0007) and the nginx config with the GIS-STD-014 headers and CSP | Platform Eng |
| `ansible/` | build agent baseline: nginx hardening, log forwarder | Platform Eng |
| `vault/` | per-service policies, agent config, secret templates | GIS Secrets Management |
| `registry/` | Artifactory configuration samples and inventory; local Verdaccio equivalent | Platform Eng |
| `terraform/` | Beacon landing zone stub. No application code. | Platform Eng for the Beacon programme |
| `governance/` | release calendar, CAB template, AI-assisted code policy, dependency policy | Release Mgmt / Platform Eng / Tech Risk |
| `docs/` | ADRs, runbooks, PR templates | |

## How the pieces connect

A repository's `Jenkinsfile` is three lines calling `meridianNodePipeline` with an agent label, a
Node version and a coverage threshold. The library runs the stages, calls `cx`, `sonar-scanner`
and `xray` from `PATH` (real binaries on the build VLAN, `mock-scanners/bin` elsewhere), reads
their reports through `ScannerReport` and fails on `QualityGate`, builds the image with the
Dockerfile from `docker/` and `NODE_VERSION` from the repo's `.nvmrc`, packages the chart from
`helm/<app>`, and deploys with `oc` and `helm upgrade`. Secrets come from Vault at every step;
nothing in this directory has one. `Jenkinsfile.release` wraps the same pipeline with the CAB
reference check and the freeze window from `governance/RELEASE_CALENDAR.md`.

## State of things, honestly

- Three Node majors on the agents (14, 16, 18) and three RHEL majors under them. The RHEL 7 /
  Node 14 pair is out of support and stays until business-web moves. Everyone knows.
- The freeze window logic in `ReleaseGuard` approximates the calendar. TOOL-1155.
- `statements-api` deploys two different ways depending on environment. DOC-1988.
- The Beacon Terraform is a stub waiting on a landing zone that has been "next quarter" for
  three quarters.
- Python services have no pipeline. TOOL-1444.
- The AI-assisted code hooks the policy describes (TOOL-1502, 1503, 1504) are partly built. The
  PR template exists; the commit hook is in review; the ReleaseGuard check is not started.

## Verification

Everything here is checked by the `platform-tooling` Jenkins job on every PR, and can be run
locally:

```
jenkins-shared-library/run-tests.sh          # groovyc + JUnit specs
mock-scanners/run-tests.sh                   # scanner fixtures, determinism
for c in helm/*/; do for e in dev uat prod; do helm lint --strict "$c" -f "$c/values-$e.yaml"; done; done
ansible-playbook -i ansible/inventory/build-agents.ini ansible/build-agent.yml --syntax-check
(cd terraform/modules/notifications-lambda && terraform init -backend=false && terraform validate)
../scripts/check-forbidden-strings.sh worktree
```

Ticket history for this directory starts at TOOL-1 (2020-11, "stand up Jenkins shared library")
and the interesting parts are in `git log`. The build log for the demo estate build is in
`_demo-notes/build/logs/platform-tooling.md`.
