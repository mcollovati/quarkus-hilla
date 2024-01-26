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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.vaadin.hilla.crud.filter.PropertyStringFilter;

/*
 * NOTE: this code has been copy/pasted from Hilla code base, credit goes to Vaadin Ltd
 * https://github.com/vaadin/hilla/blob/main/packages/java/endpoint/src/main/java/com/vaadin/hilla/crud/PropertyStringFilterSpecification.java
 *
 * Adaptation is required to remove the Spring Specification<T> interface, not supported by quarkus-spring-data extension
 */
public class PropertyStringFilterSpecification<T> {

    private final PropertyStringFilter filter;
    private final Class<?> javaType;

    public PropertyStringFilterSpecification(PropertyStringFilter filter, Class<?> javaType) {
        this.filter = filter;
        this.javaType = javaType;
    }

    public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder) {
        String value = filter.getFilterValue();
        Path<String> propertyPath = getPath(filter.getPropertyId(), root);
        if (javaType == String.class) {
            Expression<String> expr = criteriaBuilder.lower(propertyPath);
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(expr, value.toLowerCase());
                case CONTAINS:
                    return criteriaBuilder.like(expr, "%" + value.toLowerCase() + "%");
                case GREATER_THAN:
                    throw new IllegalArgumentException("A string cannot be filtered using greater than");
                case LESS_THAN:
                    throw new IllegalArgumentException("A string cannot be filtered using less than");
                default:
                    break;
            }

        } else if (isNumber(javaType)) {
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(propertyPath, value);
                case CONTAINS:
                    throw new IllegalArgumentException("A number cannot be filtered using contains");
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(propertyPath, value);
                case LESS_THAN:
                    return criteriaBuilder.lessThan(propertyPath, value);
                default:
                    break;
            }
        } else if (isBoolean(javaType)) {
            Boolean booleanValue = Boolean.valueOf(value);
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(propertyPath, booleanValue);
                case CONTAINS:
                    throw new IllegalArgumentException("A boolean cannot be filtered using contains");
                case GREATER_THAN:
                    throw new IllegalArgumentException("A boolean cannot be filtered using greater than");
                case LESS_THAN:
                    throw new IllegalArgumentException("A boolean cannot be filtered using less than");
                default:
                    break;
            }
        } else if (isDate(javaType)) {
            var path = root.<Date>get(filter.getPropertyId());
            var dateValue = Date.from(
                    LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant());
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(path, dateValue);
                case CONTAINS:
                    throw new IllegalArgumentException("A date cannot be filtered using contains");
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(path, dateValue);
                case LESS_THAN:
                    return criteriaBuilder.lessThan(path, dateValue);
                default:
                    break;
            }
        } else if (isLocalDate(javaType)) {
            var path = root.<LocalDate>get(filter.getPropertyId());
            var dateValue = LocalDate.parse(value);
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(path, dateValue);
                case CONTAINS:
                    throw new IllegalArgumentException("A date cannot be filtered using contains");
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(path, dateValue);
                case LESS_THAN:
                    return criteriaBuilder.lessThan(path, dateValue);
                default:
                    break;
            }
        } else if (isLocalTime(javaType)) {
            var path = root.<LocalTime>get(filter.getPropertyId());
            var timeValue = LocalTime.parse(value);
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.equal(path, timeValue);
                case CONTAINS:
                    throw new IllegalArgumentException("A time cannot be filtered using contains");
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(path, timeValue);
                case LESS_THAN:
                    return criteriaBuilder.lessThan(path, timeValue);
                default:
                    break;
            }
        } else if (isLocalDateTime(javaType)) {
            var path = root.<LocalDateTime>get(filter.getPropertyId());
            var dateValue = LocalDate.parse(value);
            var minValue = LocalDateTime.of(dateValue, LocalTime.MIN);
            var maxValue = LocalDateTime.of(dateValue, LocalTime.MAX);
            switch (filter.getMatcher()) {
                case EQUALS:
                    return criteriaBuilder.between(path, minValue, maxValue);
                case CONTAINS:
                    throw new IllegalArgumentException("A datetime cannot be filtered using contains");
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(path, maxValue);
                case LESS_THAN:
                    return criteriaBuilder.lessThan(path, minValue);
                default:
                    break;
            }
        }
        throw new IllegalArgumentException("No implementation for " + javaType + " using " + filter.getMatcher() + ".");
    }

    private boolean isNumber(Class<?> javaType) {
        return javaType == int.class
                || javaType == Integer.class
                || javaType == double.class
                || javaType == Double.class
                || javaType == long.class
                || javaType == Long.class;
    }

    static <T> Path<String> getPath(String propertyId, Root<T> root) {
        String[] parts = propertyId.split("\\.");
        Path<String> path = root.get(parts[0]);
        int i = 1;
        while (i < parts.length) {
            path = path.get(parts[i]);
            i++;
        }
        return path;
    }

    private boolean isBoolean(Class<?> javaType) {
        return javaType == boolean.class || javaType == Boolean.class;
    }

    private boolean isDate(Class<?> javaType) {
        return javaType == java.util.Date.class;
    }

    private boolean isLocalDate(Class<?> javaType) {
        return javaType == LocalDate.class;
    }

    private boolean isLocalTime(Class<?> javaType) {
        return javaType == LocalTime.class;
    }

    private boolean isLocalDateTime(Class<?> javaType) {
        return javaType == LocalDateTime.class;
    }
}
