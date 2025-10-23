# Release Process

This document describes the release process for the Quarkus-Hilla extension.

## Overview

The release process uses [JReleaser](https://jreleaser.org/) to automate the publication of releases to GitHub and Maven Central.

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
mvn -Pdistribution -Drevision=<version> -DskipTests \
    -DaltDeploymentRepository=local::file:./target/staging-deploy deploy

# Publish the release
mvn -N -Pdistribution -Drevision=<version> jreleaser:full-release
```

Replace `<version>` with the actual version number (e.g., `24.9.0`).

### Dry Run (Testing)

To test the release process without actually publishing:

```bash
mvn clean

mvn -Pdistribution -Drevision=<version> -DskipTests \
    -DaltDeploymentRepository=local::file:./target/staging-deploy deploy

mvn -N -Pdistribution -Drevision=<version> \
    -Djreleaser.dry.run=true jreleaser:full-release
```

The dry run will show you what would happen without making any actual changes.

## Version Format

The version format depends on the type of release:

### Regular Release
- Format: `N.N.N`
- Example: `24.9.0`, `25.0.0`

### Pre-release
- Format: `N.N.N-{alpha|beta|rc}N`
- Examples:
  - `24.9.0-alpha1`
  - `24.9.0-beta2`
  - `24.9.0-rc1`

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
   mvn -Pdistribution -Drevision=<version> -DskipTests \
       -DaltDeploymentRepository=local::file:./target/staging-deploy deploy
   mvn -N -Pdistribution -Drevision=<version> \
       -Djreleaser.dry.run=true jreleaser:full-release
   ```

4. **Execute the release**
   ```bash
   mvn clean
   mvn -Pdistribution -Drevision=<version> -DskipTests \
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

## Additional Resources

- [JReleaser Documentation](https://jreleaser.org/)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/)
- [GitHub Releases Documentation](https://docs.github.com/en/repositories/releasing-projects-on-github)

