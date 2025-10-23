# Build and Test

## Build the extension

### Prerequisites:
- ☕ JDK 21 or later (JDK 17+ for versions < 25.0)
- 📦 Maven 3.8 or later

Build the extension and its required dependencies:

```bash
mvn -DskipTests install
```

## Testing

### Run unit tests

Execute the test suite using the Maven `verify` goal:

```bash
mvn -DtrimStackTrace=false verify
```

### Run integration tests

End-to-end test modules are located in the `integration-tests` folder.  
To run integration tests, activate the Maven `it-tests` profile:

```bash
mvn -DtrimStackTrace=false -Pit-tests verify
```

### Run integration tests in production mode

Execute integration tests in production mode by activating both the `it-tests` and `production` profiles:

```bash
mvn -DtrimStackTrace=false -Pit-tests,production verify
```

### Debug tests

Run tests in debug mode and attach your IDE debugger to port `5005`:

```bash
mvn -DtrimStackTrace=false -Dmaven.surefire.debug -Pit-tests verify
```

> [!IMPORTANT]
> Integration tests use [Selenide](https://selenide.org/) for browser interaction (Chrome by default, Safari on macOS). Tests run in headless mode unless a debugger is attached via IDE or `-Dmaven.surefire.debug` flag.
