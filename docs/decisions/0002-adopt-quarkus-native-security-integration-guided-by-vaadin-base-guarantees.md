---
status: accepted
date: 2026-07-03
decision-makers: Dario Götze
---

# Adopt Quarkus-native security integration guided by Vaadin base guarantees

## Context and Problem Statement

Quarkus-Hilla positions itself as an alternative runtime for Vaadin/Hilla
applications. Vaadin's official security integration (`vaadin-spring`) is
built on Spring Security. While extending Quarkus-Hilla security support
beyond form login (OIDC, JWT, basic, and other Quarkus mechanisms — see
[docs/plans/quarkus-hilla-security-oidc-parity.md](../plans/quarkus-hilla-security-oidc-parity.md)),
the recurring question is how much of `vaadin-spring`'s security package
should be ported.

Spring and Quarkus follow different security concepts (session-bound
`SecurityContext` + `ROLE_` prefixes + first-match rule chains vs.
`SecurityIdentity` + `quarkus.http.auth.permission.*` with AND semantics +
cookie/token-based stateless auth). Porting Spring classes 1:1 would import
Spring problems that Quarkus does not have and produce non-idiomatic APIs for
Quarkus users.

## Decision

Parity with `vaadin-spring` is defined at the level of **guarantees and
documentation, never at the level of ported classes**.

1. **Quarkus is the source of truth** for authentication (OIDC, form, basic,
   JWT: challenges, tokens, sessions, identity, role mapping) and coarse HTTP
   authorization (`quarkus.http.auth.permission.*`, `HttpSecurity` observer,
   named `HttpSecurityPolicy` beans). Quarkus-Hilla implements no OIDC/token
   logic and no own authorization rule language.
2. **Quarkus-Hilla is the bridge**: framework-internal request handling,
   route/endpoint annotation security, navigation access control, login and
   logout navigation, menu filtering.
3. **Vaadin's base security guarantees must hold** on Quarkus exactly as on
   Spring. The canonical list (secure by default; framework internals
   reachable; route annotations for Flow and Hilla routes; server-side
   endpoint security; deep link ≙ client navigation; UIDL/push-aware
   redirects; menus reflect access) is maintained in
   [docs/security/README.md](../security/README.md) section 2 and must be
   codified as an integration test suite.
4. **Spring-specific mechanisms are not ported** when Quarkus solves the
   underlying problem natively or does not have it: stateless-JWT split
   cookies (Quarkus auth is cookie/token-based, not session-bound), `ROLE_`
   prefix handling (no prefix concept in Quarkus roles), request cache /
   saved-request handling (Quarkus OIDC restores the original URL natively),
   `SecurityContextHolder` strategies (Quarkus uses
   `CurrentIdentityAssociation`/context propagation).
5. **Spring parity is provided as documentation**: a migration and
   compatibility matrix mapping `VaadinWebSecurity`/`VaadinSecurityConfigurer`
   concepts to Quarkus configuration is maintained in
   [docs/security/README.md](../security/README.md) sections 5–6.

Non-goals: re-implementing Spring Security APIs on Quarkus; inventing a
Quarkus-Hilla-specific authorization rule language; supporting security
setups that bypass Quarkus security extensions.

## Consequences

* Good, because Quarkus users get idiomatic configuration (`quarkus.http.auth.*`,
  CDI beans, build items) instead of a foreign Spring-shaped API.
* Good, because the maintained surface stays small: no ports of
  `VaadinStatelessSecurityConfigurer`, `VaadinRolePrefixHolder`,
  `VaadinDefaultRequestCache`, `VaadinAwareSecurityContextHolderStrategy`.
* Good, because guarantees-as-tests make regressions and behavioral drift
  from Vaadin's expectations detectable.
* Bad, because developers migrating from `vaadin-spring` find familiar
  classes missing and must rely on the migration documentation.
* Bad, because some guarantees (UIDL-aware redirects, menu filtering) still
  need Quarkus-side implementations before the guarantee suite passes.

## Implementation Plan

* **Affected paths**: `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/`,
  `commons/deployment/src/main/java/com/github/mcollovati/quarkus/hilla/deployment/security/`,
  `integration-tests/security-form-tests/`, `integration-tests/security-oidc-tests/`,
  `docs/security/README.md`
* **Dependencies**: Quarkus security extensions only (`quarkus-oidc`,
  `quarkus-vertx-http`, `quarkus-security`); no Spring Security artifacts.
* **Patterns to follow**: security model detection via `HillaSecurityBuildItem`
  (build-time config → `SecurityInformationBuildItem` → `Capability` probing);
  configuration under the `vaadin.security.*` config root; identity access via
  `SecurityIdentity`.
* **Patterns to avoid**: porting Spring classes for their own sake; reading
  Spring concepts (e.g. `ROLE_` prefix) into Quarkus role handling; any
  Quarkus-Hilla-owned authentication endpoints for OIDC.

### Verification

- [ ] An integration test suite exists that asserts each base guarantee from
      `docs/security/README.md` section 2 for form and OIDC setups.
- [ ] `commons/runtime` has no compile dependency on Spring Security classes
      for security functionality (Spring types that Hilla itself requires,
      e.g. multipart, are out of scope).
- [ ] The migration matrix in `docs/security/README.md` covers every class of
      `com.vaadin.flow.spring.security` (25.2.x) with status and rationale.
- [ ] Secure-by-default activation for non-form models has an opt-out switch
      and migration notes.

## Alternatives Considered

* **Port `vaadin-spring` security classes 1:1**: rejected — imports Spring
  problems Quarkus does not have (session-bound auth, role prefixes), yields
  non-idiomatic APIs, and doubles the maintenance surface.
* **Own authorization rule language under `vaadin.security.*`**: rejected —
  duplicates `quarkus.http.auth.permission.*`, splits the source of truth,
  and confuses users about precedence.

## More Information

Detailed architecture and matrices: [docs/security/README.md](../security/README.md).
Related: [ADR-0003](0003-integrate-security-through-vaadin-flow-server-spis.md),
[ADR-0004](0004-use-tri-state-decisions-for-http-permission-navigation-checking.md),
[ADR-0005](0005-replace-reflection-based-path-rule-evaluation-with-public-contracts.md).
Decision emerged from the 2026-07 security review (Claude + Codex adversarial)
of branch `ai/eloquent-lehmann-c82f12`. Revisit if Vaadin ships a
runtime-agnostic security auto-configuration that changes the SPI landscape.
