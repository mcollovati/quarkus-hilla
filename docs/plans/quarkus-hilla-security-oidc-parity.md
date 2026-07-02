# 1. Goal
Complete Quarkus-Hilla security parity for Quarkus OIDC and clarify/enforce the boundary between Quarkus HTTP rules, Hilla endpoint security, and Vaadin Flow navigation security.

# 2. Approach
Use Quarkus as the authentication and coarse HTTP authorization source of truth: OIDC, Keycloak, token/session handling, role mapping, and `quarkus.http.auth.permission.*` stay in Quarkus. Keep Quarkus-Hilla responsible for Vaadin/Hilla-specific semantics that Quarkus HTTP rules cannot infer: Flow route annotations, Hilla browser-callable method annotations, framework-internal requests, anonymous endpoint/view detection, login navigation, and client-route/menu state. Reuse the local OIDC branches selectively: take the `HillaSecurityBuildItem`/security-model activation idea from `issues/30-add-oidc-support`, retain current `main` helpers like `EndpointUtil`/`RouteUtil`/`PathUtil`, and treat the `spike/security-auth-path-checker` implementation as a matcher skeleton only.

# 3. Branch Assessment
- `issues/30-add-oidc-support`: keep the core idea of replacing `AuthFormBuildItem` with `HillaSecurityBuildItem`, detecting Quarkus security models from `SecurityInformationBuildItem`/capabilities, and registering Hilla security components for non-form security. Do not copy untested restore-after-login behavior blindly; add tests first.
- `issues/30-add-oidc-support-preserve`: most valuable historically because it introduced anonymous endpoint/view and web icon handling, but much of that already exists on `main` in `EndpointUtil`, `RouteUtil`, `PathUtil`, and `HillaSecurityPolicy`. Use it as reference, not as a patch source.
- `origin/spike/security-oauth`: superseded by the newer local branch. Its `vaadin.oidc.login.path` concept should not be used as-is; a generic `vaadin.security.login-path`/existing `VaadinSecurityConfig` extension is better because navigation does not need to know the auth provider type.
- `spike/security-auth-path-checker`: useful for path normalization and Quarkus `ImmutablePathMatcher` usage. It is incomplete for production because it ignores `Principal`, roles, Quarkus role policies, and cannot express `NEUTRAL` when combined with `AnnotatedViewAccessChecker`.

