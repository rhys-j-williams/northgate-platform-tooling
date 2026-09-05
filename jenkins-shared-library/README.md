# meridian-pipeline — Jenkins shared library

Owner: @meridian/platform-engineering. On call: `platform-eng-oncall` rota in the paging tool,
business hours only, the CI is not a Sev 1 service whatever the release desk says on a Thursday.
Chat: `#platform-engineering`. Tickets: `TOOL` project, component `jenkins-shared-library`.

This is the library every `Jenkinsfile` in the CSWT organisation loads:

```groovy
@Library('meridian-pipeline@v3') _
meridianNodePipeline(agentLabel: 'nodejs16-rhel8', nodeVersion: '16.20.2', appName: 'retail-web', ...)
```

If you are here because your build is red and you did not change anything, skip to
[When the library breaks your build](#when-the-library-breaks-your-build).

## Where it is registered

Controller: `jenkins.meridian.internal`, Jenkins LTS **2.440.3** (upgraded from 2.401.1 in
TOOL-1102, February 2026; the plugin freeze from that upgrade is still in place, ask before you
request a plugin). The library is registered globally under **Manage Jenkins > System > Global
Pipeline Libraries** as `meridian-pipeline`, source `ssh://git@bitbucket.meridian.internal:7999/tool/jenkins-shared-library.git`
in the bank, and this directory in the demo estate. Implicit load is **off**. Default version is
`v3`, which is a branch, not a tag; `v3` moves. Pin a tag (`v3.14.0`) in your Jenkinsfile if you
need reproducibility for an audit build, and expect to be asked why at the architecture forum.

`v2` is still registered for the four repositories that have not migrated (TOOL-1019 tracks the
stragglers). `v2` gets security fixes only. `v1` was removed in October 2024.

## Build agents

Agents are OpenShift pods from the `cswt-jenkins-agents` namespace, one per build, image built by
`platform-tooling/ansible` and pushed to `registry.meridian.internal/cswt/jenkins-agent-*`.

| Label | Runtime | Base OS | Status | Who uses it |
|---|---|---|---|---|
| `nodejs14-rhel7` | Node 14.21.3, npm 6.14.18, Chrome 109 | RHEL 7.9 | **Out of support** | business-web |
| `nodejs16-rhel8` | Node 16.20.2, npm 8.19.4, Chrome 120 | RHEL 8.10 | Supported | canopy-ui, retail-web, iris-widget, keystone-web |
| `nodejs18-rhel9` | Node 18.19.0, npm 10.2.3, Chrome 124 | RHEL 9.4 | Supported | ledgerline-web, bff-retail, bff-business, iris-orchestrator, documents-service, mock-external |
| `maven-jdk11-rhel8` | OpenJDK 11.0.28, Maven 3.9.9 | RHEL 8.10 | Supported | Spring Boot 2.7 services |
| `maven-jdk17-rhel9` | OpenJDK 17.0.13, Maven 3.9.9 | RHEL 9.4 | Supported | entitlements-service, this library's own CI |

There is no `nodejs20` label. TOOL-1301 has the request; it is waiting on a RHEL 9 Chrome package
that GIS will approve, and on someone needing it. Nothing in the estate builds on Node 20 today.

The `nodejs14-rhel7` situation, because it comes up every quarter: RHEL 7 left maintenance support
in June 2024 and the image has not had a base layer refresh since. It has open CVEs that GIS has
granted a risk acceptance for (GIS-RA-2024-0117, expires 31 December 2026, and they have said they
will not renew it a third time). We cannot retire the label because `business-web/Jenkinsfile`
pins it, and business-web cannot move to `nodejs16-rhel8` until it drops its Node 14 `engines`
pin and `engine-strict`, which the business digital team have on their backlog as MBZ-2988 and
have deprioritised three trains running. So the image refresh is blocked on the business-web team,
the risk acceptance is blocked on the image refresh, and this paragraph gets longer every quarter.
If you own business-web and are reading this: please.

`lantern-sdk` also builds on Node 14 but does so on `nodejs16-rhel8` with `nvm use 14` inside
the Install stage, because it has no `engine-strict` and nobody noticed. That is fine. Do not
copy it as a pattern.

## What the pipelines do

Both `meridianNodePipeline` and `meridianJavaPipeline` run the same stage list; the commands
differ.

| Stage | Node | Java | Gate |
|---|---|---|---|
| Checkout | `checkout scm`, sets `IMAGE_TAG` = short sha + build number | same | |
| Registry login | writes a workspace `.npmrc` for `npm-virtual` from Jenkins credentials | validates `settings.xml` against `maven-virtual` | |
| Install | `npm ci` | `mvn dependency:go-offline` | |
| Lint | `npm run lint` + GIS-1180 forbidden strings check | `checkstyle:checkstyle`, report only (PLAT-1330) | |
| Unit tests | Karma or Jest with coverage | `mvn verify` with JaCoCo | coverage below threshold: unstable on feature branches, **fails** on `release/*`, `main`, `hotfix/*` |
| Build | `ng build --configuration production`, bundle budget from `stats.json` | `mvn package` | |
| Sonar scan | `sonar-scanner` | `sonar-scanner` with JaCoCo XML | ERROR fails, WARN unstable |
| Checkmarx scan | `cx scan` | `cx scan` | any High **fails**; Medium above threshold (default 5) unstable |
| Dependency audit | `xray scan --type npm` | `xray scan --type maven` | any Critical or High **fails** |
| Container build | `podman build` on `develop`, `release/*`, `main`, `hotfix/*` | same | |
| Helm package | `helm lint` + `helm package` | same | |
| Deploy | `oc login` + `helm upgrade --install` to `cswt-<env>` | same | `develop` and `release/*` only, `dev`/`uat` only |

Production deploys never happen from a plain `Jenkinsfile`. They go through
[`Jenkinsfile.release`](Jenkinsfile.release), which adds a CAB reference parameter, a freeze window
check and a four eyes approval step before running the same stages with `TARGET_ENV=prod`. See
`governance/CAB_TEMPLATE.md` for what the CAB reference has to be.

### Parameters

Every job gets these whether it asked or not:

| Parameter | Default | Notes |
|---|---|---|
| `AGENT_LABEL` | from Jenkinsfile | must be in the table above; the library refuses unknown labels at load (TOOL-598) |
| `NODE_VERSION` | from Jenkinsfile | must match `.nvmrc`; the agent has nvm at `/opt/nvm` with the versions for its label preinstalled |
| `COVERAGE_THRESHOLD` | from Jenkinsfile, else 30 (Node) / 20 (Java) | integer percent, compared against `coverage-summary.json` `total.lines.pct` or the JaCoCo report level LINE counter |
| `SKIP_DEPLOY` | false | stop after Helm package |
| `TARGET_ENV` | dev | `dev` or `uat`; `prod` is only in `Jenkinsfile.release` |

Configuration keys the Jenkinsfile can pass are listed in
[`src/com/meridian/pipeline/MeridianDefaults.groovy`](src/com/meridian/pipeline/MeridianDefaults.groovy).
Cluster URLs, registry hosts and credential ids are deliberately not overridable per repository.

### Scanners

On a real agent the scanners are the vendor CLIs at `/opt/meridian/scanners/bin`. The library
finds them through `MERIDIAN_SCANNER_BIN`. In the demo estate that variable points at
`platform-tooling/mock-scanners/bin`, which has command line compatible emulations that read the
same `checkmarx.yml` and `sonar-project.properties` and write the same report shapes. The quality
gates cannot tell the difference, which is the point.

## Layout

```
vars/
  meridianNodePipeline.groovy    the Node pipeline (Angular apps, libraries, NestJS)
  meridianJavaPipeline.groovy    the Java pipeline (Spring Boot)
  meridianNotify.groovy          chat notifications
src/com/meridian/pipeline/
  MeridianDefaults.groovy        every default, every cluster and registry address
  AgentLabels.groovy             the label table, validated at load
  QualityGate.groovy             gate decisions, pure functions plus thin pipeline wrappers
  ScannerReport.groovy           typed view over Checkmarx and Xray JSON
  ReleaseGuard.groovy            CAB reference, branch and freeze checks for Jenkinsfile.release
  Notifier.groovy                notification payloads
resources/com/meridian/pipeline/scripts/
  npm-login.sh                   writes the scoped .npmrc from injected credentials
  bundle-budget.js               initial bundle size from Webpack stats.json
test/com/meridian/pipeline/      JUnit 4 specs over the pure classes
Jenkinsfile.release              the production release variant
run-tests.sh                     what CI runs
```

## Developing the library

```bash
./run-tests.sh            # groovyc src, test and vars, then JUnit
./run-tests.sh compile    # syntax only
```

Groovy 4.0.x on the path or `GROOVY_HOME` set. The controller runs Groovy 2.4 inside CPS, so:
no `java.time` in `vars/` (fine in `src/` as long as it is not serialised mid pipeline), no closures
captured across `sh` steps, `JsonSlurperClassic` not `JsonSlurper`, and everything `Serializable`.
The specs run under plain Groovy and do not exercise the CPS transform. If you change a `vars/`
file, also run it against the `platform-tooling-sandbox` job on the controller before merging,
because `groovyc` accepting it proves nothing about the sandbox accepting it.

Branch from `develop`, `feature/TOOL-<n>-...`, PR needs one platform engineering approval and
@meridian/gis-appsec if you touched a gate threshold or a credential id. Merge to `develop`, and
`develop` is fast forwarded to `v3` on the Thursday of a train by the release desk. Tag
`v3.<minor>.<patch>` at the same time.

## When the library breaks your build

Read the first `[gate]` line in the console. Then:

- `Unknown agent label` — you typed it wrong, or you are asking for `nodejs20`. See the table.
- `coverage N% below M%` — on a feature branch this is unstable, not failed. On `release/*` it is
  failed and it is your team's threshold, set in your own Jenkinsfile. Lowering it needs a comment
  in the PR and the release desk will notice.
- `Checkmarx gate: high: 1 found` — open the Checkmarx HTML report on the build page. High findings
  are not negotiable; a false positive needs a GIS exception (`gis-appsec-intake@meridian.internal`,
  quote the rule id) and the exception id goes in `checkmarx.yml` under `suppressions`.
- `Xray gate: high: N found` — a dependency advisory. `npm audit` locally will show it. Overrides in
  `package.json` are acceptable for transitive advisories where the direct dependency has no fix;
  cite the GIS ticket in a comment. See `governance/DEPENDENCY_POLICY.md`.
- `Sonar quality gate ERROR` — new code coverage below 80 percent or a new blocker. Sonar's "new
  code" period is 30 days, so this catches up with you.
- Build hangs on `Install` for a Node 14 job — the RHEL 7 agent has 2 CPUs and npm 6. It is slow.
  It is not hung. Twenty minutes is normal. See the `nodejs14-rhel7` paragraph above, again.
- `TS2304: Cannot find name 'Disposable'` — your `@types/node` floated. Pin it to `16.18.11`.
  BUILD_LOG.md at the workspace root explains why.

## Known issues

- TOOL-1207 `bundle-budget.js` assumes the Webpack builder's `stats.json`. The esbuild
  application builder writes something else. Whoever moves first to the new builder owns this.
- TOOL-1290 `Jenkinsfile.release` does not verify the CAB reference against the ITSM tool, only its
  shape. The release desk cross checks by hand.
- TOOL-1041 `nodejs14-rhel7`, see above.
- TOOL-1155 the freeze window in `ReleaseGuard` is hard coded to match `RELEASE_CALENDAR.md`. Change
  both.
- The `Lint` stage runs `check-forbidden-strings.sh` from two possible paths because monorepo and
  polyrepo checkouts differ. It is ugly. It works.
