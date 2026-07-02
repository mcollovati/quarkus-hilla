///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.maven.shared:maven-invoker:3.3.0
//DEPS info.picocli:picocli:4.6.3
//JAVA 21

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "UpdateProjectVersion", mixinStandardHelpOptions = true, version = "1.0",
        description = "Updates project version across POM, GitHub workflows, dependabot config and README")
class UpdateProjectVersion implements Runnable {

    @Parameters(index = "0", description = "Project folder path")
    private File projectFolder;

    @Parameters(index = "1", description = "New version in MAJOR.MINOR format")
    private String newVersion;

    @Option(names = {"-m", "--maven-home"}, description = "Maven HOME path")
    private Path mavenHome;

    @Option(names = {"-y", "--yes"}, description = "Do not prompt for confirmation")
    private boolean assumeYes;

    @Option(names = {"-n", "--dry-run"}, description = "Print actions without writing files")
    private boolean dryRun;

    @Option(names = "--skip-readme",
            description = "Do not update README.md (Compatibility Matrix and Quick Start examples)")
    private boolean skipReadme;

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+$");
    private static final Pattern REVISION_PATTERN = Pattern.compile("<revision>(.*?)-SNAPSHOT</revision>");
    private static final Pattern HILLA_VERSION_PATTERN = Pattern.compile("<hilla\\.version>(.*?)-SNAPSHOT</hilla\\.version>");
    private static final Pattern README_QUICK_START_PATTERN = Pattern.compile("<version>\\d+\\.\\d+\\.x</version>");
    private static final Pattern README_MATRIX_TOP_ROW_PATTERN = Pattern.compile(
            "(?m)^\\| <picture><img alt=\"Maven Central (\\d+\\.\\d+\\.\\d+)\"[^>]*></picture> \\| "
                    + "(<picture><img alt=\"Quarkus[^\"]*\"[^>]*></picture>) \\|.*$");

    public static void main(String... args) {
        int exitCode = new CommandLine(new UpdateProjectVersion()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            validate();

            Path pomFile = projectFolder.toPath().resolve("pom.xml");
            String currentVersion = extractVersion(pomFile, REVISION_PATTERN, "revision");
            String hillaVersion = extractVersion(pomFile, HILLA_VERSION_PATTERN, "hilla.version");

            if (currentVersion.equals(newVersion)) {
                throw new IllegalArgumentException(
                        "Project is already at version " + newVersion + "-SNAPSHOT — nothing to do");
            }

            System.out.println("Project version " + currentVersion + ", Vaadin version " + hillaVersion);
            System.out.println("Updating project and Vaadin to version " + newVersion + (dryRun ? " (DRY RUN)" : "") + "?");
            if (!assumeYes) {
                System.out.println("Press ENTER to continue or CTRL+C to cancel");
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            }

            Path resolvedMavenHome = dryRun ? mavenHome : resolveMavenHome();
            updateMavenProperty(resolvedMavenHome, "revision", newVersion + "-SNAPSHOT");
            updateMavenProperty(resolvedMavenHome, "hilla.version", newVersion + "-SNAPSHOT");

            Path workflows = projectFolder.toPath().resolve(".github/workflows");
            patch(workflows.resolve("release.yaml"),
                    c -> insertOptionAfterMain(c, currentVersion));
            patch(workflows.resolve("update-npm-deps.yaml"),
                    c -> insertInRunScript(insertOptionAfterMain(c, currentVersion), currentVersion));
            patch(workflows.resolve("validation.yaml"),
                    c -> insertFlowArrayItemAfterMain(c, "branches", currentVersion));
            patch(workflows.resolve("validation-nightly.yaml"),
                    c -> insertFlowArrayItemAfterMain(c, "branch", currentVersion));

            patch(projectFolder.toPath().resolve(".github/dependabot.yml"),
                    c -> insertDependabotEntry(c, currentVersion));

            if (skipReadme) {
                System.out.println("Remember to manually update README.md");
            } else {
                patch(projectFolder.toPath().resolve("README.md"),
                        c -> updateReadmeContent(c, currentVersion, newVersion));
            }

            System.out.println("Upgrade completed");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void validate() {
        if (!projectFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid project folder: " + projectFolder);
        }

        Path pomFile = projectFolder.toPath().resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IllegalArgumentException("POM file not found in project folder: " + projectFolder);
        }

        if (!VERSION_PATTERN.matcher(newVersion).matches()) {
            throw new IllegalArgumentException("Invalid new version " + newVersion + ". Must be in format MAJOR.MINOR");
        }

        if (mavenHome != null && !Files.isDirectory(mavenHome)) {
            throw new IllegalArgumentException("Maven HOME is not an existing directory: " + mavenHome);
        }
    }

    private Path resolveMavenHome() {
        if (mavenHome != null) {
            return mavenHome;
        }
        for (String envName : new String[] {"MAVEN_HOME", "M2_HOME"}) {
            String value = System.getenv(envName);
            if (value != null) {
                Path candidate = Paths.get(value);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("windows");
            String[] execs = windows ? new String[] {"mvn.cmd", "mvn.bat"} : new String[] {"mvn"};
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (dir.isEmpty()) {
                    continue;
                }
                for (String exe : execs) {
                    Path candidate = Paths.get(dir, exe);
                    if (!Files.isExecutable(candidate)) {
                        continue;
                    }
                    try {
                        Path bin = candidate.toRealPath().getParent();
                        if (bin == null) {
                            continue;
                        }
                        Path home = bin.getParent();
                        if (home != null && Files.isDirectory(home)) {
                            return home;
                        }
                    } catch (IOException ignored) {
                        // try next candidate
                    }
                }
            }
        }
        throw new IllegalArgumentException(
                "Cannot detect a Maven installation. "
                        + "Specify --maven-home <path> or set the MAVEN_HOME environment variable.");
    }

    private String extractVersion(Path pomFile, Pattern pattern, String propertyName) throws IOException {
        String content = Files.readString(pomFile);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("Cannot read " + propertyName + " from " + pomFile);
        }
        return matcher.group(1);
    }

