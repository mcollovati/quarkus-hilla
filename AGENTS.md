# Quarkus-Hilla Development Guidelines

This document contains comprehensive guidelines for agentic coding agents working in the Quarkus-Hilla repository.

## Build System & Commands

### Maven Commands
```bash
# Build entire project
mvn clean install

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run single test method
mvn test -Dtest=ClassName#methodName

# Run integration tests
mvn verify -Pit-tests

# Skip tests during build
mvn clean install -DskipTests

# Run formatting
mvn spotless:apply -Pall-modules

# Check formatting
mvn spotless:check -Pall-modules

# Generate Jandex indexes
mvn jandex:index
```

### Development Mode
```bash
# Run Quarkus dev mode
mvn quarkus:dev

# Run specific module in dev mode
cd module-name && mvn quarkus:dev
```

### Native Image
```bash
# Build native image
mvn package -Pnative

# Run native tests
mvn verify -Pnative
```

## Code Style Guidelines

### Java Code Style

#### Formatting
- **Tool**: Spotless with Palantir Java Format
- **Indentation**: 4 spaces (no tabs)
- **Line length**: Under 120 characters
- **Brace style**: K&R (opening brace on same line)
- **Imports**: Explicit imports (no wildcards), organized by group

#### Import Order
1. `jakarta.*` imports
2. `java.*` and `javax.*` imports
3. Third-party imports (alphabetical by package)
4. `com.github.mcollovati.*` imports (project-specific)
5. Static imports

#### Naming Conventions
- **Classes**: PascalCase (`EndpointController`, `MutinyEndpointSubscription`)
- **Methods**: camelCase (`serveEndpoint`, `getEndpointPrefix`)
- **Variables**: camelCase (`endpointName`, `formData`)
- **Constants**: UPPER_SNAKE_CASE (`DEFAULT_ENDPOINT_PREFIX`, `FEATURE`)
- **Packages**: lowercase with dots (`com.github.mcollovati.quarkus.hilla.deployment`)

#### Annotation Usage
- Place annotations directly above declarations
- One annotation per line for multiple annotations
- Common annotations: `@BuildStep`, `@Record`, `@Inject`, `@Path`, `@Endpoint`, `@BrowserCallable`, `@AnonymousAllowed`

#### Code Structure
```java
@BrowserCallable
@AnonymousAllowed
public class HelloWorldService {

    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
```

#### Error Handling
- Use checked exceptions in method signatures when appropriate
- Use runtime exceptions for programming errors (`IllegalArgumentException`)
- Include meaningful error messages
- Validate inputs at method entry points

### TypeScript/React Code Style

#### Formatting
- **Indentation**: 2 spaces for frontend files
- **Quotes**: Single quotes for strings
- **Semicolons**: Required
- **File extensions**: `.ts`, `.tsx` for TypeScript/React

#### Import Order
```typescript
// External libraries
import { Suspense, useEffect } from 'react';
import { AppLayout, DrawerToggle } from '@vaadin/react-components';

// Hilla-specific
import { createMenuItems, useViewConfig } from '@vaadin/hilla-file-router/runtime.js';
import { effect, signal } from '@vaadin/hilla-react-signals';

// Local imports
import { HelloWorldService } from 'Frontend/generated/endpoints.js';
import Placeholder from 'Frontend/components/placeholder/Placeholder';
```

#### Component Structure
```typescript
export const config: ViewConfig = {
  menu: { order: 0, icon: 'vaadin:globe' },
  title: 'Hello Hilla',
};

export default function HelloWorldView() {
  const name = useSignal('');

  return (
    <section className="flex p-m gap-m items-end">
      <TextField
        label="Your name"
        onValueChanged={(e) => {
          name.value = e.detail.value;
        }}
      />
      <Button onClick={async () => {
        const serverResponse = await HelloWorldService.sayHello(name.value);
        Notification.show(serverResponse);
      }}>
        Say hello
      </Button>
    </section>
  );
}
```

#### File Naming
- **Views**: PascalCase with `View` suffix (`HelloWorldView.tsx`)
- **Layouts**: `@layout.tsx`, `@index.tsx` (prefix with @ for special routes)
- **Components**: PascalCase (`Placeholder.tsx`)
- **Utilities**: camelCase (`auth.ts`, `routing.ts`)

