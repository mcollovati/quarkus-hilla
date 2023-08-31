/*
 * Copyright 2023 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.testing;

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
        frontendGenerated = Paths.get(System.getProperty("user.dir")).resolve(Paths.get("frontend", "generated"));
        if (Files.isDirectory(frontendGenerated)) {
            try (Stream<Path> paths = Files.walk(frontendGenerated)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                Assertions.fail("Cannot delete frontend generated folder", e);
            }
        }
        Assertions.assertFalse(Files.exists(frontendGenerated), "Frontend generated folder was not deleted");
        return new HashMap<>();
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(
                frontendGenerated, new TestInjector.AnnotatedAndMatchesType(HillaFrontendGenerated.class, Path.class));
    }

    @Override
    public void stop() {}
}
