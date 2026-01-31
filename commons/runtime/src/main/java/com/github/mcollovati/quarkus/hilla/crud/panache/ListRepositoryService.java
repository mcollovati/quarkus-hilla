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
package com.github.mcollovati.quarkus.hilla.crud.panache;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

import com.vaadin.hilla.EndpointExposed;
import com.vaadin.hilla.crud.CountService;
import com.vaadin.hilla.crud.GetService;
import com.vaadin.hilla.crud.ListService;
import com.vaadin.hilla.crud.filter.Filter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;

/**
 * Base list service for Panache repositories that provides CRUD list operations.
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 * @param <R> the repository type extending FilterableRepository
 */
@EndpointExposed
public class ListRepositoryService<T, ID, R extends FilterableRepository<T, ID>>
        implements ListService<T>, GetService<T, ID>, CountService {
    @Inject
    R repository;

    /**
     * Creates a new instance with dependency injection.
     */
    protected ListRepositoryService() {}

    /**
     * Creates a new instance with the specified repository.
     *
     * @param repository the repository to use
     */
    protected ListRepositoryService(R repository) {
        this.repository = repository;
    }

    /**
     * Gets the repository instance.
     *
     * @return the repository instance
     */
    protected final R getRepository() {
        return repository;
    }

    @Override
    public List<T> list(Pageable pageable, @Nullable Filter filter) {
        return repository.list(pageable, filter);
    }

    @Override
    public Optional<T> get(ID id) {
        return repository.findByIdOptional(id);
    }

    @Override
    public boolean exists(ID id) {
        return repository.findByIdOptional(id).isPresent();
    }

    @Override
    public long count(@Nullable Filter filter) {
        return repository.count(filter);
    }
}