# 4. File Changes
- Modify `commons/deployment/src/main/java/com/github/mcollovati/quarkus/hilla/deployment/security/AuthFormBuildItem.java`: replace or rename to `HillaSecurityBuildItem` with models such as `NONE`, `FORM`, `OIDC`, `OAUTH2`, `BASIC`, `JWT`, and security-extension-backed identity providers where supported.
- Modify `commons/deployment/src/main/java/com/github/mcollovati/quarkus/hilla/deployment/security/QuarkusHillaSecurityProcessor.java`: register `HillaSecurityPolicy`, `EndpointUtil`, `QuarkusNavigationAccessControl`, and access checkers whenever Quarkus Security is effectively present, not only when form auth is enabled. Keep `HillaFormAuthenticationMechanism` registration strictly form-auth-only.
- Modify `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/HillaSecurityRecorder.java`: split form-login policy configuration from generic “security policy used” marking, as in the OIDC branch, while preserving the current `VaadinSecurityConfig` logout mapping.
- Modify `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/VaadinSecurityConfig.java`: add generic navigation-login properties only if needed, e.g. `loginPath` and possibly `restorePathAfterLogin`, avoiding a provider-specific `vaadin.oidc.*` namespace.
- Modify `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/QuarkusNavigationAccessControl.java`: use `SecurityIdentity` for principal/roles when Vaadin request data is unavailable; add restore-after-login only behind tests and config.
- Modify `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/HillaSecurityPolicy.java`: keep it global by default, but ensure it behaves as a Vaadin/Hilla bridge: permit framework internals/anonymous endpoints/routes/custom icons; delegate authentication challenges to Quarkus; do not reimplement OIDC.
- Create `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/QuarkusAccessPathChecker.java`: adapt the spike only as a low-level Quarkus path matcher/parser for explicit use.
- Create `commons/runtime/src/main/java/com/github/mcollovati/quarkus/hilla/security/QuarkusHttpPermissionNavigationAccessChecker.java`: if Quarkus path rules should influence Vaadin navigation, implement this as a `NavigationAccessChecker` that returns `NEUTRAL` when no Quarkus rule matches, `ALLOW` for matching permit/authenticated/role-allowed rules, and `DENY` for matching deny/authenticated/role-denied rules.
- Modify `commons/deployment/src/test/java/com/github/mcollovati/quarkus/hilla/deployment/EndpointSecurityTest.java`: add assertions that non-form Quarkus security still registers Hilla endpoint annotation checks.
- Modify `commons/deployment/src/test/java/com/github/mcollovati/quarkus/hilla/deployment/SignalsSecurityTest.java`: keep/extend Basic auth coverage to prove Signals are not form-only.
- Create `integration-tests/security-oidc-tests/pom.xml`: new Keycloak/OIDC integration-test module using Quarkus OIDC and Keycloak Dev Services.
- Create `integration-tests/security-oidc-tests/src/main/resources/application.properties`: configure `quarkus.oidc.*`, Keycloak Dev Services, role mapping, and a small set of HTTP path policies.
- Create `integration-tests/security-oidc-tests/src/main/java/...`: OIDC-secured Flow views, Hilla views, browser-callable services, and user/admin roles mirroring `security-form-tests`.
- Create `integration-tests/security-oidc-tests/src/test/java/...`: browser/API tests for anonymous, authenticated, user-role, admin-role, direct URL load, client navigation, endpoint invocation, and logout/login redirects.

# 5. Implementation Steps
## Task 1: Decouple security activation from form login
1. Replace `AuthFormBuildItem` in `commons/deployment/src/main/java/com/github/mcollovati/quarkus/hilla/deployment/security` with `HillaSecurityBuildItem` modeled after `issues/30-add-oidc-support`.
2. In `QuarkusHillaSecurityProcessor`, derive the model from Quarkus build-time form auth, `SecurityInformationBuildItem`, and relevant `Capability` entries.
3. Register `HillaSecurityPolicy` and `QuarkusNavigationAccessControl` for any auth-enabled model; register `HillaFormAuthenticationMechanism` only for `FORM`.
4. Preserve existing `EndpointUtil`, `RouteUtil`, `PathUtil`, and `WebIconsRequestMatcher` behavior already present on `main`.

## Task 2: Keep OIDC delegated to Quarkus
1. Do not add custom OIDC login/callback/token/session code.
2. Let Quarkus OIDC create `SecurityIdentity`; use that identity in `QuarkusNavigationAccessControl`, `EndpointAccessChecker`, Signals, and Hilla policy checks.
3. Add generic login navigation config only if OIDC tests prove Vaadin needs a frontend login path distinct from Quarkus challenge URLs.

## Task 3: Define HTTP-rule boundary
1. Document and test that `quarkus.http.auth.permission.*` is used for static resources, REST/admin paths, and coarse global constraints.
2. Keep Hilla service roles in Java annotations on `@BrowserCallable` classes/methods; do not require duplicating those roles into `application.properties`.
3. Keep Flow route roles in route annotations plus `NavigationAccessControl`; do not require duplicating route roles into `application.properties`.
4. If both Quarkus HTTP rules and route annotations are configured for the same path, treat them as additive: both must allow. Tests should cover one conflict and assert deterministic denial.

