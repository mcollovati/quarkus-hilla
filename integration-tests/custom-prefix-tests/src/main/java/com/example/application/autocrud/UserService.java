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
package com.example.application.autocrud;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Nullable;
import com.vaadin.hilla.crud.CountService;
import com.vaadin.hilla.crud.GetService;
import com.vaadin.hilla.crud.ListService;
import com.vaadin.hilla.crud.filter.AndFilter;
import com.vaadin.hilla.crud.filter.Filter;
import com.vaadin.hilla.crud.filter.OrFilter;
import com.vaadin.hilla.crud.filter.PropertyStringFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@BrowserCallable
@ApplicationScoped
@AnonymousAllowed
@Nonnull
public class UserService implements ListService<UserPOJO>, GetService<UserPOJO, String>, CountService {

    private final Map<String, UserPOJO> data = new ConcurrentHashMap<>();

    UserService() {
        IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new UserPOJO("Name " + i, "Surname " + i))
                .forEach(user -> data.put(user.getId(), user));
    }

    @Override
    public long count(@Nullable Filter filter) {
        return data.values().stream().filter(fromFilter(filter)).count();
    }

    @Override
    public Optional<UserPOJO> get(String id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    @Nonnull
    public List<UserPOJO> list(Pageable pageable, @Nullable Filter filter) {
        return data.values().stream()
                .filter(fromFilter(filter))
                .sorted(sortBy(pageable.getSort()))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .collect(Collectors.toList());
    }

    private Comparator<UserPOJO> sortBy(Sort sort) {
        return sort.stream()
                .filter(o -> EXTRACTORS.containsKey(o.getProperty().toUpperCase()))
                .map(o -> {
                    Function<UserPOJO, String> extractor =
                            EXTRACTORS.get(o.getProperty().toUpperCase());
                    if (o.isIgnoreCase()) {
                        extractor = extractor.andThen(s -> s != null ? s.toLowerCase(Locale.ROOT) : s);
                    }
                    Comparator<UserPOJO> comparator = Comparator.comparing(extractor);
                    if (o.isDescending()) {
                        comparator = comparator.reversed();
                    }
                    if (o.getNullHandling() != null) {
                        comparator = switch (o.getNullHandling()) {
                            case NULLS_FIRST -> Comparator.nullsFirst(comparator);
                            case NULLS_LAST -> Comparator.nullsLast(comparator);
                            default -> comparator;
                        };
                    }
                    return comparator;
                })
                .reduce(Comparator::thenComparing)
                .orElse((o1, o2) -> 0);
    }

    private Predicate<UserPOJO> fromFilter(Filter filter) {
        Predicate<UserPOJO> allEntities = entity -> true;
        if (filter == null) {
            return allEntities;
        }
        if (filter instanceof PropertyStringFilter f) {
            return propertyPredicate(f);
        } else if (filter instanceof AndFilter f) {
            return f.getChildren().stream().map(this::fromFilter).reduce(allEntities, Predicate::and);
        } else if (filter instanceof OrFilter f) {
            return f.getChildren().stream().map(this::fromFilter).reduce(entity -> false, Predicate::or);
        } else {
            throw new UnsupportedOperationException("Unknown filter " + filter.getClass());
        }
    }

    private static final Map<String, Function<UserPOJO, String>> EXTRACTORS =
            Map.of("ID", UserPOJO::getId, "NAME", UserPOJO::getName, "SURNAME", UserPOJO::getSurname);

    private Predicate<UserPOJO> propertyPredicate(PropertyStringFilter filter) {
        String propertyId = filter.getPropertyId();
        Function<UserPOJO, String> extractor = EXTRACTORS.get(propertyId.toUpperCase());
        if (extractor == null) {
            throw new IllegalArgumentException("Invalid property " + propertyId);
        }
        return pojo -> {
            Optional<String> value = Optional.ofNullable(extractor.apply(pojo)).map(v -> v.toLowerCase(Locale.ROOT));
            Optional<String> filterValue =
                    Optional.ofNullable(filter.getFilterValue()).map(v -> v.toLowerCase(Locale.ROOT));
            return switch (filter.getMatcher()) {
                case EQUALS -> Objects.equals(value, filterValue);
                case CONTAINS ->
                    value.filter(v -> v.contains(filterValue.orElse(""))).isPresent();
                default ->
                    throw new UnsupportedOperationException(
                            filter.getMatcher() + " is not supported for property " + propertyId);
            };
        };
    }
}
