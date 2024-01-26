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
import jakarta.persistence.EntityManager;
import java.util.List;

import com.vaadin.hilla.crud.filter.AndFilter;
import com.vaadin.hilla.crud.filter.OrFilter;
import com.vaadin.hilla.crud.filter.PropertyStringFilter;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.TestTransaction;
import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.groups.Tuple.tuple;

@TestTransaction
class ListRepositoryServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-hibernate-orm-panache", Version.getVersion()),
                    Dependency.of("io.quarkus", "quarkus-jdbc-h2", Version.getVersion())))
            .withConfigurationResource(testResource("application.properties"))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(testResource("import.sql"), "import.sql")
                    .addClasses(TestEntity.class, TestRepository.class, TestListRepositoryService.class));

    @Inject
    TestListRepositoryService service;

    @Inject
    EntityManager entityManager;

    @Test
    void listService_count_nullFilter() {
        Assertions.assertThat(service.count(null)).isEqualTo(5);
    }

    @Test
    void listService_count_equalsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("Two");
        filter.setMatcher(PropertyStringFilter.Matcher.EQUALS);
        Assertions.assertThat(service.count(filter)).isEqualTo(1);
    }

    @Test
    void listService_count_containsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("o");
        filter.setMatcher(PropertyStringFilter.Matcher.CONTAINS);
        Assertions.assertThat(service.count(filter)).isEqualTo(3);
    }

    @Test
    void listService_count_caseInsensitiveEqualsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("TWO");
        filter.setMatcher(PropertyStringFilter.Matcher.EQUALS);
        Assertions.assertThat(service.count(filter)).isEqualTo(1);

        filter.setFilterValue("two");
        Assertions.assertThat(service.count(filter)).isEqualTo(1);
    }

    @Test
    void listService_count_greaterThanFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("number");
        filter.setFilterValue("2");
        filter.setMatcher(PropertyStringFilter.Matcher.GREATER_THAN);
        Assertions.assertThat(service.count(filter)).isEqualTo(3);
    }

    @Test
    void listService_count_lessThanFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("number");
        filter.setFilterValue("3");
        filter.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);
        Assertions.assertThat(service.count(filter)).isEqualTo(2);
    }

    @Test
    void listService_count_orFilter() {
        PropertyStringFilter condition1 = new PropertyStringFilter();
        condition1.setPropertyId("number");
        condition1.setFilterValue("3");
        condition1.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);

        PropertyStringFilter condition2 = new PropertyStringFilter();
        condition2.setPropertyId("text");
        condition2.setFilterValue("five");
        condition2.setMatcher(PropertyStringFilter.Matcher.EQUALS);

        OrFilter filter = new OrFilter();
        filter.setChildren(List.of(condition1, condition2));

        Assertions.assertThat(service.count(filter)).isEqualTo(3);
    }

    @Test
    void listService_count_andFilter() {
        PropertyStringFilter condition1 = new PropertyStringFilter();
        condition1.setPropertyId("number");
        condition1.setFilterValue("4");
        condition1.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);

        PropertyStringFilter condition2 = new PropertyStringFilter();
        condition2.setPropertyId("text");
        condition2.setFilterValue("e");
        condition2.setMatcher(PropertyStringFilter.Matcher.CONTAINS);

        AndFilter filter = new AndFilter();
        filter.setChildren(List.of(condition1, condition2));

        Assertions.assertThat(service.count(filter)).isEqualTo(2);
    }

    @Test
    void listService_list_nullFilter() {
        List<TestEntity> list = service.list(null, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(
                        tuple("One", 1), tuple("Two", 2), tuple("Three", 3), tuple("Four", 4), tuple("Five", 5));
    }

    @Test
    void listService_list_equalsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("Two");
        filter.setMatcher(PropertyStringFilter.Matcher.EQUALS);
        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("Two", 2));
    }

    @Test
    void listService_list_containsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("o");
        filter.setMatcher(PropertyStringFilter.Matcher.CONTAINS);
        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("One", 1), tuple("Two", 2), tuple("Four", 4));
    }

    @Test
    void listService_list_caseInsensitiveEqualsFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("text");
        filter.setFilterValue("TWO");
        filter.setMatcher(PropertyStringFilter.Matcher.EQUALS);
        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("Two", 2));

        filter.setFilterValue("two");
        list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("Two", 2));
    }

    @Test
    void listService_list_greaterThanFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("number");
        filter.setFilterValue("2");
        filter.setMatcher(PropertyStringFilter.Matcher.GREATER_THAN);
        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("Three", 3), tuple("Four", 4), tuple("Five", 5));
    }

    @Test
    void listService_list_lessThanFilter() {
        PropertyStringFilter filter = new PropertyStringFilter();
        filter.setPropertyId("number");
        filter.setFilterValue("3");
        filter.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);
        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("One", 1), tuple("Two", 2));
    }

    @Test
    void listService_list_orFilter() {
        PropertyStringFilter condition1 = new PropertyStringFilter();
        condition1.setPropertyId("number");
        condition1.setFilterValue("3");
        condition1.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);

        PropertyStringFilter condition2 = new PropertyStringFilter();
        condition2.setPropertyId("text");
        condition2.setFilterValue("five");
        condition2.setMatcher(PropertyStringFilter.Matcher.EQUALS);

        OrFilter filter = new OrFilter();
        filter.setChildren(List.of(condition1, condition2));

        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("One", 1), tuple("Two", 2), tuple("Five", 5));
    }

    @Test
    void listService_list_andFilter() {
        PropertyStringFilter condition1 = new PropertyStringFilter();
        condition1.setPropertyId("number");
        condition1.setFilterValue("4");
        condition1.setMatcher(PropertyStringFilter.Matcher.LESS_THAN);

        PropertyStringFilter condition2 = new PropertyStringFilter();
        condition2.setPropertyId("text");
        condition2.setFilterValue("e");
        condition2.setMatcher(PropertyStringFilter.Matcher.CONTAINS);

        AndFilter filter = new AndFilter();
        filter.setChildren(List.of(condition1, condition2));

        List<TestEntity> list = service.list(null, filter);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactlyInAnyOrder(tuple("One", 1), tuple("Three", 3));
    }

    @Test
    void listService_list_sort() {
        Pageable page = PageRequest.of(0, 10, Sort.by("number"));
        List<TestEntity> list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(
                        tuple("One", 1), tuple("Two", 2), tuple("Three", 3), tuple("Four", 4), tuple("Five", 5));

        page = PageRequest.of(0, 10, Sort.by("number").descending());
        list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(
                        tuple("Five", 5), tuple("Four", 4), tuple("Three", 3), tuple("Two", 2), tuple("One", 1));
    }

    @Test
    void listService_list_multiSort() {
        Assertions.assertThat(entityManager
                        .createNativeQuery(
                                """
                                        insert into test_table (id, text, number)
                                        (select NEXT VALUE FOR test_table_SEQ, text, number * 10 from test_table)
                                        """)
                        .executeUpdate())
                .isEqualTo(5);

        Pageable page = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("text"), Sort.Order.desc("number")));
        List<TestEntity> list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(
                        tuple("Five", 50), tuple("Five", 5),
                        tuple("Four", 40), tuple("Four", 4),
                        tuple("One", 10), tuple("One", 1),
                        tuple("Three", 30), tuple("Three", 3),
                        tuple("Two", 20), tuple("Two", 2));
    }

    @Test
    void listService_list_pagination() {
        Pageable page = PageRequest.of(0, 2, Sort.by("number"));
        List<TestEntity> list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("One", 1), tuple("Two", 2));

        page = page.next();
        list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("Three", 3), tuple("Four", 4));

        page = page.next();
        list = service.list(page, null);
        Assertions.assertThat(list)
                .extracting(TestEntity::getText, TestEntity::getNumber)
                .containsExactly(tuple("Five", 5));

        page = page.next();
        list = service.list(page, null);
        Assertions.assertThat(list).isEmpty();
    }

    private static String testResource(String name) {
        return ListRepositoryServiceTest.class.getPackageName().replace('.', '/') + '/' + name;
    }
}
