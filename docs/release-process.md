# Release Process

This document describes the release process for the Quarkus-Hilla extension.

## Overview

The release process uses [JReleaser](https://jreleaser.org/) to automate the publication of releases to GitHub and Maven Central. Maintainers should prefer the GitHub Actions release workflow for normal releases and use the local commands below for troubleshooting or manual fallback.

## Prerequisites

Before starting a release, ensure you have:

- **JDK 17 or later** installed
- **Maven 3.8 or later** installed
- **GPG keys** configured for signing artifacts
- **Access credentials** for GitHub and Maven Central

## Required Environment Variables

The following environment variables must be set before running the release:

| Variable | Purpose |
|----------|---------|
| `JRELEASER_GITHUB_TOKEN` | Create release on GitHub |
| `JRELEASER_GPG_PUBLIC_KEY` | GPG public key for signing artifacts |
| `JRELEASER_GPG_SECRET_KEY` | GPG secret key for signing artifacts |
| `JRELEASER_GPG_PASSPHRASE` | Passphrase for GPG key |
| `JRELEASER_NEXUS2_MAVEN_CENTRAL_USERNAME` | Username for Maven Central |
| `JRELEASER_NEXUS2_MAVEN_CENTRAL_PASSWORD` | Password for Maven Central |

## Release Commands

### Standard Release

```bash
# Clean the project
mvn clean

# Build and deploy to staging
mvn -Pdistribution -Drevision=<version> -DskipTests -DskipITs=true \
    -DaltDeploymentRepository=local::file:./target/staging-deploy deploy

# Publish the release
mvn -N -Pdistribution -Drevision=<version> jreleaser:full-release
```

Replace `<version>` with the actual version number (e.g., `25.2.0` or `25.2.0-beta1`).

### Dry Run (Testing)

To test the release process without actually publishing:

```bash
mvn clean

mvn -Pdistribution -Drevision=<version> -DskipTests -DskipITs=true \
    -DaltDeploymentRepository=local::file:./target/staging-deploy deploy

mvn -N -Pdistribution -Drevision=<version> \
    -Djreleaser.dry.run=true jreleaser:full-release
```

The dry run will show you what would happen without making any actual changes.

## Version Format

The version format depends on the type of release:

### Regular Release
- Format: `N.N.N`
- Example: `25.2.0`, `25.1.2`

### Pre-release
- Format: `N.N.N-{alpha|beta|rc}N`
- Examples:
  - `25.2.0-alpha1`
  - `25.2.0-beta2`
  - `25.2.0-rc1`

> [!IMPORTANT]
> The major and minor version of Quarkus-Hilla must always match the Vaadin/Hilla version.

## Release Steps

1. **Prepare the codebase**
   - Ensure all tests pass: `mvn clean verify`
   - Update CHANGELOG if applicable
   - Commit all changes

2. **Set environment variables**
   ```bash
   export JRELEASER_GITHUB_TOKEN=<your-token>
   export JRELEASER_GPG_PUBLIC_KEY=<your-public-key>
   export JRELEASER_GPG_SECRET_KEY=<your-secret-key>
   export JRELEASER_GPG_PASSPHRASE=<your-passphrase>
   export JRELEASER_NEXUS2_MAVEN_CENTRAL_USERNAME=<your-username>
   export JRELEASER_NEXUS2_MAVEN_CENTRAL_PASSWORD=<your-password>
   ```

3. **Run a dry run** (optional but recommended)
   ```bash
   mvn clean
   mvn -Pdistribution -Drevision=<version> -DskipTests -DskipITs=true \
       -DaltDeploymentRepository=local::file:./target/staging-deploy deploy
   mvn -N -Pdistribution -Drevision=<version> \
       -Djreleaser.dry.run=true jreleaser:full-release
   ```

4. **Execute the release**
   ```bash
   mvn clean
   mvn -Pdistribution -Drevision=<version> -DskipTests -DskipITs=true \
       -DaltDeploymentRepository=local::file:./target/staging-deploy deploy
   mvn -N -Pdistribution -Drevision=<version> jreleaser:full-release
   ```

5. **Verify the release**
   - Check GitHub releases: https://github.com/mcollovati/quarkus-hilla/releases
   - Check Maven Central: https://central.sonatype.com/artifact/com.github.mcollovati/quarkus-hilla
   - Note: Maven Central sync may take a few hours

## Troubleshooting

### GPG Signing Issues

If you encounter GPG signing errors:
- Ensure your GPG keys are properly configured
- Verify the passphrase is correct
- Check that the public and secret keys match

### Maven Central Upload Failures

If the upload to Maven Central fails:
- Verify your Nexus credentials are correct
- Check that all required metadata is present
- Ensure artifacts are properly signed

### GitHub Release Issues

If the GitHub release fails:
- Verify your GitHub token has the necessary permissions
- Check that the repository URL is correct
- Ensure the version tag doesn't already exist

## Post-Release Tasks

After a successful release:

1. **Announce the release**
   - Create a GitHub announcement
   - Update documentation if needed
   - Notify users through appropriate channels

2. **Update development version**
   - Continue development with the next SNAPSHOT version

3. **Update release notes**
   - Document new features, bug fixes, and breaking changes

4. **Pre-release soak**
   - For new minor lines, publish a beta such as `25.2.0-beta1` first and let it soak before deciding on the GA release.

## Additional Resources

- [JReleaser Documentation](https://jreleaser.org/)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/)
- [GitHub Releases Documentation](https://docs.github.com/en/repositories/releasing-projects-on-github)

## Final maintenance release for 25.1

The 25.1 line no longer receives ongoing development. Scheduled snapshot validation and Dependabot updates target
main, 25.3 and 25.2. Historical compatibility rows remain in the README.

Keep 25.1 available in the manual release and NPM synchronization workflows until its final maintenance release.
Existing dependency pull requests and selected bug backports can still run pull request validation and NPM synchronization
for that release. Remove the remaining 25.1 manual targets and NPM synchronization trigger after publication.

Limit the final release to verified bug fixes and compatible patch updates. Do not backport new authentication modes,
security configuration features or adaptations specific to newer Hilla APIs. Verify the complete 25.1 release candidate
before publication.

Candidate backports are the form-logout cookie-scope fix (#2311) and the generated Hilla route authorization fix (#2261).
The latter requires a dedicated React Router 7 adaptation for 25.1, rather than an unchanged cherry-pick of the main
matcher. Existing Flow access-checker and resolved-version-catalog fixes are already present on 25.1.

Two security configuration changes are exceptions. The navigation access control opt-out (#2279) is backported,
because 25.1.4 shipped the default-deny change (#2264) that it lets applications postpone. The replaceable access
check decision resolver (#2335) is backported too, as a small extension point without behavior change. Nothing else of
that configuration area is backported, including the customization model for the navigation access control (#2331).
