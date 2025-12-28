///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.maven.shared:maven-invoker:3.3.0
//DEPS info.picocli:picocli:4.6.3
//DEPS tools.jackson.core:jackson-databind:3.0.3
//DEPS tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3
//JAVA 21

import org.apache.maven.shared.invoker.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLFactoryBuilder;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "UpdateProjectVersion", mixinStandardHelpOptions = true, version = "1.0",
        description = "Updates project version across POM and workflow files")
class UpdateProjectVersion implements Runnable {

    @Parameters(index = "0", description = "Project folder path")
    private File projectFolder;

    @Parameters(index = "1", description = "New version in MAJOR.MINOR format")
    private String newVersion;

    @CommandLine.Option(names = {"-m", "--maven-home"}, description = "Maven HOME path")
    private Path mavenHome;

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+$");
    private static final Pattern REVISION_PATTERN = Pattern.compile("<revision>(.*?)-SNAPSHOT</revision>");
    private static final Pattern HILLA_VERSION_PATTERN = Pattern.compile("<hilla\\.version>(.*?)-SNAPSHOT</hilla\\.version>");

    public static void main(String... args) {
        int exitCode = new CommandLine(new UpdateProjectVersion()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            // Validation
            validate();

            // Read current versions
            Path pomFile = projectFolder.toPath().resolve("pom.xml");
            String currentVersion = extractVersion(pomFile, REVISION_PATTERN, "revision");
            String vaadinVersion = extractVersion(pomFile, HILLA_VERSION_PATTERN, "hilla.version");

            System.out.println("Project version " + currentVersion + ", Vaadin version " + vaadinVersion);
            System.out.println("Updating project and Vaadin to version " + newVersion + "?");
            System.out.println("Press ENTER to continue or CTRL+C to cancel");
            new BufferedReader(new InputStreamReader(System.in)).readLine();

            // Update Maven properties
            // TODO: Temporarily commented out for testing
            // updateMavenProperty("revision", newVersion + "-SNAPSHOT");
            // updateMavenProperty("hilla.version", newVersion + "-SNAPSHOT");
            System.out.println("(Maven property updates commented out for testing)");

            // Update workflow files
            Path workflowsFolder = projectFolder.toPath().resolve(".github/workflows");
            updateWorkflowFiles(workflowsFolder, currentVersion);

            // Update dependabot file
            Path dependabotFile = projectFolder.toPath().resolve(".github/dependabot.yml");
            if (Files.exists(dependabotFile)) {
                updateDependabotFile(dependabotFile, currentVersion);
            }

            System.out.println("Upgrade completed");
            System.out.println("Remember to manually update README.md");

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

    private String extractVersion(Path pomFile, Pattern pattern, String propertyName) throws IOException {
        String content = Files.readString(pomFile);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Cannot read " + propertyName + " from " + pomFile);
        }
        return matcher.group(1);
    }

    private void updateMavenProperty(String property, String value) throws MavenInvocationException {
        System.out.println(". Updating " + property + " property");

        InvocationRequest request = new DefaultInvocationRequest();
        if (mavenHome != null) {
            request.setMavenHome(mavenHome.toFile());
        }
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
            throw new RuntimeException("Maven command failed with exit code " + result.getExitCode());
        }

        System.out.println(".. OK");
    }

    private void updateWorkflowFiles(Path workflowsFolder, String currentVersion) throws IOException {
        Consumer<ArrayNode> versionAppender = array -> array.insert(1, currentVersion);

        updateYamlFile(workflowsFolder.resolve("release.yaml"), yaml -> {
            System.out.println(". Updating release.yaml workflow");
            updateArray(yaml, "/on/workflow_dispatch/inputs/target-branch/options", versionAppender);
            System.out.println(".. OK");
        });

        updateYamlFile(workflowsFolder.resolve("update-npm-deps.yaml"), yaml -> {
            System.out.println(". Updating update-npm-deps.yaml workflow");
            updateArray(yaml, "/on/workflow_dispatch/inputs/target-branch/options", versionAppender);
            updateObject(yaml, "/jobs/compute-matrix/steps/0", node -> node.put("run",
                    node.get("run").stringValue().replace("\"main\"", "\"main\",\"" + currentVersion + "\"")));
            System.out.println(".. OK");
        });

        updateYamlFile(workflowsFolder.resolve("validation.yaml"), yaml -> {
            System.out.println(". Updating validation.yaml workflow");
            updateArray(yaml, "/on/push/branches", versionAppender);
            System.out.println(".. OK");
        });

        updateYamlFile(workflowsFolder.resolve("validation-nightly.yaml"), yaml -> {
            System.out.println(". Updating validation-nightly.yaml workflow");
            updateArray(yaml, "/jobs/snapshot-main/strategy/matrix/branch", versionAppender);
            System.out.println(".. OK");
        });
    }

