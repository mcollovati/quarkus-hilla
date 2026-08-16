# ⚙️ Configuration Reference

All Quarkus-Hilla settings go in `application.properties`.

> [!NOTE]
> Every setting on this page is **fixed at build time**. Changing one means rebuilding the
> application — setting it as a system property or environment variable on an existing build has
> no effect. Quarkus keeps the value that was in place when the application was built.

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
on the view, or on a layout the view uses:

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

Applications without authentication are not affected. Nothing is checked there.

#### Turning the check off

> [!IMPORTANT]
> **Views became unreachable after an upgrade to 25.1.4 or 25.2.1?** Those releases started
> checking views that were never checked before, so an application that has no access
> annotations at all loses every view at once. Turn the check off to get back to the old
> behavior, then add the annotations and turn it on again.

Set the property to `false` to skip the check for all views, then rebuild:

```properties
vaadin.security.navigation-access-control.enabled=false
```

> [!WARNING]
> Then anyone who can open the application can open every Flow view, and `@RolesAllowed` and the
> other annotations do nothing. The `quarkus.http.auth.permission.*` rules still work, but they
> only cover the first page load. After that the browser switches views on its own. Use this
> while you add the missing annotations, not as a permanent setting.

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
