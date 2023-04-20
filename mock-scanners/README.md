# mock-scanners

Owner: Platform Engineering (`@meridian/platform-engineering`). Rule content: GIS Application
Security (`@meridian/gis-appsec`), see `CODEOWNERS` at the component root.

Local, offline, deterministic stand-ins for the three security tools every CSWT pipeline calls:
the Checkmarx CLI (`cx`), the SonarQube scanner (`sonar-scanner`) and the Xray dependency audit
(`xray`). The Jenkins shared library calls whatever is at `MERIDIAN_SCANNER_BIN` (default
`/opt/meridian/scanners/bin`). On the bank network that mount holds the vendor CLIs. Everywhere
else (developer laptops, the demo environment, the DR rehearsal cluster) the agent image sets it to
a checkout of `platform-tooling/mock-scanners/bin` and gets these. Nobody should need to
change a `Jenkinsfile` to move between the two; if you do, that is a TOOL bug.

Why these exist at all: TOOL-1188. The vendor CLIs need network access to their servers and a
licence seat per agent. The pipeline authors kept commenting the stages out on their laptops and
then forgetting to put them back (GIS-3305 was the incident that followed). The mocks make the
stages always run, so the report shape and the quality gate behaviour are exercised on every
build, and the findings they raise are real findings in the code, not canned ones.

## Requirements

Node 14 or later, nothing else. `lib/common.js` has no dependencies on purpose; the agents
running `nodejs14-rhel7` cannot install anything from the public registry and the internal
registry mirror is not reachable from the demo boxes. Do not add a `package.json` here.

Everything is deterministic: identifiers are SHA-1 of the finding's location and rule, ordering
is fixed, timestamps come from `SOURCE_DATE_EPOCH` when it is set (the Jenkins library sets it to
the commit time). Two runs on the same tree produce byte identical `report.json`.

## `bin/cx`

```
cx scan --config checkmarx.yml [--source DIR] [--output DIR] [--fail-on high|medium|none] [--quiet]
cx rules
cx version
```

Reads the repository's `checkmarx.yml` (project name, team, excludes, thresholds, suppressions),
walks the source and applies `rules/checkmarx-rules.json`. Twenty rules at revision 41: sanitizer
bypass, `innerHTML`, hard coded credentials, PEM blocks, `strict-ssl=false`, npm tokens, `eval`,
disabled TLS verification, weak crypto, sensitive data in logs, wildcard CORS, CSRF disabled,
insecure cookies, concatenated SQL, root containers, unpinned base images, debug leftovers,
security TODOs and weakened CSP. Each carries a CWE and a remediation paragraph that GIS wrote for
the real preset; the identifiers (`CX-ANG-001` and so on) match the custom query names on the
Checkmarx server so a finding here can be looked up there.

Rules with `commentedSeverity` fire at a lower severity when the match is in a comment. That is
deliberate: a commented out `strict-ssl=false` in an `.npmrc` is reported at Medium, because it
tells you what someone did the last time the CA changed and what they will do next time.

Output: `report.json`, `report.html`, optionally `report.sarif`. Exit 1 when the thresholds in
`checkmarx.yml` are breached (default `high: 0`, `medium: 5`). `--fail-on` overrides the thresholds
for local runs; the pipeline does not pass it.

Suppressions in `checkmarx.yml` need a `reason` with a GIS exception id and an `expires` date.
Expired suppressions are ignored and the finding comes back. High findings need a real exception,
not a suppression, but the CLI cannot tell the difference so this is enforced at review.

## `bin/sonar-scanner`

```
sonar-scanner [-Dsonar.projectBaseDir=DIR] [-Dsonar.<key>=<value> ...] [-X]
```

Same invocation as the real scanner. Reads `sonar-project.properties`, counts lines, imports
coverage from whatever it finds first: `sonar.javascript.lcov.reportPaths`,
`sonar.coverage.jacoco.xmlReportPaths`, `coverage/coverage-summary.json`, `coverage/lcov.info`,
`coverage/<project>/...`, `target/site/jacoco/jacoco.xml`. Imports lint issues from
`sonar.eslint.reportPaths`, `sonar.java.checkstyle.reportPaths`, or the `lint.log` the pipeline's
Lint stage tees. ESLint rule ids are mapped onto the Sonar rule keys that the server would assign
(`no-eval` is `javascript:S1523`, a CRITICAL vulnerability, and so on; the table is in the script).

