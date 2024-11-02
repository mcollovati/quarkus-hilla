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
package com.github.mcollovati.quarkus.hilla.deployment.crud.panache;

import jakarta.inject.Inject;
import java.util.List;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GeneratedIsNewTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-hibernate-orm-panache", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-jdbc-h2", Version.getVersion())))
            .withConfigurationResource(testResource("application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(""), "import.sql")
                    .addClasses(
                            EntityIds.class,
                            EntityIds.GetterEntity.class,
                            GetterEntityRepository.class,
                            EntityIds.PrimitiveIdEntity.class,
                            PrimitiveIdEntityRepository.class,
                            EntityIds.StringIdEntity.class,
                            StringIdEntityRepository.class));

    @Inject
    GetterEntityRepository getterEntityRepository;

    @Inject
    PrimitiveIdEntityRepository primitiveIdEntityRepository;

    @Inject
    StringIdEntityRepository stringIdEntityRepository;

    @Test
    void isNew_getterAnnotatedId() {
        EntityIds.GetterEntity entity = new EntityIds.GetterEntity();
        Assertions.assertTrue(getterEntityRepository.isNew(entity));

        entity.setId(10L);
        Assertions.assertFalse(getterEntityRepository.isNew(entity));
    }

    @Test
    void isNew_primitiveId() {
        EntityIds.PrimitiveIdEntity entity = new EntityIds.PrimitiveIdEntity();
        Assertions.assertTrue(primitiveIdEntityRepository.isNew(entity));

        entity.setId(10L);
        Assertions.assertFalse(primitiveIdEntityRepository.isNew(entity));
    }

    @Test
    void isNew_StringId() {
        EntityIds.StringIdEntity entity = new EntityIds.StringIdEntity();
        Assertions.assertTrue(stringIdEntityRepository.isNew(entity));

        entity.setId("123-43534-6456");
        Assertions.assertFalse(stringIdEntityRepository.isNew(entity));
    }

    private static String testResource(String name) {
        return GeneratedIsNewTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