    private void updateObject(JsonNode root,  String path, Consumer<ObjectNode> updater) {
        JsonNode node = findNode(root, path);
        if (!node.isObject()) {
            throw  new IllegalArgumentException("Element at " + path + " is not a object");
        }
        updater.accept((ObjectNode) node);
    }

    private void updateArray(JsonNode root, String path, Consumer<ArrayNode> updater) {
        JsonNode node = findNode(root, path);
        if (!node.isArray()) {
            throw new IllegalArgumentException("Element at " + path + " is not an array");
        }
        updater.accept((ArrayNode) node);
    }

    private static JsonNode findNode(JsonNode root, String path) {
        JsonNode node = root.at(path);
        if (node.isMissingNode()) {
            throw new IllegalArgumentException("Element at " + path + " not found");
        }
        return node;
    }

    private void updateDependabotFile(Path dependabotFile, String currentVersion) throws IOException {
        System.out.println(". Updating dependabot.yml");

        updateYamlFile(dependabotFile, yaml -> {
            ObjectNode newEntry = yaml.withArray("updates").insertObject(1);

            newEntry.put("package-ecosystem", "maven");
            newEntry.put("directory", "/");
            newEntry.put("target-branch", currentVersion);
            newEntry.putObject("schedule").put("interval", "daily");

            ArrayNode ignore = newEntry.putArray("ignore");
            ignore.addObject()
                    .put("dependency-name", "com.vaadin.hilla:*")
                    .putArray("update-types").add("version-update:semver-major").add("version-update:semver-minor");

            ignore.addObject()
                    .put("dependency-name", "com.vaadin:*")
                    .putArray("update-types").add("version-update:semver-major").add("version-update:semver-minor");
        });

        System.out.println(".. OK");
    }

    private void updateYamlFile(Path yamlFile, YamlUpdater updater) throws IOException {
        if (!Files.exists(yamlFile)) {
            return; // Skip if file doesn't exist
        }

        YAMLFactory yamlFactory = new YAMLFactoryBuilder(new YAMLFactory())
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();
        ObjectMapper objectMapper = new ObjectMapper(yamlFactory);
        JsonNode root = objectMapper.readTree(yamlFile);

        // Apply updates using text-based manipulation
        updater.update(root);

        // Write back
        objectMapper.writer().writeValue(yamlFile, root);
    }

    @FunctionalInterface
    interface YamlUpdater {
        void update(JsonNode root) throws IOException;
    }

    private void prependToYamlArray(List<String> lines, List<String> values, String... path) {
        int arrayLine = findYamlPath(lines, path);
        int indent = getIndentLevel(lines.get(arrayLine));
        int elementIndent = indent + 2;

        // Find first element line (right after the key line)
        int insertLine = arrayLine + 1;

        // Skip "main" if it's already the first element
        if (insertLine < lines.size() && lines.get(insertLine).trim().equals("- main")) {
            insertLine++;
        }

        // Insert new values
        for (int i = values.size() - 1; i >= 0; i--) {
            String value = values.get(i);
            String line = " ".repeat(elementIndent) + "- " + (value.contains(" ") || value.matches("\\d+\\.\\d+") ? "\"" + value + "\"" : value);
            lines.add(insertLine, line);
        }
    }

