# 🗃️ Quarkus-Hilla v24 Docs

Setup notes, workarounds, and reference for Quarkus-Hilla on **v24 and earlier** (everything before 25.0). The main [README](../README.md) and [Feature Details](features.md) target v25+.

> [!TIP]
> Upgrading an existing project? See the [v24 → v25 Migration Guide](migration-v24-to-v25.md).

## 🗺️ Version Overview

| Version | Change |
|---------|--------|
| [25.0](migration-v24-to-v25.md) | Embedded production build support moved to the official Vaadin Quarkus extension, enabled by default — see the migration guide. |
| [24.7–24.9](#vaadin-247-249-embedded-build-plugin--workaround) | Production builds may need a workaround because Hilla endpoint generation depends on a Spring process; Quarkus-Hilla also provided an experimental embedded Vaadin build plugin. |
| [24.4](#vaadin-244-unified-platform) | Flow and Hilla unified under the Vaadin platform; Quarkus-Hilla versions started following Vaadin platform releases. |
| [2.4.1](#241-lit-and-react-extensions) | Split artifacts by frontend framework: `quarkus-hilla` for Lit and `quarkus-hilla-react` for React. |

<a id="vaadin-247-249-embedded-build-plugin--workaround"></a>

## 🛠️ Vaadin 24.7–24.9: Embedded Build Plugin & Workaround

With Vaadin 24.7, the frontend build may fail because Hilla endpoint generation tasks depend on a Spring process.

> [!NOTE]
> The dependency workaround is only required for production builds. In development mode, Quarkus-Hilla replaces the offending class.

> [!CAUTION]
> This workaround is not required in 24.8+ because endpoint generation was refactored and Hilla added a pluggable endpoint discovery API.

Two ways to unblock the build:

### 🧪 Option 1: Experimental Embedded Plugin

Quarkus-Hilla provided an experimental embedded Vaadin build plugin for 24.7-24.9, replacing direct `vaadin-maven-plugin` or Vaadin Gradle plugin setup entirely. Enable it in `application.properties`:

```properties
vaadin.build.enabled=true
```

> [!NOTE]
> Default was `false` in 24.x — it had to be set explicitly. Since Vaadin 25.0, the official extension defaults this to `true`.

Add workspace discovery to `pom.xml`, required because the Quarkus Maven plugin did not provide workspace information needed by Vaadin internals:

```xml
<quarkus.bootstrap.workspace-discovery>true</quarkus.bootstrap.workspace-discovery>
```

See [Quarkus issue #45363](https://github.com/quarkusio/quarkus/issues/45363) for background.

### 🔧 Option 2: Workaround Dependency

Keep your existing `vaadin-maven-plugin` setup and add the workaround dependency:

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

As of Vaadin 25.0, production build support is provided by the official [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/) and enabled by default — neither option above is needed anymore. The upstream change merged in [vaadin/quarkus#215](https://github.com/vaadin/quarkus/pull/215) on October 14, 2025. See the [Migration Guide](migration-v24-to-v25.md#build-plugin) for what changes when upgrading.

<a id="vaadin-244-unified-platform"></a>

## 🔗 Vaadin 24.4: Unified Platform

Since Vaadin 24.4, Flow and Hilla are unified in a single platform. Quarkus-Hilla versions started following Vaadin platform releases (`24.x` instead of `2.x`).

Breaking changes:

- Hilla's Maven groupId changed from `dev.hilla` to `com.vaadin.hilla`.
- Java package names changed accordingly.
- Minimum Quarkus version became 3.7+.

<a id="241-lit-and-react-extensions"></a>

## 🎨 2.4.1: Lit and React Extensions

Starting with 2.4.1, the extension was subdivided into two artifacts based on frontend framework:

- `quarkus-hilla` for Lit applications.
- `quarkus-hilla-react` for React applications.

## 📦 Release History

The `24.9`, `2.5`, and `1.x` rows in the main README's [Compatibility Matrix](../README.md#-compatibility-matrix) are the historical reference for these releases — kept there directly so visitors don't have to jump to this page just to see version numbers.
