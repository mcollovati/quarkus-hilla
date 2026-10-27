# ⚙️ Configuration Reference

All Quarkus-Hilla settings go in `application.properties`.

> [!NOTE]
> All `vaadin.*` properties on this page are fixed at build time. A changed value takes effect
> after the application is rebuilt; system properties and environment variables set for an
> existing build are ignored. `quarkus.*` properties follow the Quarkus configuration rules of
> the respective property.

<a id="live-reload"></a>

## 🔄 Live Reload

| Property                                  | Type      | Default   | Description                                                                                                                                                                          |
|-------------------------------------------|-----------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `vaadin.hilla.live-reload.enable`         | Boolean   | `false`   | Enable automatic regeneration of client-side code when endpoint classes change in dev mode.                                                                                          |
| `vaadin.hilla.live-reload.watch-strategy` | Enum      | `CLASS`   | Strategy to watch for changes: `SOURCE` (watch Java source files) or `CLASS` (watch compiled classes). Use `CLASS` with `quarkus.live-reload.instrumentation=true` for best results. |
| `vaadin.hilla.live-reload.watched-paths`  | Set<Path> | All paths | Comma-separated list of paths to watch for changes, relative to source/class root. Example: `com/example/service,com/example/model`                                                  |

See [Endpoints Live Reload](features.md#endpoints-live-reload) for how the feature works.

<a id="endpoints"></a>

## 🎯 Endpoints

| Property                 | Type   | Default    | Description                                                                                                                            |
|--------------------------|--------|------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `vaadin.endpoint.prefix` | String | `/connect` | Custom prefix for Hilla endpoints. The extension automatically generates a custom `connect-client.ts` file with the configured prefix. |

<a id="security"></a>

## 🔒 Security

| Property                                                 | Type    | Default   | Description                                                                                    |
|----------------------------------------------------------|---------|-----------|--------------------------------------------------------------------------------------------------|
| `vaadin.security.logout-path`                            | String  | `/logout` | Path of the logout HTTP POST endpoint handling logout requests.                                |
| `vaadin.security.post-logout-redirect-uri`               | String  | -         | URI to redirect to after successful logout.                                                    |
| `vaadin.security.logout-invalidate-session`              | Boolean | `true`    | Whether HTTP session should be invalidated on logout.                                          |
| `vaadin.security.navigation-access-control.enabled`      | Boolean | `true`    | Whether access to Flow views is checked. See [Route Security](#route-security).                |

<a id="route-security"></a>

### Route Security

![Since 25.1.5](https://flat.badgen.net/static/Since/25.1.5/007bff?scale=1.1)
![Since 25.2.2](https://flat.badgen.net/static/Since/25.2.2/007bff?scale=1.1)
![Since 25.3.0](https://flat.badgen.net/static/Since/25.3.0/007bff?scale=1.1)

With Quarkus form authentication enabled (`quarkus.http.auth.form.enabled=true`), the extension
checks access to Flow views with Vaadin's `NavigationAccessControl`. Access is declared with an
annotation on the view class or one of its superclasses:

| Annotation          | Access                                          |
|---------------------|-------------------------------------------------|
| `@AnonymousAllowed` | All users, including anonymous users            |
| `@PermitAll`        | Authenticated users                             |
| `@RolesAllowed`     | Authenticated users with one of the given roles |
| `@DenyAll`          | No user                                         |

```java
@Route("dashboard")
@PermitAll
public class DashboardView extends VerticalLayout {
}
```

Views without an annotation on the class or a superclass are denied, as in Vaadin's default
configuration. An annotation on a router layout does not apply to the views it contains; it is
checked in addition to the annotation of the view.

#### Login view

Anonymous users who open a view that requires authentication are redirected to the login view.
The extension uses `quarkus.http.auth.form.login-page` as the login view, so navigation within
the application and the Quarkus form login redirect lead to the same page.

Since 25.3.0, the access control is customized with a
[configurer](#customizing-the-access-control). An application can still replace it with its own
`NavigationAccessControl` bean, declared `@Singleton` or `@ApplicationScoped`; a bean extending
`QuarkusNavigationAccessControl` has to be `@Singleton`. The extension applies the login page to
that bean as well, unless the bean defines its own login view:

- A bean extending `QuarkusNavigationAccessControl` with a login URL that differs from
  `quarkus.http.auth.form.login-page` fails the application startup, also when the access check is
  disabled. The error names both values.
- A login view class set by a bean extending `QuarkusNavigationAccessControl` is kept.
- A bean that does not extend `QuarkusNavigationAccessControl` keeps its login view if it rejects
  the login page.

In both cases, the startup log names the kept login view and the configured login page.

A `@Dependent` bean gives every injection point its own instance, so the extension cannot
configure the instances that check navigation. Such a bean fails the startup when
`vaadin.security.navigation-access-control.enabled=false`; otherwise the startup logs a warning and
the login page is not applied.

`QuarkusNavigationAccessControl` is internal API. Extending it still works, but the class may
change in any release.

#### Disabling the access check

> [!IMPORTANT]
> Quarkus-Hilla 25.1.4 and 25.2.1 enabled the access check for Flow views by default
> ([#2264](https://github.com/mcollovati/quarkus-hilla/pull/2264)). After the upgrade, applications
> using Quarkus form authentication can no longer open Flow views without an access annotation.
> The following property restores the previous behavior until the annotations are in place.

```properties
vaadin.security.navigation-access-control.enabled=false
```

The property is fixed at build time and takes effect after a rebuild. An environment variable
therefore cannot disable the access check of a deployed application.

> [!WARNING]
> With the access check disabled, every Flow view is accessible to anyone who can reach the
> application, and `@RolesAllowed` and the other annotations are not evaluated. Use the property
> only as a transitional measure.

Quarkus HTTP permissions remain in effect and can require authentication for selected view
paths:

```properties
quarkus.http.auth.permission.app.paths=/dashboard,/admin/*
quarkus.http.auth.permission.app.policy=authenticated
```

Restrict such rules to the paths that need protection. An `authenticated` rule for `/*` also
blocks anonymous access to the login page and the resources Vaadin needs to render it, unless
more specific rules permit them. HTTP permissions check requests to view URLs, such as the initial
page load. Navigation between Flow views after that does not request the target URL, so only the
access control can check it.

Set to `false`, the property disables the `NavigationAccessControl` at startup, including an
access control provided by the application. It never enables one: with the value `true`, an
access control that the application disabled itself stays disabled.

With the access control disabled, Vaadin's default Spring Security configuration requires
authentication for requests to Flow views unless another rule permits them. Quarkus-Hilla adds no
restriction for Flow views instead, because a Quarkus HTTP permission cannot override a denial
from another security policy, so individual views could not be made public again. Evaluating
Quarkus HTTP permissions for views is planned
([#2332](https://github.com/mcollovati/quarkus-hilla/issues/2332)) and may change this behavior.

#### Customizing the access control

![Since 25.3.0](https://flat.badgen.net/static/Since/25.3.0/007bff?scale=1.1)

An application customizes the access control by producing a `NavigationAccessControlConfigurer`
bean with `@Singleton` scope; other scopes fail the startup. The configurer selects the access
checkers, the decision resolver and the login view, or disables the access control. Like the
access check itself, it applies only with Quarkus form authentication.

```java
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

class NavigationSecurity {

    @Produces
    @Singleton
    NavigationAccessControlConfigurer navigationAccessControlConfigurer(MyAccessChecker checker) {
        return new NavigationAccessControlConfigurer()
                .withAnnotatedViewAccessChecker()
                .withNavigationAccessChecker(checker);
    }
}
```

Without an application configurer, the extension uses all `NavigationAccessChecker` beans,
including those of the application. An application configurer uses only the checkers it selects:

- `withAnnotatedViewAccessChecker()` selects the available `AnnotatedViewAccessChecker` bean,
  including subclasses and CDI client proxies. The startup fails if none is available.
- `withNavigationAccessChecker(...)` and `withNavigationAccessCheckers(...)` add checker instances.
- `withAvailableNavigationAccessCheckers(...)` selects checker beans matching a predicate; a later
  call replaces the predicate. `MyAccessChecker.class::isInstance` also matches subclasses and CDI
  client proxies.

An enabled access control without checkers fails the startup.

The built-in `AnnotatedViewAccessChecker` evaluates the view access annotations with the
`AccessAnnotationChecker` bean (since 25.3.0). As in Vaadin's Spring integration, an application
`AccessAnnotationChecker` bean replaces the extension's default; the endpoint access check uses it
as well.

`withDecisionResolver(...)` takes precedence over the `AccessCheckDecisionResolver` bean. Without
it, the application's resolver bean is used, or the extension's default.

`withLoginView(...)` is optional. Without it, the extension uses
`quarkus.http.auth.form.login-page`. A login URL set on the configurer must match that property;
a different URL fails the startup, also with the access check disabled, and the error names both
values. A login view class is kept, and the startup log names both values, as its route cannot be
compared with the property.
Setting two different login views on one configurer fails immediately.

`disabled()` disables the access control. Setting the build-time property
`vaadin.security.navigation-access-control.enabled` to `false` disables it as well; setting it to
`true` does not override `disabled()`.

Replacing the `NavigationAccessControl` bean is deprecated, logs a warning at startup and will be removed in 26.0. To
migrate, remove the bean and produce a configurer instead. Checker beans need no change: the
default configurer uses them, and an application configurer selects them as shown above. An
application bean and an application configurer together fail the startup. If the configurer does
not cover a use case, please describe it in an
[issue](https://github.com/mcollovati/quarkus-hilla/issues).

> [!IMPORTANT]
> **Breaking change in 25.3.0:** the access control is only available as
> `NavigationAccessControl`. Injecting `QuarkusNavigationAccessControl` fails at build time with
> an unsatisfied dependency; inject `NavigationAccessControl` instead.

Configurer producers, checkers and resolvers must not use runtime-init synthetic beans while they
are constructed. The startup order is tracked in
[#2329](https://github.com/mcollovati/quarkus-hilla/issues/2329).

### Form logout cookie scope

Form logout expires the authentication cookie using Quarkus's configured path,
domain, HttpOnly and SameSite settings. On HTTPS it also sets Secure, including
for `__Host-` and `__Secure-` cookie names.

For reliable logout, configure an explicit `quarkus.http.auth.form.cookie-path`;
the default is `/`. An empty value lets browsers derive cookie paths from login
and renewal request URLs. Logout cannot reconstruct all those previous scopes.
The existing root fallback covers root-level login.

### Coming from Vaadin's Spring integration

Spring applications set up security with `VaadinSecurityConfigurer`. Quarkus-Hilla has no such
class: it applies the Vaadin specific parts itself once form authentication is enabled, and the
rest is regular Quarkus configuration.

| Spring (`VaadinSecurityConfigurer`)                     | Quarkus-Hilla                                                                                                                                      |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `vaadin()` applied to `HttpSecurity`                    | Nothing to apply. Enabling `quarkus.http.auth.form.enabled` activates the extension's security setup.                                             |
| `loginView(String)`                                     | `quarkus.http.auth.form.login-page`. The extension uses it as the Flow login view too, see [Login view](#login-view).                              |
| `loginView(Class)`                                      | `withLoginView(Class)` on a [configurer](#customizing-the-access-control), plus `quarkus.http.auth.form.login-page` for the HTTP redirect.         |
| `loginView(..., logoutSuccessUrl)`                      | `vaadin.security.post-logout-redirect-uri`                                                                                                         |
| `oauth2LoginPage(...)`                                  | Not available yet, see [#2276](https://github.com/mcollovati/quarkus-hilla/pull/2276).                                                             |
| `defaultSuccessUrl(...)`                                | `quarkus.http.auth.form.landing-page`                                                                                                              |
| `logoutSuccessHandler(...)`, `addLogoutHandler(...)`    | `vaadin.security.logout-path`, `vaadin.security.post-logout-redirect-uri` and `vaadin.security.logout-invalidate-session`. No custom handlers.    |
| `anyRequest(...)`, `enableAuthorizedRequestsConfiguration(...)` | Vaadin's internal requests, public resources and anonymous endpoints and views are permitted, everything else requires a login. Add rules with `quarkus.http.auth.permission.*`; they combine with the extension's policy and cannot make a path public again. |
| `defaultPermitMatcher()`                                | Applied internally, not exposed.                                                                                                                   |
| `enableNavigationAccessControl(false)`                  | `vaadin.security.navigation-access-control.enabled=false`, see [Disabling the access check](#disabling-the-access-check), or `disabled()` on a configurer.  |
| `NavigationAccessControlConfigurer` bean                | `NavigationAccessControlConfigurer` bean with the same method names, except `withRoutePathAccessChecker()` (since 25.3.0).                         |
| `AccessAnnotationChecker` bean                          | `AccessAnnotationChecker` bean, used by the built-in `AnnotatedViewAccessChecker` (since 25.3.0) and the endpoint access check.                    |
| `enableCsrfConfiguration(...)`, `enableRequestCacheConfiguration(...)`, `enableExceptionHandlingConfiguration(...)`, `enableLogoutConfiguration(...)` | No equivalent. The extension does not change Quarkus CSRF handling, Quarkus form authentication remembers the requested page itself, and logout is always handled at `vaadin.security.logout-path`. |

The navigation access control is on by default with form authentication. In Spring it is only
turned on through `VaadinSecurityConfigurer`.

Quarkus also offers a programmatic `HttpSecurity` API, comparable to Spring's `HttpSecurity`.
Configure form login through the `quarkus.http.auth.form.*` properties for now: the extension
does not pick up a form mechanism set up in code yet
([#2338](https://github.com/mcollovati/quarkus-hilla/issues/2338),
[#2339](https://github.com/mcollovati/quarkus-hilla/issues/2339)).

<a id="copilot"></a>

## ✨ Copilot

![Since 25.1.2](https://flat.badgen.net/static/Since/25.1.2/007bff?scale=1.1)

| Property                                         | Type        | Default | Description                                                                                                            |
|--------------------------------------------------|-------------|---------|------------------------------------------------------------------------------------------------------------------------|
| `vaadin.copilot.flow-services.discovery`         | Enum        | `SERVICES` | Flow UI service discovery mode: `NONE`, `SERVICES`, or `ALL`. Explicit includes still work when discovery is `NONE`. |
| `vaadin.copilot.flow-services.packages`          | Enum        | `APPLICATION` | Package discovery mode: `APPLICATION` for root application archive classes, or `ALL` for all discovered bean packages. |
| `vaadin.copilot.flow-services.include-scopes`    | Set<String> | `application`<br>`singleton`<br>`dependent`<br>`vaadin-service`<br>`vaadin-session`<br>`vaadin-ui`<br>`vaadin-route` | Scope keys included when discovery is `SERVICES`. |
| `vaadin.copilot.flow-services.include-packages`  | Set<String> | - | Package prefixes that are always added to Flow UI service discovery. |
| `vaadin.copilot.flow-services.exclude-packages`  | Set<String> | - | Package prefixes that are always removed from Flow UI service discovery. |
| `vaadin.copilot.flow-services.include-classes`   | Set<String> | - | Fully qualified class names that are always added to Flow UI service discovery. |
| `vaadin.copilot.flow-services.exclude-classes`   | Set<String> | - | Fully qualified class names that are always removed from Flow UI service discovery. Excludes win over includes. |

Example:

```properties
vaadin.copilot.flow-services.discovery=services
vaadin.copilot.flow-services.packages=application
vaadin.copilot.flow-services.include-packages=com.example.shared
vaadin.copilot.flow-services.exclude-packages=com.example.internal
vaadin.copilot.flow-services.include-classes=com.example.admin.AdminFacade
```

See [Vaadin Copilot Integration](copilot-integration.md) for service discovery modes, filtering options, and implementation details.

<a id="vaadin-build"></a>

## 🏗️ Vaadin Build

Vaadin build properties such as `vaadin.build.enabled` and `vaadin.build.*` are provided by the official [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/), not by Quarkus-Hilla. See [Vaadin Quarkus Production Mode](https://vaadin.com/docs/latest/flow/integrations/quarkus#production-mode) for current behavior. Legacy Quarkus-Hilla build plugin behavior before Vaadin 25.0 is documented in [v24 Docs](v24-docs.md#vaadin-247-249-embedded-build-plugin--workaround).
