# ADR-0007: Application repositories do not carry their own Dockerfiles

Status: Accepted, 2021-09-14. Owners: Platform Engineering, CSWT Architecture. Ticket TOOL-612.

## Context

By mid 2021 there were nine Dockerfiles across the CSWT repositories. Five were copies of each
other with drift, two used `FROM nginx:latest` from Docker Hub (GIS-2418), one ran as root, and one
had the Artifactory token as a build arg (GIS-2911, found later but present at the time). Each
GIS finding took a PR per repository and a chase per team.

## Decision

Dockerfiles live in `platform-tooling/docker/<kind>/` and the Jenkins shared library passes
`-f` with the repository root as the build context. Repositories may not carry a Dockerfile;
the `Container build` stage fails if one is present at the repo root (a `.dockerignore` is fine).
Variation between applications is expressed through build arguments the pipeline derives from
the repository (`NODE_VERSION` from `.nvmrc`, `APP` from the job name, `JDK_VERSION` from the pom).

## Consequences

- One place to fix a base image finding. GIS-STD-021 compliance is a platform-tooling change.
- Teams lose the ability to do unusual things in their image. So far the requests have been a
  custom nginx location (handled with a values key), a font package (DOC-2044, not handled) and
  a second exposed port (refused).
- The Dockerfile has to cope with every Angular version's `dist/` layout, which is why the build
  stage has the `find index.html` fallback.
- Node version divergence between repositories (14, 16, 18) is visible in one file's argument list
  rather than hidden in nine `FROM` lines. This was considered a feature.

## Revisit

When the estate is on one Node major, or when a team has a requirement the argument model
cannot express. Neither has happened.
