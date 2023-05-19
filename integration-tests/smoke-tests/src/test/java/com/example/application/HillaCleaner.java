package com.example.application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.junit.jupiter.api.Assertions;

public class HillaCleaner implements QuarkusTestResourceLifecycleManager {

    private Path frontendGenerated;

    @Override
    public Map<String, String> start() {
        frontendGenerated = Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get("frontend", "generated"));
        if (Files.isDirectory(frontendGenerated)) {
            try (Stream<Path> paths = Files.walk(frontendGenerated)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                Assertions.fail("Cannot delete frontend generated folder", e);
            }
        }
        Assertions.assertFalse(Files.exists(frontendGenerated),
                "Frontend generated folder was not deleted");
        return new HashMap<>();
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(frontendGenerated,
                new TestInjector.AnnotatedAndMatchesType(
                        HillaFrontendGenerated.class, Path.class));
    }

    @Override
    public void stop() {

    }
}
