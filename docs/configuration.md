# ⚙️ Configuration Reference

All Quarkus-Hilla settings go in `application.properties`.

> [!NOTE]
> Every `vaadin.*` setting on this page is **fixed at build time**. Changing one means rebuilding
> the application — setting it as a system property or environment variable on an existing build
> has no effect. Quarkus keeps the value that was in place when the application was built.
> The `quarkus.*` properties mentioned here follow the Quarkus rules for each property.

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

In a secured application, each Flow view needs an annotation that says who may open it. Put it
on the view class or a superclass of it:

| Annotation          | Who gets in            |
|---------------------|------------------------|
| `@AnonymousAllowed` | everyone, no login     |
| `@PermitAll`        | any logged-in user     |
| `@RolesAllowed`     | users with those roles |
| `@DenyAll`          | nobody                 |

```java
@Route("dashboard")
@PermitAll
public class DashboardView extends VerticalLayout {
}
```

**A view with no annotation is not shown.** That is the Vaadin default. If a view is suddenly
unreachable, a missing annotation is the most likely cause.

An annotation on a router layout does not count for the views inside it. If a layout carries
one, it is checked in addition, so the user has to pass both.

Views are only checked when Quarkus form authentication is enabled
(`quarkus.http.auth.form.enabled=true`).

#### Login view

When an anonymous user navigates to a view they may not open, they are sent to the login view.
The extension takes it from `quarkus.http.auth.form.login-page`, the same page Quarkus uses for
its own login redirect, so both always agree.

A custom `NavigationAccessControl` bean replaces the extension's and should leave the login view
to the extension. Since 25.3.0, use a [configurer](#customizing-the-access-control) instead.
`QuarkusNavigationAccessControl` is internal API: extending it still works, but it may change in
any release. If a bean extending it sets a login URL that differs from
`quarkus.http.auth.form.login-page`, the application fails at startup, naming both values.
A Flow login view class set by the bean, and the login view of a bean that does not extend
`QuarkusNavigationAccessControl`, are kept instead. The startup log names both, because the two
login pages may then differ.

#### Turning the check off

> [!IMPORTANT]
> **Views became unreachable after an upgrade to 25.1.4 or 25.2.1?** Those releases started
> checking views that were never checked before, so an application that has no access
> annotations at all loses every view at once. Turn the check off to get back to the old
> behavior, then add the annotations and turn it on again.

Set the property to `false` to skip the check for all views, then rebuild. Requiring a rebuild
is deliberate: an environment variable cannot turn off the access checks of an application that
is already deployed.

```properties
vaadin.security.navigation-access-control.enabled=false
```

> [!WARNING]
> Then anyone who can open the application can open every Flow view, and `@RolesAllowed` and the
> other annotations do nothing. Use this while you add the missing annotations, not as a
> permanent setting.

Quarkus HTTP permissions still apply. To keep requiring a login for some views, add a rule for
their paths:

```properties
quarkus.http.auth.permission.app.paths=/dashboard,/admin/*
quarkus.http.auth.permission.app.policy=authenticated
```

Keep such rules to the paths you want to protect. A rule for all paths (`/*`) also locks out the
login page and the resources Vaadin needs to show it. Such rules only cover the first page load,
though. After that the browser switches views on its
own, and only the access check sees those navigations.

The setting turns off the `NavigationAccessControl` at startup, so it also applies to an
application that provides its own bean. Nothing is turned on: when the setting is `true`, an
access control that the application disabled itself stays disabled.

This opt-out restores the behavior before 25.1.4 and is meant as an intermediate step. Vaadin's
Spring integration requires a login for every Flow view when the access control is off.
Quarkus-Hilla does not, because a Quarkus permission rule could not make a single view public
again. Evaluating Quarkus permissions for views is planned, after which this may change.

#### Customizing the access control

![Since 25.3.0](https://flat.badgen.net/static/Since/25.3.0/007bff?scale=1.1)

Provide a `NavigationAccessControlConfigurer` bean to choose checkers and a decision resolver.
Like the access check itself, it only takes effect with Quarkus form authentication:

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

The default configurer uses all `NavigationAccessChecker` CDI beans, including application
checkers. An application configurer selects only the checkers it requests. Add instances with
`withNavigationAccessChecker(...)` or `withNavigationAccessCheckers(...)`, or select available
CDI beans with `withAvailableNavigationAccessCheckers(MyAccessChecker.class::isInstance)`.
`withAnnotatedViewAccessChecker()` selects the available annotation checker, including subclasses
and CDI proxies, and fails if none exists. An enabled access control with no selected checkers
fails at startup; select a checker or disable it.

`withDecisionResolver(...)` takes precedence over the CDI `AccessCheckDecisionResolver` bean.
Without an explicit resolver, the application's resolver bean or the extension's default is used.
`disabled()` turns checks off. The build-time property
`vaadin.security.navigation-access-control.enabled=false` always wins and never turns checks on.

Without `withLoginView(...)`, the extension uses
`quarkus.http.auth.form.login-page`. A configured URL must match that property; different URLs
fail at startup, naming both values. A login view class is kept, and both values are logged at
INFO because its route cannot be compared reliably at startup. Setting different login views
on one configurer fails immediately.

Replacing the `NavigationAccessControl` bean is deprecated and logs a startup warning. To migrate,
remove that bean and provide a configurer instead. A custom checker bean needs no configurer:
the default already uses it. With your own configurer, select that checker explicitly, as above.
Providing both an application access control bean and an application configurer fails at startup.
Legacy access control beans still receive the form login settings and the opt-out property.
`QuarkusNavigationAccessControl` is internal API: a legacy bean may still extend it, but it can
change in any release.

> [!IMPORTANT]
> **Breaking change in 25.3.0:** the access control is only available as `NavigationAccessControl`.
> Code that injects `QuarkusNavigationAccessControl` fails at build time with an unsatisfied
> dependency. Inject `NavigationAccessControl` instead.

Configurer producers, checkers and resolvers must not use runtime-init synthetic beans while
being constructed. Startup ordering is tracked in [#2329](https://github.com/mcollovati/quarkus-hilla/issues/2329).

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
| `enableNavigationAccessControl(false)`                  | `vaadin.security.navigation-access-control.enabled=false`, see [Turning the check off](#turning-the-check-off), or `disabled()` on a configurer.  |
| `NavigationAccessControlConfigurer` bean                | `NavigationAccessControlConfigurer` bean with the same method names, except `withRoutePathAccessChecker()` (since 25.3.0).                         |
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
