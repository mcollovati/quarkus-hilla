# Architecture Decision Records (ADR)

An Architecture Decision Record (ADR) captures an important architecture decision along with its context and consequences.

## Conventions

- Language: English for all ADR files, index/log entries, status notes, implementation plans, and verification criteria.
- Directory: `docs/decisions`
- Naming:
  - Prefer numbered files when starting fresh: `0001-choose-database.md`
  - If the repo already uses slug-only names, keep that: `choose-database.md`
- Status values: `proposed`, `accepted`, `rejected`, `deprecated`, `superseded`

## Workflow

- Create a new ADR as `proposed`.
- Discuss and iterate.
- When the team commits: mark it `accepted` (or `rejected`).
- If replaced later: create a new ADR and mark the old one `superseded` with a link.

## ADRs

- [Adopt architecture decision records](0001-adopt-architecture-decision-records.md) (accepted, 2026-07-03)
- [Adopt Quarkus-native security integration guided by Vaadin base guarantees](0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md) (accepted, 2026-07-03)
- [Integrate security through Vaadin flow-server SPIs](0003-integrate-security-through-vaadin-flow-server-spis.md) (accepted, 2026-07-03)
- [Use tri-state decisions for HTTP-permission navigation checking](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md) (accepted, 2026-07-03)
- [Replace reflection-based path-rule evaluation with public contracts](0005-replace-reflection-based-path-rule-evaluation-with-public-contracts.md) (accepted, 2026-07-03)