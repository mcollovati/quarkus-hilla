---
status: accepted
date: 2026-07-03
decision-makers: Dario Götze
---

# Use tri-state decisions for HTTP-permission navigation checking

## Context and Problem Statement

Quarkus HTTP permission rules (`quarkus.http.auth.permission.*`) must also
govern Vaadin router navigation, because client-side route changes produce no
HTTP request per route (deep links would be protected while in-app navigation
stays open).

`vaadin-spring` solves the equivalent problem with `SpringAccessPathChecker`,
which implements Vaadin's **boolean** `AccessPathChecker` SPI on top of
Spring's `WebInvocationPrivilegeEvaluator`. A boolean answer works on Spring
because Spring security configurations effectively always contain a catch-all
rule (`anyRequest()`), so every path has a defined allow/deny answer.

Quarkus rule sets are typically **sparse**: many paths have no matching
permission at all, and at the Quarkus HTTP layer the absence of a rule is not
a deny. Forcing that into a boolean would either turn "no rule" into ALLOW
(security hole when combined with other checkers) or into DENY (breaks every
route that is protected by annotations instead of HTTP rules).

## Decision

The navigation checker (`QuarkusHttpPermissionNavigationAccessChecker` backed
by `QuarkusAccessPathChecker`) returns a **tri-state** result and is a
`NavigationAccessChecker` (not a boolean `AccessPathChecker`):

| Quarkus rule evaluation | Navigation decision |
|---|---|
| No rule matches the path | `NEUTRAL` |
| All matching policies permit | `ALLOW` |
| Any matching policy denies | `DENY` |
| Path matches, HTTP method does not | `DENY` (mirrors Quarkus HTTP behavior; navigation is evaluated as `GET`) |

`NEUTRAL` composes correctly in Vaadin's default decision resolver:
annotation-allow + no-http-rule → allow; annotation-allow + http-deny → deny
(consensus violation); no decision at all → reject (secure by default).

Non-goals: implementing the boolean `AccessPathChecker` SPI for Quarkus rules;
re-implementing Vaadin's decision resolver.

## Consequences

* Good, because sparse Quarkus rule sets combine correctly with
  `AnnotatedViewAccessChecker` and any future checkers.
* Good, because Quarkus AND-semantics (all matching policies must permit) and
  method-mismatch denial are preserved for navigation, so deep links and
  client navigation agree.
* Bad, because behavior deviates from `vaadin-spring` (`RoutePathAccessChecker`
  is not reused); the difference must be documented for migrating users.
* Bad, because "no rule + no annotation → reject" surprises applications that
  previously ran without navigation access control — requires migration notes
  (tracked in ADR-0002 verification).

## Implementation Plan

* **Affected paths**:
  `commons/runtime/.../security/QuarkusAccessPathChecker.java`
  (`Decision`/`AccessCheck` types),
  `commons/runtime/.../security/QuarkusHttpPermissionNavigationAccessChecker.java`
  (mapping to `context.neutral()/allow()/deny()`),
  `integration-tests/security-oidc-tests/` (decision assertions).
* **Dependencies**: none.
* **Patterns to follow**: `NO_MATCH` must stay strictly neutral — never map it
  to allow or deny; error-handling navigation contexts return neutral.
* **Patterns to avoid**: short-circuiting the annotation checker; interpreting
  Quarkus rule absence; evaluating navigation with a method other than `GET`.

### Verification

- [ ] Unit/integration tests assert all four mapping rows of the decision
      table (including lowercase/mismatching `methods=` configuration).
- [ ] Parity tests show identical outcomes for direct HTTP request vs.
      navigation checker for each row.
- [ ] A route with `@AnonymousAllowed` and no HTTP rule stays accessible
      (NEUTRAL does not override annotation allow).
- [ ] A route with annotation allow but HTTP deny is denied.

## Alternatives Considered

* **Boolean `AccessPathChecker` SPI (Spring approach)**: rejected — requires a
  defined answer for every path; Quarkus has no catch-all convention, so
  "no rule" cannot be answered truthfully with allow/deny.
* **Treat unmatched paths as DENY**: rejected — breaks annotation-protected
  routes and all public framework paths not covered by rules.
* **Treat unmatched paths as ALLOW**: rejected — silently disables
  secure-by-default when the annotation checker is absent.

## More Information

Related: [ADR-0002](0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md),
[ADR-0003](0003-integrate-security-through-vaadin-flow-server-spis.md),
[ADR-0005](0005-replace-reflection-based-path-rule-evaluation-with-public-contracts.md).
Design rationale: [docs/security/README.md](../security/README.md) section 3.3.
