<p align="center">
  <img src="etc/quarkus-hilla-banner.png" alt="Quarkus-Hilla"/>
</p>

<h2 align="center">
A <a href="https://quarkus.io">Quarkus</a> extension to run <a href="https://vaadin.com/hilla">Hilla</a> applications on Quarkus
</h2>

<p align="center">
  <strong>Build modern full-stack Java applications with reactive TypeScript frontends powered by Quarkus</strong>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 25.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=25." /></a>
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 2.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2." /></a>
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1" /></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img alt="Apache License 2.0" src="https://img.shields.io/github/license/mcollovati/quarkus-hilla?style=for-the-badge&logo=apache" /></a>
</p>

<p align="center">
  <a href="#-quick-start">🚀 Quick Start</a> &nbsp; • &nbsp;
  <a href="#-feature-overview">✨ Features</a> &nbsp; • &nbsp;
  <a href="#-documentation">📚 Documentation</a> &nbsp; • &nbsp;
  <a href="#%EF%B8%8F-configuration-reference">⚙️️ Configuration</a> &nbsp; • &nbsp;
  <a href="#-current-releases">📦 Releases</a> &nbsp; • &nbsp;
  <a href="#-development-version">🔧 Development</a>
</p>

---

## 📖 About

Hilla is an open source framework, provided by [Vaadin Ltd.](https://vaadin.com), that integrates a Spring Boot Java backend with a reactive TypeScript frontend.

**Quarkus-Hilla** replaces the Spring Boot backend with **Quarkus Context & Dependency Injection (CDI)** and **RESTEasy Reactive** for a simpler integration with Quarkus, while preserving the main features of the Hilla Framework, such as [Endpoints](https://vaadin.com/docs/latest/hilla/guides/endpoints), [Reactive Endpoints](https://vaadin.com/docs/latest/hilla/guides/reactive-endpoints), and [Security](https://vaadin.com/docs/latest/hilla/guides/security).

> [!NOTE]
> This is an **unofficial community extension**, and it is **neither** directly related to **nor** supported by Vaadin Ltd.

---

## ✨ Feature Overview

| Feature | Since | Status | Framework | Details |
|---------|-------|--------|-----------|---------|
| 🏗️ Auto CRUD, Auto Grid and Auto Form | 24.4.1 | Quarkus-Hilla feature | React components; Lit and React services | [Feature details](docs/features.md#auto-crud-auto-grid-and-auto-form) |
| 🔄 Endpoints Live Reload | 24.5 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#endpoints-live-reload) |
| 🚀 Native Image Support | 24.5 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#native-image-support) |
| 🔌 Vaadin Quarkus Alignment | 24.5 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#vaadin-quarkus-alignment) |
| 🎯 Custom Endpoint Prefix | 24.6 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#custom-endpoint-prefix) |
| 🖥️ Quarkus Dev UI Integration | 24.7 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#quarkus-dev-ui-integration) |
| ⚡ Mutiny Multi Support | 24.7 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#mutiny-multi-support) |
| 🧭 Vaadin Copilot Integration | 25.1.2 | Quarkus-Hilla feature | Lit and React | [Feature details](docs/features.md#vaadin-copilot-integration) |
| 📦 Official Vaadin Quarkus embedded build | 25.0 | Provided by Vaadin Quarkus | Lit and React | [Vaadin Quarkus Production Mode](https://vaadin.com/docs/latest/flow/integrations/quarkus#production-mode) |
| 🧪 Experimental Quarkus-Hilla embedded build | 24.7-24.9 | Quarkus-Hilla legacy | Lit and React | [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md#experimental-embedded-build-plugin-in-vaadin-247-249) |

For Vaadin 25.0+, production build support comes from the official Vaadin Quarkus extension and is enabled by default. Quarkus-Hilla had an experimental equivalent in 24.7-24.9.

---

## 🚀 Quick Start

> [!TIP]
> - 📘 [Quick Start Guide](../../wiki/QuickStart) — Detailed setup instructions
> - 🎬 [Starter Project](https://github.com/mcollovati/quarkus-hilla-starter) — Download and start coding immediately
> - ⚙️ [Configuration Reference](#%EF%B8%8F-configuration-reference) — Learn about configuration options

### Setup

Choose your frontend framework:

**For React (recommended) applications:**
```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>quarkus-hilla-react</artifactId>
    <version>25.2.x</version>
</dependency>
```

**For Lit applications:**
```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>quarkus-hilla</artifactId>
    <version>25.2.x</version>
</dependency>
```

> [!NOTE]
> Hilla prioritizes React, so new features are typically available first or exclusively for React.

> [!NOTE]
> For Vaadin 24.x and older setup notes and workarounds, see [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md).

### Create Your First Endpoint

```java
@BrowserCallable
@AnonymousAllowed
public class GreetingService {

    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
```

That's it! The TypeScript client is automatically generated and type-safe.

---

## 📚 Documentation

- 📖 [Wiki Documentation](../../wiki)
- ✨ [Feature Details](docs/features.md)
- 🔧 [CRUD & Repository Services](../../wiki/Crud-List-repository-service)
- 🛠️ [Build and Test](docs/build-and-test.md)
- 🧭 [Vaadin Copilot Integration](docs/copilot-integration.md)
- 🚢 [Release Process](docs/release-process.md)
- 🧬 [Update Codestarts](docs/update-codestarts.md)
- 🔢 [Bump Project Version](docs/bump-project-version.md)
- 🗃️ [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md)
- 📘 [Hilla Official Docs](https://vaadin.com/docs/latest/hilla)
- 🚀 [Quarkus Guides](https://quarkus.io/guides/)

---

## ⚠️ Current Limitations

The current Hilla support has some known limitations that we aim to address in future releases.

- ⚠️ Vaadin Copilot support does not include JPA/Data helpers, Spring Security user switching, or full JVM hotswap integration
- ❌ [Stateless Authentication](https://vaadin.com/docs/latest/hilla/guides/security/spring-stateless) is not supported

Older limitations and workarounds for Vaadin versions before 25.0 are kept in [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md).

---

## ⚙️ Configuration Reference

Quarkus-Hilla provides various configuration parameters to customize the behavior of the extension. All parameters can be set in your `application.properties` file.

### Live Reload Configuration

| Property                                  | Type      | Default   | Since | Description                                                                                                                                                                          |
|-------------------------------------------|-----------|-----------|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `vaadin.hilla.live-reload.enable`         | Boolean   | `false`   | 24.5  | Enable automatic regeneration of client-side code when endpoint classes change in dev mode.                                                                                          |
| `vaadin.hilla.live-reload.watch-strategy` | Enum      | `CLASS`   | 24.5  | Strategy to watch for changes: `SOURCE` (watch Java source files) or `CLASS` (watch compiled classes). Use `CLASS` with `quarkus.live-reload.instrumentation=true` for best results. |
| `vaadin.hilla.live-reload.watched-paths`  | Set<Path> | All paths | 24.5  | Comma-separated list of paths to watch for changes, relative to source/class root. Example: `com/example/service,com/example/model`                                                  |

### Endpoint Configuration

| Property                 | Type   | Default    | Since | Description                                                                                                                            |
|--------------------------|--------|------------|-------|----------------------------------------------------------------------------------------------------------------------------------------|
| `vaadin.endpoint.prefix` | String | `/connect` | 24.6  | Custom prefix for Hilla endpoints. The extension automatically generates a custom `connect-client.ts` file with the configured prefix. |

### Security Configuration

| Property                                    | Type    | Default   | Since | Description                                                     |
|---------------------------------------------|---------|-----------|-------|-----------------------------------------------------------------|
| `vaadin.security.logout-path`               | String  | `/logout` | 24.7  | Path of the logout HTTP POST endpoint handling logout requests. |
| `vaadin.security.post-logout-redirect-uri`  | String  | -         | 24.7  | URI to redirect to after successful logout.                     |
| `vaadin.security.logout-invalidate-session` | Boolean | `true`    | 24.7  | Whether HTTP session should be invalidated on logout.           |

### Copilot Configuration

> [!NOTE]
> Quarkus-Hilla supports Vaadin Copilot in development mode since 25.1.2. See [Vaadin Copilot Integration](docs/copilot-integration.md) for behavior details and limitations.

| Property                                         | Type       | Default                                                                 | Since | Description                                                                                                                    |
|--------------------------------------------------|------------|-------------------------------------------------------------------------|-------|--------------------------------------------------------------------------------------------------------------------------------|
| `vaadin.copilot.flow-services.discovery`         | Enum       | `SERVICES`                                                              | 25.1.2 | Flow UI service discovery mode: `NONE`, `SERVICES`, or `ALL`. Explicit includes still work when discovery is `NONE`.           |
| `vaadin.copilot.flow-services.packages`          | Enum       | `APPLICATION`                                                           | 25.1.2 | Package discovery mode: `APPLICATION` for root application archive classes, or `ALL` for all discovered bean packages.          |
| `vaadin.copilot.flow-services.include-scopes`    | Set<String> | `application,singleton,dependent,vaadin-service,vaadin-session,vaadin-ui,vaadin-route` | 25.1.2 | Scope keys included when discovery is `SERVICES`.                                                                              |
| `vaadin.copilot.flow-services.include-packages`  | Set<String> | -                                                                       | 25.1.2 | Package prefixes that are always added to Flow UI service discovery.                                                           |
| `vaadin.copilot.flow-services.exclude-packages`  | Set<String> | -                                                                       | 25.1.2 | Package prefixes that are always removed from Flow UI service discovery.                                                       |
| `vaadin.copilot.flow-services.include-classes`   | Set<String> | -                                                                       | 25.1.2 | Fully qualified class names that are always added to Flow UI service discovery.                                                |
| `vaadin.copilot.flow-services.exclude-classes`   | Set<String> | -                                                                       | 25.1.2 | Fully qualified class names that are always removed from Flow UI service discovery. Excludes win over includes.                 |

Example:

```properties
vaadin.copilot.flow-services.discovery=services
vaadin.copilot.flow-services.packages=application
vaadin.copilot.flow-services.include-packages=com.example.shared
vaadin.copilot.flow-services.exclude-packages=com.example.internal
vaadin.copilot.flow-services.include-classes=com.example.admin.AdminFacade
```

See [Vaadin Copilot Integration](docs/copilot-integration.md) for details.

### Vaadin Build Configuration

Vaadin build properties such as `vaadin.build.enabled` and `vaadin.build.*` are provided by the official [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/), not by Quarkus-Hilla. See [Vaadin Quarkus Production Mode](https://vaadin.com/docs/latest/flow/integrations/quarkus#production-mode) for current behavior. Legacy Quarkus-Hilla build plugin behavior before Vaadin 25.0 is documented in [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md#experimental-embedded-build-plugin-in-vaadin-247-249).

---

## 📊 Usage Statistics

As discussed in [Hilla issue #211](https://github.com/vaadin/hilla/issues/211), the extension reports itself to Vaadin's usage statistics mechanism to help understand adoption and potentially encourage official support from Vaadin.

- 📈 Statistics are collected **only during development mode**
- 🔒 **No sensitive data** is collected
- 🚫 [How to opt out](https://github.com/vaadin/vaadin-usage-statistics#opting-out)

---

## 📦 Current Releases

 |                                                                                        Quarkus-Hilla                                                                                        |                                                                Quarkus                                                                 |                                                           Vaadin / Hilla                                                            |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------:|:-----------------------------------------------------------------------------------------------------------------------------------:|
| <picture><img alt="Maven Central 25.2" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=25.2"></picture> | <picture><img alt="Quarkus 3.33+" src="https://img.shields.io/badge/QUARKUS-v3.33%2B-blue?style=for-the-badge&logo=Quarkus"></picture> |   <picture><img alt="Vaadin 25.2" src="https://img.shields.io/badge/VAADIN-v25.2-blue?style=for-the-badge&logo=Vaadin"></picture>   |
| <picture><img alt="Maven Central 25.1" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=25.1"></picture> | <picture><img alt="Quarkus 3.27+" src="https://img.shields.io/badge/QUARKUS-v3.27%2B-blue?style=for-the-badge&logo=Quarkus"></picture> |   <picture><img alt="Vaadin 25.1" src="https://img.shields.io/badge/VAADIN-v25.1-blue?style=for-the-badge&logo=Vaadin"></picture>   |
| <picture><img alt="Maven Central 25.0" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=25.0"></picture> | <picture><img alt="Quarkus 3.27+" src="https://img.shields.io/badge/QUARKUS-v3.27%2B-blue?style=for-the-badge&logo=Quarkus"></picture> |   <picture><img alt="Vaadin 25.0" src="https://img.shields.io/badge/VAADIN-v25.0-blue?style=for-the-badge&logo=Vaadin"></picture>   |
| <picture><img alt="Maven Central 24.9" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=24.9"></picture> | <picture><img alt="Quarkus 3.20+" src="https://img.shields.io/badge/QUARKUS-v3.20%2B-blue?style=for-the-badge&logo=Quarkus"></picture> |   <picture><img alt="Vaadin 24.9" src="https://img.shields.io/badge/VAADIN-v24.9-blue?style=for-the-badge&logo=Vaadin"></picture>   |
|  <picture><img alt="Maven Central 2.5" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2.5"></picture>  |  <picture><img alt="Quarkus 3.1+" src="https://img.shields.io/badge/QUARKUS-v3.1%2B-blue?style=for-the-badge&logo=Quarkus"></picture>  |   <picture><img alt="Vaadin 24.2" src="https://img.shields.io/badge/VAADIN-v24.2-blue?style=for-the-badge&logo=Vaadin"></picture>   |
|   <picture><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1"></picture>   | <picture><img alt="Quarkus 2.16+" src="https://img.shields.io/badge/QUARKUS-v2.16%2B-blue?style=for-the-badge&logo=Quarkus"></picture> | <picture><img alt="Vaadin 23.3+" src="https://img.shields.io/badge/VAADIN-v23.3%2B-blue?style=for-the-badge&logo=Vaadin"></picture> |

> [!NOTE]
> The major and minor version of Quarkus-Hilla always matches the Vaadin/Hilla version.
> Older rows are kept as a historical compatibility reference. See [Quarkus-Hilla Legacy Notes](docs/legacy-notes.md) for old setup details and workarounds.

---

## 🔧 Development Version

|                                                                  Quarkus-Hilla                                                                  |                                                                Quarkus                                                                |                                                         Vaadin / Hilla                                                         |
|:-----------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------------------------------------------------:|
| <picture><img alt="Development 25.3-SNAPSHOT" src="https://img.shields.io/badge/25.3--SNAPSHOT-blue?style=for-the-badge&logo=github"></picture> | <picture><img alt="Quarkus 3.33+" src="https://img.shields.io/badge/Quarkus-3.33%2B-blue?style=for-the-badge&logo=Quarkus"></picture> | <picture><img alt="Vaadin 25.3" src="https://img.shields.io/badge/Vaadin-25.3-blue?style=for-the-badge&logo=Vaadin"></picture> |

---

## 🤝 Contributors

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/mcollovati"><img src="https://avatars.githubusercontent.com/u/4648894?s=100" width="100px;" alt="Marco Collovati"/><br /><sub><b>Marco Collovati</b></sub></a><br /><a href="https://github.com/mcollovati/quarkus-hilla/commits?author=mcollovati" title="Code">💻</a> <a href="#maintenance-mcollovati" title="Maintenance">🚧</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Dudeplayz"><img src="https://avatars.githubusercontent.com/u/15174076?v=4?s=100" width="100px;" alt="Dario Götze"/><br /><sub><b>Dario Götze</b></sub></a><br /><a href="https://github.com/mcollovati/quarkus-hilla/commits?author=Dudeplayz" title="Code">💻</a> <a href="#maintenance-Dudeplayz" title="Maintenance">🚧</a></td>
    </tr>
  </tbody>
</table>
<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind are welcome!

---

## 🙏 Credits

The banner for this project was created using the awesome [Banner Maker](https://github.com/obarlik/banner-maker) by [@obarlik](https://github.com/obarlik).
