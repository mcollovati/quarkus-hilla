# Quarkus - Hilla

A [Quarkus](https://quarkus.io) extension to run [Hilla](https://hilla.dev)
applications on Quarkus.

Hilla is an open source framework, provided
by [Vaadin Ltd.](https://vaadin.com),
that integrates a Spring Boot Java backend with a reactive TypeScript frontend.

This extension replaces the Spring Boot backend with Quarkus Context &
Dependency Injection (CDI) and
RESTEasy Reactive for a simpler integration with Quarkus, but preserves the
main features of the Hilla Framework, such
as [Endpoints](https://hilla.dev/docs/lit/guides/endpoints),
[Reactive Endpoints](https://hilla.dev/docs/lit/guides/reactive-endpoints)
and [Security](https://hilla.dev/docs/lit/guides/security).

**NOTE**: This is an **unofficial community extension**, and it is **not** directly
related nor supported by Vaadin Ltd.

## Limitations

The current Hilla support has some known limitations:

* The endpoint prefix is not configurable
* [Stateless Authentication](https://hilla.dev/docs/lit/guides/security/spring-stateless)
  is not supported
* Native image compilation does not work

## Release

```terminal
mvn clean
mvn -Pdistribution -Drevision=<version-to-release> -DskipTests -DaltDeploymentRepository=local::file:./target/staging-deploy -Dmaven.javadoc.failOnError=false deploy 
mvn -N -Pdistribution -Drevision=<version-to-relese> -Djreleaser.dry.run=true -Djreleaser.signing.mode=FILE -Djreleaser.release.skip=true jreleaser:full-release
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