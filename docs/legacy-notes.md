# Legacy Notes

This document keeps historical setup notes for Quarkus-Hilla versions targeting Vaadin 24.x and older, meaning everything before Vaadin 25.0. The current README focuses on Vaadin 25.x usage.

## Vaadin 24.7 Build Workaround

With Vaadin 24.7, the frontend build may fail because Hilla endpoint generation tasks depend on a Spring process.

> [!NOTE]
> The dependency workaround is only required for production builds. In development mode, Quarkus-Hilla replaces the offending class.

> [!CAUTION]
> This workaround is not required in 24.8+ because endpoint generation was refactored and Hilla added a pluggable endpoint discovery API.

### Option 1: Embedded Plugin

Enable the embedded build plugin in `application.properties`:

```properties
vaadin.build.enabled=true
```

Add workspace discovery to `pom.xml`:

```xml
<quarkus.bootstrap.workspace-discovery>true</quarkus.bootstrap.workspace-discovery>
```

### Option 2: Workaround Dependency

Add the workaround dependency to `vaadin-maven-plugin`:

```xml
<plugin>
    <groupId>com.vaadin</groupId>
    <artifactId>vaadin-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>prepare-frontend</goal>
                <goal>build-frontend</goal>
            </goals>
            <phase>compile</phase>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>com.github.mcollovati</groupId>
            <artifactId>aot-browser-finder-callable-workaround</artifactId>
            <version>${quarkus-hilla.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

## Embedded Build Plugin In Vaadin 24.7-24.9

For Vaadin 24.7-24.9, Quarkus-Hilla provided an experimental embedded Vaadin build plugin. It could be enabled with:

```properties
vaadin.build.enabled=true
```

The following Maven property was required because the Quarkus Maven plugin did not provide workspace information needed by Vaadin internals:

```xml
<quarkus.bootstrap.workspace-discovery>true</quarkus.bootstrap.workspace-discovery>
```

See [Quarkus issue #45363](https://github.com/quarkusio/quarkus/issues/45363) for background.

As of Vaadin 25.0, this feature is provided by the official [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/) and is enabled by default.

## Vaadin 24.4 Unified Platform

Since Vaadin 24.4, Flow and Hilla are unified in a single platform. Quarkus-Hilla versions started following Vaadin platform releases (`24.x` instead of `2.x`).

Breaking changes:

- Hilla's Maven groupId changed from `dev.hilla` to `com.vaadin.hilla`.
- Java package names changed accordingly.
- Minimum Quarkus version became 3.7+.

## Lit And React Extensions

Starting with 2.4.1, the extension was subdivided into two artifacts based on frontend framework:

- `quarkus-hilla` for Lit applications.
- `quarkus-hilla-react` for React applications.
