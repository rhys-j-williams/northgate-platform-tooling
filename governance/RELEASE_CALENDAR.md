# CSWT release calendar 2026

Owner: Release Management (CSWT), with Platform Engineering maintaining this page. Changes to the
calendar itself are a CAB item (standard change RM-STD-002). Last agreed at the December 2025
release planning session; the Q4 freeze dates are confirmed with Finance and Treasury Operations.

This page is the calendar. The Jira release versions, the Jenkins `RELEASE_TRAIN` parameter and
the `release/2026.MM` branches are derived from it, not the other way round. If they disagree,
this page wins and someone has made a mistake elsewhere.

## The train

Fortnightly. One train carries every CSWT deployable that has something merged to `develop` by
code freeze. Nothing rides a train it was not on the manifest for by code freeze; the exceptions
are emergency changes (see below) and they go through a different CAB.

| day | what |
|---|---|
| Friday, week 1 | **Code freeze** 17:00 ET. `release/2026.MM` cut from `develop` for the month if not already cut; the train tag is applied. Only fixes for the train's own regressions after this. |
| Monday | uat deploy of the full manifest. Regression suite runs overnight. |
| Tuesday | **CAB** 10:00 ET. Submission deadline is Monday 17:00 (`CAB_TEMPLATE.md`). Evidence bundle from `Jenkinsfile.release` attached. |
| Wednesday | Soak in uat. Go / no-go 16:00 ET with the release manager and one representative per riding team. |
| Thursday | **Production deploy** 20:00 to 23:00 ET. Rolling, prod-east then prod-west. |
| Friday | Hypercare. Release retro is the following Tuesday. |

Trains are named `2026.MM.N` where N is the Thursday's week of month. The release branch is per
month (`release/2026.09` carries both September trains) because the branch-per-train experiment in
2023 produced 26 long lived branches and nobody merged back (RM-1180).

## 2026 trains

| train | code freeze (Fri) | CAB (Tue) | prod deploy (Thu) | notes |
|---|---|---|---|---|
| 2026.01.3 | 2026-01-09 | 2026-01-13 | 2026-01-15 | first train of the year; anything held over the December freeze rides here, expect it to be large |
| 2026.01.5 | 2026-01-23 | 2026-01-27 | 2026-01-29 | |
| 2026.02.2 | 2026-02-06 | 2026-02-10 | 2026-02-12 | |
| 2026.02.4 | 2026-02-20 | 2026-02-24 | 2026-02-26 | |
| 2026.03.2 | 2026-03-06 | 2026-03-10 | 2026-03-12 | **last train before Q1 freeze** |
| ~~2026.03.4~~ | | | | skipped, Q1 quarter end freeze |
| 2026.04.2 | 2026-04-03 | 2026-04-07 | 2026-04-09 | first post-freeze train, double manifest |
| 2026.04.4 | 2026-04-17 | 2026-04-21 | 2026-04-23 | |
| 2026.05.1 | 2026-05-01 | 2026-05-05 | 2026-05-07 | |
| 2026.05.3 | 2026-05-15 | 2026-05-19 | 2026-05-21 | Memorial Day the following Monday; hypercare is thin |
| 2026.06.1 | 2026-05-29 | 2026-06-02 | 2026-06-04 | |
| 2026.06.3 | 2026-06-12 | 2026-06-16 | 2026-06-18 | **last train before Q2 freeze** |
| ~~2026.07.1~~ | | | | skipped, Q2 quarter end freeze (also July 4) |
| 2026.07.3 | 2026-07-10 | 2026-07-14 | 2026-07-16 | |
| 2026.07.5 | 2026-07-24 | 2026-07-28 | 2026-07-30 | |
| 2026.08.2 | 2026-08-07 | 2026-08-11 | 2026-08-13 | |
| 2026.08.4 | 2026-08-21 | 2026-08-25 | 2026-08-27 | |
| 2026.09.2 | 2026-09-04 | 2026-09-08 | 2026-09-10 | **last train before Q3 freeze**. Current train. |
| ~~2026.09.4~~ | | | | skipped, Q3 quarter end freeze |
| 2026.10.2 | 2026-10-02 | 2026-10-06 | 2026-10-08 | |
| 2026.10.4 | 2026-10-16 | 2026-10-20 | 2026-10-22 | |
| 2026.11.1 | 2026-10-30 | 2026-11-03 | 2026-11-05 | |
| 2026.11.3 | 2026-11-13 | 2026-11-17 | 2026-11-19 | |
| 2026.12.1 | 2026-11-27 | 2026-12-01 | 2026-12-03 | **last train of 2026**. Code freeze is the day after Thanksgiving; the freeze is Thursday 2026-11-26 17:00 in practice. |
| ~~2026.12.3~~ | | | | skipped, year end freeze |
| ~~2026.12.5~~ | | | | skipped, year end freeze |

## Freeze windows

Quarter end freezes cover month-end close, regulatory reporting (call report, FR Y-9C) and, for
Q4, the holiday period. No production change to any CSWT system during a freeze except an
emergency change with a Sev-1 or Sev-2 incident attached and CTO-delegate approval. That includes
"just a config change", "just a feature flag" and "just the WAF rule". Ask Release Management
before assuming something is out of scope.

| freeze | from | to | why |
|---|---|---|---|
| Q1 | Thu 2026-03-26 17:00 ET | Mon 2026-04-06 09:00 ET | Q1 close |
| Q2 | Thu 2026-06-25 17:00 ET | Mon 2026-07-06 09:00 ET | Q2 close, July 4 |
| Q3 | Thu 2026-09-24 17:00 ET | Mon 2026-10-05 09:00 ET | Q3 close |
| Q4 / year end | Thu 2026-12-10 17:00 ET | Mon 2027-01-11 09:00 ET | Q4 close, holiday period, year end |

uat and dev deploys continue during freezes. The Jenkins library refuses the prod deploy stage
when `ReleaseGuard.inFreeze()` is true unless the `CAB_REFERENCE` is an emergency change number
(`CHGnnnnnnnE`, see `jenkins-shared-library/src/com/meridian/pipeline/ReleaseGuard.groovy`). That
class approximates the table as "the last fourteen days of March, June, September and December",
which is close enough for Q1 to Q3 and wrong for the first week of the following month and for
the long Q4 window. Release Management knows; TOOL-1155 is the ticket to read the dates from
this file and it has been open since 2023. Until then the release manager holds the deploy job
disabled in Jenkins during the parts of the freeze the class does not cover.

## Emergency changes

Sev-1 / Sev-2 incident, an emergency change record from the emergency CAB (paged via the release
manager on-call; the CHG number carries an `E` suffix), `Jenkinsfile.release` with that number as
`CAB_REFERENCE`. Retrospective CAB paperwork within two business days. Do not use an emergency change to catch a missed train; Release Management
reads every ECAB and this has been noticed before (RM-1402).

## Things that are not on this calendar but affect it

- Angular and Node lifecycle. The version map for the estate has three Angular majors and three
  Node majors in flight; several teams are asking for a train to be reserved for a platform
  upgrade. Not agreed. Raise at release planning, not on the day.
- The Artifactory upgrade (ART-3320) is pencilled for the Q2 freeze because nothing is deploying
  anyway. It will break publishing for a day.
- OpenShift 4.14 to 4.16 cluster upgrade, also targeting a freeze window. The DeploymentConfig in
  `openshift/` has to be gone before that (DOC-1988, OCP-4471).
- DR test, first weekend of May and of November. prod-west is unavailable Saturday; hypercare for
  2026.05.1 and 2026.11.1 should know.