Quality gate is the "Meridian Way" gate, revision 7, as configured on `sonar.meridian.internal`:
coverage below 30 percent, duplication over 5 percent, any blocker or critical, any unreviewed
security hotspot, reliability worse than B or security worse than A fails it. With
`sonar.qualitygate.wait=true` a failed gate exits 1, which is what the Jenkins library relies on.
The real server evaluates on new code; we only see the whole project, so the thresholds here are the
relaxed legacy ones the Engineering Standards forum agreed for repositories older than 2022. This
is why a build can pass here and fail on the server. Known, TOOL-1402, will not fix.

Output: `.sonar-reports/report.json`, `report.html`, `quality-gate.json` (the
`api/qualitygates/project_status` shape) and `report-task.txt` in the format the Jenkins SonarQube
plugin polls. `ceTaskUrl` in that file points at the internal server and will not resolve outside
the bank; the shared library reads `quality-gate.json` rather than calling `waitForQualityGate`,
which is the whole reason that file exists.

## `bin/xray`

```
xray audit [--dir DIR] [--output DIR] [--fail-on critical|high|medium|low|none] [--online] [--quiet]
```

Reads `package-lock.json` (any lockfile version) or `target/dependency-tree.txt` (from
`mvn dependency:tree -DoutputFile=target/dependency-tree.txt`), falling back to declared
dependencies in `package.json` or `pom.xml` with a warning. Resolves every installed version
against `rules/advisories.json`, an offline snapshot of the Xray feed curated by GIS for the
components actually present in the CSWT estate. About 170 npm advisories and 70 Maven ones, with
the bank's own `MERIDIAN-EOL-*` lifecycle rules from GIS-STD-021 (Node 14, Angular 14, Spring
Boot 2, JDK 11 and friends) mixed in and reported at the severity GIS assigned them. The runtime
target is read from `.nvmrc`, `engines.node` or the pom's compiler target and audited the same way.

`--online` additionally runs `npm audit --json` and merges its output. On the bank network
Artifactory proxies the audit endpoint so this works; on a demo box it usually does not, and the
scanner says so and carries on. Determinism only holds offline.

Default gate: any Critical or High fails. Waivers are applied on the Xray server against the
`meridian-cswt-prod` watch, not here, so a waived finding still shows in the local report. That is
intentional and has been argued about (TOOL-1510). The lifecycle rules are the ones the
modernisation programme will want to look at first.

## Fixtures

`fixtures/angular-app` and `fixtures/java-service` are small deliberately dirty projects used by
`run-tests.sh` to check the scanners still find what they are supposed to find. They are not
examples of how to write anything. When a rule is added, add a line to a fixture that trips it and
a line that should not.

## Running against the estate

From the repository root:

```
platform-tooling/mock-scanners/bin/cx scan --config canopy-ui/checkmarx.yml --source canopy-ui
platform-tooling/mock-scanners/bin/sonar-scanner -Dsonar.projectBaseDir=canopy-ui
platform-tooling/mock-scanners/bin/xray audit --dir platform-services/libs/ts/domain-fixtures
```

Every component directory already carries a `checkmarx.yml` and `sonar-project.properties` that
point here. Report directories (`.cx-reports`, `.sonar-reports`, `.xray-reports`) are ignored by
each component's `.gitignore`; if one is not, that is the component's problem to fix, not ours.

`xray scan` still works as an alias for `xray audit` because the shared library was written
against the 3.x verb and nobody has updated both sides in the same release.

## Known issues

- `cx` is line based. A `bypassSecurityTrustHtml(` split across two lines is not found. The real
  product does data flow analysis and finds far more than this does; the rule count here is a
  fraction of the preset. Do not read a clean local scan as a clean Checkmarx scan.
- `sonar-scanner` duplication detection is a six line window hash. It under counts.
- `xray` semver handling ignores prerelease tags, so `2.0.0-rc.1` compares equal to `2.0.0`.
- Neither `cx` nor `sonar-scanner` follows symlinks. Ledgerline's `shared -> ../common` layout
  used to be missed until the excludes were fixed (TOOL-1377).
- HTML reports are self contained but unstyled beyond the basics. Nobody has asked for more.