    private void replaceInYamlString(List<String> lines, String find, String replace, String... path) {
        int startLine = findYamlPath(lines, path);

        // Check if it's a multiline string (|)
        if (lines.get(startLine).contains(": |")) {
            int indent = getIndentLevel(lines.get(startLine));
            // Replace in all continuation lines
            for (int i = startLine + 1; i < lines.size(); i++) {
                int lineIndent = getIndentLevel(lines.get(i));
                if (lineIndent <= indent && !lines.get(i).trim().isEmpty()) {
                    break; // End of multiline block
                }
                String line = lines.get(i);
                if (line.contains(find)) {
                    lines.set(i, line.replace(find, replace));
                }
            }
        } else {
            // Single-line string
            String line = lines.get(startLine);
            if (line.contains(find)) {
                lines.set(startLine, line.replace(find, replace));
            }
        }
    }

    private void insertIntoYamlArray(List<String> lines, int position, Map<String, Object> entry, String... path) {
        int arrayLine = findYamlPath(lines, path);
        int indent = getIndentLevel(lines.get(arrayLine));
        int elementIndent = indent + 2;

        // Find insertion point
        int insertLine = arrayLine + 1;
        int currentPos = 0;
        while (currentPos < position && insertLine < lines.size()) {
            String line = lines.get(insertLine).trim();
            if (line.startsWith("- ")) {
                currentPos++;
                if (currentPos < position) {
                    // Skip to next array element
                    insertLine++;
                    int entryIndent = elementIndent + 2;
                    while (insertLine < lines.size()) {
                        int nextIndent = getIndentLevel(lines.get(insertLine));
                        if (nextIndent < entryIndent && !lines.get(insertLine).trim().isEmpty()) {
                            break;
                        }
                        insertLine++;
                    }
                }
            } else {
                insertLine++;
            }
        }

        // Insert new entry as YAML
        List<String> newLines = convertMapToYamlLines(entry, elementIndent);
        for (int i = newLines.size() - 1; i >= 0; i--) {
            lines.add(insertLine, newLines.get(i));
        }
    }

    private int findYamlPath(List<String> lines, String... pathParts) {
        int currentLine = 0;
        int currentIndent = -1;

        for (String part : pathParts) {
            if (part.startsWith("[") && part.endsWith("]")) {
                // Array index - not implemented for simplicity (not needed in our use case)
                throw new UnsupportedOperationException("Array index navigation not implemented");
            } else {
                // Map key - find "key:" at current or deeper indent
                for (int i = currentLine; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int indent = getIndentLevel(line);

                    // Skip if indentation is less than expected (backtracked to parent)
                    if (currentIndent >= 0 && indent < currentIndent) {
                        continue;
                    }

                    String trimmed = line.trim();
                    if (trimmed.startsWith(part + ":")) {
                        currentLine = i;
                        currentIndent = indent;
                        break;
                    }
                }
            }
        }

        return currentLine;
    }

    private int getIndentLevel(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private List<String> convertMapToYamlLines(Map<String, Object> map, int baseIndent) {
        List<String> lines = new ArrayList<>();
        String indent = " ".repeat(baseIndent);

        // First line is the array indicator
        boolean isFirst = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isFirst) {
                lines.add(indent + "- " + key + ": " + formatValue(value, baseIndent + 2));
                isFirst = false;
            } else {
                if (value instanceof Map || value instanceof List) {
                    lines.add(indent + "  " + key + ":");
                    lines.addAll(formatComplexValue(value, baseIndent + 4));
                } else {
                    lines.add(indent + "  " + key + ": " + formatValue(value, baseIndent + 4));
                }
            }
        }

        return lines;
    }

    private String formatValue(Object value, int indent) {
        if (value instanceof String) {
            String str = (String) value;
            if (str.contains(" ") || str.contains(":")) {
                return "\"" + str + "\"";
            }
            return str;
        }
        return String.valueOf(value);
    }

    private List<String> formatComplexValue(Object value, int indent) {
        List<String> lines = new ArrayList<>();
        String indentStr = " ".repeat(indent);

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    lines.add(indentStr + "- " + formatValue(item, indent));
                } else {
                    lines.add(indentStr + "- " + item);
                }
            }
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                lines.add(indentStr + entry.getKey() + ": " + formatValue(entry.getValue(), indent + 2));
            }
        }

        return lines;
    }

}
