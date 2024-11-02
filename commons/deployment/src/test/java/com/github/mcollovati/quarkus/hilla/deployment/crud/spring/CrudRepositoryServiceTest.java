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
package com.github.mcollovati.quarkus.hilla.deployment.crud.spring;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

@TestTransaction
class CrudRepositoryServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-spring-data-jpa", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-jdbc-h2", Version.getVersion())))
            .withConfigurationResource(testResource("application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(testResource("import.sql"), "import.sql")
                    .addClasses(TestEntity.class, TestRepository.class, TestCrudRepositoryService.class));

    @Inject
    TestCrudRepositoryService service;

    @Inject
    EntityManager entityManager;

    @Test
    void save_newEntity_entityPersisted() {
        TestEntity newEntity = new TestEntity();
        newEntity.setText("Six");
        newEntity.setNumber(6);
        service.save(newEntity);

        Assertions.assertThat(newEntity.getId()).isNotNull();
    }

    @Test
    void save_existingEntity_entityUpdated() {
        List<TestEntity> allEntities =
                entityManager.createQuery("from TestEntity", TestEntity.class).getResultList();

        TestEntity existingEntity = allEntities.get(0);
        long id = existingEntity.getId();
        existingEntity.setText("Ten");
        existingEntity.setNumber(10);
        service.save(existingEntity);

        entityManager.flush();
        entityManager.clear();

        TestEntity fetchedEntity = entityManager.find(TestEntity.class, id);
        Assertions.assertThat(fetchedEntity)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly("Ten", 10);
    }

    @Test
    void save_detachedEntity_entityUpdated() {
        List<TestEntity> allEntities =
                entityManager.createQuery("from TestEntity", TestEntity.class).getResultList();

        TestEntity existingEntity = allEntities.get(0);
        long id = existingEntity.getId();
        entityManager.clear();

        TestEntity copy = new TestEntity();
        copy.setId(id);
        copy.setText("Ten");
        copy.setNumber(10);
        service.save(copy);

        entityManager.flush();
        entityManager.clear();

        TestEntity fetchedEntity = entityManager.find(TestEntity.class, id);
        Assertions.assertThat(fetchedEntity)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly("Ten", 10);
    }

    @Test
    void delete_entityDeleted() {
        List<TestEntity> allEntities =
                entityManager.createQuery("from TestEntity", TestEntity.class).getResultList();
        int initialSize = allEntities.size();

        TestEntity existingEntity = allEntities.get(0);
        service.delete(existingEntity.getId());

        entityManager.flush();
        entityManager.clear();
        allEntities =
                entityManager.createQuery("from TestEntity", TestEntity.class).getResultList();
        Assertions.assertThat(allEntities).hasSize(initialSize - 1).noneMatch(e -> e.getId()
                .equals(existingEntity.getId()));
    }

    private static String testResource(String name) {
        return CrudRepositoryServiceTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
