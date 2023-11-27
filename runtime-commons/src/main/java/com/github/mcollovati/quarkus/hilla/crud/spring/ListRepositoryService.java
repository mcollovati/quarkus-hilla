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

import jakarta.inject.Inject;
import java.util.List;

import dev.hilla.EndpointExposed;
import dev.hilla.Nullable;
import dev.hilla.crud.CountService;
import dev.hilla.crud.GetService;
import dev.hilla.crud.ListService;
import dev.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

@EndpointExposed
public class ListRepositoryService<T, ID, R extends CrudRepository<T, ID> & FilterableRepository<T, ID>>
        implements ListService<T>, GetService<T, ID>, CountService {

    @Inject
    R repository;

    protected ListRepositoryService() {}

    protected ListRepositoryService(R repository) {
        this.repository = repository;
    }

    protected final R getRepository() {
        return repository;
    }

    @Override
    public List<T> list(Pageable pageable, @Nullable Filter filter) {
        return repository.list(pageable, filter);
    }

    @Override
    public T get(ID id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public boolean exists(ID id) {
        return repository.existsById(id);
    }

    @Override
    public long count(@Nullable Filter filter) {
        return repository.count(filter);
    }
}
