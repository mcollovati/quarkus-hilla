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
package com.github.mcollovati.quarkus.hilla.crud.spring;

import java.util.List;

import dev.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Represents a Repository extension for a specific type of entity {@code T}, with an ID type of {@code ID},
 * that provides count and list operations supporting filtering and sorting.
 *
 * @param <T> Entity type
 * @param <ID> Entity ID type
 */
@NoRepositoryBean
public interface FilterableRepository<T, ID> extends Repository<T, ID> {

    default long count(Filter filter) {
        throw new IllegalStateException("This method is normally automatically overridden at build time in subclasses");
    }

    default List<T> list(Pageable pageable, Filter filter) {
        throw new IllegalStateException("This method is normally automatically overridden at build time in subclasses");
    }
}
