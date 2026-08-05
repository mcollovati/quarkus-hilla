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

`QuarkusAccessPathChecker` remains the internal seam, but enforcement reuses
Quarkus's authoritative runtime objects instead of rebuilding their model:

1. Invoke the actual `PathMatchingHttpSecurityPolicy` and every installed
   unnamed global `HttpSecurityPolicy` except the Hilla bridge itself. Named
   policies, programmatic `HttpSecurity` rules, shared rules, method matching
   and runtime configuration therefore remain Quarkus-owned.
2. Isolate the few package-private operations in a narrow same-package
   `QuarkusHillaSecurityBridge`. This is compile-time compatibility coupling,
   not reflection: an incompatible pinned-Quarkus upgrade fails compilation.
3. Evaluate policies with a target-reauthenticated `SecurityIdentity`.
   Capture the identity after application augmentors but before
   transport-path role augmentation, then rerun authentication and augmentors
   for every target path so tenant/path-dependent identity state is not
   inherited. Endpoint checks keep the transport identity.
4. Build a sanitized target `RoutingContext` from the live request and rerun
   authentication through Quarkus's `HttpAuthenticator`, including when no
   explicit mechanism constraint exists. Never reuse an identity created for
   another path or by a non-selected authentication mechanism.
5. Fail closed when identity/context state is missing, the principal differs,
   execution is attempted on the event loop, a policy fails, or exact target
   authentication cannot be established.
6. Pursue an upstream public evaluator in `quarkus-vertx-http`, roughly
   `CheckResult evaluate(String path, String method, SecurityIdentity identity)`.
   When available, replace the compatibility bridge behind the existing seam.

Non-goals: keeping reflection; replaying Quarkus configuration as a second
enforcement engine; fabricating identity claims, credentials, roles or
permissions; treating an evaluation failure as `NO_MATCH`.

## Consequences

* Good, because the fail-open inversion for claim/role-set-based policies is
  eliminated (real identity or fail-closed).
* Good, because Quarkus owns path matching, named/programmatic policies,
  authentication-mechanism selection and identity augmentation.
* Good, because Quarkus upgrades break the narrow bridge at compile time
  instead of private reflection at first navigation.
* Good, because the same evaluator serves navigation and `MenuAccessControl`.
* Bad, because the same-package bridge intentionally compiles against
  package-private Quarkus contracts and must be reviewed on every Quarkus
  upgrade.
* Bad, because policies receive a sanitized target context rather than a real
  second network request. Arbitrary transport-context, body and upload state is
  deliberately not copied; policies depending on earlier handler state must
  reconstruct it or treat absence as deny.
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
* **Patterns to follow**: capture the request-scoped `SecurityIdentity`
  (`CurrentIdentityAssociation`) at the servlet/endpoint boundary; preserve
  base vs. transport identity; delegate canonicalization to
  `HttpSecurityUtils.normalizePath`; use the actual root-path-aware Quarkus
  matcher and `HttpAuthenticator`.
* **Patterns to avoid**: `setAccessible(true)` on Quarkus classes; synthetic
  identity state; interpreting missing identity state as "no roles/claims"
  (must fail closed); silent `NO_MATCH` on evaluation errors; executing
  remaining policies after a deny only for diagnostics.

### Verification

- [ ] Upstream issue/PR filed at quarkusio/quarkus (after the Quarkus-Hilla
      implementation PR exists, referencing it) and linked here.
- [x] No reflection on `io.quarkus.vertx.http.runtime.security` internals in
      `commons/runtime` (grep for `getDeclaredField`/`setAccessible`).
- [x] A named policy that inspects `SecurityIdentity.getRoles()` or
      `getAttribute()` yields the same decision via HTTP and via navigation,
      or the navigation check fails closed with a diagnostic.
- [x] Parity test suite runs every permission scenario as direct HTTP request
      and as navigation check with identical outcomes, including encoded
      paths, dot segments, and non-default `quarkus.http.root-path`.
- [x] Bearer-only target rules reject an identity created by another
      authentication mechanism in direct and synthetic checks.

## Alternatives Considered

* **Keep reflection permanently**: rejected — no API contract, fail-open
  identity semantics, breakage surfaces at first navigation in production.
* **Rebuild from configuration and `HttpSecurity` observers**: rejected after
  implementation review — duplicates Quarkus's effective model, cannot
  enumerate all programmatic/custom state, and risks direct/navigation drift.
* **Fork Quarkus matching code into the extension**: rejected — duplicates
  matcher semantics and increases upgrade drift.
* **Wait for upstream before shipping anything**: rejected — OIDC support is
  needed now; the seam keeps the migration cheap.

## More Information

Related: [ADR-0002](0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md),
[ADR-0004](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md).
Review findings and roadmap: [docs/security/README.md](../security/README.md)
sections 3.4, 4 (D7), and 7. Revisit trigger: upstream evaluator API released
→ replace the compatibility bridge and update this ADR under More Information.

2026-07-12 implementation update: the reflection/config-replay interim was
replaced by the authoritative-policy compatibility bridge described above.
OIDC parity tests cover named and programmatic rules, global policies, role
augmentation isolation, authentication-mechanism selection, encoded paths
and config/annotation conjunction in development and production modes. A
dedicated fixture verifies direct and synthetic decisions below a non-default
`quarkus.http.root-path` in both modes.
