# Policy: AI-assisted code in CSWT systems

| | |
|---|---|
| Policy id | TECH-POL-031 |
| Version | 1.3 (2025-08-19). Supersedes 1.2 (2025-02) and the interim guidance memo of 2024-06. |
| Applies to | All source code, infrastructure code, configuration, tests, pipeline definitions and technical documentation in repositories owned by Consumer and Small-business Web Technology (CSWT), regardless of environment. |
| Policy owner (first line) | Head of Platform Engineering, CSWT |
| Control owners | see section 7 |
| Second line review | Technology Risk, Operational Risk Management. Reviewed 2025-07-30; next review 2026-07. |
| Approved by | CSWT Technology Risk Committee, 2025-08-14 (minute TRC-2025-08-06) |
| Related | GIS-STD-014, GIS-STD-021, GIS-STD-030, TECH-POL-012 (Secure SDLC), TECH-POL-019 (Third party software), DEPENDENCY_POLICY.md, CAB_TEMPLATE.md section 9 |

## 1. Purpose

AI coding tools produce plausible code quickly. The bank's obligations do not change because of
how the code was produced: we must be able to say who is accountable for every change to a
system that touches customer money or customer data, show a competent human reviewed it, show
the review actually looked at the right things, and reproduce that evidence for an examiner two
years later. This policy sets the minimum controls under which AI-assisted code may enter a CSWT
repository. It exists because the 2024 pilot (TRC-2024-11-03) found that none of that evidence
existed for the changes the pilot produced, not because the code was bad.

The policy is deliberately conservative. It will be revisited as tooling and the regulatory view
mature. Requests to relax a control go to the policy owner with evidence, not to the reviewer on
the day.

## 2. Definitions

