# 🧭 Migrating From v24.x To v25.x

Quarkus-Hilla-specific changes when upgrading an existing 24.x project to 25.x. This covers Quarkus-Hilla integration changes only — for Vaadin platform-level changes (Flow, Hilla, components), see the official [Hilla docs](https://vaadin.com/docs/latest/hilla).

<a id="build-plugin"></a>

## 🏗️ Build Plugin

- Remove `vaadin-maven-plugin` (or the Vaadin Gradle plugin) from your build — the official [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/) now provides embedded build support automatically, enabled by default.
- Drop the `aot-browser-finder-callable-workaround` dependency and any extra `vaadin-maven-plugin` executions added for the 24.7 Spring-process workaround — resolved upstream, no longer needed.
- `quarkus.bootstrap.workspace-discovery=true` in `pom.xml` is no longer required, but still recommended for best results.
- Heads-up: `vaadin.build.enabled` now defaults to `true` (was `false` in 24.x). If your build relied on the old default rather than setting it explicitly, double-check behavior after upgrading.

See [v24 Docs](v24-docs.md#vaadin-247-249-embedded-build-plugin--workaround) for what the old setup looked like.

## 🗃️ Need The Old Setup Docs?

Staying on Vaadin 24.x for now? See [v24 Docs](v24-docs.md).
