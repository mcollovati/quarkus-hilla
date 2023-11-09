# Quarkus - Hilla

[![Maven Central 2.x](https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2)](https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla)
[![Maven Central 1.x](https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1)](https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla)
[![Apache License 2.0](https://img.shields.io/github/license/mcollovati/quarkus-hilla?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)

A [Quarkus](https://quarkus.io) extension to run [Hilla](https://hilla.dev)
applications on Quarkus.

Hilla is an open source framework, provided by [Vaadin Ltd.](https://vaadin.com),
that integrates a Spring Boot Java backend with a reactive TypeScript frontend.

This extension replaces the Spring Boot backend with Quarkus Context &
Dependency Injection (CDI) and
RESTEasy Reactive for a simpler integration with Quarkus, but preserves the
main features of the Hilla Framework, such
as [Endpoints](https://hilla.dev/docs/lit/guides/endpoints),
[Reactive Endpoints](https://hilla.dev/docs/lit/guides/reactive-endpoints)
and [Security](https://hilla.dev/docs/lit/guides/security).

**NOTE**: This is an **unofficial community extension**, and it is **not**
directly related **nor** supported by Vaadin Ltd.

## Limitations

The current Hilla support has some known limitations:

* The endpoint prefix is not configurable
* [Stateless Authentication](https://hilla.dev/docs/lit/guides/security/spring-stateless)
  is not supported
* Native image compilation does not work

## Usage statistics

As discussed in this Hilla [ticket](https://github.com/vaadin/hilla/issues/211),
the extension report itself to the Vaadin usage statistics mechanism in order to
get a better understanding of how widely the extension is used compared to Hilla
usage in general.
The hope is that, based on this data, Vaadin may consider in the future to provide
an official extension.
Statistic are collected only in development mode and no sensitive data is
gathered.
For instructions on how to opt-out, see
the [client-side collector repository](https://github.com/vaadin/vaadin-usage-statistics#opting-out).

## Getting started

Get started with `quarkus-hilla` by following the [Quick Start Guide](../../wiki/QuickStart)
or download the [starter project](https://github.com/mcollovati/quarkus-hilla-starter).

```xml
<dependency>
    <groupId>com.github.mcollovati</groupId>
    <artifactId>quarkus-hilla</artifactId>
    <version>2.0.0-alpha1</version>
</dependency>
```

## Compatibility Matrix

|                                                                                                     Quarkus-Hilla                                                                                                      |                                                                               Quarkus                                                                               |                                                        Hilla                                                         |                                                                             Vaadin                                                                             |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| <picture><img alt="Maven Central 2.2" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2.2" style="visibility: visible;"></picture> |  <picture><img alt="Quarkus 3.1+" src="https://img.shields.io/badge/QUARKUS-v3.1%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture>  | <picture><img alt="Hilla 2.2" src="https://tinyurl.com/quarkus-hilla-h-2-2" style="visibility: visible;"></picture>  |   <picture><img alt="Flow 24.2" src="https://img.shields.io/badge/VAADIN-v24.2-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture>   |
| <picture><img alt="Maven Central 2.1" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=2.1" style="visibility: visible;"></picture> |  <picture><img alt="Quarkus 3.1+" src="https://img.shields.io/badge/QUARKUS-v3.1%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture>  | <picture><img alt="Hilla 2.1" src="https://tinyurl.com/quarkus-hilla-h-2-1" style="visibility: visible;"></picture>  |   <picture><img alt="Flow 24.1" src="https://img.shields.io/badge/VAADIN-v24.1-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture>   |
|  <picture><img alt="Maven Central 1.x" src="https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla?style=for-the-badge&logo=apache-maven&versionPrefix=1" style="visibility: visible;"></picture>  | <picture><img alt="Quarkus 2.16+" src="https://img.shields.io/badge/QUARKUS-v2.16%2B-blue?style=for-the-badge&logo=Quarkus" style="visibility: visible;"></picture> | <picture><img alt="Hilla 1.3+" src="https://tinyurl.com/quarkus-hilla-h-1-3" style="visibility: visible;"></picture> | <picture><img alt="Flow 23.3+" src="https://img.shields.io/badge/VAADIN-v23.3%2B-blue?style=for-the-badge&logo=Vaadin" style="visibility: visible;"></picture> |

**NOTE**: These versions are known to work together. Other combinations may work, but are not tested.

## Build and test

To build the extension locally you need to install JDK 17 or later and Maven 3.8 or later.

The extension and its required dependencies can be built by typing the following command:

```terminal
mvn -DskipTests install
```

To run the test suite, execute the maven `verify` goal:

```terminal
mvn -DtrimStackTrace=false verify
```

End-to-end test modules can be found in the `integration-tests` folder.
Integration tests use [Selenide](https://selenide.org/) for browser interaction,
the browser used by default is Chrome, except for MacOS, where Safari is used.
Execution of end-to-end integration tests requires the activation of the maven `it-tests` profile.

```terminal
mvn -DtrimStackTrace=false -Pit-tests verify
```

The same tests can also be executed in production mode, by activating the `production` profile in addition
to `it-tests`.

```terminal
mvn -DtrimStackTrace=false -Pit-tests,production verify
```

Tests run by default in headless mode, meaning that the browser window will not be visible during the execution,
unless a debugger is attached to the JVM, either by running the tests in debug mode from the IDE, or by providing the
`-Dmaven.surefire.debug` system property to the maven command line.

```terminal
mvn -DtrimStackTrace=false -Dmaven.surefire.debug -Pit-tests verify
```

## Release

To perform a manual release type the following commands.
Version must be in format N.N.N, for example `1.0.0`.
Pre-releases can use `-alpha`, `-beta` and `-rc` suffix, followed by a number,
for example `1.0.0-beta2`.

Environment variables required by the release process:

* JRELEASER_GITHUB_TOKEN: to create release on GitHub
* JRELEASER_GPG_PUBLIC_KEY, JRELEASER_GPG_SECRET_KEY and
  JRELEASER_GPG_PASSPHRASE: to sign artifacts
* JRELEASER_NEXUS2_MAVEN_CENTRAL_USERNAME and
  JRELEASER_NEXUS2_MAVEN_CENTRAL_PASSWORD: to publish on maven central

Use `-Djreleaser.dry.run=true` flag to test the release without publishing
artifacts.

```terminal
mvn clean
mvn -Pdistribution -Drevision=<version-to-release> -DskipTests -DaltDeploymentRepository=local::file:./target/staging-deploy deploy
mvn -N -Pdistribution -Drevision=<version-to-release> jreleaser:full-release
```

## Contributors ✨

Thanks goes to these wonderful
people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

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

This project follows
the [all-contributors](https://github.com/all-contributors/all-contributors)
specification. Contributions of any kind are welcome!
