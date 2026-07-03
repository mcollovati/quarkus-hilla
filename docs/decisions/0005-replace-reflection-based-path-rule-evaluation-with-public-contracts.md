---
status: accepted
date: 2026-07-03
decision-makers: Dario Götze
---

# Replace reflection-based path-rule evaluation with public contracts

## Context and Problem Statement

`QuarkusAccessPathChecker` answers "would a `GET` to path *P* be permitted for
identity *I*?" outside a real HTTP request (Vaadin navigation, later menu
filtering). Spring exposes a public API for exactly this
(`WebInvocationPrivilegeEvaluator`, used by `vaadin-spring` and Thymeleaf);
**Quarkus has no public equivalent**.

The current implementation therefore reads private fields of
`io.quarkus.vertx.http.runtime.security.*` via reflection
(`AbstractPathMatchingHttpSecurityPolicy`, inner `HttpMatcher`,
`RolesAllowedHttpSecurityPolicy`, `HttpSecurityConfiguration`, `RolesMapping`)
and evaluates policies against a **synthetic** `SecurityIdentity` built from
the Vaadin `NavigationContext` (principal + role predicate only).

The 2026-07 security review (Claude + Codex adversarial) identified this as
the main weakness:

* **Fail-open risk (high)**: custom/named policies that inspect claims
  (`getAttribute`), credentials, or the role *set* (`getRoles`) receive empty
  or null values from the synthetic identity and can silently invert their
  decision (e.g. "deny when role BLOCKED present" becomes permit).
* **Fragility**: any Quarkus upgrade that renames a private field breaks at
  first navigation (`ExceptionInInitializerError`), not at startup, with no
  API contract protecting against it.

## Decision

Phased replacement; `QuarkusAccessPathChecker` remains the internal seam so
callers never change:

1. **Interim first**: rebuild the internal rule model from **public contracts
   only** — parse `quarkus.http.auth.permission.*` / `quarkus.http.auth.policy.*`
   configuration (public contract), observe the `HttpSecurity` event to
   capture programmatic rules (public API), resolve named `HttpSecurityPolicy`
   beans via CDI (public). Evaluate custom policies with the **real
   request-scoped `SecurityIdentity`** — never a synthetic one. Identity state
   that is unavailable **fails closed**. Shipping this workaround now is an
   explicit, accepted trade-off.
2. **Hardening while any reflection remains**: fail-fast startup validation
   of all reflected fields (fail at boot, not at first navigation) and a
   Quarkus version-matrix CI job.
3. **Upstream afterwards**: once the Quarkus-Hilla implementation (PR) exists,
   open a feature request / PR at quarkusio/quarkus for a public evaluator in
   `quarkus-vertx-http`, roughly
   `CheckResult evaluate(String path, String method, SecurityIdentity identity)`
   returning `PERMIT`/`DENY`/`NO_MATCH` — describing the problem and
   **referencing the Quarkus-Hilla PR/code** as concrete motivation and prior
   art. Rationale for acceptance: Quarkus owns matcher and policies,
   evaluation with the real identity is trivial internally, the need is
   generic for UI frameworks (Spring precedent), and it also serves menu
   filtering.
4. When the upstream API is available, the internal model becomes a
   delegation and is deleted.

Non-goals: keeping reflection as a permanent solution; evaluating policies
with fabricated identity state; supporting policies that require a live
`RoutingContext` beyond path/method (these fail closed with a diagnostic).

## Consequences

* Good, because the fail-open inversion for claim/role-set-based policies is
  eliminated (real identity or fail-closed).
* Good, because Quarkus upgrades stop being a runtime-breakage risk; drift
  becomes test-detectable instead of crash-detectable.
* Good, because the same evaluator serves navigation and `MenuAccessControl`.
* Bad, because the interim model re-implements Quarkus path-matching
  semantics (canonicalization, method matching, AND combination, roles
  mapping) — drift risk, mitigated by the parity test suite.
* Bad, because the upstream timeline is not under our control; the interim
  model may live longer than intended.

## Implementation Plan

* **Affected paths**:
  `commons/runtime/.../security/QuarkusAccessPathChecker.java` (rewrite
  internals, keep `check(...)`/`AccessCheck` seam),
  `commons/runtime/.../security/QuarkusNavigationAccessControl.java`
  (identity sourcing), `commons/deployment/.../security/QuarkusHillaSecurityProcessor.java`
  (drop `ReflectiveClassBuildItem` for Quarkus internals once reflection is
  gone), `integration-tests/security-oidc-tests/` (parity suite).
* **Dependencies**: none new; later an optional dependency on the upstream
  evaluator API when released.
* **Patterns to follow**: inject the request-scoped `SecurityIdentity`
  (`CurrentIdentityAssociation`) instead of building one; path
  canonicalization equivalent to Quarkus (`HttpSecurityUtils`-style:
  percent-decoding, dot segments, backslashes, matrix parameters);
  `quarkus.http.root-path`-aware matching.
* **Patterns to avoid**: `setAccessible(true)` on Quarkus classes; synthetic
  `SecurityIdentity`; interpreting missing identity state as "no roles/claims"
  (must fail closed); silent `NO_MATCH` on evaluation errors.

### Verification

- [ ] Upstream issue/PR filed at quarkusio/quarkus (after the Quarkus-Hilla
      implementation PR exists, referencing it) and linked here.
- [ ] No reflection on `io.quarkus.vertx.http.runtime.security` internals in
      `commons/runtime` (grep for `getDeclaredField`/`setAccessible`).
- [ ] A named policy that inspects `SecurityIdentity.getRoles()` or
      `getAttribute()` yields the same decision via HTTP and via navigation,
      or the navigation check fails closed with a diagnostic.
- [ ] Parity test suite runs every permission scenario as direct HTTP request
      and as navigation check with identical outcomes, including encoded
      paths, dot segments, and non-default `quarkus.http.root-path`.
- [ ] Until reflection removal: startup fails fast when a reflected field is
      missing, and CI runs the security ITs against the pinned and the latest
      Quarkus 3.x.

## Alternatives Considered

* **Keep reflection permanently**: rejected — no API contract, fail-open
  identity semantics, breakage surfaces at first navigation in production.
* **Fork Quarkus matching code into the extension**: rejected — same drift
  risk as the config-model without the benefit of staying on public
  contracts; license/maintenance overhead.
* **Wait for upstream before shipping anything**: rejected — OIDC support is
  needed now; the seam keeps the migration cheap.

## More Information

Related: [ADR-0002](0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md),
[ADR-0004](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md).
Review findings and roadmap: [docs/security/README.md](../security/README.md)
sections 3.4, 4 (D7), and 7. Revisit trigger: upstream evaluator API released
→ execute step 4 and update this ADR under More Information.
