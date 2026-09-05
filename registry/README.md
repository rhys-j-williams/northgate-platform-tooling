# registry

How CSWT reaches package registries. Short version: through Artifactory, only through Artifactory,
and `governance/DEPENDENCY_POLICY.md` is why.

- `npmrc.sample` — `~/.npmrc` for an engineer or agent. `npm-virtual`, identity token, `save-exact`.
- `settings.xml` — `~/.m2/settings.xml`. `mirrorOf *` to `maven-virtual`. The Jenkins library
  injects this at the Maven stage with the token from Vault; agents keep no copy.
- `repositories.json` — inventory of the Artifactory repositories the estate uses, with the notes
  that are not in Artifactory itself (why Confluent is there, why the npm cache is 365 days, which
  repo audit reads).
- `npmrc.local.sample`, `settings.local.xml`, `verdaccio/` — the demo estate's local stand-ins.
  Verdaccio on `:4873` (PORTS.md) replaces `npm-virtual`; Maven goes to Central directly because
  nobody wanted to run Nexus on a laptop.

## Things people get wrong

- "npm install says the version does not exist." It does; Xray blocked the download. Artifactory
  -> the package -> Xray tab. Raise a GIS exception if you need it, see DEPENDENCY_POLICY.md s5.
- `legacy-peer-deps` goes in the component's own `.npmrc`, not in `~/.npmrc` and not on the
  command line. Agents run with a clean `~/.npmrc` from `npmrc.sample`.
- The `@meridian` scope is reserved in Artifactory and in Verdaccio and never proxies. If
  `@meridian/canopy-ui@x.y.z` 404s, it was not published, full stop (GIS-2601).
- Publishing from a laptop was removed in TOOL-1122. Only `jenkins-cswt` can deploy to
  `npm-cswt-local` and `maven-cswt-local`.
- Artifactory identity tokens expire at 90 days. The symptom is a 401 on the Monday of week 13.

Artifactory is 7.77.10 as of the last export; the upgrade to 7.9x is with the Artifactory team
(ART-3320) and is expected to break the user plugin that refuses `latest` tags.
