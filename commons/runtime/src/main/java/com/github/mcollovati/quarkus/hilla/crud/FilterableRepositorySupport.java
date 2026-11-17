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
package com.github.mcollovati.quarkus.hilla.crud;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaSelect;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

import com.vaadin.hilla.crud.filter.AndFilter;
import com.vaadin.hilla.crud.filter.Filter;
import com.vaadin.hilla.crud.filter.OrFilter;
import com.vaadin.hilla.crud.filter.PropertyStringFilter;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static com.github.mcollovati.quarkus.hilla.crud.PropertyStringFilterSpecification.getPath;

@SuppressWarnings("unchecked")
public final class FilterableRepositorySupport {

    private FilterableRepositorySupport() {}

    public static <T> long count(Filter filter, Class<T> entityClass) {
        EntityManager entityManager = JpaOperations.INSTANCE.getEntityManager(entityClass);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<T> root = countQuery.from(entityClass);
        Predicate predicate = toPredicate(filter, entityClass, builder, root);
        countQuery.select(builder.count(root));
        if (predicate != null) {
            countQuery.where(predicate);
        }
        return entityManager.createQuery(asSelect(countQuery)).getSingleResult();
    }

    public static <T> List<T> list(Pageable pageable, Filter filter, Class<T> entityClass) {
        EntityManager entityManager = JpaOperations.INSTANCE.getEntityManager(entityClass);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        Predicate predicate = toPredicate(filter, entityClass, builder, root);
        if (predicate != null) {
            query.where(predicate);
        }
        if (pageable != null && pageable.getSortOr(Sort.unsorted()).isSorted()) {
            query.orderBy(toOrder(pageable.getSort(), builder, root));
        }
        TypedQuery<T> typedQuery = entityManager.createQuery(asSelect(query));
        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        return typedQuery.getResultList();
    }

    /**
     * Widens a criteria query to {@link CriteriaSelect}.
     * <p>
     * Jakarta Persistence 4 removed {@code EntityManager.createQuery(CriteriaQuery)}; the
     * {@code createQuery(CriteriaSelect)} overload exists in both 3.2 and 4. Calling it keeps a build against
     * Quarkus 3 working on Quarkus 4.
     */
    private static <T> CriteriaSelect<T> asSelect(CriteriaQuery<T> query) {
        return query;
    }

    private static <T> Predicate toPredicate(Filter rawFilter, Class<T> entity, CriteriaBuilder builder, Root<T> root) {
        if (rawFilter == null) {
            return null;
        }
        if (rawFilter instanceof AndFilter filter) {
            // List variant: and(Predicate...) is gone in Jakarta Persistence 4, and(List) has the same
            // erased signature in 3.2 and 4, so a build against either version runs on both
            return builder.and(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, root))
                    .toList());
        } else if (rawFilter instanceof OrFilter filter) {
            return builder.or(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, root))
                    .toList());
        } else if (rawFilter instanceof PropertyStringFilter filter) {
            Class<?> javaType = extractPropertyJavaType(entity, filter.getPropertyId(), builder);
            return new PropertyStringFilterSpecification<>(filter, javaType).toPredicate((Root) root, builder);
        } else {
            throw new IllegalArgumentException(
                    "Unknown filter type " + rawFilter.getClass().getName());
        }
    }

    private static <T> List<Order> toOrder(Sort sort, CriteriaBuilder builder, Root<T> root) {
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

    private static Class<?> extractPropertyJavaType(Class<?> entity, String propertyId, CriteriaBuilder builder) {
        if (propertyId.contains(".")) {
            String[] parts = propertyId.split("\\.");
            Root<?> root = builder.createQuery(entity).from(entity);
            Path<?> path = root.get(parts[0]);
            int i = 1;
            while (i < parts.length) {
                path = path.get(parts[i]);
                i++;
            }
            return path.getJavaType();
        } else {
            return JpaOperations.INSTANCE
                    .getEntityManager(entity)
                    .getMetamodel()
                    .entity(entity)
                    .getAttribute(propertyId)
                    .getJavaType();
        }
    }
}
