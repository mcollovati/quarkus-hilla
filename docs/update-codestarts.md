# Update Codestarts

This document describes how to update the extension codestarts for both Lit and React frameworks.

## Overview

The extension codestarts are starter templates that provide a quick way to generate new projects with the Quarkus-Hilla extension. These codestarts are built by downloading a base project from [start.vaadin.com](https://start.vaadin.com) and applying necessary updates to make them compatible with Quarkus.

## Prerequisites

- JDK 17 or later
- Maven 3.8 or later
- Internet connection (to download projects from start.vaadin.com)

## Update Process

### Step 1: Update Codestart Templates

The codestart templates need to be updated in both the Lit and React runtime modules.

**Navigate to the runtime folders and run the update command:**

```bash
# For Lit codestarts
cd lit/runtime
mvn -Pupdate-hilla-codestart

# For React codestarts
cd react/runtime
mvn -Pupdate-hilla-codestart
```

This Maven profile will:
1. Download the latest Hilla project template from start.vaadin.com
2. Apply Quarkus-specific modifications
3. Update the codestart files in the `src/main/resources/codestarts` directory

### Step 2: Update Snapshot Files

After updating the codestart templates, you need to regenerate the snapshot files used by integration tests.

```bash
cd integration-tests/codestart-tests
mvn clean verify -Dsnap
```

The `-Dsnap` flag tells the test framework to regenerate the snapshot files based on the updated codestarts.

### Step 3: Verify Generated Projects

After running the update commands, Maven generates sample projects in the `target` folder for manual verification.

**Locations of generated projects:**

- **Lit projects**: `lit/runtime/target/`
- **React projects**: `react/runtime/target/`
- **Test projects**: `integration-tests/codestart-tests/target/`

**Manual verification steps:**

1. Navigate to the generated project directory
2. Check that all necessary files are present:
   - `pom.xml` with correct dependencies
   - `application.properties` with Quarkus/Vaadin configuration
   - Frontend files (TypeScript, views, components)
   - Test files
3. Verify the project structure matches Quarkus conventions
4. Test the project builds successfully:
   ```bash
   cd target/<generated-project>
   mvn clean install
   ```
5. Test in dev mode:
   ```bash
   mvn quarkus:dev
   ```
6. Open browser and verify the application works as expected

## What Gets Updated

When updating codestarts, the following elements are typically modified:

### Maven Configuration
- **Dependencies**: Updated to use Quarkus-Hilla dependencies instead of Spring Boot
- **Plugins**: Configured for Quarkus build process
- **Properties**: Quarkus-specific configuration properties

### Application Configuration
- **application.properties**: Quarkus and Vaadin configuration
- **Security configuration**: CDI-based security instead of Spring Security
- **Database configuration**: Quarkus datasource configuration

### Backend Code
- **Dependency Injection**: CDI annotations instead of Spring annotations
- **Endpoints**: Hilla `@BrowserCallable` endpoints with Quarkus support
- **Services**: Quarkus CDI beans and services

### Frontend Code
- **TypeScript configuration**: Updated for Hilla/Quarkus integration
- **Vite configuration**: Adjusted for Quarkus dev mode
- **Views and components**: Framework-specific (Lit or React) templates

## Troubleshooting

### Download Failures

If the download from start.vaadin.com fails:

- **Check your internet connection**
- **Verify start.vaadin.com is accessible**
- **Check if there are any proxy settings** that might block the download
- **Try again later** if the service is temporarily unavailable

### Build Failures

If the codestart update fails during the build:

- **Check Maven logs** for specific error messages
- **Verify Java version** (JDK 17+ required)
- **Clean Maven cache** if dependencies are corrupted:
  ```bash
  rm -rf ~/.m2/repository/com/vaadin
  rm -rf ~/.m2/repository/com/github/mcollovati
  ```

### Snapshot Update Failures

If snapshot generation fails:

- **Ensure codestarts were updated first** in the runtime modules
- **Clean the test module** before running:
  ```bash
  cd integration-tests/codestart-tests
  mvn clean
  mvn verify -Dsnap
  ```
- **Check for test errors** in the console output

### Generated Project Issues

If the generated project doesn't work correctly:

- **Compare with working examples** in the integration tests
- **Check for missing dependencies** in `pom.xml`
- **Verify configuration** in `application.properties`
- **Review changes** in the `src/main/resources/codestarts` directory

## Testing Updated Codestarts

After updating codestarts, it's important to test them thoroughly:

### Automated Tests

Run the codestart integration tests:

```bash
cd integration-tests/codestart-tests
mvn clean verify
```

This will generate projects from the updated codestarts and run automated tests to ensure they work correctly.

### Manual Testing

1. **Generate a new project** using the Quarkus CLI or Maven:
   ```bash
   # For Lit
   mvn io.quarkus:quarkus-maven-plugin:create \
       -DprojectGroupId=org.acme \
       -DprojectArtifactId=test-hilla-lit \
       -Dextensions="quarkus-hilla"
   
   # For React
   mvn io.quarkus:quarkus-maven-plugin:create \
       -DprojectGroupId=org.acme \
       -DprojectArtifactId=test-hilla-react \
       -Dextensions="quarkus-hilla-react"
   ```

2. **Build and run the project**:
   ```bash
   cd test-hilla-lit  # or test-hilla-react
   mvn quarkus:dev
   ```

3. **Test all features**:
   - Endpoint generation and TypeScript types
   - Hot reload functionality
   - Frontend routing
   - Security (if included)
   - Database integration (if included)

4. **Test production build**:
   ```bash
   mvn clean package -Pproduction
   java -jar target/quarkus-app/quarkus-run.jar
   ```

## Best Practices

### Before Updating

1. **Check the current version** of Vaadin/Hilla being used
2. **Review release notes** for any breaking changes
3. **Create a backup** of current codestarts if needed
4. **Ensure main branch is stable** before applying updates

### During Update

1. **Update one framework at a time** (Lit first, then React)
2. **Test each step** before moving to the next
3. **Document any custom modifications** made to templates
4. **Keep track of changes** for release notes

### After Update

1. **Run full test suite** to ensure nothing broke
2. **Update documentation** if there are user-facing changes
3. **Create a PR** with the codestart updates
4. **Tag the commit** with the corresponding Vaadin version

## When to Update Codestarts

Codestarts should be updated in the following scenarios:

- **New Vaadin/Hilla release** - Update to match the latest version
- **New Quarkus version** - Ensure compatibility with latest Quarkus features
- **Bug fixes** - When issues are found in generated projects
- **New features** - When adding new functionality to the extension
- **Security updates** - When dependencies need to be updated for security reasons

## Additional Resources

- [Quarkus Codestarts Documentation](https://quarkus.io/guides/extension-codestart)
- [Vaadin Starter Projects](https://start.vaadin.com)
- [Hilla Documentation](https://hilla.dev/docs)
- [Quarkus Extension Development](https://quarkus.io/guides/building-my-first-extension)

## Related Files

The codestart templates are located in:

```
lit/runtime/src/main/resources/codestarts/
react/runtime/src/main/resources/codestarts/
integration-tests/codestart-tests/src/test/
```

Each codestart directory contains:
- `base/` - Common files for all projects
- `codestart.yml` - Codestart metadata and configuration
- `java/` - Java source templates
- `typescript/` - Frontend source templates
- `resources/` - Configuration and resource templates

