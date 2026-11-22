# ✨ Vaadin Copilot Integration

Quarkus-Hilla supports Vaadin Copilot in development mode by adapting Copilot's Spring-oriented backend bridge to Quarkus CDI, Hilla, and Vaadin Quarkus runtime APIs.

The integration is intended for application inspection, Flow view editing, Hilla endpoint discovery, and Flow UI service discovery. It does not add Spring runtime dependencies.

> [!NOTE]
> Quarkus-Hilla adapts Vaadin Copilot in development mode only. Production builds do not need Copilot-specific runtime behavior.

## Supported Features

- Hilla `@BrowserCallable` and `@Endpoint` discovery through the Hilla `EndpointRegistry`
- Flow UI service discovery through Quarkus Arc bean discovery
- Quarkus configuration property lookup
- Vaadin route security status lookup
- Quarkus-Hilla version reporting
- Context-classloader-aware custom component class loading

## Known Limitations

> [!NOTE]
> Java edits use Quarkus Live Reload when no JVM hotswap agent is available. The edit is applied, but Copilot may show a warning and the browser reload can take longer.

- Spring Security user switching has no Quarkus equivalent in this integration.
- Spring Data/JPA helper panels are not implemented.
- H2 datasource helper information is not implemented.

## Browser Callables

Copilot browser-callable metadata is read from the Hilla `EndpointRegistry`.

The integration exposes public endpoint methods from application endpoints and skips Vaadin internal endpoints under `com.vaadin.*`.

Hilla endpoints are also removed from Flow UI service discovery, so Copilot does not show them twice.

## Flow UI Services

Flow UI services are discovered from Quarkus Arc beans. The default mode is intentionally conservative:

```properties
vaadin.copilot.flow-services.discovery=services
vaadin.copilot.flow-services.packages=application
vaadin.copilot.flow-services.include-scopes=application,singleton,dependent,vaadin-service,vaadin-session,vaadin-ui,vaadin-route
```

> [!NOTE]
> Default discovery is conservative: only service-like beans from the root application archive are exposed.

The default discovers service-like beans from the root application archive only. The following infrastructure packages are always excluded from automatic service discovery:

- `com.vaadin.`
- `com.github.mcollovati.quarkus.hilla.`
- `io.quarkus.`
- `io.smallrye.`
- `io.vertx.`
- `jakarta.`
- `javax.`
- `org.jboss.`
- `org.eclipse.microprofile.`
- `org.springframework.`

## Discovery Modes

| Mode       | Behavior                                                                 |
|------------|--------------------------------------------------------------------------|
| `none`     | Disable automatic discovery. Explicit includes still add matching beans. |
| `services` | Discover service-like Arc, Vaadin-scoped, and Spring `@Service` beans.   |
| `all`      | Discover all Arc application beans allowed by package and exclude rules. |

## Package Modes

| Mode          | Behavior                                                              |
|---------------|-----------------------------------------------------------------------|
| `application` | Limit automatic discovery to classes from the root application archive. |
| `all`         | Allow automatic discovery from all discovered bean packages.           |

Hard infrastructure excludes still apply in both modes.

## Include And Exclude Overlays

Include and exclude overlays are applied independently of the discovery mode.

> [!IMPORTANT]
> Include and exclude overlays always apply, independent of discovery mode. Excludes win over includes.

```properties
vaadin.copilot.flow-services.include-packages=com.example.shared
vaadin.copilot.flow-services.exclude-packages=com.example.internal
vaadin.copilot.flow-services.include-classes=com.example.admin.AdminFacade
vaadin.copilot.flow-services.exclude-classes=com.example.internal.SecretFacade
```

Rules:

- `include-packages` adds matching beans.
- `include-classes` adds matching classes by fully qualified class name.
- `exclude-packages` removes matching beans.
- `exclude-classes` removes matching classes by fully qualified class name.
- Exclude wins over include.

## Scope Keys

`vaadin.copilot.flow-services.include-scopes` accepts normalized scope keys.

Built-in keys:

| Key               | Scope annotations                                                              |
|-------------------|--------------------------------------------------------------------------------|
| `application`     | `jakarta.enterprise.context.ApplicationScoped`                                 |
| `singleton`       | `jakarta.inject.Singleton`                                                     |
| `dependent`       | `jakarta.enterprise.context.Dependent`                                         |
| `request`         | `jakarta.enterprise.context.RequestScoped`                                     |
| `session`         | `jakarta.enterprise.context.SessionScoped`                                     |
| `conversation`    | `jakarta.enterprise.context.ConversationScoped`                                |
| `vaadin-service`  | `com.vaadin.quarkus.annotation.VaadinServiceScoped`                            |
| `vaadin-session`  | `com.vaadin.quarkus.annotation.VaadinSessionScoped`                            |
| `vaadin-ui`       | `com.vaadin.quarkus.annotation.UIScoped`, `NormalUIScoped`                     |
| `vaadin-route`    | `com.vaadin.quarkus.annotation.RouteScoped`, `NormalRouteScoped`               |

> [!WARNING]
> Custom CDI scope annotations are not discovered through `include-scopes` yet. Use `include-packages` or `include-classes` for those beans.

## Runtime Bridge

Vaadin Copilot calls internal Spring bridge methods. Quarkus-Hilla rewrites those calls at build time so they target `CopilotQuarkusIntegration` instead.

> [!NOTE]
> Bytecode transformers use string-based targets so Quarkus-Hilla does not require Copilot at compile time.

The bridge provides safe Quarkus implementations for:

- application context lookup
- configuration property lookup
- application class lookup
- route security status
- endpoint methods
- Flow UI service methods
- version information

Unsupported Spring-specific methods return safe defaults.

## Bytecode Patches

The build-time transformer redirects Copilot calls without requiring Copilot at compile time.

Patched areas:

- `SpringBridge.callSpring*` calls are redirected to `CopilotQuarkusIntegration`.
- `SpringBridge.isSpringAvailable(VaadinContext)` is redirected so Copilot enables Flow UI services in Quarkus.
- `CustomComponents.isCustomComponent(String)` uses the context classloader aware class lookup from `SpringReplacements`.
