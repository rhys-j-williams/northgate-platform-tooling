#!/usr/bin/env python3
"""Generates manifest.json for the platform-tooling history replay. Estate construction tooling."""
import json
from datetime import date, timedelta
from pathlib import Path

P = "platform-tooling/"
J = P + "jenkins-shared-library/"
SRC = J + "src/com/meridian/pipeline/"
TST = J + "test/com/meridian/pipeline/"
MS = P + "mock-scanners/"
H = P + "helm/"
V = P + "vault/"

# platform engineering
LEAD = "d.okafor"          # charlotte, team lead
TN = "t.nakamura"          # jersey city, jenkins / groovy
PV = "p.venkatesan"        # chennai, helm / openshift
KS = "k.subramani"         # chennai, ansible / agents
GM = "g.mwangi"            # chester, containers / nginx
BA = "b.arceneaux"         # plano, registry / terraform
OL = "o.lindqvist"         # chester, scanners
# GIS
SW = "s.whitfield"         # GIS AppSec, rules
FA = "f.adeyemi"           # GIS secrets management
# release / governance
MC = "m.calderon"          # release management
BOT = "meridian-dependency-bot"

commits = []


def weekday(d: date) -> date:
    while d.weekday() >= 5:
        d += timedelta(days=1)
    return d


def c(d, author, message, paths=None, body=None, empty=False, time=None):
    d = weekday(date.fromisoformat(d))
    entry = {"date": d.isoformat() + ("T" + time if time else "T00:00:00"), "author": author,
             "message": message}
    if body:
        entry["body"] = body
    if empty or not paths:
        entry["empty"] = True
    else:
        entry["paths"] = paths
    commits.append(entry)


def merge(d, train, author=MC):
    c(d, author, "Merge release/{0} into main for train {0}".format(train), empty=True)


# ---------------------------------------------------------------- 2020: scaffold
c("2020-11-09", LEAD, "TOOL-1 stand up the CSWT Jenkins shared library",
  [P + ".editorconfig", P + ".gitattributes", P + ".gitignore", J + "vars/meridianNodePipeline.groovy"],
  body="Replaces the copy-pasted Jenkinsfiles in retail-web and business-web with one\ndeclarative pipeline. Registered on the controller as meridian-pipeline, default branch develop.")
c("2020-11-10", TN, "TOOL-4 pipeline defaults class and agent label inventory",
  [SRC + "MeridianDefaults.groovy", SRC + "AgentLabels.groovy"])
c("2020-11-12", TN, "TOOL-4 spec for AgentLabels, wire up run-tests.sh",
  [TST + "AgentLabelsSpec.groovy", J + "run-tests.sh"])
c("2020-11-16", BA, "TOOL-7 sample .npmrc pointing at npm-virtual",
  [P + "registry/npmrc.sample"],
  body="Artifactory is live. Nobody should have registry.npmjs.org in their npmrc after this week.")
c("2020-11-17", TN, "TOOL-9 npm-login helper script for the registry stage",
  [J + "resources/com/meridian/pipeline/scripts/npm-login.sh"])
c("2020-11-19", LEAD, "TOOL-12 CODEOWNERS, contributing guide, PR template",
  [P + "CODEOWNERS", P + "CONTRIBUTING.md", P + ".github/pull_request_template.md"])
c("2020-11-24", BA, "TOOL-15 Maven settings.xml with mirrorOf * to maven-virtual",
  [P + "registry/settings.xml"])
c("2020-12-01", TN, "TOOL-18 checkout and install stages honour NODE_VERSION parameter")
c("2020-12-03", LEAD, "TOOL-20 first cut of the dependency policy",
  [P + "governance/DEPENDENCY_POLICY.md"],
  body="Written after the third time someone asked why their package 404s. Section 6 came later.")
c("2020-12-08", TN, "TOOL-22 unit test stage archives coverage/lcov.info")
c("2020-12-15", LEAD, "TOOL-24 README for the library, controller details and label table",
  [J + "README.md"])

