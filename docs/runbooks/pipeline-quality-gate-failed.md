# Runbook: a CSWT pipeline failed at a quality gate

For the engineer whose build went red. Platform on-call: `#cswt-platform`, or the pager if it is
release night. Owner: Platform Engineering.

## Which gate

The stage name tells you. `Sonar`, `Checkmarx`, `Dependency audit` (Xray), `Unit tests`
(coverage threshold). The build description carries the report link. The report is also archived
under the build's artifacts (`.cx-reports/`, `.sonar-reports/`, `.xray-reports/`,
`coverage/`).

## Checkmarx (cx)

1. Open `report.html`. Findings are grouped by rule; each has the rule id (`CX-*`), CWE, file and
   line, and the matched text.
2. High or Critical fails the build. Medium and Low do not, but they accumulate in the CAB record.
3. False positive: add a suppression to the repository's `checkmarx.yml` with the rule id, the
   path, a `reason` carrying the GIS exception id and an `expires` date (see
   `mock-scanners/README.md`). Expired suppressions are ignored and the finding comes back. GIS
   reviews suppressions monthly.
4. True positive: fix it. If the finding is a `bypassSecurityTrust*` you believe is needed, that is
   a GIS exception with an expiry, not a suppression (SECURITY.md item 4).
5. The emulated `cx` and the real Checkmarx do not always agree. If the real scan found something
   the local one did not, the local ruleset is behind; tell GIS. If the local one found something
   the real one did not, still fix it.

## Sonar

1. `quality-gate.json` lists the conditions that failed. Coverage is the usual one.
2. Coverage threshold comes from the job's `COVERAGE_THRESHOLD` parameter, default 80 on new
   code, and the library reads `coverage/lcov.info` or `jacoco.xml`. No coverage file at all
   reads as 0%, which is what happens when the test stage was skipped or Karma crashed silently
   (TOOL-1409: check the Karma log for `Disconnected`, it is nearly always Chrome running out of
   memory on the nodejs14 agents).
3. Lint findings map to Sonar issues. `error` severity is a blocker.
4. Do not lower the threshold in the Jenkinsfile to get a build green. Raise it with the team
   lead; the CAB asks about coverage drops.

## Xray

1. `report.json` lists advisories by package and version, with the path through the tree for
   transitives.
2. High or Critical fails. `MERIDIAN-EOL-*` findings are informational and do not fail; they do
   appear in section 5 of the CAB record.
3. Fix by upgrading the direct dependency within the estate version map, by an `overrides` entry
   with a ticket and expiry, or by a GIS exception (`governance/DEPENDENCY_POLICY.md` s5). Not by
   `npm audit fix --force`, which will move an Angular major and get the PR closed.
4. If the advisory is for a package you cannot upgrade because the framework version pins it
   (this is common in the Angular 14 repositories), that is exactly the conversation
   DEPENDENCY_POLICY.md s4 "end of life" is trying to start. Raise it in the team, record the
   exception, and note it in the CAB record.

## When it is the platform's fault

- All builds on one label failing at the same stage at the same time: agent problem, page us.
- `cx: rules file not found` or a `sonar-scanner` crash with a stack trace: library or scanner
  bug, TOOL ticket with the build URL.
- Gate passed locally with the emulated scanner, failed in the pipeline with the real one: see
  Checkmarx step 5.
