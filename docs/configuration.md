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

An application can replace the access control with its own `NavigationAccessControl` bean,
declared `@Singleton` or `@ApplicationScoped`. The extension applies the login page to that bean
as well, unless the bean defines its own login view:

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

### Form logout cookie scope

Form logout expires the authentication cookie using Quarkus's configured path,
domain, HttpOnly and SameSite settings. On HTTPS it also sets Secure, including
for `__Host-` and `__Secure-` cookie names.

For reliable logout, configure an explicit `quarkus.http.auth.form.cookie-path`;
the default is `/`. An empty value lets browsers derive cookie paths from login
and renewal request URLs. Logout cannot reconstruct all those previous scopes.
The existing root fallback covers root-level login.

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
