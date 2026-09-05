# Dependency policy — CSWT repositories

TECH-STD-044, v2.4, 2025-03. Owner: Platform Engineering (CSWT). GIS owns section 5 (the
exception process) and the Xray policy referenced in section 2. Every `CONTRIBUTING.md` in the
estate links here; if you are reading this because a build failed, section 6 is probably what you
want.

## 1. The rule

All third party software consumed by a CSWT build — npm packages, Maven artifacts, Python
packages, container base images, Helm charts, Terraform providers and modules, Jenkins plugins,
tool binaries — is obtained from the bank's internal Artifactory (`artifactory.meridian.internal`)
and from nowhere else. The repositories are listed in `platform-tooling/registry/repositories.json`.

Nothing else. Not the public registry "just for this one package", not a GitHub release tarball,
not a `git+https://` dependency, not a vendored copy of a library checked into the repo, not a
CDN script tag, not a base image from Docker Hub. The build agents cannot reach any of those hosts
and the pipeline fails if a lockfile references one, but the rule applies to laptops as well: a
package that resolved from the public registry on a laptop and got into a lockfile is a breach
even though it will be caught at CI.

Why: supply chain. Artifactory gives us a scanned, cached, retained copy of everything we depend
on. The Xray policy blocks known-critical packages before they are downloaded; the cache means we
can rebuild an eighteen month old release when the public copy has been unpublished (this
happened, TOOL-1288); and the access log tells GIS who first pulled a package when an advisory
lands. None of that exists for anything fetched directly.

## 2. What "allowed" means in practice

A dependency is allowed if it resolves from the appropriate virtual repository and is not blocked
by the Xray policy `cswt-block-critical-high`. That policy blocks download of any version with an
open Critical or High vulnerability, and of anything on the GIS deny list (typosquats, packages
with known malicious history, packages whose licence is on the prohibited list in TECH-POL-019).

There is no allow list of packages. If it is in the virtual repository and Xray lets it through,
you may use it, subject to the rest of this document. There is a short **watch list** GIS
publishes of packages that are allowed but need a GIS reviewer on the PR that introduces them
(crypto libraries, anything that spawns processes or opens sockets, anything that touches the
DOM outside the framework).

## 3. Repository configuration

- npm: `~/.npmrc` from `platform-tooling/registry/npmrc.sample`, pointing at `npm-virtual`. The
  component's own `.npmrc` may add `legacy-peer-deps=true`, `engine-strict=true` and scope
  settings. It may not set `registry` to anything other than `npm-virtual` (the pipeline checks).
- Maven: `~/.m2/settings.xml` from `platform-tooling/registry/settings.xml` with `mirrorOf *`. A
  `<repository>` element in a pom is ignored because of the mirror, which is intended; do not add
  them, they confuse people.
- Python: `PIP_INDEX_URL` to `pypi-virtual`. `requirements.txt` with `==` and hashes.
- Containers: `FROM` lines reference `docker-redhat-remote` or `docker-remote` through Artifactory.
  Runtime images from the GIS-STD-021 approved list only (UBI).
- Terraform: `terraform-virtual`; providers pinned to an exact version in `required_providers`.
- Jenkins plugins: the controller's update centre is the Artifactory proxy. Plugin changes are a
  TOOL ticket.

## 4. Versioning and lockfiles

- **Exact versions.** No `^`, no `~`, no `latest`, no `x`, no ranges, in `package.json`, poms,
  `requirements.txt` or `required_providers`. `save-exact=true` is in the sample `.npmrc`.
- **Lockfiles are committed** and are the source of truth for what is built. `npm ci`, never `npm
  install`, in the pipeline. A PR whose lockfile diff does not correspond to its manifest diff is
  sent back.
- **The estate version map applies.** Framework and runtime majors (Angular, Angular Material,
  TypeScript, RxJS, Node, Spring Boot, JDK) are fixed per repository by the estate version map in
  the root README and change only through an ADR agreed with CSWT Architecture and a scheduled
  train. A dependency bump PR that moves one of those is closed regardless of whether it builds.
  Dependencies *within* a major are the owning team's call, subject to Xray.
