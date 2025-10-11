<p align="center">
  <img src="etc/quarkus-hilla-banner.png" alt="Quarkus-Hilla"/>
</p>

<h2 align="center">
A <a href="https://quarkus.io">Quarkus</a> extension to run <a href="https://hilla.dev">Hilla</a> applications on Quarkus
</h2>

<p align="center">
  <strong>Build modern full-stack Java applications with reactive TypeScript frontends powered by Quarkus</strong>
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 24.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=24." /></a>
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 2.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2." /></a>
  <a href="https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla"><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1" /></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img alt="Apache License 2.0" src="https://img.shields.io/github/license/mcollovati/quarkus-hilla?style=for-the-badge&logo=apache" /></a>
</p>

<p align="center">
  <a href="#-quick-start">🚀 Quick Start</a> &nbsp; • &nbsp;
  <a href="#-key-features">✨ Features</a> &nbsp; • &nbsp;
  <a href="#-documentation">📚 Documentation</a> &nbsp; • &nbsp;
  <a href="#releases">📦 Releases</a> &nbsp; • &nbsp;
  <a href="#development">🔧 Development</a> &nbsp; • &nbsp;
  <a href="#-community--support">💬 Community</a>
</p>

---

## 📖 About

Hilla is an open source framework, provided by [Vaadin Ltd.](https://vaadin.com), that integrates a Spring Boot Java backend with a reactive TypeScript frontend.

**Quarkus-Hilla** replaces the Spring Boot backend with **Quarkus Context & Dependency Injection (CDI)** and **RESTEasy Reactive** for a simpler integration with Quarkus, while preserving the main features of the Hilla Framework, such as [Endpoints](https://hilla.dev/docs/lit/guides/endpoints), [Reactive Endpoints](https://hilla.dev/docs/lit/guides/reactive-endpoints), and [Security](https://hilla.dev/docs/lit/guides/security).

> [!NOTE]
> This is an **unofficial community extension**, and it is **not** directly related **nor** supported by Vaadin Ltd.

---

## ✨ Key Features

- 🎯 **Type-Safe Communication** - Automatically generated TypeScript types from Java endpoints
- ⚡ **Reactive Streaming** - Support for Mutiny `Multi` and reactive endpoints
- 🔒 **Security Integration** - Built-in support for authentication and authorization
- 🔄 **Hot Reload** - Endpoints live reload in development mode
- 🏗️ **Auto CRUD** - Automatic CRUD operations with Auto Grid and Auto Form (React)
- 🚀 **Native Image** - Full GraalVM native image support (since 24.5)
- 🎨 **Framework Choice** - Support for both Lit and React frontends
- 🔌 **Panache Integration** - Custom repository services for Hibernate ORM Panache
- 📦 **Embedded Plugin** - Optional built-in Vaadin Maven plugin (experimental)

---

## 🚀 Quick Start

### Installation

Choose your frontend framework:

**For Lit applications:**
```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>quarkus-hilla</artifactId>
    <version>24.9.x</version>
</dependency>
```

**For React applications:**
```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>quarkus-hilla-react</artifactId>
    <version>24.9.x</version>
</dependency>
```

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

### Resources

- 📘 [Quick Start Guide](../../wiki/QuickStart) - Detailed setup instructions
- 🎬 [Starter Project](https://github.com/mcollovati/quarkus-hilla-starter) - Download and start coding immediately
- 🔍 [Live Demo](https://ly.safepoint.cloud/hSMd4SH) - See it in action

---

## 📚 Documentation

- 📖 [Wiki Documentation](https://github.com/mcollovati/quarkus-hilla/wiki)
- 🔧 [CRUD & Repository Services](https://github.com/mcollovati/quarkus-hilla/wiki/Crud-List-repository-service)
- 🎯 [Hilla Official Docs](https://hilla.dev/docs)
- 🏃 [Quarkus Guides](https://quarkus.io/guides/)

---

## 🎯 What's New

<h3>Mutiny Multi Support <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.7</span></h3>

Support for [Mutiny](https://smallrye.io/smallrye-mutiny/latest/) `Multi` return type in Hilla endpoints. The `Multi` instance is automatically converted into a `Flux`, which is currently the only reactive type supported by Hilla.

```java
@BrowserCallable
@AnonymousAllowed
public class ClockService {

    public Multi<String> getClock() {
        return Multi.createFrom()
                .ticks()
                .startingAfter(Duration.ofSeconds(1))
                .every(Duration.ofSeconds(1))
                .onOverflow().drop()
                .map(unused -> LocalTime.now().toString())
                .onFailure()
                .recoverWithItem(err -> "Sorry, something failed...");
    }

    public MutinyEndpointSubscription<String> getCancellableClock() {
        return MutinyEndpointSubscription.of(getClock(), () -> {
            // unsubscribe callback
        });
    }
}
```

<h3>Experimental Embedded Vaadin Plugin <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.7</span></h3>

Simplify application setup by removing Vaadin Maven (or Gradle) plugin. The extension has a built-in implementation that can be enabled by setting `vaadin.build.enabled=true` in `application.properties`.

**Maven Setup:**
```properties
# In application.properties
vaadin.build.enabled=true
```

```xml
<!-- In pom.xml properties section -->
<quarkus.bootstrap.workspace-discovery>true</quarkus.bootstrap.workspace-discovery>
```
> [!WARNING]
> This is required because Quarkus Maven plugin does not provide workspace information needed by Vaadin internals. See [issue #45363](https://github.com/quarkusio/quarkus/issues/45363) for details.

<h3>Custom Endpoint Prefix <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.6</span></h3>

Configure a custom endpoint prefix via `vaadin.endpoint.prefix` in `application.properties`. The extension automatically creates a custom `connect-client.ts` file with the configured prefix.

```properties
vaadin.endpoint.prefix=/api/custom
```

<h3>Vaadin Quarkus Integration <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.5</span></h3>

Starting with 24.5, `quarkus-hilla` depends on the existing [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/), eliminating code duplication and ensuring tighter alignment with Vaadin's ecosystem.

<details>
<summary><strong>📜 Older Changes (24.4 and earlier)</strong></summary>

<h3>Vaadin Unified Platform <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.4</span></h3>

Since Vaadin 24.4, Flow and Hilla are unified in a single platform. The extension version now follows Vaadin platform releases (24.x instead of 2.x).

**Breaking Changes:**
- Maven groupId changed from `dev.hilla` to `com.vaadin.hilla`
- Java package names updated accordingly
- Minimum Quarkus version: 3.7+

<h3>Lit and React Extensions <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 2.4.1</span></h3>

The extension is subdivided into two artifacts:
- `quarkus-hilla` for **Lit** based applications
- `quarkus-hilla-react` for **React** based applications

</details>

---

## ⚙️ Features

<h3>Endpoints Live Reload <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.5</span></h3>

In dev mode, the extension automatically regenerates client-side code when endpoint classes change, without requiring a full rebuild.

**Configuration Example:**
```properties
quarkus.live-reload.instrumentation=true
vaadin.hilla.live-reload.enable=true
vaadin.hilla.live-reload.watch-strategy=source
vaadin.hilla.live-reload.watched-paths=com/example/ui
```

**Options:**
- `vaadin.hilla.live-reload.enable` - Enable/disable live reload (default: `false`)
- `vaadin.hilla.live-reload.watch-strategy` - Watch `source` files or compiled `class` files (default: `class`)
- `vaadin.hilla.live-reload.watched-paths` - Restrict watched folders (relative paths)

> [!NOTE]
> Source file watching currently supports only Java files, not Kotlin.

<h3>Auto CRUD, Auto Grid and Auto Form <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.4.1</span></h3>

Support for [Auto CRUD](https://hilla.dev/docs/react/components/auto-crud), [Auto Grid](https://hilla.dev/docs/react/components/auto-grid), and [Auto Form](https://hilla.dev/docs/react/components/auto-crud) is available in `quarkus-hilla-react`.

The extension provides custom implementations of `CrudRepositoryService` and `ListRepositoryService` based on:
- `quarkus-spring-data-jpa`
- `quarkus-hibernate-orm-panache`

> [!TIP]
> [See documentation](https://github.com/mcollovati/quarkus-hilla/wiki/Crud-List-repository-service) for details.

<h3>Native Image Support <span style="background-color: rgba(0, 123, 255, 0.1); color: #007bff; padding: 2px 6px; border: 1px solid rgba(0, 123, 255, 0.2); border-radius: 5px; font-size: 12px;">Since 24.5</span></h3>

Full GraalVM native image generation support without any known limitations.

---

## ⚠️ Limitations

The current Hilla support has some known limitations:

- ❌ Vaadin Copilot is not supported
- ❌ [Stateless Authentication](https://hilla.dev/docs/lit/guides/security/spring-stateless) is not supported

<details>
<summary><strong>⚠️ Vaadin 24.7 Build Workaround (Not required in 24.8+)</strong></summary>

With Vaadin 24.7, frontend build fails because Hilla endpoint generation relies on a Spring process. 

**Workaround Options:**

1. **Enable experimental embedded plugin** (recommended):
   ```properties
   vaadin.build.enabled=true
   ```

2. **Add workaround dependency** to `vaadin-maven-plugin`:
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
> [!IMPORTANT]
> **This workaround is not required in 24.8+** because the generation was refactored with a pluggable API for endpoint discovery.

</details>

---

## 📊 Usage Statistics

As discussed in [Hilla issue #211](https://github.com/vaadin/hilla/issues/211), the extension reports itself to Vaadin's usage statistics mechanism to help understand adoption and potentially encourage official support from Vaadin.

- 📈 Statistics are collected **only in development mode**
- 🔒 **No sensitive data** is gathered
- 🚫 [Opt-out instructions](https://github.com/vaadin/vaadin-usage-statistics#opting-out)

---

## Releases

 |                                                                                                  Quarkus-Hilla / Hilla                                                                                                   |                                                                               Quarkus                                                                               |                                                                              Vaadin                                                                              |
|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| <picture><img alt="Maven Central 24.9" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=24.9" style="visibility: visible;"></picture> | <picture><img alt="Quarkus 3.20+" src="https://img.shields.io/badge/QUARKUS-v3.20%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture> |   <picture><img alt="Vaadin 24.9" src="https://img.shields.io/badge/VAADIN-v24.9-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture>   |
| <picture><img alt="Maven Central 24.8" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=24.8" style="visibility: visible;"></picture> | <picture><img alt="Quarkus 3.15+" src="https://img.shields.io/badge/QUARKUS-v3.15%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture> |   <picture><img alt="Vaadin 24.8" src="https://img.shields.io/badge/VAADIN-v24.8-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture>   |
|  <picture><img alt="Maven Central 2.5" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2.5" style="visibility: visible;"></picture>  |  <picture><img alt="Quarkus 3.1+" src="https://img.shields.io/badge/QUARKUS-v3.1%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture>  |   <picture><img alt="Vaadin 24.2" src="https://img.shields.io/badge/VAADIN-v24.2-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture>   |
|   <picture><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1" style="visibility: visible;"></picture>   | <picture><img alt="Quarkus 2.16+" src="https://img.shields.io/badge/QUARKUS-v2.16%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture> | <picture><img alt="Vaadin 23.3+" src="https://img.shields.io/badge/VAADIN-v23.3%2B-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture> |

> [!NOTE]
> The major and minor version of Quarkus-Hilla always matches the Vaadin/Hilla version.

---

## Development

![Development 25.0-SNAPSHOT](https://img.shields.io/badge/25.0--SNAPSHOT-blue?style=for-the-badge&logo=github)
![Quarkus 3.27+](https://img.shields.io/badge/Quarkus-3.27%2B-blue?style=for-the-badge&logo=Quarkus)
![Vaadin 25.0](https://img.shields.io/badge/Vaadin-25.0-blue?style=for-the-badge&logo=Vaadin)

### Build and Test

**Prerequisites:**
- JDK 17 or later
- Maven 3.8 or later

**Build the extension:**
```bash
mvn -DskipTests install
```

**Run tests:**
```bash
mvn -DtrimStackTrace=false verify
```

**Run integration tests:**
```bash
mvn -DtrimStackTrace=false -Pit-tests verify
```

**Run integration tests in production mode:**
```bash
mvn -DtrimStackTrace=false -Pit-tests,production verify
```

**Debug tests:**
```bash
mvn -DtrimStackTrace=false -Dmaven.surefire.debug -Pit-tests verify
```

> [!IMPORTANT]
> Integration tests use [Selenide](https://selenide.org/) for browser interaction. Default browser: Chrome (Safari on macOS). Tests run in headless mode unless a debugger is attached.

### Update Codestarts

The extension codestarts are built by downloading a project from start.vaadin.com and applying necessary updates.

```bash
# In lit/runtime and react/runtime folders
mvn -Pupdate-hilla-codestart

# Update snapshot files
cd integration-tests/codestart-tests
mvn clean verify -Dsnap
```

Generated projects can be found in the `target` folder for manual verification.

### Release Process

The release process uses [JReleaser](https://jreleaser.org/).

**Required Environment Variables:**
- `JRELEASER_GITHUB_TOKEN` - Create release on GitHub
- `JRELEASER_GPG_PUBLIC_KEY`, `JRELEASER_GPG_SECRET_KEY`, `JRELEASER_GPG_PASSPHRASE` - Sign artifacts
- `JRELEASER_NEXUS2_MAVEN_CENTRAL_USERNAME`, `JRELEASER_NEXUS2_MAVEN_CENTRAL_PASSWORD` - Publish to Maven Central

**Release Commands:**
```bash
mvn clean
mvn -Pdistribution -Drevision=<version> -DskipTests \
    -DaltDeploymentRepository=local::file:./target/staging-deploy deploy
mvn -N -Pdistribution -Drevision=<version> jreleaser:full-release
```

Use `-Djreleaser.dry.run=true` to test without publishing.

**Version Format:**
- Release: `N.N.N` (e.g., `1.0.0`)
- Pre-release: `N.N.N-{alpha|beta|rc}N` (e.g., `1.0.0-beta2`)

---

## 💬 Community & Support

- 💭 [GitHub Discussions](https://github.com/mcollovati/quarkus-hilla/discussions) - Ask questions and share ideas
- 🐛 [Issue Tracker](https://github.com/mcollovati/quarkus-hilla/issues) - Report bugs or request features
- 🗣️ [Discord](https://discord.gg/SVnZGzHFvn) - Chat with the community
- 📧 [GitHub](https://github.com/mcollovati/quarkus-hilla) - Star the project

---

## Contributors ✨

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

## Credits

The banner for this project was created using the awesome [Banner Maker](https://github.com/obarlik/banner-maker) by [@obarlik](https://github.com/obarlik).