# ---------------------------------------------------------------- 2021
c("2021-01-05", TN, "TOOL-31 ScannerReport reader for Checkmarx and Sonar JSON",
  [SRC + "ScannerReport.groovy", TST + "ScannerReportSpec.groovy"])
c("2021-01-07", TN, "TOOL-31 QualityGate fails the build on high severity findings",
  [SRC + "QualityGate.groovy", TST + "QualityGateSpec.groovy"])
c("2021-01-11", SW, "GIS-1502 checkmarx.yml preset and exclusions for the tooling repo",
  [P + "checkmarx.yml", P + "sonar-project.properties"])
c("2021-01-13", LEAD, "TOOL-33 SECURITY.md against GIS-STD-014 rev 7", [P + "SECURITY.md"])
c("2021-01-14", LEAD, "TOOL-33 data classification statement", [P + "DATA_CLASSIFICATION.md"])
c("2021-01-19", TN, "TOOL-36 Sonar stage waits for the quality gate")
c("2021-01-26", PV, "TOOL-40 first Helm chart: retail-web", [H + "retail-web"],
  body="Deployment, Service, Route, ConfigMap for env.json. NetworkPolicy to follow once GIS\nagree the egress list.")
c("2021-01-28", PV, "TOOL-40 business-web chart", [H + "business-web"])
c("2021-02-02", TN, "TOOL-44 meridianJavaPipeline for the Spring services",
  [J + "vars/meridianJavaPipeline.groovy"])
c("2021-02-04", TN, "TOOL-44 Java pipeline reads JaCoCo xml for the coverage gate")
c("2021-02-09", PV, "TOOL-47 charts for bff-retail and bff-business", [H + "bff-retail", H + "bff-business"])
c("2021-02-11", GM, "TOOL-49 nginx.conf with GIS-STD-014 headers for the Angular runtime image",
  [P + "docker/nginx/nginx.conf"])
c("2021-02-16", PV, "TOOL-51 statements-api chart",
  [H + "statements-api"])
c("2021-02-18", PV, "DOC-1988 legacy OpenShift template for statements-api dev",
  [P + "openshift/statements-api-template.yaml", P + "openshift/README.md"],
  body="The documents team deploy dev by hand with oc process. This is that, checked in so we can\nat least see it. UAT and prod go through the chart.")
c("2021-02-23", TN, "TOOL-53 Helm package and oc deploy stages")
c("2021-02-25", MC, "TOOL-55 CAB submission template", [P + "governance/CAB_TEMPLATE.md"])
merge("2021-03-04", "2021.03.1")
c("2021-03-09", TN, "TOOL-58 meridianNotify step, chat and email on failure",
  [J + "vars/meridianNotify.groovy", SRC + "Notifier.groovy", TST + "NotifierSpec.groovy"])
c("2021-03-11", PV, "TOOL-60 NetworkPolicy templates for every chart",
  [H + "README.md"])
c("2021-03-16", FA, "GIS-1611 Vault policies for bff-retail and bff-business",
  [V + "policies/bff-retail.hcl", V + "policies/bff-business.hcl"])
c("2021-03-17", FA, "GIS-1611 jenkins-cswt policy, read-only on the CI paths", [V + "policies/jenkins-cswt.hcl"])
c("2021-03-18", FA, "GIS-1611 vault-agent.hcl and env file templates",
  [V + "vault-agent.hcl", V + "templates"])
c("2021-03-23", FA, "GIS-1611 Vault README: Vault is the source of truth", [V + "README.md"])
merge("2021-03-25", "2021.03.2")
c("2021-04-06", TN, "TOOL-66 ReleaseGuard with the quarter-end freeze window",
  [SRC + "ReleaseGuard.groovy", TST + "ReleaseGuardSpec.groovy"])
c("2021-04-08", TN, "TOOL-66 Jenkinsfile.release requires a CAB reference",
  [J + "Jenkinsfile.release"])
