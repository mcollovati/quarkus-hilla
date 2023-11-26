{#include readme-header /}

## Hello-World application

Hilla provides the `init-app` maven goal to create a Hello-World endpoint and view, and also the required front-end
dependencies and TypeScript configurations to boost development.
However, this currently doesn't work out-of-the-box with the Quarkus-Hilla extension.

To be able to run the `init-app` goal you need some temporary workarounds in your project:

- Add a simple Java class in your base package folder with a comment containing the `@SpringBootApplication` text.
  ```java
    package {project.package-name};
    // @SpringBootApplication
    public class temp {
    }
   ```
