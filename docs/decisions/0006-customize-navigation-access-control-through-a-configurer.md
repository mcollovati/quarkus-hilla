---
status: accepted
date: 2026-10-20
decision-makers: Dario Götze
consulted: Marco Collovati
---

# Customize the navigation access control through a configurer

## Context and Problem Statement

Up to 25.2, an application customizes the Flow navigation access control by
replacing the `NavigationAccessControl` bean, which the extension provides as a
`@DefaultBean` (`QuarkusNavigationAccessControl`). The replacement owns its
checkers, decision resolver and login view, and the extension can only apply
the form login page and the opt-out from #2279 to it after it is built.

25.3 introduces `NavigationAccessControlConfigurer` (#2331). With two ways to
customize the access control, the extension has to define which one is
supported, how they interact, and what happens to applications that already
replace the bean.

Vaadin's Spring integration supports only the configurer: its
`navigationAccessControl` bean has no `@ConditionalOnMissingBean`, while the
`NavigationAccessControlConfigurer` bean has.

## Considered Options

1. The configurer is the supported way. Replacing the bean keeps working, is
   deprecated and logs a warning at startup.
2. The configurer and a replaced bean are equal alternatives, without a
   warning.

## Decision

Option 1.

* The extension builds the `NavigationAccessControl` from the configurer and
  exposes it only as `NavigationAccessControl`.
* An application `NavigationAccessControl` bean still replaces the extension's
  and still receives the login page and the opt-out. The extension logs a
  warning that replacing the bean is deprecated, asking to describe uncovered
  use cases in an issue.
* An application bean together with an application configurer fails the
  startup, because the configurer would otherwise be ignored silently.
* `QuarkusNavigationAccessControl` is internal API. Extending it works, but it
  may change in any release.

Option 2 stays open: if issues show use cases the configurer cannot cover,
for example an access control with its own `checkAccess` logic, removing the
warning and documenting the bean as an alternative is enough.

## Consequences

* Good, because applications have one documented way to customize the access
  control, as in Vaadin's Spring integration.
* Good, because the extension controls construction and can validate the
  configuration (checkers, resolver, login view) before anything is built.
* Good, because existing applications that replace the bean keep working and
  are pointed to the configurer.
* Bad, because the extension still has to support replaced beans, including
  their login view handling, until the deprecation ends.
* Bad, because injecting `QuarkusNavigationAccessControl` no longer works
  (breaking change in 25.3.0).

## More Information

* #2331: customization model for the navigation access control.
* #2279: opt-out and login page handling for replaced beans.
* #2337: implementation.
