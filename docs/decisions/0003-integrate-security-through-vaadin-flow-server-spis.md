---
status: accepted
date: 2026-07-03
decision-makers: Dario Götze
---

# Integrate security through Vaadin flow-server SPIs

## Context and Problem Statement

Quarkus-Hilla must plug Quarkus security decisions into Vaadin's navigation
and menu machinery. Vaadin (flow-server 25.x) exposes public SPIs for this:
`NavigationAccessControl`, `NavigationAccessChecker`, `AccessPathChecker`,
`MenuAccessControl`, and the access-check decision resolver. The alternative
would be to patch or fork Vaadin internals — Quarkus-Hilla already has
bytecode patching infrastructure (`AtmospherePatches`,
`OffendingMethodCallsReplacer`) that could technically be used for that.

The risk to manage: SPI-level integration keeps Quarkus-Hilla compatible with
Vaadin's evolution, while bytecode patching creates invisible coupling to
Vaadin internals that breaks silently on upgrades.

## Decision

Security integrates **exclusively through Vaadin's public flow-server SPIs**:

* `QuarkusNavigationAccessControl` extends `NavigationAccessControl`.
* Quarkus HTTP permission enforcement for navigation is a
  `NavigationAccessChecker` implementation
  (`QuarkusHttpPermissionNavigationAccessChecker`).
* Vaadin stock checkers (`AnnotatedViewAccessChecker`) are reused, not
  re-implemented. One documented exception: a minimal variant of the
  annotated checker that stays `NEUTRAL` for views without any security
  annotation, required for correct composition with the HTTP-permission
  checker (see [ADR-0004](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md));
  it changes only the unannotated case and remains a plain
  `NavigationAccessChecker` SPI implementation.
* Menu filtering (planned) implements the `MenuAccessControl` SPI.

**Bytecode patching is allowed only to remove hard Spring dependencies inside
Hilla/Vaadin classes** (the established pattern in this project), never to
bypass, replace, or short-circuit a Vaadin security SPI.

Non-goals: forking Vaadin security classes; maintaining patched copies of
flow-server auth logic.

## Consequences

* Good, because Vaadin maintains the decision-combining semantics (resolver,
  deny-by-default) and Quarkus-Hilla inherits fixes and evolution.
* Good, because SPI implementations are unit-testable against public,
  documented contracts.
* Bad, because SPI limitations must be accepted or worked around at the SPI
  level (e.g. `NavigationContext` exposes only principal + role predicate,
  not the full identity — see ADR-0005).
* Bad, because a Vaadin SPI change (major version) requires adaptation —
  mitigated by pinning the Vaadin version (`hilla.version`) and the
  integration test suite.

## Implementation Plan

* **Affected paths**:
  `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/QuarkusNavigationAccessControl.java`,
  `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/QuarkusHttpPermissionNavigationAccessChecker.java`,
  a future `QuarkusMenuAccessControl`,
  `commons/deployment/.../security/QuarkusHillaSecurityProcessor.java`
  (checker/bean registration).
* **Dependencies**: `com.vaadin:flow-server` (via `hilla.version`), no new ones.
* **Patterns to follow**: register SPI implementations as CDI beans via
  `AdditionalBeanBuildItem` in the security processor; keep checkers stateless
  and delegate rule evaluation to `QuarkusAccessPathChecker`.
* **Patterns to avoid**: `BytecodeTransformerBuildItem` against
  `com.vaadin.flow.server.auth.*`; duplicating logic that a stock Vaadin
  checker already provides; subclassing Vaadin internals not marked as API.

### Verification

- [ ] No `BytecodeTransformerBuildItem` targets classes in
      `com.vaadin.flow.server.auth`.
- [ ] Navigation security works with stock `AnnotatedViewAccessChecker` +
      Quarkus-Hilla checkers combined by Vaadin's default decision resolver
      (asserted by integration tests).
- [ ] `MenuAccessControl` implementation registered and menu entries filtered
      by access in the OIDC integration test app.
- [ ] Vaadin version bumps (25.x) pass the security integration test suite
      without patching.

## Alternatives Considered

* **Bytecode-patch Vaadin security internals**: rejected — invisible coupling,
  silent breakage on Vaadin upgrades, impossible for users to reason about.
* **Own navigation listener outside `NavigationAccessControl`**: rejected —
  would bypass Vaadin's decision resolver and break composition with
  annotation-based checks and future Vaadin checkers.

## More Information

Related: [ADR-0002](0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md),
[ADR-0004](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md).
Architecture details: [docs/security/README.md](../security/README.md) section 3.