## Task 4: Path checker integration
1. Implement `QuarkusAccessPathChecker` from the spike as a reusable path matcher only, extended to understand `permit`, `deny`, `authenticated`, and role policies.
2. Do not wire Vaadin `RoutePathAccessChecker` blindly together with `AnnotatedViewAccessChecker`, because boolean `AccessPathChecker` cannot express neutral no-match and can cause mixed-consensus rejects.
3. Wire an internal `QuarkusHttpPermissionNavigationAccessChecker` only when Quarkus route-path policy integration is enabled or when explicit matching route policies exist.
4. Ensure no matching Quarkus path rule returns `NEUTRAL`, not implicit allow or deny, so annotation-based navigation remains the default.

## Task 5: OIDC/Keycloak tests
1. Add a new `security-oidc-tests` integration module based on `security-form-tests` structure.
2. Configure Keycloak Dev Services and two users/roles: `USER` and `ADMIN`.
3. Test direct HTTP load of public Flow/Hilla views, protected Flow/Hilla views, user-only/admin-only views, and anonymous denial.
4. Test Hilla browser-callable services for `@AnonymousAllowed`, `@PermitAll`, `@RolesAllowed`, and default-deny under OIDC.
5. Test Hilla Push/Signals under OIDC if the current test harness can pass bearer/session credentials to WebSocket; otherwise add a focused follow-up test plan for that transport.

# 6. Acceptance Criteria
- With `quarkus-oidc` and no `quarkus.http.auth.form.enabled`, `HillaSecurityPolicy` is registered and Vaadin/Hilla security behavior is active.
- With form auth, current form-login TypeScript behavior and logout config remain compatible with `security-form-tests`.
- OIDC login, role extraction, and `SecurityIdentity.hasRole` drive Flow route checks and Hilla endpoint checks.
- Anonymous Hilla endpoints/views remain accessible under OIDC without requiring coarse `/connect/*=permit` user configuration.
- Protected Hilla endpoint methods return 401 for anonymous, 403 for authenticated wrong role, and 200 for matching role.
- Flow route annotations deny in-app server navigation even when no full page reload occurs.
- Hilla client-route/menu tests show only allowed routes for anonymous, `USER`, and `ADMIN`; sensitive data remains protected by endpoint tests.
- Quarkus HTTP path rule `permit` for a static resource path succeeds without Vaadin annotation involvement.
- Quarkus HTTP path rule `deny` or role mismatch for a configured route/API path denies access even if Vaadin annotations would otherwise allow, with deterministic test expectations.
- No OIDC-specific code handles tokens/callbacks manually inside Quarkus-Hilla.

# 7. Verification Steps
- `mvn test -pl commons/deployment -Dtest=EndpointSecurityTest,SignalsSecurityTest`
- `mvn verify -pl integration-tests/security-form-tests -Pit-tests`
- `mvn verify -pl integration-tests/security-oidc-tests -Pit-tests`
- `mvn spotless:check -Pall-modules`
- Manual smoke check in OIDC app: open anonymous public route, protected route redirects/challenges through Quarkus OIDC, login as user, verify user/admin menu differences, call user/admin endpoints.

# 8. Risks & Mitigations
- Risk: `SecurityInformationBuildItem` or capability detection misses security mechanisms that do not publish a model. Mitigation: include a capability fallback and tests for Basic plus OIDC.
- Risk: A global Hilla policy conflicts with user-defined Quarkus HTTP permissions. Mitigation: keep the policy pass-through for non-Vaadin/Hilla paths and document that explicit Quarkus HTTP rules are additive constraints.
- Risk: Standard Vaadin `RoutePathAccessChecker` conflicts with annotation checks because it cannot return `NEUTRAL`. Mitigation: do not auto-wire it with annotations; implement a Quarkus-specific `NavigationAccessChecker` that can return neutral.
- Risk: Restore-after-login behavior from the spike changes existing form-login redirect semantics. Mitigation: keep it disabled or unchanged until browser tests cover form and OIDC redirects.
- Risk: Keycloak Dev Services tests may be slow/flaky. Mitigation: keep one focused OIDC module with minimal users/roles and avoid duplicating all form UI tests.