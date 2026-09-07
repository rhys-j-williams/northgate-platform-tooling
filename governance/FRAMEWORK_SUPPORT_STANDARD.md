# Supported software standard — frameworks and runtimes in production

GIS-STD-022, v3.1, 2026-01. Owner: Global Information Security (GIS Architecture). Technology
Risk owns section 5 (risk acceptance). CSWT Platform Engineering maintains the inventory in
section 6. TECH-STD-044 (dependency policy) refers to this document for the end of life paragraph.

## 1. The rule

Software that Meridian Trust Bank runs in production, or that produces artefacts which run in
production, must be within vendor or community security support. This applies to application
frameworks (Angular, Spring Boot), language runtimes (Node.js, JDK, Python), operating system
images and the build toolchain that produces the deployable.

"Within support" means the upstream maintainer still publishes security fixes for that major or
LTS line. For Angular the reference is the published LTS schedule: a major is supported for 18
months from release, six of them active and twelve LTS. For Node.js it is the release working
group's schedule. For everything else the reference is the entry in section 6.

Running out of support software in production is a breach of this standard. It is not, by
itself, a control failure; it becomes one when it is not recorded and risk accepted under
section 5, or when the acceptance has lapsed.

## 2. Why

An out of support framework receives no fixes for vulnerabilities disclosed after its end of
life. The bank cannot patch what the vendor does not patch, and compensating controls (WAF
rules, CSP, interceptor review) reduce exposure without removing it. Regulatory examiners ask
for the list in section 6 and for the risk acceptances against it; the OCC 2024 IT examination
raised the age of the digital channel frameworks as an observation, not yet a matter requiring
attention. GIS's stated position to the board risk committee is that observations do not become
matters.

## 3. Obligations

**Product owners** own the risk for their deployables. They may accept it under section 5; they
may not ignore it. Once an acceptance expires without renewal the deployable is non-compliant
and CAB will not approve non-security changes to it until it is either upgraded or re-accepted.

**Engineering leads** maintain an upgrade plan for every deployable within twelve months of its
framework's end of life date, as an ADR in the repository, and keep it current. "Deferred" is an
acceptable plan if the ADR says until when and why.

**Shared library owners** (Canopy, Lantern, `@meridian/common-starter`) publish a supported
version of their library for the target framework major before the first consumer needs it, and
keep the previous major in security support until the last consumer has moved or ninety days,
whichever is later. A library upgrade that breaks a consumer's build at the consumer's pinned
version is a change to that consumer and goes through that consumer's CAB record.

**GIS** maintains this standard, reviews acceptances at renewal, and raises a finding (`GIS-nnnn`)
against any deployable that appears in the inventory as out of support without a live acceptance.

## 4. What counts as production

Anything customer facing, anything an associate uses to act on customer data, and anything in
the build path for either. Meridian Online, Meridian Business, Keystone, Iris, Ledgerline and
their BFFs are all in scope. The Canopy showcase is not customer facing but the library it
demonstrates ships inside every one of the above, so Canopy is in scope. Local mocks under
`mock-external/` are out of scope.

## 5. Risk acceptance

An acceptance is a Technology Risk record (`TR-nnnn`) signed by the product owner and the CSWT
CIO delegate, with GIS AppSec as reviewer. It names the deployable, the out of support component,
the compensating controls, the upgrade plan (by ADR reference) and an expiry date.

- Initial acceptance: up to twelve months from end of life.
- Renewal: up to twelve months, once. A second renewal needs the CIO, not a delegate, and a
  dated remediation commitment in the record.
- No acceptance runs past thirty-six months from the component's end of life date. After that
  the only path to compliance is the upgrade.

Compensating controls are reviewed at each renewal. Controls that were accepted in 2024 (per
release CSP and interceptor review, `npm audit` thresholds, `overrides` pinning of known
advisories) remain acceptable for renewal but do not extend the thirty-six month ceiling.

## 6. Inventory — CSWT digital channels, as of the 2026.01 review

| Deployable | Framework / runtime | Upstream end of life | Acceptance | Expires | Plan |
|---|---|---|---|---|---|
| Meridian Online (retail-web) | Angular 14.3 / Node 16 | 2023-11-18 / 2023-09-11 | TR-1188 (GIS-2207), renewed twice | **2026-11-18** — ceiling, cannot be renewed | retail-web ADR 0014, epic MOL-4471 |
| Meridian Business (business-web) | Angular 14.2 / Node 14 | 2023-11-18 / 2023-04-30 | TR-1190 (GIS-2209), renewed twice | **2026-11-18** — ceiling | business-web ADR 0004, epic MBZ-2140 |
| Canopy (`@meridian/canopy-ui` 3.x) | Angular 14.3 / Material 14 | 2023-11-18 | TR-1188, TR-1190 (covered through consumers) | with consumers | CNPY-2140, Canopy 4 |
| Iris widget | Angular 14.3 | 2023-11-18 | TR-1203 | 2026-11-18 — ceiling | IRIS-0900, follows retail-web |
| Lantern SDK wrapper (`@meridian/lantern-sdk` 2.x) | Angular 12.2, View Engine | 2022-11-12 | TR-1102 | **expired 2025-11-12** — see GIS-2618 | LNTN-401; vendor Ivy build LNTN-140 |
| Keystone (keystone-web) | Angular 15.2 / Node 16 | 2024-05-18 / 2023-09-11 | TR-1301 (GIS-2410) | 2027-05-18 | KEY-2210 MDC migration then 17+ |
| Ledgerline (ledgerline-web) | Angular 16.2 / Node 18 | 2024-11-08 / 2025-04-30 | TR-1355 | 2026-11-08 | LDG-1350 |
| Retail BFF, Business BFF | Spring Boot 2.7 / JDK 11 | 2023-11-24 (OSS) | TR-1240, commercial support to 2026-12 | 2026-12 | PLAT-2600 |

GIS-2618 (Lantern) is open as a High. The wrapper is in the build path of every Angular deployable
that reports analytics, so the Lantern line is what stops the retail-web and business-web
acceptances from being the only clock that matters. See LNTN-401.

The Angular 14 acceptances for Meridian Online and Meridian Business reach the thirty-six month
ceiling in the 2026.11 train. Both product owners have been told in writing (2026-01-22, TR review
minutes) that there is no further renewal path; the upgrade lands before the 2026.11.2 code freeze
or the deployables are non-compliant and frozen for non-security change under section 3. The
current Angular major at the time of the review is 18; Architecture's position (CSWT-ARCH minutes
2026-01-15) is that the target is the current major at the time of upgrade, not 15, because a
second out of support upgrade inside the same acceptance window would not be approved.

## 7. Related

- TECH-STD-044 Dependency policy, section 4 (end of life findings) — `DEPENDENCY_POLICY.md`
- TECH-POL-031 AI-assisted code — `AI_ASSISTED_CODE_POLICY.md`, for how upgrade work that uses
  AI coding tools is reviewed
- GIS-STD-014 Application Security Requirements for Internet Facing Applications
- `CAB_TEMPLATE.md` section 5, which is where the acceptance reference is recorded per change
- retail-web `docs/adr/0014-defer-angular-upgrade-2024.md`, business-web `docs/adr/0004-canopy-pin.md`