#### State Management
- Use Hilla signals: `useSignal('')`
- Access: `name.value`, update: `name.value = 'new'`
- Use React hooks when needed: `useState`, `useEffect`

## Testing Guidelines

### Test Structure
```java
@QuarkusTest
class EndpointTest extends AbstractTest {

    @Test
    void methodName_scenarioUnderTest() {
        // Given - setup
        // When - action
        // Then - assertion
        assertThat(result).isEqualTo(expected);
    }
}
```

### Test Naming
- Method names should describe the scenario: `invokeEndpoint_singleSimpleParameter()`
- Use Given-When-Then structure for complex tests
- Separate test classes for different concerns

### Test Organization
- Unit tests: `src/test/java/`
- Integration tests: `integration-tests/*/src/test/java/`
- Base classes for common functionality: `AbstractTest`
- Test utilities in dedicated classes

### Frontend Testing
- Use component testing patterns appropriate for React/Lit
- Test service integration with mock endpoints
- Verify routing and navigation

## Package Structure

### Runtime Packages
```
com.github.mcollovati.quarkus.hilla.runtime/
├── crud/           # CRUD operations
│   ├── spring/     # Spring Data integration
│   └── panache/    # Panache integration
├── graal/          # GraalVM native support
├── multipart/      # File upload handling
├── reload/         # Live reload functionality
└── security/       # Security components
```

### Frontend Structure
```
src/main/frontend/
├── views/           # Page components
├── components/      # Reusable UI components
├── util/           # Utility functions
└── themes/         # Custom themes
```

## Development Workflow

### Before Committing
1. **Run formatting**: `mvn spotless:apply -Pall-modules`
2. **Run tests**: `mvn test`
3. **Check build**: `mvn clean compile`
4. **Run integration tests**: `mvn verify -Pit-tests`

### Making Changes
1. Follow existing code patterns and naming conventions
2. Add appropriate JavaDoc documentation
3. Include tests for new functionality
4. Update documentation if needed
5. Verify formatting before committing

## Agent Behavior Guidelines

### File Deletion Policy
- **ALWAYS** ask for explicit confirmation before deleting any files
- Use `rm`, `git rm`, or any deletion commands only after receiving explicit approval
- Consider alternatives like moving files to backup locations when possible
- Provide clear information about what will be deleted before requesting approval

### Endpoint Development
```java
@BrowserCallable
@AnonymousAllowed  // Add security constraints as needed
public class MyService {

    public String method(String param) {
        return result;
    }
}
```

### Configuration
- Use `@ConfigMapping` for type-safe configuration
- Follow existing prefix patterns: `vaadin.hilla.*`
- Provide sensible defaults

## Important Patterns

### Null Safety
- Use `@NonNullApi` package annotations
- Return `Optional<T>` for nullable values
- Validate null inputs in public methods

### Reactive Programming
- Use Mutiny patterns: `Multi`, `Uni`
- Convert between reactive types appropriately
- Handle backpressure correctly

### Error Responses
- Use appropriate HTTP status codes
- Include meaningful error messages
- Structure error responses consistently

### Security
- Always apply security constraints to endpoints
- Use `@AnonymousAllowed` only for public endpoints
- Validate user permissions in business logic

## Module-Specific Notes

### Quarkus Extension Development
- Use `@BuildStep` for build-time processing
- Follow Quarkus extension patterns
- Implement proper feature detection

### Frontend Development
- Use Vaadin components from `@vaadin/react-components`
- Follow Hilla routing patterns
- Implement proper loading states and error handling

### Integration Tests
- Use abstract base classes for common setup
- Test both happy path and error scenarios
- Include native image testing when relevant

## Documentation Requirements

### JavaDoc
- Document all public classes and methods
- Include `@param` and `@return` tags
- Provide usage examples for complex APIs

### README Updates
- Update feature documentation
- Include configuration examples
- Document breaking changes

This file serves as the authoritative guide for development practices in the Quarkus-Hilla project. All agents should reference these guidelines when making changes to the codebase.
