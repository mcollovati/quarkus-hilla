{#include readme-header /}

## Hello-World application

Hilla provides the `init-app` maven goal to create a Hello-World endpoint and view, and also required front-end
dependencies and TypeScript configurations to boost development.
However, this currently doesn't work out-of-the-box with the Quarkus-Hilla extension.

To be able to run the `init-app` goal you need some temporary workarounds in your project:

- Add a simple Java class in your base package folder with a comment containing the `@SpringBootApplication` text.
  ```java
    package com.acme;
    // @SpringBootApplication
    public class temp {
    }
   ```
- If you plan to use `react` for your project, add the following dependency definition to the POM file
   ```xml
    <dependency>
      <groupId>dev.hilla</groupId>
      <artifactId>hilla-react</artifactId>
      <exclusions>
        <exclusion>
          <groupId>*</groupId>
          <artifactId>*</artifactId>
        </exclusion>
      </exclusions>
    </dependency> 
   ```

Once the maven goal creates the Hilla application code examples, you can remove both the temporary Java class and the
`hilla-react` dependency.