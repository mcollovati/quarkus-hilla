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

import jakarta.transaction.Transactional;

import dev.hilla.EndpointExposed;
import dev.hilla.Nullable;
import dev.hilla.crud.CrudService;
import org.springframework.data.repository.CrudRepository;

@EndpointExposed
public class CrudRepositoryService<T, ID, R extends CrudRepository<T, ID> & FilterableRepository<T, ID>>
        extends ListRepositoryService<T, ID, R> implements CrudService<T, ID> {

    protected CrudRepositoryService() {}

    protected CrudRepositoryService(R repository) {
        super(repository);
    }

    @Override
    @Transactional
    public @Nullable T save(T value) {
        return getRepository().save(value);
    }

    @Override
    @Transactional
    public void delete(ID id) {
        getRepository().deleteById(id);
    }
}
