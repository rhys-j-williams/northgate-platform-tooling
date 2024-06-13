# terraform

Landing zone for the Beacon migration. **There is no application code here and nothing in this
directory is deployed to anything customers touch.**

Context, for whoever finds this in 2026 and wonders: Beacon is the programme to move the
notifications fan-out (and, on the roadmap, alerts preferences) off the OpenShift estate and onto
AWS Lambda behind SNS. The business case was approved in 2023 (BCN-101), the AWS sandbox account
was provisioned in 2024-Q2 (BCN-201), and the landing zone proper (VPC, CMK, permissions boundary,
state bucket, Secrets Manager sync from Vault) is with the cloud platform team as BCN-207. Until
that lands, this module exists so that:

- the *shape* of the target is agreed (Lambda + SNS + DLQ, CMK everywhere, 400 day logs, mandatory
  tags from GIS-STD-040, permissions boundary), and
- the platform-tooling Jenkins job can run `terraform validate` and `tflint` on it so we are not
  writing Terraform for the first time on the day BCN-207 closes.

`modules/notifications-lambda` is the stub module. `src/index.js` is a placeholder handler that logs
the SNS envelope and returns 200; the Beacon team replaces it under BCN-240, at which point the
handler moves to its own repository and this module takes an artifact rather than a directory.
`envs/sandbox` is the only environment and every account-specific value in it is `CHANGEME-*`.

Nothing here is applied by CI. `terraform apply` needs the sandbox role, which two people have.

## Running the checks

```
cd modules/notifications-lambda && terraform init -backend=false && terraform validate
cd envs/sandbox && terraform init -backend=false && terraform validate
```

`init -backend=false` because the state bucket does not exist yet. Provider is pinned to
`5.31.0` in both places; bumping it is a TOOL ticket like any other dependency
(`../governance/DEPENDENCY_POLICY.md` applies to providers, the internal Terraform registry mirror
is `artifactory.meridian.internal/artifactory/api/terraform/terraform-virtual`).

## Decisions so far

- arm64. Cheaper, and nothing in the handler cares.
- No Secrets Manager permissions in the role yet. When they are added, the secret is fed from Vault
  (see `../vault/README.md`), not created in Terraform, and the role reads one named ARN.
- SNS -> Lambda -> SQS DLQ rather than SQS -> Lambda. Ordering does not matter for notifications
  and SNS lets the OpenShift side keep publishing during cutover (BCN-240 design note).
- `nodejs18.x`, to match platform-services. Moving to 20 waits for PLAT to move.

## Open

- BCN-207 landing zone. Everything `CHANGEME` here is waiting on it.
- Whether alerts-preferences-service follows. Not decided; the exposure-calc team have opinions.
- Cost tag `CC-4471` is the Beacon programme code and will need to change to the run cost centre
  at go live. Finance have been told.