c("2021-04-13", KS, "TOOL-70 ansible baseline for the build agents",
  [P + "ansible/ansible.cfg", P + "ansible/build-agent.yml", P + "ansible/inventory",
   P + "ansible/group_vars/all.yml", P + "ansible/group_vars/build_agents.yml", P + "ansible/roles/build_agent_common"])
c("2021-04-15", KS, "TOOL-70 nginx_hardening role for the artifact cache vhost",
  [P + "ansible/roles/nginx_hardening"])
c("2021-04-20", KS, "TOOL-72 log_forwarder role, Splunk UF with HEC token from Vault",
  [P + "ansible/roles/log_forwarder", P + "ansible/requirements.yml"])
c("2021-04-22", KS, "TOOL-72 ansible README", [P + "ansible/README.md"])
merge("2021-04-29", "2021.05.1")
c("2021-05-11", PV, "TOOL-78 keystone-web and ledgerline-web charts", [H + "keystone-web", H + "ledgerline-web"])
c("2021-05-18", FA, "GIS-1702 policies for statements-api and documents-service",
  [V + "policies/statements-api.hcl", V + "policies/documents-service.hcl"])
c("2021-05-25", TN, "TOOL-81 coverage threshold parameter, default 80 on new code")
merge("2021-06-10", "2021.06.1")
c("2021-06-15", MC, "TOOL-84 freeze dates for H2 in the release calendar")
c("2021-06-22", PV, "TOOL-86 HPA and PDB for the web charts")
c("2021-07-06", TN, "TOOL-88 xray audit stage after install, gate on high")
c("2021-07-13", PV, "TOOL-90 documents-service chart with the statements PVC", [H + "documents-service"])
c("2021-07-20", TN, "TOOL-92 fix: Sonar stage swallowed the exit code when the report was missing")
merge("2021-07-22", "2021.07.2")
c("2021-08-03", BA, "TOOL-95 repositories.json inventory of the Artifactory virtuals",
  [P + "registry/repositories.json"])
c("2021-08-10", TN, "TOOL-97 formatting sweep across vars and src, no behaviour change")
c("2021-08-24", PV, "TOOL-99 alerts-preferences-service and txn-posting-service charts",
  [H + "alerts-preferences-service", H + "txn-posting-service"])
merge("2021-09-02", "2021.09.1")
c("2021-09-14", GM, "TOOL-612 shared Angular Dockerfile, multi stage, NODE_VERSION from .nvmrc",
  [P + "docker/angular/Dockerfile", P + "docs/adr/ADR-0007-shared-dockerfiles.md"],
  body="ADR-0007. Repositories lose their own Dockerfiles. Runtime is the unprivileged UBI nginx\nfrom docker-redhat-remote, 8080, uid 1001.")
c("2021-09-16", GM, "TOOL-612 java runtime Dockerfile and entrypoint sourcing Vault env files",
  [P + "docker/java"])
c("2021-09-21", GM, "TOOL-614 security-headers.inc so location blocks keep the headers",
  [P + "docker/nginx/security-headers.inc"])
c("2021-09-23", TN, "TOOL-615 container build stage uses the shared Dockerfile with -f")
c("2021-09-28", GM, "TOOL-616 docker README", [P + "docker/README.md"])
merge("2021-09-30", "2021.10.1")
c("2021-10-12", FA, "GIS-1820 policies for alerts-preferences, txn-posting, pii-vault, audit-trail",
  [V + "policies/alerts-preferences-service.hcl", V + "policies/txn-posting-service.hcl",
   V + "policies/pii-vault-service.hcl", V + "policies/audit-trail-service.hcl"])
c("2021-10-19", PV, "TOOL-620 pii-vault-service and audit-trail-service charts",
  [H + "pii-vault-service", H + "audit-trail-service"])
c("2021-11-02", TN, "TOOL-624 bundle-budget.js, warn when main.js grows more than 5 percent",
  [J + "resources/com/meridian/pipeline/scripts/bundle-budget.js"])