    private void updateMavenProperty(Path resolvedMavenHome, String property, String value) throws MavenInvocationException {
        if (dryRun) {
            System.out.println("[dry-run] would set Maven property " + property + "=" + value);
            return;
        }
        System.out.println(". Updating " + property + " property");

        InvocationRequest request = new DefaultInvocationRequest();
        request.setMavenHome(resolvedMavenHome.toFile());
        request.setBatchMode(true);
        request.setNoTransferProgress(true);
        request.setQuiet(true);
        request.setBaseDirectory(projectFolder);
        request.addArgs(Arrays.asList(
                "-N",
                "versions:set-property",
                "-Dproperty=" + property,
                "-DnewVersion=" + value
        ));

        Invoker invoker = new DefaultInvoker();
        InvocationResult result = invoker.execute(request);

        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Maven command failed with exit code " + result.getExitCode());
        }

        System.out.println(".. OK");
    }

    private void patch(Path file, UnaryOperator<String> transform) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        Path relative = projectFolder.toPath().relativize(file);
        System.out.print(". Updating " + relative);
        String original = Files.readString(file);
        String updated = transform.apply(original);
        if (updated.equals(original)) {
            System.out.println(" .. nothing to do");
            return;
        }
        if (!dryRun) {
            Files.writeString(file, updated);
        }
        System.out.println(dryRun ? " .. would update" : " .. OK");
    }

    /**
     * Inserts {@code - "<version>"} immediately after {@code - main} under an {@code options:} list.
     * Used for {@code release.yaml} and {@code update-npm-deps.yaml}.
     */
    static String insertOptionAfterMain(String content, String version) {
        String marker = "          - main\n";
        String inserted = "          - \"" + version + "\"\n";
        if (content.contains(marker + inserted)) {
            return content;
        }
        if (!content.contains(marker)) {
            throw new IllegalStateException("Cannot find '- main' option entry to insert version after");
        }
        return content.replace(marker, marker + inserted);
    }

    /**
     * Replaces the literal {@code "main"} token in the {@code compute-matrix} run script with
     * {@code "main","<version>"}. Used for {@code update-npm-deps.yaml}.
     */
    static String insertInRunScript(String content, String version) {
        String token = "\"main\"";
        String replacement = "\"main\",\"" + version + "\"";
        if (content.contains(replacement)) {
            return content;
        }
        if (!content.contains(token)) {
            return content;
        }
        return content.replace(token, replacement);
    }

    /**
     * Inserts {@code "<version>"} immediately after {@code main} in a flow array of the form
     * {@code <key>: [main, ...]}. Used for {@code validation.yaml} ({@code branches}) and
     * {@code validation-nightly.yaml} ({@code branch}).
     */
    static String insertFlowArrayItemAfterMain(String content, String key, String version) {
        String marker = key + ": [main, ";
        String inserted = key + ": [main, \"" + version + "\", ";
        if (content.contains(inserted)) {
            return content;
        }
        if (!content.contains(marker)) {
            throw new IllegalStateException("Cannot find flow array '" + key + ": [main, ...]' to insert version into");
        }
        return content.replace(marker, inserted);
    }

    /**
     * Inserts a new maintenance branch entry into {@code .github/dependabot.yml}, between the
     * {@code main} updates entry (the first one) and the next one.
     */
    static String insertDependabotEntry(String content, String version) {
        if (content.contains("target-branch: \"" + version + "\"")) {
            return content;
        }
        String marker = "\n  - package-ecosystem:";
        int first = content.indexOf(marker);
        if (first < 0) {
            throw new IllegalStateException("Cannot find dependabot 'updates' entry");
        }
        int second = content.indexOf(marker, first + marker.length());
        if (second < 0) {
            throw new IllegalStateException("Expected at least two dependabot 'updates' entries");
        }
        String entry = """
                  - package-ecosystem: "maven"
                    directory: "/"
                    target-branch: "%s"
                    schedule:
                      interval: "daily"
                    ignore:
                      - dependency-name: "com.vaadin.hilla:*"
                        update-types:
                          - "version-update:semver-major"
                          - "version-update:semver-minor"
                      - dependency-name: "com.vaadin:*"
                        update-types:
                          - "version-update:semver-major"
                          - "version-update:semver-minor"
                """.formatted(version);
        return content.substring(0, second) + "\n" + entry.stripTrailing() + content.substring(second);
    }

    /**
     * Updates the SNAPSHOT row of the Compatibility Matrix, the Quick Start XML examples and
     * inserts the new release row in {@code README.md}.
     */
    static String updateReadmeContent(String content, String currentVersion, String newVersion) {
        String updated = content;
        updated = updated.replace(currentVersion + "--SNAPSHOT", newVersion + "--SNAPSHOT");
        updated = updated.replace(currentVersion + "-SNAPSHOT", newVersion + "-SNAPSHOT");
        updated = updated.replace("Vaadin " + currentVersion, "Vaadin " + newVersion);
        updated = updated.replace("VAADIN-v" + currentVersion, "VAADIN-v" + newVersion);
        updated = README_QUICK_START_PATTERN.matcher(updated)
                .replaceAll("<version>" + currentVersion + ".x</version>");
        updated = insertCompatibilityMatrixEntry(updated, currentVersion);
        return updated;
    }

    /**
     * Inserts a new entry at the top of the Compatibility Matrix for {@code <version>.0} (assuming
     * the matured minor's first release is the {@code .0} patch), cloning the Quarkus badge from
     * the existing top row.
     *
     * <p>This clone is a starting point only, not a verified value: Vaadin's Vaadin Quarkus
     * extension has changed its minimum Quarkus version between patch releases before (e.g.
     * Vaadin 25.0.9 raised it from 3.27 to 3.32 for a Jackson update), without a corresponding
     * Quarkus-Hilla minor bump. A warning is printed so this has to be confirmed manually — see
     * the manual follow-up steps in {@code docs/bump-project-version.md}.
     */
    static String insertCompatibilityMatrixEntry(String content, String version) {
        String firstPatch = version + ".0";
        if (content.contains("Maven Central " + firstPatch + "\"")) {
            return content;
        }
        Matcher m = README_MATRIX_TOP_ROW_PATTERN.matcher(content);
        if (!m.find()) {
            throw new IllegalStateException("Cannot find Compatibility Matrix table top row");
        }
        String quarkusBadge = m.group(2);
        int lineStart = m.start();
        String newLine = "| <picture><img alt=\"Maven Central " + firstPatch + "\" "
                + "src=\"https://img.shields.io/maven-central/v/com.github.mcollovati/quarkus-hilla"
                + "?style=for-the-badge&logo=apache-maven&versionPrefix=" + firstPatch + "\"></picture> | "
                + quarkusBadge + " | <picture><img alt=\"Vaadin " + version + "\" "
                + "src=\"https://img.shields.io/badge/VAADIN-v" + version + "-blue?style=for-the-badge&logo=Vaadin\">"
                + "</picture> |";
        System.out.println(
                "! Cloned the Quarkus baseline for " + firstPatch + " from the previous row — "
                        + "verify it manually, see docs/bump-project-version.md");
        return content.substring(0, lineStart) + newLine + "\n" + content.substring(lineStart);
    }
}
