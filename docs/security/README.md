# Quarkus-Hilla Security Architecture

This document records the architecture, the design decisions, and the Spring
compatibility story for security support in Quarkus-Hilla (form login, OIDC,
and other Quarkus security mechanisms). It complements the implementation plan
in [docs/plans/quarkus-hilla-security-oidc-parity.md](../plans/quarkus-hilla-security-oidc-parity.md).

Status: partially implemented (see [Roadmap](#roadmap-and-known-gaps)).

The decisions in this document are formally recorded as ADRs:
[ADR-0002](../decisions/0002-adopt-quarkus-native-security-integration-guided-by-vaadin-base-guarantees.md)
(Quarkus-native integration, base guarantees),
[ADR-0003](../decisions/0003-integrate-security-through-vaadin-flow-server-spis.md)
(Vaadin SPI boundary),
[ADR-0004](../decisions/0004-use-tri-state-decisions-for-http-permission-navigation-checking.md)
(tri-state navigation decisions),
[ADR-0005](../decisions/0005-replace-reflection-based-path-rule-evaluation-with-public-contracts.md)
(replacement of reflection-based rule evaluation).

---

## 1. Guiding principle

> **Vaadin's base security guarantees must hold. The implementation follows
> Quarkus concepts and idioms — Quarkus-Hilla does not re-build Spring
> Security.**

Quarkus-Hilla positions itself as an alternative runtime for Vaadin/Hilla
applications. Where Spring and Quarkus solve the same problem differently,
Quarkus-Hilla adopts the Quarkus solution and only bridges the gap towards
Vaadin's expectations. Spring parity matters at the level of *guarantees* and
*documentation*, never at the level of ported classes.

### Layered responsibility model

| Layer | Owner | Responsibility |
|---|---|---|
| Authentication (OIDC, form, basic, JWT, …) | **Quarkus** | Challenges, token/session handling, identity creation (`SecurityIdentity`), role mapping |
| Coarse HTTP authorization | **Quarkus** | `quarkus.http.auth.permission.*` / `quarkus.http.auth.policy.*`, `HttpSecurity` observer API, named `HttpSecurityPolicy` beans |
| Vaadin/Hilla bridge | **Quarkus-Hilla** | Framework-internal request handling, route/endpoint annotation security, navigation access control, login/logout navigation, menu filtering |
| UI framework SPIs | **Vaadin (flow-server)** | `NavigationAccessControl`, `NavigationAccessChecker`, `AccessPathChecker`, `MenuAccessControl`, decision resolver |

Quarkus-Hilla never implements OIDC/token logic itself and never invents its
own authorization rule language. It consumes Quarkus results and translates
them into Vaadin SPI decisions.

## 2. Vaadin base guarantees

These guarantees are runtime-independent. Any Vaadin/Hilla application must be
able to rely on them regardless of whether it runs on Spring or Quarkus. They
are the acceptance criteria for the security integration and should be
codified as an integration test suite:

1. **Secure by default.** When security is active, a Flow route without any
   access decision (no annotation, no matching HTTP rule) is denied. This is
   Vaadin's standard `NavigationAccessControl` behavior (all checkers
   `NEUTRAL` → reject).
2. **Framework internals stay reachable.** UIDL, heartbeat, push, `/VAADIN/*`,
   service-worker and web icon requests are always permitted
   (`HillaSecurityPolicy`, `WebIconsRequestMatcher`).
3. **Route annotations work everywhere.** `@AnonymousAllowed`, `@PermitAll`,
   `@RolesAllowed`, `@DenyAll` are enforced for Flow routes and Hilla client
   routes.
4. **Endpoint security is server-side.** `@BrowserCallable` method access is
   checked on the server against the real identity; unannotated methods are
   denied by default.
5. **Deep links and client-side navigation agree.** A direct URL load and an
   in-app router navigation to the same route yield the same access decision.
6. **UIDL/push-aware redirects.** Authentication challenges and logout during
   a UIDL/XHR/push request must use Vaadin client commands, not plain HTTP
   302 (an XHR cannot follow a redirect to an IdP).
7. **Menus reflect access.** The client route/menu list only advertises routes
   the current user may access (`MenuAccessControl` SPI).

## 3. How it works

### 3.1 Security model detection (build time)

`QuarkusHillaSecurityProcessor` produces a `HillaSecurityBuildItem` with a
`SecurityModel` derived from, in order:

1. Build-time config `quarkus.http.auth.form` → `FORM`
2. `SecurityInformationBuildItem` published by Quarkus security extensions
   (`oidc`, `oauth2`, `jwt`, `basic`)
3. Quarkus `Capability` probing (`OIDC`, `JWT`, `SECURITY_ELYTRON_OAUTH2`,
   `SECURITY_JPA`, `SECURITY_ELYTRON_JDBC`, `SECURITY_ELYTRON_LDAP`, generic
   `SECURITY` → `SECURITY_EXTENSION`)
4. Otherwise `NONE`

Any model other than `NONE` activates the generic security integration.
`FORM` additionally activates the form-login specifics
(`HillaFormAuthenticationMechanism`, login/error page handling in
`HillaSecurityPolicy.withFormLogin`).

### 3.2 HTTP layer: `HillaSecurityPolicy`

A global Quarkus `HttpSecurityPolicy` that acts as the Vaadin/Hilla bridge for
direct HTTP requests:

- permits framework-internal requests, public resources, anonymous Hilla
  endpoints and anonymous Flow/Hilla routes;
- delegates everything else to `AuthenticatedHttpSecurityPolicy`, i.e. the
  application is authenticated-by-default at the HTTP level;
- never issues challenges itself — authentication challenges remain with the
  active Quarkus mechanism (OIDC redirect, basic header, form redirect).

Because Quarkus combines all matching policies with AND semantics, user
defined `quarkus.http.auth.permission.*` rules apply *in addition* to this
policy.

### 3.3 Navigation layer: `NavigationAccessControl` + checkers

`QuarkusNavigationAccessControl` (extends Vaadin's `NavigationAccessControl`)
resolves principal and role membership from the Vaadin request, falling back
to the CDI `SecurityIdentity` (covers push/websocket situations where the
Vaadin request carries no auth data).

Two Vaadin-SPI `NavigationAccessChecker`s are registered:

- **`AnnotatedViewAccessChecker`** (Vaadin stock) — route annotation
  semantics; registered when the application has security annotations on
  routes.
- **`QuarkusHttpPermissionNavigationAccessChecker`** (Quarkus-Hilla) — makes
  Quarkus HTTP permission rules count for router navigation, so a rule like
  `quarkus.http.auth.permission.admin.paths=/admin/*` protects the `/admin`
  route also for client-side navigation, where no HTTP request per route
  happens. Delegates to `QuarkusAccessPathChecker`.

#### Tri-state decision design

The HTTP permission checker deliberately returns a **tri-state** result
instead of Spring's boolean `AccessPathChecker` contract:

| Quarkus rule evaluation | Navigation decision |
|---|---|
| No rule matches the path | `NEUTRAL` |
| All matching policies permit | `ALLOW` |
| Any matching policy denies | `DENY` |
| Path matches, HTTP method does not | `DENY` (mirrors Quarkus behavior) |

Rationale: in Spring setups there is effectively always a catch-all rule
(`anyRequest()`), so a boolean answer is meaningful. Quarkus HTTP permissions
are typically sparse — the absence of a rule must not be interpreted as
allow *or* deny, otherwise the combination with the annotation checker in
Vaadin's decision resolver breaks. `NEUTRAL` composes correctly:
annotation-allow + no-http-rule → allow; annotation-allow + http-deny → deny;
no decision at all → deny (guarantee 1).

### 3.4 Path rule evaluation: `QuarkusAccessPathChecker`

Answers "would a `GET` to path *P* be permitted for identity *I*?" outside a
real HTTP request. It reuses Quarkus's effective path-matching security policy
(runtime config, `HttpSecurity` observer permissions, named policy beans,
shared permissions, method matching, roles mapping) so that navigation
decisions cannot drift from direct-request decisions.

> **Interim implementation, scheduled for replacement.** The current
> implementation reads private fields of
> `io.quarkus.vertx.http.runtime.security.*` via reflection and evaluates
> policies against a synthetic `SecurityIdentity`/`RoutingContext`. This is
> the main known weakness of the design — see decision D7 and the
> [Roadmap](#roadmap-and-known-gaps).

### 3.5 Endpoint layer

Hilla `@BrowserCallable` security is annotation-driven and enforced by the
Hilla endpoint access checker wired to the Quarkus `SecurityIdentity`
(unannotated methods deny by default — guarantee 4). This layer is
independent of the navigation layer.

### 3.6 Login path for non-form mechanisms

`vaadin.security.login-path` (build & runtime fixed config) tells the
navigation access control where to send an unauthenticated user on a denied
navigation when a non-form mechanism (e.g. OIDC) is active. If unset, denied
anonymous navigations surface as rejections and authentication challenges are
only triggered by direct HTTP requests.

## 4. Design decisions

**D1 — Quarkus is the source of truth for authentication and coarse HTTP
authorization.** No OIDC/callback/token/session code in Quarkus-Hilla.
`quarkus.http.auth.permission.*` is the rule language; Quarkus-Hilla does not
introduce its own.

**D2 — Integrate through Vaadin SPIs, patch bytecode only where Hilla
hardcodes Spring.** The integration points are the public flow-server SPIs
(`NavigationAccessChecker`, `NavigationAccessControl`, `AccessPathChecker`,
`MenuAccessControl`). The existing bytecode patching infrastructure
(`AtmospherePatches`, `OffendingMethodCallsReplacer`) is used to remove hard
Spring dependencies inside Hilla classes, never to bypass Vaadin SPIs.

**D3 — Quarkus idioms instead of Spring ports.** Spring-specific mechanisms
are not ported when Quarkus solves the underlying problem natively or does
not have the problem at all (see [matrix](#5-spring--quarkus-compatibility-matrix)).

**D4 — Tri-state navigation decisions.** See
[section 3.3](#tri-state-decision-design). Deviating from Spring's boolean
`AccessPathChecker` is intentional and required for correct composition with
annotation-based checks under sparse Quarkus rule sets.

**D5 — HTTP permission rules govern navigation.** Client-side route changes
produce no HTTP request per route; without this bridge,
`quarkus.http.auth.permission.*` rules would silently not apply to in-app
navigation (deep link protected, router navigation open). The checker closes
that gap (guarantee 5).

**D6 — Secure by default for every auth model.** The generic integration
(global policy, navigation control) activates for *any* detected Quarkus
security model, not only form login. Note: this changes behavior for
applications that previously used e.g. OIDC without the Hilla security
integration — public paths now need `@AnonymousAllowed` routes/endpoints or
explicit permit rules. An opt-out configuration switch and migration notes
are planned (see roadmap).

**D7 — Reflection-based rule evaluation is interim; move to public contracts
and upstream API.** Target state, in order of preference:

1. **Upstream Quarkus evaluator API.** File a feature request / draft PR at
   quarkusio/quarkus for a public equivalent of Spring's
   `WebInvocationPrivilegeEvaluator`, roughly
   `CheckResult evaluate(String path, String method, SecurityIdentity identity)`
   with `PERMIT`/`DENY`/`NO_MATCH`. Quarkus owns matcher and policies, can
   evaluate with the real identity, and the need is generic for any UI
   framework on Quarkus (Spring precedent: Thymeleaf `sec:authorize-url`).
2. **Until then: build the rule model from public contracts only** —
   `quarkus.http.auth.permission.*`/`policy.*` config (public contract),
   observing the `HttpSecurity` event to capture programmatic rules (public
   API), resolving named `HttpSecurityPolicy` beans via CDI (public), and
   evaluating custom policies with the *real request-scoped*
   `SecurityIdentity` (never a synthetic one). Anything not representable
   fails closed.
3. `QuarkusAccessPathChecker` stays the seam: when the upstream API lands,
   the internal model becomes a delegation and is deleted.

Drift risk against Quarkus matching semantics is handled by a parity test
suite that runs every scenario twice — as a direct HTTP request and through
the navigation checker — plus a Quarkus version matrix in CI.

## 5. Spring ↔ Quarkus compatibility matrix

What vaadin-spring (25.2.x) provides, what Quarkus-Hilla does about it, and
why:

| vaadin-spring | Purpose | Quarkus-Hilla | Status / rationale |
|---|---|---|---|
| `VaadinSecurityConfigurer` / `SpringSecurityAutoConfiguration` | Wire Vaadin into Spring Security's `HttpSecurity`, permit framework paths, login view | `QuarkusHillaSecurityProcessor` + `HillaSecurityPolicy` | ✅ Equivalent, Quarkus build steps + global `HttpSecurityPolicy` |
| `SpringNavigationAccessControl` | Principal/roles from Spring `SecurityContext` | `QuarkusNavigationAccessControl` | ✅ Equivalent, based on `SecurityIdentity` |
| `AnnotatedViewAccessChecker` (flow-server) | Route annotation security | Same class, registered by the extension | ✅ Shared Vaadin SPI |
| `SpringAccessPathChecker` (via `WebInvocationPrivilegeEvaluator`) | "Would this URL be allowed?" for navigation | `QuarkusAccessPathChecker` + `QuarkusHttpPermissionNavigationAccessChecker` | ⚠️ Equivalent intent; interim reflection-based implementation, see D7. Tri-state instead of boolean by design (D4) |
| `WebIconsRequestMatcher` | Permit icon/PWA resources | Ported (`WebIconsRequestMatcher`) | ✅ Runtime-agnostic Vaadin mechanics |
| `AuthenticationContext` | Injectable app API: current user, `logout()`, listeners | Planned: thin CDI helper over `SecurityIdentity` + Quarkus logout | 🔜 Same need (UI-thread logout, guarantee 6), Quarkus-idiomatic API instead of a port |
| `SpringMenuAccessControl` | Filter client menu/routes by access | Planned: `MenuAccessControl` implementation on `SecurityIdentity` | 🔜 Missing; small, high user-visible value |
| `VaadinDefaultRequestCache` + `VaadinSavedRequestAwareAuthenticationSuccessHandler` | Redirect back to the original (client-side) route after login | Not ported | ❌ Quarkus OIDC restores the original URL natively (`redirect_uri` handling); form auth uses Quarkus landing-page/cookie handling. Only the guarantee is tested (deep link → challenge → return) |
| `UidlRedirectStrategy` | Vaadin-client-aware redirect during UIDL/push | Planned | 🔜 Genuine Vaadin mechanics, needed for logout/session-expiry UX (guarantee 6) |
| `VaadinAwareSecurityContextHolderStrategy` | Make Spring's `ThreadLocal` `SecurityContext` visible on Vaadin session/push threads | Not ported | ❌ Spring-specific problem. Quarkus analog is `CurrentIdentityAssociation`/context propagation; the *guarantee* (identity available during push) is covered by the `SecurityIdentity` fallback in `QuarkusNavigationAccessControl` |
| `VaadinRolePrefixHolder` | Strip/apply Spring's `ROLE_` authority prefix | Not ported | ❌ Pure Spring-ism. Quarkus roles are plain strings from claims/groups; `@RolesAllowed("ADMIN")` matches role `ADMIN` directly. Role renaming exists natively (`quarkus.http.auth.roles-mapping`, `quarkus.oidc.roles.*`) |
| `security/stateless/*` (`VaadinStatelessSecurityConfigurer`, JWT split cookies) | Keep authentication out of the HTTP session for horizontal scaling | Not ported | ❌ Problem does not exist on Quarkus: bearer/JWT is stateless by definition, OIDC web-app stores token state in encrypted cookies, even Quarkus form auth uses an encrypted `quarkus-credential` cookie instead of the session. Note: Vaadin *UI* state (VaadinSession/Push) remains server-side on both runtimes — that is not auth state |

## 6. Migration guide: vaadin-spring → Quarkus-Hilla

Conceptual mapping for developers coming from `VaadinWebSecurity` /
`VaadinSecurityConfigurer`:

| Spring | Quarkus-Hilla |
|---|---|
| `extends VaadinWebSecurity` / `VaadinSecurityConfigurer` bean | Nothing — auto-activated when a Quarkus security extension is present |
| `http.authorizeHttpRequests(r -> r.requestMatchers("/admin/**").hasRole("ADMIN"))` | `quarkus.http.auth.permission.admin.paths=/admin/*`<br>`quarkus.http.auth.permission.admin.policy=admin`<br>`quarkus.http.auth.policy.admin.roles-allowed=ADMIN` |
| Programmatic rules | `void configure(@Observes HttpSecurity http)` (Quarkus `HttpSecurity` observer) |
| Custom `AuthorizationManager` | Named `HttpSecurityPolicy` CDI bean + `quarkus.http.auth.permission.<name>.policy=<bean-name>` |
| `setLoginView(LoginView.class)` | Form: `quarkus.http.auth.form.login-page`<br>Other mechanisms: `vaadin.security.login-path` |
| Spring OAuth2/OIDC client + `setOAuth2LoginPage` | `quarkus-oidc` extension (`quarkus.oidc.*`), challenges handled by Quarkus |
| `hasRole("ADMIN")` → authority `ROLE_ADMIN` | Role is the plain string `ADMIN`; no prefix concept |
| Role/authority mapping (`GrantedAuthoritiesMapper`) | `quarkus.http.auth.roles-mapping.*`, `quarkus.oidc.roles.role-claim-path` |
| `AuthenticationContext.getAuthenticatedUser()` | `@Inject SecurityIdentity` (helper API planned) |
| `AuthenticationContext.logout()` | `vaadin.security.logout-path` + planned helper; OIDC logout via `quarkus.oidc.logout.*` |
| `VaadinStatelessSecurityConfigurer` | Not needed — Quarkus auth is cookie/token-based, see matrix |
| Session-based CSRF (Spring) | Hilla CSRF token handling built into the extension |
| `@EnableWebSecurity` debug etc. | `quarkus.log.category."io.quarkus.vertx.http.runtime.security".level=DEBUG` |

Behavioral differences to be aware of:

- **AND vs first-match:** Quarkus evaluates *all* matching HTTP policies and
  every one must permit; Spring evaluates the first matching rule. Overlapping
  path rules therefore behave differently.
- **Method matching:** a Quarkus permission with `methods=` set denies
  non-listed methods on matched paths (the navigation checker mirrors this,
  including treating navigation as `GET`).
- **Sparse rules:** in Quarkus, a path without any matching permission is not
  an implicit deny at the HTTP layer. The navigation layer, however, stays
  deny-by-default (guarantee 1) — protect routes with annotations or rules.

## 7. Roadmap and known gaps

Tracked outcomes of the 2026-07 security review (Claude + Codex adversarial):

1. **Replace synthetic `SecurityIdentity`** in `QuarkusAccessPathChecker` with
   the real request-scoped identity; fail closed for identity state that is
   not available (claims via `getAttribute`, credentials, permission checks).
   Without this, custom policies that inspect claims or the role *set* can
   silently invert during navigation (review finding, high).
2. **Remove reflection on Quarkus internals** per D7 (public-contract model,
   upstream evaluator API issue at quarkusio/quarkus). Until removal: fail-fast
   startup validation of the reflected fields + Quarkus version-matrix CI job.
3. **Path canonicalization parity** with Quarkus (`HttpSecurityUtils`-style
   percent-decoding, dot segments, backslashes) before matching.
4. **`quarkus.http.root-path` awareness** — permission paths are root-path
   relative, `Location.getPath()` is not; non-default root paths currently
   bypass the navigation checker (silent `NO_MATCH`).
5. **Opt-out switch + migration notes** for the secure-by-default activation
   on non-form models (D6).
6. **`MenuAccessControl` implementation** (guarantee 7).
7. **`AuthenticationContext`-style CDI helper** (current user, logout from UI
   thread).
8. **`UidlRedirectStrategy` equivalent** for challenge/logout during
   UIDL/push (guarantee 6).
9. **Parity test suite** (same scenario via HTTP and via navigation checker)
   and guarantee-based acceptance tests for section 2.
10. Cleanup: remove the now-unused `AuthFormBuildItem`.
