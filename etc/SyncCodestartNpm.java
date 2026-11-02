///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.google.code.gson:gson:2.11.0
//DEPS info.picocli:picocli:4.6.3
//JAVA 21

/*
 * Copyright 2026 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "SyncCodestartNpm", mixinStandardHelpOptions = true,
        description = "Regenerates codestart NPM files with Vaadin in projects created from the installed extension")
class SyncCodestartNpm implements Callable<Integer> {

    private static final List<String> NPM_FILES = List.of("package.json", "package-lock.json");
    private static final String CODESTART_BASE =
            "/runtime/src/main/codestarts/quarkus/quakus-hilla-maven-codestart/base";

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Repository folder path")
    private Path projectFolder;

    @Option(names = "--maven", defaultValue = "mvn", description = "Maven executable (defaults to mvn on PATH)")
    private String maven;

    public static void main(String... args) {
        System.exit(new CommandLine(new SyncCodestartNpm()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        projectFolder = projectFolder.toAbsolutePath().normalize();
        for (String framework : List.of("lit", "react")) {
            for (String file : NPM_FILES) {
                requireFile(template(framework).resolve(file));
            }
        }
        Path target = projectFolder.resolve("target");
        Files.createDirectories(target);
        // A fresh directory on every run; preserve generated projects and logs for inspection.
        Path work = Files.createTempDirectory(target, "codestart-npm-");
        System.out.println("Generating codestart NPM files in " + work);
        String extensionVersion = evaluate(work, "project.version");
        String quarkusVersion = evaluate(work, "quarkus.version");
        String vaadinVersion = evaluate(work, "vaadin.version");

        for (String framework : List.of("lit", "react")) {
            generate(work, framework, extensionVersion, quarkusVersion, vaadinVersion);
        }
        // Do not update either template until both frontend builds and validations have succeeded.
        for (String framework : List.of("lit", "react")) {
            for (String file : NPM_FILES) {
                Path destination = template(framework).resolve(file);
                Files.copy(work.resolve(framework).resolve(file), destination, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Updated " + destination);
            }
        }
        return 0;
    }

    private void generate(Path work, String framework, String extensionVersion, String quarkusVersion,
            String vaadinVersion) throws IOException, InterruptedException {
        String artifactId = framework.equals("lit") ? "quarkus-hilla" : "quarkus-hilla-react";
        // Run outside the reactor so create does not add a module to the repository POM.
        run(work, "io.quarkus:quarkus-maven-plugin:" + quarkusVersion + ":create",
                "-DplatformGroupId=io.quarkus.platform", "-DplatformArtifactId=quarkus-bom",
                "-DplatformVersion=" + quarkusVersion, "-DquarkusRegistryClient=false",
                "-Dextensions=com.github.mcollovati:" + artifactId + ":" + extensionVersion,
                "-DprojectGroupId=org.acme", "-DprojectArtifactId=" + framework,
                "-DprojectVersion=1.0-SNAPSHOT", "-DjavaVersion=21", "-DnoBuildToolWrapper=true");
        Path generated = work.resolve(framework);
        requireFile(generated.resolve("pom.xml"));
        Path original = work.resolve(framework + "-original-npm");
        Files.createDirectory(original);
        for (String file : NPM_FILES) {
            // These files prove the extension codestart was selected. Never feed stale pins to npm.
            requireFile(generated.resolve(file));
            Files.move(generated.resolve(file), original.resolve(file));
        }
        seedApplicationDependencies(original.resolve("package.json"), generated.resolve("package.json"));
        String plugin = "com.vaadin:vaadin-maven-plugin:" + vaadinVersion;
        // Compile first so Vaadin scans the actual application classes. Explicit goals work for
        // both codestarts, even when their POM does not bind the frontend plugin (React).
        run(generated, "process-classes", plugin + ":prepare-frontend", plugin + ":build-frontend",
                "-DskipTests", "-Dvaadin.force.production.build=true", "-Dvaadin.ci.build=false",
                "-Dvaadin.pnpm.enable=false", "-Dvaadin.bun.enable=false",
                "-Dvaadin.react.enable=" + framework.equals("react"), "-Dvaadin.commercialWithBanner=true");
        validate(generated);
    }

    private static void seedApplicationDependencies(Path original, Path destination) throws IOException {
        JsonObject source = read(original);
        JsonObject seed = new JsonObject();
        seed.addProperty("name", "no-name");
        seed.addProperty("license", "UNLICENSED");
        seed.addProperty("type", "module");
        // Vaadin records its managed entries separately. Preserve application dependencies such
        // as Lit's mobx bindings and prettier, but let Flow recreate all managed pins and overrides.
        if (!source.has("vaadin") || !source.get("vaadin").isJsonObject()) {
            throw new IOException("Expected Vaadin dependency metadata in " + original);
        }
        JsonObject vaadin = source.getAsJsonObject("vaadin");
        for (String section : List.of("dependencies", "devDependencies")) {
            JsonObject application = new JsonObject();
            JsonObject managed = vaadin.has(section) ? vaadin.getAsJsonObject(section) : new JsonObject();
            if (source.has(section)) {
                for (var entry : source.getAsJsonObject(section).entrySet()) {
                    if (!managed.has(entry.getKey())) {
                        application.add(entry.getKey(), entry.getValue().deepCopy());
                    }
                }
            }
            seed.add(section, application);
        }
        Files.writeString(destination,
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(seed) + "\n");
    }

    private String evaluate(Path work, String expression) throws IOException, InterruptedException {
        Path output = work.resolve(expression + ".txt");
        run(projectFolder, "-N", "org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate",
                "-Dexpression=" + expression, "-Doutput=" + output);
        String value = Files.readString(output).trim();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) {
            throw new IOException("Cannot resolve " + expression + ": " + value);
        }
        return value;
    }

    private static void validate(Path generated) throws IOException {
        JsonObject manifest = read(generated.resolve("package.json"));
        JsonObject lock = read(generated.resolve("package-lock.json"));
        if (!manifest.has("vaadin") || !manifest.getAsJsonObject("vaadin").has("hash")) {
            throw new IOException("Vaadin did not generate dependency metadata in " + generated);
        }
        // Flow uses projectFolder in node_modules/.vaadin/vaadin.json, not in either exported file.
        // Reject local paths/links rather than silently exporting a broken standalone lockfile.
        requirePortable(manifest, generated);
        requirePortable(lock, generated);
        if (!lock.has("packages") || !lock.getAsJsonObject("packages").has("")) {
            throw new IOException("Expected npm lockfile v2/v3 with a root package in " + generated);
        }
        JsonObject root = lock.getAsJsonObject("packages").getAsJsonObject("");
        for (String section : List.of("dependencies", "devDependencies", "optionalDependencies")) {
            if (!java.util.Objects.equals(manifest.get(section), root.get(section))) {
                throw new IOException("Manifest and lockfile disagree on " + section + " in " + generated);
            }
        }
    }

    private static void requirePortable(JsonElement json, Path generated) throws IOException {
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("projectFolder") || entry.getKey().equals("link")
                        && entry.getValue().isJsonPrimitive() && entry.getValue().getAsBoolean()) {
                    throw new IOException("Project-specific NPM metadata in " + generated + ": " + entry.getKey());
                }
                requirePortable(entry.getValue(), generated);
            }
        } else if (json.isJsonArray()) {
            for (JsonElement item : json.getAsJsonArray()) {
                requirePortable(item, generated);
            }
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String value = json.getAsString();
            if (value.startsWith("file:") || value.startsWith("link:") || value.startsWith("workspace:")
                    || value.contains(generated.getParent().toString())) {
                throw new IOException("Non-portable NPM value in " + generated + ": " + value);
            }
        }
    }

    private Path template(String framework) {
        return projectFolder.resolve(framework + CODESTART_BASE);
    }

    private static JsonObject read(Path file) throws IOException {
        requireFile(file);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void requireFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Required file missing: " + file);
        }
    }

    private void run(Path directory, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(maven, "-B", "-ntp"));
        command.addAll(List.of(arguments));
        System.out.println(directory + ": " + String.join(" ", command));
        int exitCode = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start().waitFor();
        if (exitCode != 0) {
            throw new IOException("Maven failed in " + directory + " (exit " + exitCode + ")");
        }
    }
}
