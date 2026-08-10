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

### Composition with the annotation checker

Verified against flow-server 25.2.1 bytecode: Vaadin's stock
`AnnotatedViewAccessChecker` **denies** views without any security annotation,
and `DefaultAccessCheckDecisionResolver` turns a mixed ALLOW+DENY vote into a
"no unanimous consensus" REJECT (an exception in dev mode). With both
checkers active this breaks the core use case *"protect a route solely via
`quarkus.http.auth.permission.*`"*: unannotated view + permitting HTTP rule →
annotation-DENY + http-ALLOW → blocked, while the direct HTTP request passes —
violating base guarantee 5 (deep link ≙ client navigation). Vaadin's own
documentation discourages combining annotation and path checkers for exactly
this reason.

Therefore Quarkus-Hilla registers a **variant of the annotated checker that
returns `NEUTRAL` instead of `DENY` for views without any security
annotation** whenever the HTTP-permission checker is active (annotated-view
decisions themselves are unchanged). Resulting matrix:

| View annotation | HTTP rule | Result |
|---|---|---|
| satisfied | none | allow |
| satisfied | deny | blocked → deny (conflict surfaced) |
| violated | allow | blocked → deny (stricter wins) |
| none | allow/roles satisfied | **allow** (matches direct HTTP) |
| none | none | all-neutral → deny (secure by default) |

For annotated routes, the HTTP decision and annotation decision are
conjunctive: neither may override the other. Runtime configuration can own an
unannotated route or tighten an annotated route, but a permitting rule cannot
weaken `@RolesAllowed` or `@DenyAll`.

The checker also evaluates unnamed global `HttpSecurityPolicy` beans. The
Hilla bridge policy is excluded to prevent recursion, and named policies stay
inside Quarkus's authoritative path policy. A global deny is decisive; a
global permit without a matching path rule does not by itself opt an
unannotated route out of secure-by-default behavior.

Non-goals: implementing the boolean `AccessPathChecker` SPI for Quarkus rules;
re-implementing Vaadin's decision resolver; allowing configuration or
annotations to override the other authorization layer.

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
  a neutral-on-unannotated variant of `AnnotatedViewAccessChecker` plus its
  registration in `commons/deployment/.../security/QuarkusHillaSecurityProcessor.java`,
  `integration-tests/security-oidc-tests/` (decision assertions).
* **Dependencies**: none.
* **Patterns to follow**: `NO_MATCH` must stay strictly neutral — never map it
  to allow or deny; error-handling navigation contexts return neutral; the
  `DENY` result identifies the policy that denied. Enforcement always
  short-circuits on the first deny, matching Quarkus semantics; remaining
  custom policies are never executed only for diagnostics because they may be
  expensive, have side effects, or depend on prior identity augmentation.
* **Patterns to avoid**: short-circuiting the annotation checker; interpreting
  Quarkus rule absence; evaluating navigation with a method other than `GET`;
  changing annotated-view semantics in the checker variant.

### Verification

- [x] Unit/integration tests assert all four mapping rows of the decision
      table (including lowercase/mismatching `methods=` configuration).
- [x] Parity tests run through the **full `NavigationAccessControl`**
      (both checkers + decision resolver), not only the checker in isolation,
      and show identical outcomes to the direct HTTP request for each row.
- [x] A direct `GET` and a navigation to an **unannotated** route protected
      only by an HTTP permission rule both succeed for an authorized user and
      are both denied otherwise (composition matrix row 4).
- [x] A route with `@AnonymousAllowed` and no HTTP rule stays accessible
      (NEUTRAL does not override annotation allow).
- [x] A route with annotation allow but HTTP deny is denied.
- [x] An unnamed global policy deny produces the same direct and synthetic
      result in the OIDC integration suite.

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

2026-07-12 implementation update: global unnamed policies are now evaluated;
config-only unannotated routes, conflicting annotations/configuration,
`@DenyAll`, global policies and direct/navigation parity are covered by the
OIDC integration suite. Configuration/annotation mismatches are diagnostic
(`off|warn|fail`) and never change the conjunctive enforcement rule.
