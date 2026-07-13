# Quarkus-Hilla Security Architecture

This document records the architecture, the design decisions, and the Spring
compatibility story for security support in Quarkus-Hilla (form login, OIDC,
and other Quarkus security mechanisms). It complements the implementation plan
in [docs/plans/quarkus-hilla-security-oidc-parity.md](../plans/quarkus-hilla-security-oidc-parity.md).

Status: partially implemented (see [Roadmap](#roadmap-and-known-gaps)).

This end-to-end Quarkus-native security bridge is exclusive to Quarkus-Hilla:
it covers Flow navigation, Hilla client routes, and browser-callable endpoints.
The official
[Vaadin Quarkus extension](https://vaadin.com/docs/latest/flow/integrations/quarkus#limitations)
is Flow-only and does not support Hilla. Vaadin Flow itself still provides the
generic navigation-security SPIs used by this integration.

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
3. **Route constraints work everywhere.** `@AnonymousAllowed`, `@PermitAll`,
   `@RolesAllowed` and `@DenyAll` are enforced for Flow routes; Hilla client
   routes enforce their `loginRequired` and `rolesAllowed` metadata.
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
  endpoints and authorized Flow/Hilla routes;
- denies registered Flow or Hilla client routes when their composed access
  checks deny, while paths that belong to neither routing model remain owned
  by Quarkus HTTP/JAX-RS security;
- never issues challenges itself — authentication challenges remain with the
  active Quarkus mechanism (OIDC redirect, basic header, form redirect).

Because Quarkus combines all matching policies with AND semantics, user
defined `quarkus.http.auth.permission.*` rules apply *in addition* to this
policy.

### 3.3 Navigation layer: `NavigationAccessControl` + checkers

`QuarkusNavigationAccessControl` (extends Vaadin's `NavigationAccessControl`)
resolves principal and role membership from a request-captured
`SecurityIdentity`. For navigation it uses the identity captured after
application identity augmentors but before transport-path policies, then
reapplies Quarkus's global role mapping. This prevents roles granted only to
the `/connect` transport request from leaking into the target route.

One composite Vaadin-SPI `NavigationAccessChecker` is registered:

- **`QuarkusHttpPermissionNavigationAccessChecker`** (Quarkus-Hilla) — makes
  Quarkus HTTP permission rules count for router navigation, so a rule like
  `quarkus.http.auth.permission.admin.paths=/admin/*` protects the `/admin`
  route also for client-side navigation, where no HTTP request per route
  happens. It delegates HTTP evaluation to `QuarkusAccessPathChecker` and
  then applies Vaadin's annotation semantics through
  `QuarkusAnnotatedViewAccessChecker`.

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

#### Composition with the annotation checker

Vaadin's stock `AnnotatedViewAccessChecker` *denies* unannotated views, and
Vaadin's decision resolver turns a mixed ALLOW+DENY vote into a blocking
"no unanimous consensus" result. Registering both checkers as-is would
therefore block the core use case "protect a route solely via
`quarkus.http.auth.permission.*`" (unannotated view + permitting rule →
blocked navigation, passing deep link — a guarantee-5 violation). Quarkus-Hilla
registers a variant of the annotated checker that returns `NEUTRAL` for views
**without any** security annotation when the HTTP-permission checker is
active; annotated views keep stock semantics. See ADR-0004 for the full
composition matrix.

All unnamed global `HttpSecurityPolicy` beans are also evaluated for
synthetic navigation, in Quarkus CDI order. The Hilla bridge policy itself is
excluded to prevent recursion; named policies remain owned by Quarkus's path
policy. A denying or failing custom policy fails closed. A permitting global
policy without a matching path rule does not turn an otherwise unannotated
route into an explicitly owned route; secure-by-default therefore remains
intact.

### 3.4 Path rule evaluation: `QuarkusAccessPathChecker`

Answers "would a `GET` to path *P* be permitted for identity *I*?" outside a
real HTTP request. It reuses Quarkus's effective path-matching security policy
(runtime config, `HttpSecurity` observer permissions, named policy beans,
shared permissions, method matching, roles mapping) so that navigation
decisions cannot drift from direct-request decisions.

The checker invokes Quarkus's actual `PathMatchingHttpSecurityPolicy`; it does
not reflect private fields and does not replay permission configuration for
enforcement. A narrow same-package compatibility bridge exposes only the
package-private operations needed to determine path-specific authentication
mechanisms, the authoritative path-match marker, the global role mapping and
the blocking authorization context. This coupling fails at compile time on
an incompatible Quarkus upgrade instead of silently changing authorization.

The target request receives a synthetic `RoutingContext`, but policy
evaluation uses the real captured `SecurityIdentity`, including claims,
credentials and permissions. Authentication is re-run through Quarkus's
`HttpAuthenticator` for every target path, even without an explicit
`auth-mechanism` constraint, so path-dependent tenant selection and identity
augmentors are recomputed. A different or unreproducible principal is denied;
an identity created by another target mechanism is never reused.

The target context starts with a narrow allowlist rather than copied transport
state. Global Quarkus role mapping is preserved, but arbitrary
`RoutingContext.data()`, body and upload state are not. A custom policy that
depends on state produced by an earlier application handler must reconstruct
that state for the target or treat its absence as deny. Such policies can
detect this path through
`QuarkusAccessPathChecker.SYNTHETIC_NAVIGATION_ATTRIBUTE`. Exact parity cannot
be claimed for a policy that treats missing target-only state as permit. Missing
identity, event-loop invocation, policy exceptions and malformed target paths
fail closed. Path canonicalization delegates to
`HttpSecurityUtils.normalizePath`.

### 3.5 Endpoint layer

Hilla `@BrowserCallable` security is annotation-driven and enforced by
`QuarkusEndpointAccessChecker` against the request-captured transport
`SecurityIdentity`. The REST controller is explicitly blocking and preserves
the identity across the REST/Hilla thread boundary. Path-specific role
augmentation for `/connect` therefore applies to endpoint annotations, while
unannotated methods remain denied by default (guarantee 4).

Registered Hilla client routes use their `loginRequired`/`rolesAllowed`
metadata as a separate tri-state check. A registered denial is decisive; a
path that is neither a Flow nor a Hilla client route remains owned by Quarkus
HTTP/JAX-RS security instead of receiving a new Hilla-wide default.

The server loads the complete generated Hilla route tree and composes the
metadata of every layout/ancestor with the leaf route. Matching mirrors React
Router for required and optional parameters, optional static segments,
wildcards, parameter suffixes, absolute nested routes, case-insensitive static
segments, ranking ties and encoded slashes inside parameters. If the complete
tree cannot be loaded, a path that is still identifiable as a Hilla route is
denied rather than evaluated from incomplete ancestry metadata.

### 3.6 Login path for non-form mechanisms

`vaadin.security.login-path` is runtime-initialized configuration (read when
the process starts). It tells the navigation access control where to send an
unauthenticated user on a denied navigation when a non-form mechanism (e.g.
OIDC) is active. If unset, denied anonymous navigations surface as rejections
and authentication challenges are only triggered by direct HTTP requests.

### 3.7 Annotation/configuration composition and diagnostics

For an annotated route or endpoint, effective authorization is always:

`Quarkus HTTP authorization AND Vaadin/Hilla annotation authorization`

Neither layer can weaken the other. In particular, a `permit` HTTP rule never
overrides `@RolesAllowed` or `@DenyAll`, and an open annotation never overrides
a stricter HTTP policy. A completely unannotated Flow route may instead be
owned by a matching Quarkus HTTP permission; with neither a matching rule nor
an annotation it remains denied by default.

`vaadin.security.annotation-config-mismatch=off|warn|fail` controls a
startup diagnostic, not authorization precedence. The default is `warn`.
`fail` aborts startup only for a mismatch that can be proven from the static
permission configuration. Configured custom policies, role augmentation,
authentication-mechanism constraints and parameterized routes are reported as
unverified and never treated as proven failures. Programmatic and unnamed
global policies are outside this static comparison because they cannot be
enumerated without replaying application code; enforcement still evaluates
the authoritative Quarkus policies. This makes deployment-time policy changes
visible without introducing a configuration switch that can silently override
code security invariants.

The startup comparison currently inventories Flow routes. Hilla endpoint
method annotations are enforced conjunctively at request time, but are not yet
included in the static mismatch report.

Here, runtime configuration means that operators can choose the value per
application start or deployment. It is not a promise that authorization rules
are hot-reloaded safely inside an already running process.

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

**D7 — Reuse authoritative Quarkus policies through a narrow compatibility
bridge; pursue an upstream public evaluator.** The implementation:

1. Invokes the actual Quarkus path and unnamed global policies with the real
   target-reauthenticated identity and a sanitized target request context.
2. Isolates package-private Quarkus access in
   `QuarkusHillaSecurityBridge`; it uses no reflection or private-field model.
3. Fails closed when exact evaluation is unavailable and verifies direct vs.
   synthetic parity in integration tests.
4. Still targets an upstream Quarkus equivalent of Spring's
   `WebInvocationPrivilegeEvaluator`, roughly
   `CheckResult evaluate(String path, String method, SecurityIdentity identity)`
   with `PERMIT`/`DENY`/`NO_MATCH`.
5. Keeps `QuarkusAccessPathChecker` as the seam so the compatibility bridge
   can become an upstream delegation when that API exists.

**D8 — Configuration and annotations compose conjunctively.** Runtime
configuration can tighten or add policy for unannotated routes, but cannot
silently weaken code annotations. Mismatch handling is diagnostic only
(`off|warn|fail`); there is intentionally no "configuration wins" override.

## 5. Spring ↔ Quarkus compatibility matrix

What vaadin-spring (25.2.x) provides, what Quarkus-Hilla does about it, and
why:

| vaadin-spring | Purpose | Quarkus-Hilla | Status / rationale |
|---|---|---|---|
| `VaadinSecurityConfigurer` / `SpringSecurityAutoConfiguration` | Wire Vaadin into Spring Security's `HttpSecurity`, permit framework paths, login view | `QuarkusHillaSecurityProcessor` + `HillaSecurityPolicy` | ✅ Equivalent, Quarkus build steps + global `HttpSecurityPolicy` |
| `SpringNavigationAccessControl` | Principal/roles from Spring `SecurityContext` | `QuarkusNavigationAccessControl` | ✅ Equivalent, based on `SecurityIdentity` |
| `AnnotatedViewAccessChecker` (flow-server) | Route annotation security | `QuarkusAnnotatedViewAccessChecker`, delegating annotated cases to Vaadin | ✅ Vaadin semantics; unannotated routes stay neutral for HTTP-rule composition |
| `SpringAccessPathChecker` (via `WebInvocationPrivilegeEvaluator`) | "Would this URL be allowed?" for navigation | `QuarkusAccessPathChecker` + `QuarkusHttpPermissionNavigationAccessChecker` | ✅ Equivalent intent using authoritative Quarkus policies; narrow compile-time compatibility bridge until a public Quarkus evaluator exists. Tri-state instead of boolean by design (D4) |
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
| Conflicting code annotation and deployment rule | Effective access is the conjunction; configure `vaadin.security.annotation-config-mismatch=warn` (default), `fail`, or `off` for startup diagnostics |

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
- **Annotations and configuration:** both apply. Configuration is runtime
  deployable and may tighten policy or own an unannotated route, but does not
  override `@DenyAll`, `@RolesAllowed`, `@PermitAll` or `@AnonymousAllowed`.

## 7. Roadmap and known gaps

Completed outcomes of the 2026-07 security review:

- Real request identity capture across HTTP, servlet, Flow and Hilla endpoint
  boundaries; transport-only role augmentation is isolated from target
  navigation and global role mapping is reapplied.
- Reflection-free enforcement through the actual Quarkus path policy and all
  unnamed global policies, with fail-closed custom-policy behavior.
- Quarkus path canonicalization, method semantics, root-path-aware matching
  and path-specific authentication-mechanism enforcement.
- Full Hilla client-route hierarchy enforcement, including protected layouts,
  parameterized and encoded routes, React-compatible matching/ranking and
  fail-closed handling of incomplete route metadata.
- Neutral-on-unannotated annotation composition and direct/navigation parity
  for config-only routes.
- Startup mismatch diagnostics (`off|warn|fail`) with conservative static
  analysis and no authorization override.
- OIDC development and production parity tests, including encoded paths,
  invalid credentials, `@DenyAll`, conflicting roles, global and path role
  augmentation, global policies, Hilla client-route metadata, target-path
  reauthentication, cross-mechanism isolation and non-default HTTP root paths.

Remaining roadmap:

1. **Opt-out switch + migration notes** for the secure-by-default activation
   on non-form models (D6).
2. **`MenuAccessControl` implementation** (guarantee 7).
3. **`AuthenticationContext`-style CDI helper** (current user, logout from UI
   thread).
4. **`UidlRedirectStrategy` equivalent** for challenge/logout during
   UIDL/push (guarantee 6).
5. **Upstream public evaluator API** in Quarkus, replacing the narrow
   same-package compatibility bridge.
6. **Policy-name diagnostics** that report all matched policies while
   preserving Quarkus short-circuit enforcement semantics.
7. **Guarantee-suite completion** for menu filtering, UIDL-aware redirects
   and push identity.
8. **Synthetic target-context extension point** for applications whose custom
   policies require safe reconstruction of handler-produced request state.