c("2021-11-09", KS, "TOOL-627 nodejs16-rhel8 agents added to the inventory")
merge("2021-11-11", "2021.11.2")
c("2021-11-30", TN, "TOOL-630 nodejs16 label supported by the Node pipeline")
c("2021-12-07", LEAD, "TOOL-632 README: nodejs16-rhel8 is the default label for new repos")

# ---------------------------------------------------------------- 2022
c("2022-01-11", BOT, "TOOL-640 bump ansible collections community.hashi_vault 3.0.0 -> 3.2.0")
c("2022-01-18", PV, "TOOL-642 entitlements-service and bedrock-adapter charts",
  [H + "entitlements-service", H + "bedrock-adapter"])
c("2022-01-25", FA, "GIS-1903 policies for entitlements-service and bedrock-adapter",
  [V + "policies/entitlements-service.hcl", V + "policies/bedrock-adapter.hcl"])
merge("2022-02-03", "2022.02.1")
c("2022-02-15", TN, "TOOL-648 Checkmarx stage runs incremental on PR builds")
c("2022-02-22", TN, "Revert \"TOOL-648 Checkmarx stage runs incremental on PR builds\"",
  body="Incremental scans missed the GIS-2418 pattern on retail-web PR 1412. Back to full scans\nuntil Checkmarx support come back to us. TOOL-651.")
c("2022-03-08", SW, "GIS-2911 remove ARTIFACTORY_TOKEN build arg, use a BuildKit secret",
  [P + "docker/angular/Dockerfile"],
  body="The token was visible in docker history on every image built since September. Rotated.\nSee the GIS incident record; this commit is the code half.")
c("2022-03-09", TN, "GIS-2911 pipeline passes .npmrc as --secret id=npmrc")
merge("2022-03-17", "2022.03.2")
c("2022-04-05", KS, "TOOL-660 RHEL 7 group_vars: TLS 1.2 only, rsyslog omhttp instead of the UF",
  [P + "ansible/group_vars/nodejs14_rhel7.yml"],
  body="GIS exception GIS-EX-2023-118 was raised for the TLS floor. The UF 9 build does not run on\nRHEL 7 so the nodejs14 agents forward with rsyslog. Not great.")
c("2022-04-19", PV, "TOOL-663 values-uat.yaml for every chart, replica counts from capacity review")
c("2022-05-10", TN, "TOOL-668 maven-jdk17-rhel9 label, Java pipeline picks JDK from the pom")
c("2022-05-12", GM, "TOOL-668 JDK_VERSION build arg on the java Dockerfile")
merge("2022-05-26", "2022.06.1")
c("2022-06-14", MC, "TOOL-672 release calendar: CAB moves to Tuesday, prod to Thursday")
c("2022-06-28", TN, "TOOL-675 formatting sweep: four space indent in Groovy, trailing commas")
c("2022-07-12", PV, "TOOL-678 canopy-showcase and iris-widget charts", [H + "canopy-showcase", H + "iris-widget"])
c("2022-07-26", BOT, "TOOL-680 bump ansible collections ansible.posix 1.3.0 -> 1.4.0")
merge("2022-08-04", "2022.08.1")
c("2022-08-16", PV, "TOOL-683 iris-orchestrator and exposure-calc charts", [H + "iris-orchestrator", H + "exposure-calc"])
c("2022-08-18", FA, "GIS-2044 policies for iris-orchestrator and exposure-calc",
  [V + "policies/iris-orchestrator.hcl", V + "policies/exposure-calc.hcl"])
c("2022-09-06", KS, "TOOL-686 nodejs18-rhel9 agents in the inventory")
c("2022-09-20", TN, "TOOL-688 nodejs18 label in AgentLabels")
merge("2022-09-29", "2022.10.1")
c("2022-10-11", LEAD, "TOOL-690 README: nodejs14-rhel7 marked out of support from 2023-04",
  body="Business-web are the only consumer. MBZ-2231 tracks their Node 16 move. Until then the\nlabel stays and the RHEL 7 image does not get refreshed.")
c("2022-11-01", GM, "TOOL-693 40-csp.sh renders connect-src from the environment",
  [P + "docker/nginx/40-csp.sh"])