**AI coding tool.** Any software that generates, completes, transforms, reviews or explains code,
configuration or technical documentation using a machine learning model, whether run locally, in
the IDE, in CI, or as an autonomous agent. Includes code review bots and automated migration
tools. Excludes deterministic tooling: compilers, linters, formatters, schematics and codemods
whose output is a fixed function of their input and version (`ng update` is deterministic; a tool
that reads `ng update`'s output and decides what to do next is not).

**AI-assisted change.** A commit, pull request or generated artefact where an AI coding tool
produced or materially modified content that is committed. Tab-completion of a single identifier
is not material. A function body, a test, a template, a chart, a pipeline stage, a paragraph of a
runbook is.

**Prompter.** The engineer who operated the tool. **Reviewer.** The engineer who reviewed the
output. They may not be the same person (section 4.3).

**Approved-tool register.** The list of AI coding tools, versions, deployment modes and data
handling terms that GIS and Technology Risk have assessed. Maintained by GIS Architecture,
published on the GIS wiki, referenced by id (`AIT-nnn`) in commit trailers.

## 3. Principles

P1. **A human is accountable for every change.** The tool is never the author of record. The
prompter is accountable for what they submit; the reviewer is accountable for what they approve;
the change owner on the CAB record is accountable for what goes to production. No control in this
policy transfers accountability to a tool or its vendor.

P2. **Provenance is recorded at the point of creation and is not reconstructable later.** If the
labelling in section 4.1 was not done when the commit was made, the content is treated as
unlabelled AI-assisted content (section 6) rather than as human-written.

P3. **Review effort scales with what the change touches, not with how the change was produced.**
AI-assisted content does not get a lighter review because it "looks fine", and it does not get a
heavier one for its own sake. It does get a *different* review, because the failure modes differ
(section 4.3).

P4. **The same gates apply.** Sonar, Checkmarx, Xray, coverage thresholds, DEPENDENCY_POLICY.md
and the freeze calendar apply identically. An AI-assisted change that introduces a dependency
from outside the internal registry is a DEPENDENCY_POLICY.md breach, not an AI policy question.

P5. **Only approved tools, only approved data.** Tools not on the register may not be used on bank
code. Customer data, credentials, and anything classified Confidential or Restricted
(DATA_CLASSIFICATION.md) may not be sent to a tool unless the register entry explicitly permits
that classification.

## 4. Requirements

### 4.1 Labelling

Every AI-assisted commit carries a trailer:

```
AI-Assisted: AIT-014
AI-Assisted-Scope: src/app/accounts/**, platform-tooling/helm/bff-retail/**
```

`AI-Assisted` names the register entry. `AI-Assisted-Scope` lists the paths whose content the tool
produced or materially changed; `all` is acceptable for a commit that is entirely generated. The
pull request description repeats the information in a section headed **AI-assisted content** and
states, in one or two sentences, what the tool was asked to do.

Files whose entire content is generated and is expected to be regenerated (a scaffolded chart, a
migration script produced by an agent) additionally carry a header comment in the file's comment
syntax: `Generated with AI assistance (AIT-nnn) on YYYY-MM-DD; reviewed by <handle>.` Files a
human then edits keep the header until the human-edited proportion is the majority, at which point
the header is removed and the change that removes it says so.

The commit hook in the repository template (`platform-tooling` provides it, TOOL-1502) rejects
an `AI-Assisted` trailer whose register id is not on the published list, and rejects a PR whose
description section is missing when any commit carries the trailer. The hook cannot detect an
unlabelled AI-assisted commit. That is what section 4.3 and section 6 are for.

### 4.2 Permitted uses

Within the approved-tool register's terms, AI coding tools may be used for: implementation of
specified changes; test generation; refactoring and migration (including framework version
migrations, which is the use case the estate is most interested in); documentation drafts;
explaining existing code; first-pass code review in addition to, never instead of, human review.

They may not be used for: generating or modifying anything under `vault/`, `registry/` or
security-relevant configuration (`nginx.conf`, CSP, NetworkPolicy, IAM policy) **without a GIS
reviewer** (CODEOWNERS enforces the reviewer; the policy requirement is that the AI-assisted label
is present so GIS knows what they are reviewing); producing customer communications or
regulatory text; deciding whether a scanner finding is a false positive (a human decides; the tool
may summarise); anything involving production data.

### 4.3 Review

AI-assisted changes are reviewed by at least one engineer who is not the prompter, who has
write access to the repository under normal rules, and who completes the review checklist in the
PR template (`platform-tooling/docs/templates/PR_REVIEW_AI.md`, TOOL-1503). The checklist
requires the reviewer to record, for the AI-assisted scope:

- that they read all of it, not a sample (for changes over 400 lines of AI-assisted content the
  review is split across two reviewers or two sessions, and says so);
- that behaviour is covered by tests the reviewer considers meaningful, and that generated tests
  assert behaviour rather than restate the implementation;
- that no dependency, import or API is used that does not exist at the pinned version in the
  repository (the most common defect class in the 2024 pilot: APIs from a newer major of the
  framework than the one installed);
- that no dependency was added or changed outside DEPENDENCY_POLICY.md;
- that security-sensitive constructs (sanitiser bypasses, `innerHTML`, `eval`, disabled TLS
  verification, credentials, CSP changes; the Checkmarx ruleset is the reference list) are either
  absent or individually justified in the PR;
- that generated comments and documentation are accurate, and that none of them are the tool
  describing its own edit rather than the code;
- that the change does what the ticket asked and does not do other things.

The reviewer's approval on an AI-assisted PR is a personal attestation to the above. Reviewers who
do not have time to do the review at that standard decline the review rather than approve it.

For **Medium and High risk** changes (RM-STD-003 appendix A) with AI-assisted content, one of the
reviewers must be from outside the requesting team.

### 4.4 Evidence

The evidence bundle produced by `Jenkinsfile.release` must contain, for any release with
AI-assisted content: the list of commits carrying the trailer; the PR review checklist(s); the
scanner reports; and the CAB record's section 9. Retention is the same as other release evidence
(seven years, `generic-cswt-release-evidence`). The `ReleaseGuard` check for a missing section 9
is TOOL-1504 and is not yet implemented; the release manager checks it by hand.

### 4.5 Attribution and intellectual property

The author and committer of record are the prompter. No tool, vendor or model is named as an
author in git metadata. Third party licence obligations attach to generated code the same way
they attach to copied code: the reviewer confirms that generated content does not reproduce
identifiable third party code under an incompatible licence, using the register tool's
provenance feature where the tool provides one. Where the tool cannot provide provenance,
content over roughly fifty lines that the prompter did not materially edit is treated as
third party code under TECH-POL-019.

### 4.6 Autonomous agents

Tools that plan and execute multi-step changes without a human approving each step are permitted
only where the register entry says so, only against non-production branches, only with the
credentials the register entry specifies (a dedicated service identity with no production or
Vault access), and with the entire agent run recorded and attached to the PR. Every commit an
agent produces carries the trailer. An agent may not approve, merge, or deploy. An agent may not
be the second reviewer.

Agents running framework migrations (the Angular and Node work in the estate roadmap) are within
scope of this section. The migration plan an agent produces is reviewed by the owning team and
CSWT Architecture before execution starts, and the executed changes are reviewed under section
4.3 per PR. "The agent ran the tests" is not review evidence; the reviewer runs them.

## 5. Data handling

Prompts and context sent to a tool are bank data. The register entry states what may be sent.
As of this version: no register entry permits Confidential or Restricted data; two entries
(`AIT-014`, `AIT-021`) permit Internal, which covers source code in CSWT repositories other than
`vault/` and the fixtures packages' generator seeds. Engineers confirm the classification of what
they are pasting before they paste it. Logging of prompts is per register entry; where the vendor
retains prompts, the entry says for how long and that is disclosed to the engineer in the tool.

## 6. Non-compliance

An AI-assisted change found without labelling is treated as a Secure SDLC control failure
(TECH-POL-012 s9) and recorded as such. The change is reviewed retrospectively under 4.3 and the
PR is annotated. Repeat instances by the same engineer are escalated through line management. A
change found to have sent Confidential or Restricted data to a tool is a data incident and goes
through the incident process, not this policy.

Reviewers who approve AI-assisted content without completing the checklist are treated the same
as reviewers who approve without reading. This has consequences.

## 7. Control owners

| control | id | owner | evidence of operation | frequency |
|---|---|---|---|---|
| Approved-tool register maintained; entries assessed by GIS and Technology Risk | AI-C01 | Head of GIS Architecture | register change log; assessment records | on change, attested quarterly |
| Commit trailer and PR section enforced by hook (TOOL-1502) | AI-C02 | Head of Platform Engineering, CSWT | hook version in `platform-tooling`; sample of rejected commits | quarterly |
| AI-assisted PRs reviewed by a non-prompter using the checklist | AI-C03 | Engineering managers, each CSWT team (named in the team's control register) | PR review records; monthly sample of 10 by Technology Risk | monthly |
| Out-of-team reviewer on Medium/High AI-assisted changes | AI-C04 | Release Manager, CSWT | CAB records section 9 and 11 | per train |
| Evidence bundle includes AI-assisted inventory and checklists | AI-C05 | Head of Platform Engineering, CSWT | Artifactory evidence bundles; annual audit sample | per release, audited annually |
| Agent runs use dedicated identity with no prod/Vault access; runs recorded | AI-C06 | Head of GIS Secrets Management | Vault audit log; identity access reviews | quarterly |
| Data classification of prompts | AI-C07 | Data Protection Officer's delegate, CSWT | attestation in annual training; DLP alerts | annual, plus on alert |
| Policy review | AI-C08 | Head of Platform Engineering, CSWT with Technology Risk | TRC minute | annual |

Where a control owner is a role, the named individual is in the CSWT control register, which
Technology Risk holds. Changes of owner are notified to Technology Risk within ten business days.

## 8. Exceptions

Exceptions to this policy are granted by the policy owner and the Head of Technology Risk jointly,
in writing, for a stated period not exceeding six months, and are logged in the technology risk
register. There is no verbal exception. There is no exception to section 5.

## 9. History

| version | date | change |
|---|---|---|
| 0.9 | 2024-06-12 | Interim memo following pilot approval. Labelling only. |
| 1.0 | 2024-11-20 | First policy. Review checklist, register concept, control owners. |
| 1.2 | 2025-02-11 | Section 4.6 added after the first agent tooling assessment. Section 5 tightened after a near miss (TR-2025-014). |
| 1.3 | 2025-08-19 | CAB template section 9 made mandatory. Control table restated with evidence and frequency at second line's request. Clarified deterministic tooling exclusion (the `ng update` question). |
