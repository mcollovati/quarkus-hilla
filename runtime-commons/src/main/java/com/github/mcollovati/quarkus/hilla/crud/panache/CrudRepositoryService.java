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

import jakarta.transaction.Transactional;

import com.vaadin.hilla.EndpointExposed;
import com.vaadin.hilla.Nullable;
import com.vaadin.hilla.crud.CrudService;

@EndpointExposed
public class CrudRepositoryService<T, ID, R extends FilterableRepository<T, ID>> extends ListRepositoryService<T, ID, R>
        implements CrudService<T, ID> {

    protected CrudRepositoryService() {}

    protected CrudRepositoryService(R repository) {
        super(repository);
    }

    @Override
    @Transactional
    public @Nullable T save(T value) {
        if (getRepository().isNew(value)) {
            getRepository().persist(value);
        } else {
            getRepository().getEntityManager().merge(value);
        }
        return value;
    }

    @Override
    @Transactional
    public void delete(ID id) {
        getRepository().deleteById(id);
    }
}