c("2022-11-15", TN, "TOOL-695 CSP_CONNECT_SRC passed through from the chart values")
merge("2022-11-17", "2022.11.2")
c("2022-12-06", TN, "TOOL-697 fix: ReleaseGuard treated 31 December as outside the freeze")

# ---------------------------------------------------------------- 2023
c("2023-01-17", BA, "TOOL-702 Verdaccio config for local development, port 4873",
  [P + "registry/verdaccio", P + "registry/npmrc.local.sample", P + "registry/settings.local.xml"])
c("2023-01-24", BA, "TOOL-702 registry README with the common failure modes", [P + "registry/README.md"])
merge("2023-02-02", "2023.02.1")
c("2023-02-14", KS, "TOOL-706 build-agent.yml asserts the RHEL major and warns on 7")
c("2023-03-07", BOT, "TOOL-710 bump ansible collections community.hashi_vault 3.2.0 -> 4.2.1")
merge("2023-03-16", "2023.03.2")
c("2023-04-03", OL, "TOOL-1301 mock-scanners: cx emulation with the GIS regex ruleset",
  [MS + "bin/cx", MS + "lib/common.js", P + "docs/adr/ADR-0012-mock-scanners-in-repo.md"],
  body="ADR-0012. Engineers cannot reach Checkmarx from a laptop, so the gate logic had no local\nreproduction. Deterministic, same report shape, same exit codes.")
c("2023-04-04", SW, "GIS-3120 checkmarx-rules.json revision 1, the obvious things list",
  [MS + "rules/checkmarx-rules.json"])
c("2023-04-06", OL, "TOOL-1301 sonar-scanner emulation reading lcov, jacoco and lint output",
  [MS + "bin/sonar-scanner"])
c("2023-04-11", OL, "TOOL-1301 xray emulation over npm and Maven trees", [MS + "bin/xray"])
c("2023-04-12", SW, "GIS-3120 offline advisory mirror for xray", [MS + "rules/advisories.json"])
c("2023-04-13", OL, "TOOL-1301 fixtures: an Angular app and a Java service with planted findings",
  [MS + "fixtures"])
c("2023-04-18", OL, "TOOL-1301 run-tests.sh for the scanners, determinism check",
  [MS + "run-tests.sh"])
c("2023-04-20", OL, "TOOL-1301 mock-scanners README", [MS + "README.md"])
c("2023-04-25", TN, "TOOL-1305 library resolves cx, sonar-scanner and xray from PATH")
merge("2023-04-27", "2023.05.1")
c("2023-05-16", SW, "GIS-3141 rules rev 7: disabled certificate validation in Java and Node clients")
c("2023-06-06", GM, "TOOL-1312 node service Dockerfile and entrypoint", [P + "docker/node"])
c("2023-06-13", SW, "GIS-3160 rules rev 12: wildcard CORS, disabled CSRF, insecure cookie flags")
merge("2023-06-15", "2023.06.2")
c("2023-07-11", OL, "TOOL-1320 cx SARIF output for the code scanning upload")
c("2023-07-25", TN, "TOOL-1322 formatting sweep in test specs")
c("2023-08-08", BOT, "TOOL-1325 bump ansible collections ansible.posix 1.4.0 -> 1.5.4")
c("2023-08-22", SW, "GIS-3201 rules rev 19: private key material and registry tokens in source")
merge("2023-09-07", "2023.09.1")
c("2023-09-19", OL, "TOOL-1330 sonar-scanner: coverage on new code, not whole file")
c("2023-10-03", GM, "TOOL-1334 python Dockerfile on the approved UBI base", [P + "docker/python/Dockerfile"])
c("2023-10-17", TN, "TOOL-1336 fix: quality gate counted suppressed findings toward the threshold")
merge("2023-10-19", "2023.11.1")
c("2023-11-07", PV, "TOOL-1340 values-prod.yaml: PDB minAvailable and prod HPA ceilings")
c("2023-11-21", SW, "GIS-3250 rules rev 27: root containers and unpinned base images")
c("2023-12-05", TN, "TOOL-1343 fix: Java pipeline ignored COVERAGE_THRESHOLD when JaCoCo ran per module")

