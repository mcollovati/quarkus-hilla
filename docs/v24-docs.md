# 🗃️ Quarkus-Hilla v24 Docs

Setup notes, workarounds, and reference for Quarkus-Hilla on **Vaadin 24.x and earlier** (everything before 25.0). The main [README](../README.md) and [Feature Details](features.md) target Vaadin 25.x+.

> [!TIP]
> Upgrading an existing project? See the [v24 → v25 Migration Guide](migration-v24-to-v25.md).

## 🧭 Version Overview

| Version / Range | Change | Current Status |
|-----------------|--------|----------------|
| [2.4.1](#241-lit-and-react-extensions) | Split artifacts by frontend framework: `quarkus-hilla` for Lit and `quarkus-hilla-react` for React. | Historical |
| [24.4](#vaadin-244-unified-platform) | Flow and Hilla unified under the Vaadin platform; Quarkus-Hilla versions started following Vaadin platform releases. | Historical |
| [24.7-24.9](#vaadin-247-249-embedded-build-plugin--workaround) | Production builds may need a workaround because Hilla endpoint generation depends on a Spring process; Quarkus-Hilla also provided an experimental embedded Vaadin build plugin. | Legacy workaround |
| 25.0 | Embedded production build support moved to the official Vaadin Quarkus extension and is enabled by default. | Current behavior outside Quarkus-Hilla |

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

<a id="release-history"></a>

## 📦 Release History

Older Quarkus-Hilla releases, kept as a historical compatibility reference. See the main [README](../README.md#-current-releases) for currently active releases.

|                                                                  Quarkus-Hilla                                                                  |                                                                Quarkus                                                                |                                                         Vaadin / Hilla                                                         |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------------------------------------------------------:|
| <picture><img alt="Maven Central 24.9" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=24.9"></picture> | <picture><img alt="Quarkus 3.20+" src="https://img.shields.io/badge/QUARKUS-v3.20%2B-blue?style=for-the-badge&logo=Quarkus"></picture> |   <picture><img alt="Vaadin 24.9" src="https://img.shields.io/badge/VAADIN-v24.9-blue?style=for-the-badge&logo=Vaadin"></picture>   |
|  <picture><img alt="Maven Central 2.5" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2.5"></picture>  |  <picture><img alt="Quarkus 3.1+" src="https://img.shields.io/badge/QUARKUS-v3.1%2B-blue?style=for-the-badge&logo=Quarkus"></picture>  |   <picture><img alt="Vaadin 24.2" src="https://img.shields.io/badge/VAADIN-v24.2-blue?style=for-the-badge&logo=Vaadin"></picture>   |
|   <picture><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1"></picture>   | <picture><img alt="Quarkus 2.16+" src="https://img.shields.io/badge/QUARKUS-v2.16%2B-blue?style=for-the-badge&logo=Quarkus"></picture> | <picture><img alt="Vaadin 23.3+" src="https://img.shields.io/badge/VAADIN-v23.3%2B-blue?style=for-the-badge&logo=Vaadin"></picture> |
