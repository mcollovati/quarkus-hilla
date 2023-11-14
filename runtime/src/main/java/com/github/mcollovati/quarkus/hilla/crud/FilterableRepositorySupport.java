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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;

import dev.hilla.crud.filter.AndFilter;
import dev.hilla.crud.filter.Filter;
import dev.hilla.crud.filter.OrFilter;
import dev.hilla.crud.filter.PropertyStringFilter;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static com.github.mcollovati.quarkus.hilla.crud.PropertyStringFilterSpecification.getPath;

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
        return entityManager.createQuery(countQuery).getSingleResult();
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
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        if (pageable != null && pageable.isPaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        return typedQuery.getResultList();
    }

    public static <T, ID> boolean isNew(T value, Class<T> entity, Class<ID> idType) {
        Objects.requireNonNull(value, "Cannot determine ID for null value");
        EntityManager entityManager = JpaOperations.INSTANCE.getEntityManager(entity);
        SingularAttribute<? super T, ID> id =
                entityManager.getMetamodel().entity(entity).getId(idType);
        Member member = id.getJavaMember();
        Object idValue;
        try {
            if (member instanceof Field f) {
                if (Modifier.isPublic(f.getModifiers())) {
                    idValue = f.get(value);
                } else {
                    String getter = getGetterName(f.getName(), f.getType().isPrimitive());
                    member = entity.getMethod(getter);
                }
            }
            if (member instanceof Method m) {
                idValue = m.invoke(value);
            } else {
                throw new IllegalStateException(
                        "Cannot determine ID value from " + id.getName() + " for entity of type " + entity);
            }
            return idValue != null;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot determine ID value from " + id.getName() + " for entity of type " + entity, e);
        }
    }

    private static <T> Predicate toPredicate(Filter rawFilter, Class<T> entity, CriteriaBuilder builder, Root<T> root) {
        if (rawFilter == null) {
            return null;
        }
        if (rawFilter instanceof AndFilter filter) {
            return builder.and(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, root))
                    .toArray(Predicate[]::new));
        } else if (rawFilter instanceof OrFilter filter) {
            return builder.or(filter.getChildren().stream()
                    .map(f -> toPredicate(f, entity, builder, root))
                    .toArray(Predicate[]::new));
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
                    .getEntityManager()
                    .getMetamodel()
                    .entity(entity)
                    .getAttribute(propertyId)
                    .getJavaType();
        }
    }

    private static String getGetterName(String name, boolean isPrimitiveBoolean) {
        String prefix = isPrimitiveBoolean ? "is" : "get";
        return prefix + capitalize(name);
    }

    public static String capitalize(String name) {
        if (name != null && !name.isEmpty()) {
            if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
                return name;
            } else {
                char[] chars = name.toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                return new String(chars);
            }
        } else {
            return name;
        }
    }
}
