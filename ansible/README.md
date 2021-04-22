# ansible

Configuration for the Jenkins build agents. One playbook, `build-agent.yml`, three roles. Runs
weekly from the platform Jenkins and after every agent image refresh. Owner: Platform Engineering.
Ticket key TOOL; the original build-out was TOOL-1019.

What it does:

- `build_agent_common` — packages, the jenkins user, internal root CA, `.npmrc` and
  `settings.xml` from `../registry/`, motd.
- `nginx_hardening` — GIS-STD-014 headers, TLS settings and `server_tokens off` on the agent's
  local nginx (which serves the Artifactory read-through cache, TOOL-988).
- `log_forwarder` — Splunk UF with `inputs.conf`/`outputs.conf` on RHEL 8/9; rsyslog omhttp to
  HEC on RHEL 7 because the UF package was pulled from the RHEL 7 repo (SPLK-902). The HEC token
  comes from Vault at run time; the `CHANGEME-splunk-hec-token` in `group_vars` is only there so
  `--check` works without Vault.

Inventory is `inventory/build-agents.ini`. Groups match the Jenkins agent labels. The
`nodejs14_rhel7` group is out of support and the play still runs against it because business-web
still builds there (MBZ-2231); the pre_task prints a warning rather than failing. TOOL-1290
tried to drop the group and was reverted within a day.

Running:

```
ansible-playbook build-agent.yml --syntax-check
ansible-playbook build-agent.yml --limit nodejs16_rhel8 --check --diff
ansible-playbook build-agent.yml --tags nginx
```

Needs `community.hashi_vault` (`ansible-galaxy collection install -r requirements.yml`) for the
Vault lookup; without it the lookup errors and the placeholder is used, which is fine for
`--check` and wrong for anything else.

Known issues: the RHEL 7 TLS exception GIS-EX-2023-118 has lapsed; `build_agent_common` ignores
package errors on RHEL 7 because buildah/skopeo are not in that repo; the root CA file in this
directory is a placeholder and the real one is distributed by PKI.
