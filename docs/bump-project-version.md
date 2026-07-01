# Bump Project Version

This document describes how to bump the project to a new development version (the next `MAJOR.MINOR-SNAPSHOT`) and fork off the previous version line as a maintenance branch.

## Overview

When a new minor is opened on `main` (e.g. moving from `25.2-SNAPSHOT` to `25.3-SNAPSHOT`), the previous version becomes a maintenance branch. CI workflows, the Dependabot configuration and the README need to be updated so that active lines keep building, get dependency updates and are reflected in the documentation.

The `etc/UpdateProjectVersion.java` [JBang](https://www.jbang.dev) script automates this process.

## Prerequisites

- JDK 21 or later
- Maven 3.8 or later
- [JBang](https://www.jbang.dev/download) installed
- The `pom.xml` `<revision>` and `<hilla.version>` properties must end with `-SNAPSHOT`

## Usage

```bash
jbang etc/UpdateProjectVersion.java <project-folder> <new-version>
```

For example, to bump the current project from `25.2-SNAPSHOT` to `25.3-SNAPSHOT`:

```bash
jbang etc/UpdateProjectVersion.java . 25.3
```

The new version must be in `MAJOR.MINOR` format (no `-SNAPSHOT`, no patch).

### Options

| Option                    | Description                                                                          |
|---------------------------|--------------------------------------------------------------------------------------|
| `-y`, `--yes`             | Skip the interactive confirmation prompt                                             |
| `-n`, `--dry-run`         | Print the actions that would be taken without writing any file                       |
| `--skip-readme`           | Do not update `README.md`                                                            |
| `-m`, `--maven-home PATH` | Use the given Maven installation instead of the one on `PATH`                        |

## What it changes

Given a current version `C` (read from the `<revision>` property in `pom.xml`) and a target version `N` (the script argument), the following files are updated:

### `pom.xml`

The Maven properties are bumped via `mvn versions:set-property`:

- `<revision>` → `<N>-SNAPSHOT`
- `<hilla.version>` → `<N>-SNAPSHOT`

### `.github/workflows/`

The previous version `C` is added as a target/branch entry alongside `main`, so the maintenance branch keeps running CI:

- `release.yaml` — `on.workflow_dispatch.inputs.target-branch.options`
- `update-npm-deps.yaml` — same options list, plus the embedded matrix in `jobs.compute-matrix.steps[0].run`
- `validation.yaml` — `on.push.branches`
- `validation-nightly.yaml` — `jobs.snapshot-main.strategy.matrix.branch`

### `.github/dependabot.yml`

A new `updates` entry is inserted, targeting branch `C` with `daily` schedule and ignoring `semver-major` and `semver-minor` updates of `com.vaadin.hilla:*` and `com.vaadin:*`.

### `README.md`

Unless `--skip-readme` is passed:

- The Development Version row is updated (`<C>-SNAPSHOT` → `<N>-SNAPSHOT` and the Vaadin column from `<C>` to `<N>`).
- The Quick Start XML examples are updated to reference `<C>.x` (the previous minor, which has now become the latest maintenance branch).
- A new entry for version `<C>` is added at the top of the Compatibility Matrix, cloning the Quarkus column from the existing top row and marking the row for manual verification.

The Maven Central badges at the top of the README are **not** touched.

### ⚠️ Always verify the cloned Quarkus baseline

The script clones the previous row's Quarkus column as a starting point, not a verified value. Vaadin's [Vaadin Quarkus extension](https://github.com/vaadin/quarkus/) has silently raised its minimum Quarkus version between **patch** releases before — e.g. Vaadin `25.0.9` moved the baseline from Quarkus `3.27` to `3.32` after Flow adopted Jackson `3.1.x`, with no corresponding Quarkus-Hilla minor bump to hang the change on. Before removing the inserted row's `⚠️ verify Quarkus baseline` marker, check the actual `vaadin-quarkus-extension-parent` / Quarkus version pulled in by the new release, not just what the previous minor used.

## Idempotency

Each file mutation skips itself if the version is already present, so re-running with the same arguments after a partial failure is safe for the YAML and README mutations. The script refuses to run if the project is already at the target version (i.e. `<revision>` already equals `<N>-SNAPSHOT`).

## Manual follow-up

After running the script:

1. Review the diff (`git diff`) to confirm the changes look right.
2. Create the actual `<C>` maintenance branch on the remote (`git checkout -b <C>` from the commit before the bump, then push).
3. Keep automation focused on `main` plus the last three active 25.x lines. Remove older lines such as 24.x from workflow matrices and Dependabot when they are no longer maintained.
4. Historical compatibility rows may remain in the README, but move bulky old setup/workaround notes to a legacy document.
5. Update the wiki and any external references that pin a specific version.
