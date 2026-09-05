# Change Advisory Board submission — CSWT

Template version 4.2 (RM-STD-003, revised 2025-08 to add section 9). Copy the whole thing into the
CHG record's description field in the ITSM tool; the fields there are not wide enough for the
tables, so the record body is this markdown and the mandatory ITSM fields are filled from section
1. Submissions after the Monday 17:00 ET deadline are heard the following fortnight. The CAB does
not read attachments it was not pointed at; if the evidence is in the bundle, say where.

Delete the guidance in *italics* before submitting. Do not delete sections; write "not
applicable" and say why.

---

## 1. Record

| field | value |
|---|---|
| CHG number | CHG_______ *(assigned by ITSM on save; add the `E` suffix only if this is an emergency change)* |
| Change type | Standard / Normal / Emergency |
| Release train | 2026.MM.N *(from RELEASE_CALENDAR.md)* |
| Requested implementation window | Thu YYYY-MM-DD 20:00 to 23:00 ET |
| Requesting team | |
| Change owner (accountable) | *name, must be a permanent employee at grade M2 or above* |
| Implementer | *name(s) who will be on the bridge* |
| Business sponsor | |
| Application(s) and CMDB app-id(s) | *e.g. retail-web APP-10442, bff-retail APP-10450* |
| Environment(s) | prod-east, prod-west |
| Jira release version | |
| Evidence bundle | *Artifactory URL under generic-cswt-release-evidence, produced by Jenkinsfile.release* |

## 2. Summary of change

*Three to six sentences a CAB member who does not know the system can follow. What is changing,
for whom, and why now. Ticket keys, not ticket titles. If it is a dependency or platform upgrade
say so in the first sentence; the CAB treats those differently (section 5).*

## 3. Scope

### In scope

*Repositories, services, charts, config. One line each with the git tag or chart version.*

| component | from | to | change |
|---|---|---|---|
| | | | |

### Out of scope / explicitly not changing

*Things a reader might assume are changing and are not. Database schema, IdP configuration, WAF
rules, third party contracts.*

## 4. Risk assessment

| | |
|---|---|
| Risk rating | Low / Medium / High *(RM-STD-003 appendix A matrix; customer facing plus anything touching payments, statements or authentication is at least Medium)* |
| Customer impact during implementation | *None expected / degraded / outage, with duration* |
| Customer impact if it goes wrong | |
| Regulatory or data classification considerations | *DATA_CLASSIFICATION.md level of anything touched; say if PII flows change* |
| Dependencies on other changes | *CHG numbers* |
| Blast radius | *which other services consume this one* |

*For Medium and High: name the specific failure mode you are most worried about and what you did
about it. "General regression risk" is not an answer.*

## 5. Dependency and platform changes

*Complete if any dependency version, base image, Node or JDK version, Angular or Spring major, or
build agent label changes. Otherwise "not applicable, no dependency changes".*

| dependency | from | to | reason | DEPENDENCY_POLICY.md exception ref (if any) |
|---|---|---|---|---|
| | | | | |

- Xray report for the new versions: *link into the evidence bundle*
- Lifecycle status of everything in the "to" column: *supported / maintenance / end of life, with the vendor date*
- Confirm no version moves outside the estate version map without an ADR: *yes / ADR-nnnn*

## 6. Testing and evidence

*Every row links into the evidence bundle. The CAB will spot-check two.*

| evidence | location | result |
|---|---|---|
| Unit tests and coverage (threshold from the Jenkins job) | | |
| Sonar quality gate | | |
| Checkmarx scan (no High or Critical open) | | |
| Xray dependency scan (no High or Critical open) | | |
| uat regression suite | | |
| Manual UAT sign off | | *name, date* |
| Performance test *(Medium and High only)* | | |
| Accessibility check *(customer facing UI only)* | | |
| Security review *(if GIS-STD-014/021/030 material changed)* | | *GIS ticket* |

Open scanner findings carried into prod, with the GIS risk acceptance reference for each:

| finding id | severity | GIS acceptance | expiry |
|---|---|---|---|
| | | | |

## 7. Implementation plan

*Numbered steps with the person, the command or job, and the expected duration. Include the
verification after each step. Rolling deploy across prod-east then prod-west is the default and
needs no justification; anything else does.*

1.
2.

Estimated duration: ____ minutes. Bridge: *conference bridge id*. Communications: *who tells the
service desk and when*.

## 8. Rollback plan

*Must be executable by the on-call engineer without the implementer. State the trigger, the
steps, and how long it takes. Helm rollback to the previous release is acceptable when the change
carries no schema or data migration; otherwise describe the reverse migration or the forward fix
and why rollback is not possible.*

| | |
|---|---|
| Rollback trigger | |
| Rollback steps | |
| Rollback duration | |
| Point of no return | *the step after which rollback is no longer clean* |
| Rollback tested in uat on | *date, evidence link* |

## 9. AI-assisted changes

*Required since 2025-08 under AI_ASSISTED_CODE_POLICY.md. State whether any code, configuration,
test or documentation in this change was produced or materially modified by an AI coding tool.*

| | |
|---|---|
| AI-assisted content present | Yes / No |
| Tool(s) and approved-tool register entry | |
| Commits or PRs carrying the `AI-Assisted:` trailer | *links* |
| Human reviewer(s) of the AI-assisted content (not the prompter) | |
| Review evidence | *link to the PR review with the required checklist completed* |
| Scanner results for AI-assisted files specifically | *cx and Sonar reports filtered, or "same as section 6, no delta"* |

*A "Yes" with an empty reviewer field is returned without being heard.*

## 10. Post implementation

- Hypercare owner and duration: *name, 48 hours minimum for Medium and High*
- Success criteria: *observable, e.g. error rate on bff-retail below 0.5% for 24 hours, login
  success rate unchanged*
- Monitoring dashboards to watch: *links*
- PIR required: Yes for High, or for any change that triggers rollback

## 11. Approvals

| role | name | date |
|---|---|---|
| Change owner | | |
| Technical approver (not on the requesting team) | | |
| GIS approver *(if section 6 has accepted findings or GIS standards material changed)* | | |
| Business approver *(Medium and High)* | | |
| CAB chair | | |

---

*Guidance for the release manager, not part of the submission: the Jenkins release job checks the
CHG number format and the freeze window, nothing else. It does not check that the CAB approved
it. That is you.*
