# openshift

Raw OpenShift objects that are not (yet) Helm charts. As of 2024 that is one file.

`statements-api-template.yaml` — legacy `Template` with a `DeploymentConfig`, used by the dev
namespace only. Process with `oc process -f ... -p IMAGE_TAG=<tag> | oc apply -f -`. The Helm
chart in `../helm/statements-api` is the supported path and is what uat and prod run. DOC-1988
retires this file; OCP-4471 (DeploymentConfig deprecation) is the deadline.

Anything else here is a mistake or a hotfix that should have been a chart change. If you find one,
raise a TOOL ticket rather than adding to it.

Validation: `oc process --local -f statements-api-template.yaml -p IMAGE_TAG=x | oc apply --dry-run=client -f -`
needs the `oc` binary; the platform-tooling job runs it with 4.14.
