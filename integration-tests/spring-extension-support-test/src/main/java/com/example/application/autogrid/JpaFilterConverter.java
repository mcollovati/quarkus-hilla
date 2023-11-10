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
package com.example.application.autogrid;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

import dev.hilla.crud.filter.AndFilter;
import dev.hilla.crud.filter.Filter;
import dev.hilla.crud.filter.OrFilter;
import dev.hilla.crud.filter.PropertyStringFilter;
import org.springframework.data.domain.Sort;

import static com.example.application.autogrid.PropertyStringFilterSpecification.getPath;

/*
 * NOTE: this code has been adapted from Hilla code base, credit goes to Vaadin Ltd.
 * https://github.com/vaadin/hilla/blob/main/packages/java/endpoint/src/main/java/dev/hilla/crud/PropertyStringFilterSpecification.java
 *
 * Adaptation is required to remove the Spring Specification<T> interface, not supported by quarkus-spring-data extension
 */

@ApplicationScoped
public class JpaFilterConverter {

    private final EntityManager em;

    public JpaFilterConverter(EntityManager em) {
        this.em = em;
    }

    /**
     * Converts the given Hilla filter specification into a JPA filter
     * specification.
     *
     * @param <T>
     *            the type of the entity
     * @param rawFilter
     *            the filter to convert
     * @param entity
     *            the entity class
     * @return a JPA filter specification for the given filter
     */
    public <T> Predicate toPredicate(
            Filter rawFilter, Class<T> entity, CriteriaBuilder builder, CriteriaQuery<T> query, Root<T> root) {
        if (rawFilter == null) {
            return null;
        }
        if (rawFilter instanceof AndFilter filter) {
            return builder.and(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, query, root))
                    .toArray(Predicate[]::new));
        } else if (rawFilter instanceof OrFilter filter) {
            return builder.or(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, query, root))
                    .toArray(Predicate[]::new));
        } else if (rawFilter instanceof PropertyStringFilter filter) {
            Class<?> javaType = extractPropertyJavaType(entity, filter.getPropertyId());
            return new PropertyStringFilterSpecification<>(filter, javaType).toPredicate((Root) root, query, builder);
        } else {
            throw new IllegalArgumentException(
                    "Unknown filter type " + rawFilter.getClass().getName());
        }
    }

    public <T> List<Order> toOrder(Sort sort, CriteriaBuilder builder, Root<T> root) {
        return sort.stream()
                .map(order -> {
                    Expression<String> expr = getPath(order.getProperty(), root);
                    if (order.isIgnoreCase()) {
                        expr = builder.lower(expr);
                    }
                    return (order.isAscending()) ? builder.asc(expr) : builder.desc(expr);
                })
                .toList();
    }

    private Class<?> extractPropertyJavaType(Class<?> entity, String propertyId) {
        if (propertyId.contains(".")) {
            String[] parts = propertyId.split("\\.");
            Root<?> root = em.getCriteriaBuilder().createQuery(entity).from(entity);
            Path<?> path = root.get(parts[0]);
            int i = 1;
            while (i < parts.length) {
                path = path.get(parts[i]);
                i++;
            }
            return path.getJavaType();
        } else {
            return em.getMetamodel().entity(entity).getAttribute(propertyId).getJavaType();
        }
    }
}