# ---------------------------------------------------------------- 2024
c("2024-01-16", KS, "TOOL-1350 GIS-EX-2023-118 expiry noted in the RHEL 7 group_vars")
c("2024-01-30", BOT, "TOOL-1352 bump ansible collections community.hashi_vault 4.2.1 -> 6.2.0")
merge("2024-02-01", "2024.02.1")
c("2024-02-13", PV, "TOOL-1355 beacon-notifications chart", [H + "beacon-notifications"])
c("2024-02-15", FA, "GIS-3302 beacon-notifications policy", [V + "policies/beacon-notifications.hcl"])
c("2024-02-27", FA, "GIS-3310 gis-secrets-admin policy and dev-seed.sh with CHANGEME placeholders",
  [V + "policies/gis-secrets-admin.hcl", V + "dev-seed.sh"])
merge("2024-03-14", "2024.03.2")
c("2024-04-09", SW, "GIS-3355 rules rev 34: debug artefacts and weakened CSP directives")
c("2024-04-23", OL, "TOOL-1362 xray: lifecycle findings for end of life runtimes, informational")
c("2024-05-07", TN, "TOOL-1365 pipeline-quality-gate-failed runbook",
  [P + "docs/runbooks/pipeline-quality-gate-failed.md"])
merge("2024-05-23", "2024.06.1")
c("2024-06-11", BA, "TOOL-1370 terraform: Beacon notifications Lambda landing zone stub",
  [P + "terraform/modules/notifications-lambda", P + "terraform/envs/sandbox"],
  body="Landing zone only. There is no application code yet and the account is not provisioned.\nAWS provider pinned at 5.31.0 as agreed with cloud platform.")
c("2024-06-13", BA, "TOOL-1370 terraform README", [P + "terraform/README.md"])
c("2024-06-25", MC, "TOOL-1372 release calendar: 2025 trains and freeze dates")
c("2024-07-09", SW, "GIS-3401 rules rev 41: sensitive data in log statements")
merge("2024-07-25", "2024.08.1")
c("2024-08-13", OL, "TOOL-1378 fix: cx walked node_modules when checkmarx.yml had no excludes")
c("2024-08-27", TN, "TOOL-1380 formatting sweep across vars, src and test")
c("2024-09-10", LEAD, "TOOL-1502 AI-assisted code policy TECH-POL-031 v1.0",
  [P + "governance/AI_ASSISTED_CODE_POLICY.md"],
  body="Second line signed off 2024-09-06. Control owners named in section 9. Commit trailer and\nPR template are TOOL-1503 and TOOL-1504.")
c("2024-09-17", LEAD, "TOOL-1504 PR review template for AI-assisted content",
  [P + "docs/templates/PR_REVIEW_AI.md"])
merge("2024-09-19", "2024.10.1")
c("2024-10-08", MC, "TOOL-1388 release calendar: 2026 trains and quarter-end freezes",
  [P + "governance/RELEASE_CALENDAR.md"])
c("2024-10-22", SW, "GIS-3442 advisories.json refresh")
c("2024-11-05", LEAD, "TOOL-1390 dependency policy v2.4: section 6 and the EOL paragraph")
c("2024-11-12", KS, "TOOL-1393 inventory: nodejs14-rhel7 still present, still out of support")
c("2024-11-19", SW, "GIS-3460 advisories.json refresh 2024-11-18")
merge("2024-11-21", "2024.12.1")
c("2024-11-26", LEAD, "TOOL-1395 top-level README and state of things", [P + "README.md"])
c("2024-12-03", TN, "TOOL-1397 .history manifest for the estate build", [P + ".history"])

Path(__file__).with_name("manifest.json").write_text(
    json.dumps({"component": "platform-tooling", "commits": commits}, indent=2) + "\n")
print(len(commits), "commits")
