# ADR-0012: Scanner CLIs are emulated in-repo for local and demo builds

Status: Accepted, 2023-04-03. Owners: Platform Engineering with GIS AppSec. Ticket TOOL-1301.

## Context

The Checkmarx, SonarQube and Xray services are reachable only from the build VLAN. Engineers could
not reproduce a failed quality gate locally, and the shared library's gate logic had no tests
because it needed a live scanner. Separately, the estate demonstration environment needs the
pipeline to run end to end on a laptop.

## Decision

`platform-tooling/mock-scanners/bin/{cx,sonar-scanner,xray}` implement the subset of each CLI's
interface that the shared library uses, produce reports in the same shape, and are deterministic.
The Checkmarx ruleset is a GIS-maintained regular expression approximation of the real preset
(`rules/checkmarx-rules.json`, revision numbered). The Xray advisory mirror is an offline extract
refreshed by GIS. The shared library selects the real or the emulated binary by `PATH`; the
pipeline code does not know which it got.

## Consequences

- Gate logic is tested (`jenkins-shared-library/test/`, `mock-scanners/run-tests.sh`).
- A clean local `cx` run does not mean a clean Checkmarx run. The README says so in bold. The
  emulated rules catch the pattern-level things (sanitiser bypass, `innerHTML`, secrets, TLS off);
  they do not do data flow.
- GIS has to maintain a second ruleset. They agreed on the basis that it is the "obvious things"
  list they already publish to teams, expressed as regular expressions.
- The advisory mirror goes stale between refreshes. The refresh date is in the report header.
