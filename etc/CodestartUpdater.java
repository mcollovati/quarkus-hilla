/// usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.maven.shared:maven-invoker:3.3.0
//DEPS info.picocli:picocli:4.6.3

//JAVA 17

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "CodestartUpdater", mixinStandardHelpOptions = true, version = "1.0",
        description = "Updates Quarkus-Hilla code starts")
class CodestartUpdater implements Callable<Integer> {

    private static final Pattern ZIP_EXCLUDES = Pattern.compile("^([^/]+/)(\\..*|mvnw.*|README\\.md|LICENSE\\.md|src/main/resources/(?!META-INF/resources/).*)$");

    enum Preset {
        REACT("partial-hilla-example-views"),
        LIT("hilla");

        private final String preset;

        Preset(String preset) {
            this.preset = preset;
        }
    }

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", description = "Hilla preset for code start generation")
    private Preset preset;

    @Parameters(index = "1", description = "Base path for the codestart resources")
    private Path codestartPath;

    @Option(names = {"-p", "--pre-releases"},
            defaultValue = "false",
            description = "Use Hilla pre release.")
    private boolean preReleses;


    @Option(names = {"-m", "--maven-home"}, description = "Maven HOME path")
    private Path mavenHome;

    @Option(names = {"-v", "--verbose"},
            defaultValue = "false",
            description = "Print debug information.")
    private boolean verbose;

    public static void main(String... args) {
        int exitCode = new CommandLine(new CodestartUpdater()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(codestartPath)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Base codestart path is not an existing directory");
        }
        if (mavenHome != null && !Files.isDirectory(mavenHome)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Maven HOME is not an existing directory: " + mavenHome);
        }
        info("Updating Codestart " + codestartPath + " with " + preset + " preset");
        Path extractPath = downloadAndExtract("partial-hilla-example-views");
        updateJavaFiles(extractPath);
        updateCodestart(extractPath);
        deleteAllFiles(extractPath, null);
        info("Update completed");
        return 0;
    }

    private void deleteAllFiles(Path pathToBeDeleted, Predicate<Path> skip) throws IOException {
        if (Files.isDirectory(pathToBeDeleted)) {
            debug("Cleaning up " + pathToBeDeleted);
            Files.walkFileTree(pathToBeDeleted,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult postVisitDirectory(
                                Path dir, IOException exc) throws IOException {
                            if (Files.list(dir).toList().isEmpty()) {
                                Files.delete(dir);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs)
                                throws IOException {
                            if (skip == null || !skip.test(file)) {
                                Files.delete(file);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }
    }


    private Path downloadAndExtract(String presets) throws IOException {
        if (preReleses) {
            presets += ",partial-prerelease";
        }
        String appFolderName = "qh-codestart";
        URL url = new URL(String.format("https://start.vaadin.com/dl?preset=base,%s&projectName=%s", presets, appFolderName));
        info("Downloading template application from " + url);
        Path tempFile = Files.createTempFile(appFolderName, ".zip");
        try (var stream = url.openStream()) {
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path tempDirectory = Files.createTempDirectory(appFolderName);
        try (InputStream fis = Files.newInputStream(tempFile); ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = tempDirectory.resolve(entry.getName()).normalize(); // Prevent Zip Slip
                if (!target.startsWith(tempDirectory)) {
                    throw new IOException("Invalid zip entry: " + entry.getName()); // Protect against Zip Slip
                }
                String entryName = entry.getName();
                Matcher matcher = ZIP_EXCLUDES.matcher(entryName);
                if (matcher.matches()) {
                    debug("Ignoring zip entry: " + entryName);
                } else if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    debug("Extracting zip entry " + entryName + " to " + target);
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return tempDirectory.resolve(appFolderName);
    }

    private void updateCodestart(Path extractPath) throws IOException {
        info("Updating codestart files...");
        Path javaFolder = codestartPath.resolve("java");
        Path baseFolder = codestartPath.resolve(Path.of("base"));
        deleteAllFiles(javaFolder, null);
        deleteAllFiles(baseFolder, path ->
                path.getFileName().toString().contains(".tpl.qute"));
        Path relativeFrontendFolder = Path.of("src", "main", "frontend");
        Path relativeJavaFolder = Path.of("src", "main", "java");
        Files.walkFileTree(extractPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relativePath = extractPath.relativize(file);
                if (relativePath.startsWith(relativeJavaFolder)) {
                    debug("Copying Java file: " + relativePath + " to " + javaFolder);
                    Path target = javaFolder.resolve(relativePath);
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                } else {
                    debug("Copying Base file: " + relativePath + " to " + baseFolder);
                    Path target = baseFolder.resolve(relativePath);
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        info("Updating codestart files...");
    }

    private void updateJavaFiles(Path projectFolder) throws IOException {
        info("Updating Java files...");
        Path openrewriteRecipe = projectFolder.resolve("rewrite.yml");
        Files.writeString(openrewriteRecipe, RECIPE);
        InvocationRequest request = new DefaultInvocationRequest();
        Path pomFile = projectFolder.resolve("pom.xml");
        if (mavenHome != null) {
            request.setMavenHome(mavenHome.toFile());
        }
        request.setBatchMode(true);
        request.setNoTransferProgress(true);
        request.setPomFile(pomFile.toFile());
        request.setQuiet(!verbose);
        request.setBaseDirectory(projectFolder.toFile());
        request.addArgs(List.of(
                "org.openrewrite.maven:rewrite-maven-plugin:runNoFork",
                "-Drewrite.activeRecipes=com.github.mcollovati.quarkus.hilla.UpdateCodestart"
        ));
        DefaultInvoker invoker = new DefaultInvoker();
        InvocationResult result = null;
        try {
            result = invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new IOException(e);
        }
        int exitCode = result.getExitCode();
        if (exitCode != 0) {
            String error = "Maven invocation failed with exit code " + exitCode + ".";
            if (!verbose) {
                error += " Rerun with -v for debug information.";
            }
            throw new IOException(error);
        }
        Files.deleteIfExists(pomFile);
        Files.deleteIfExists(openrewriteRecipe);
    }

    private void info(String message) {
        System.out.println(message);
    }

    private void debug(String message) {
        if (verbose) {
            System.err.println(message);
        }
    }

    private static final String RECIPE = """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: com.github.mcollovati.quarkus.hilla.UpdateCodestart
            causesAnotherCycle: true
            recipeList:
              - org.openrewrite.java.ChangePackage:
                  oldPackageName: com.example.application
                  newPackageName: org.acme
                  recursive: true
              - org.openrewrite.java.RemoveAnnotation:
                  annotationPattern: "@org.springframework.boot.autoconfigure.SpringBootApplication"
              - org.openrewrite.java.RemoveAnnotation:
                  annotationPattern: "@org.springframework.stereotype.Service"
              - org.openrewrite.java.RemoveMethodInvocations:
                  methodPattern: "org.springframework.boot.SpringApplication *(..)"
              - org.openrewrite.text.FindAndReplace:
                  find: "(.*public class [^{]+)\\\\{.*public static void main\\\\(String\\\\[\\\\].*}\\r?\\n?$"
                  replace: "$1{\\n}"
                  regex: true
                  dotAll: true
                  filePattern: "**/Application.java"
            """;
}