- **Transitive dependencies** are still dependencies. If Xray flags a transitive, the fix is an
  upgrade of the direct dependency that pulls it, an `overrides` entry with a ticket reference and
  an expiry comment, or an exception (section 5). Not `npm audit fix --force`.
- **End of life.** A direct dependency whose upstream is end of life is a finding
  (`MERIDIAN-EOL-*` in the Xray mirror). It does not block the build by itself but it appears in
  the CAB record section 5 and the CAB has started asking. Node 14 and Angular 14 are the
  well-known cases; see the estate roadmap. This document does not require the upgrade. It
  requires that the risk is visible.

## 5. Exceptions

An exception is required to use a dependency that Xray blocks, a source other than Artifactory, a
licence on the prohibited list, or a version outside the estate version map without an ADR.

Process:

1. Raise a GIS ticket, type `Dependency exception`, from the requesting team's engineering
   manager. State the package and exact version, the reason it is needed, why the alternatives
   (different package, different version, doing without) were rejected, the compensating controls,
   and the requested expiry (six months maximum, twelve for a licence exception).
2. GIS AppSec assesses within ten business days. For Xray blocks, that means reading the
   advisory against how the package is used. "We do not call the vulnerable function" needs a
   code reference and, usually, a unit test that proves it.
3. Approved exceptions are recorded in the GIS exception register with an id `GIS-EX-YYYY-nnn`,
   and the id goes in the `overrides` comment, the `.npmrc`, or the Dockerfile next to the thing
   it excuses. The Xray policy gets a scoped allow for that package and version only.
4. Expired exceptions are re-raised or removed. The Xray allow is removed automatically at expiry;
   the build starts failing; that is the reminder.

Things that are never granted: fetching from a public source at build time; disabling TLS
verification to reach anything (`strict-ssl=false`, `-Dmaven.wagon.http.ssl.insecure=true`,
`--trusted-host`); a licence exception for GPL-family code in a distributed artefact; an
exception "for the team" rather than for a package.

Current exceptions relevant to platform-tooling are listed in `platform-tooling/SECURITY.md`. The
one that gets asked about most is GIS-EX-2023-118 (TLS 1.2 only on the RHEL 7 agents), which
expired in June 2024 and has not been renewed. It is not this document's job to fix that.

## 6. When the build fails because of this

| symptom | cause | what to do |
|---|---|---|
| `npm ERR! 404 Not Found` for a version that exists publicly | Xray blocked the download | Artifactory -> package -> Xray tab. Pick a version without the finding, or section 5. |
| `npm ERR! 403` on publish | you are not `jenkins-cswt` | publishing from a laptop was removed (TOOL-1122). Push and let the pipeline publish. |
| `ENOTFOUND registry.npmjs.org` on an agent | a lockfile entry resolves to the public registry | `npm ci` locally with the sample `.npmrc`, commit the lockfile. |
| `Could not transfer artifact ... from/to central` | settings.xml missing or mirror not applied | copy `registry/settings.xml`; the Jenkins library injects it, so this is laptop-only. |
| `ERESOLVE unable to resolve dependency tree` | peer dependency conflict in an old Angular workspace | `legacy-peer-deps=true` in the component `.npmrc`, never on the command line, so the agent behaves like you. |
| `Dependency policy: floating range in package.json` in the lint stage | a `^` or `~` | pin it. |
| Xray gate fails on a package you did not add | transitive | section 4, transitive dependencies. |

## 7. Ownership and review

Platform Engineering owns the document, the sample configuration files and the pipeline checks.
GIS AppSec owns the Xray policy, the deny and watch lists and the exception register. CSWT
Architecture owns the estate version map. Reviewed annually; last review 2025-03 (added section 6
and the EOL paragraph at CAB's request). Questions to the `#cswt-platform` channel; exception
requests to GIS, not to the channel.
