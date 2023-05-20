package com.example.application;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.jayway.awaitility.Awaitility;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(HillaCleaner.class)
class BootstrapTest {

    @HillaFrontendGenerated
    Path frontendGeneratedFolder;

    @Test
    void devMode_applicationStart_endpointsGenerated() {
        assertThat(frontendGeneratedFolder).isDirectory().exists();
        Awaitility.await().atMost(60, TimeUnit.SECONDS)
                .until(() -> assertThat(frontendGeneratedFolder)
                        .isDirectoryContaining(fileWithName("endpoints.ts"))
                        .isDirectoryContaining(fileWithName("vaadin.ts"))
                        .isDirectoryContaining(fileWithName("theme.js"))
                        .isDirectoryContaining(
                                fileWithName("UserInfoEndpoint.ts"))
                        .isDirectoryContaining(
                                fileWithName("HelloWorldEndpoint.ts"))
                        .isDirectoryRecursivelyContaining(
                                "glob:**/entities/User*.ts"));
    }

    private static Predicate<Path> fileWithName(String expectedFileName) {
        return path -> path.getFileName().toString().equals(expectedFileName);
    }
}
