# Security policy

This project is an initial development implementation. Security fixes are maintained on `develop`; no production release or long-term support line has been published yet.

Report suspected vulnerabilities privately to **security@qrun.io**. Include the affected commit/version, a minimal reproduction with synthetic data, the impact and relevant environment details. Do not open a public issue containing exploit details, passwords, device credentials, real scans or private identifiers. The QRun-IO maintainers coordinate investigation and disclosure.

## Security boundaries

- User authentication and device approval are both required. Installation IDs and reported hardware properties are not proof of device identity.
- Device tokens are supplied in HTTP headers and stored as hashes in a private table. They are not native QQQ process inputs.
- The host must enable `enforcePermissionsInCoreActions=true` and restrict `mobile.admin` separately from `mobile.scan`.
- Approval is checked for each scan, including async/resumed work. Direct mutations and private credential reads through generic table APIs are blocked.
- Scan event IDs make retries idempotent; server-derived user/device provenance is not accepted from client fields.

Use HTTPS for deployed servers and an appropriate production identity provider, database, backup and secret-management configuration. The loopback H2 demo and its sample accounts are for local development. Clearing app data or losing enrollment credentials requires fresh enrollment and administrator approval.

Keep dependency updates and security checks enabled. Physical TC22 acceptance, production authentication configuration and a deployment review are still required before a production rollout.
