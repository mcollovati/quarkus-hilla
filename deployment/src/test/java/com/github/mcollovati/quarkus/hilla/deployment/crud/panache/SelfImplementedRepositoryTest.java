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
import io.quarkus.test.TestTransaction;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.groups.Tuple.tuple;

@TestTransaction
class SelfImplementedRepositoryTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-hibernate-orm-panache", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-jdbc-h2", Version.getVersion())))
            .withConfigurationResource(testResource("application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(testResource("import.sql"), "import.sql")
                    .addClasses(TestEntity.class, SelfImplementedRepository.class));

    @Inject
    SelfImplementedRepository repository;

    @Test
    void count_implemented_notGeneratedAtRuntime() {
        Assertions.assertThat(repository.count(null)).isEqualTo(-99);
    }

    @Test
    void list_implemented_notGeneratedAtRuntime() {
        List<TestEntity> list = repository.list((Pageable) null, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("Fixed", -99));
    }

    @Test
    void isNew_implemented_notGeneratedAtRuntime() {
        // Test repository fake implementation returns true for null or negative ids
        // and false for zero or positive ids
        TestEntity entity = new TestEntity();
        Assertions.assertThat(repository.isNew(entity)).isTrue();

        entity.id = 10L;
        Assertions.assertThat(repository.isNew(entity)).isFalse();

        entity.id = -10L;
        Assertions.assertThat(repository.isNew(entity)).isTrue();

        entity.id = 0L;
        Assertions.assertThat(repository.isNew(entity)).isFalse();
    }

    private static String testResource(String name) {
        return SelfImplementedRepositoryTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
