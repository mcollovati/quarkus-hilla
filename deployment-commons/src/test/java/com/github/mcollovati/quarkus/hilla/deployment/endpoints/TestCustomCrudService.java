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
package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.BrowserCallable;
import dev.hilla.Nullable;
import dev.hilla.crud.CountService;
import dev.hilla.crud.CrudService;
import dev.hilla.crud.GetService;
import dev.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;

@BrowserCallable
@ApplicationScoped
@AnonymousAllowed
public class TestCustomCrudService implements CrudService<Pojo, Integer>, GetService<Pojo, Integer>, CountService {

    @Override
    public long count(@Nullable Filter filter) {
        return 0;
    }

    @Override
    public @Nullable Pojo save(Pojo value) {
        return null;
    }

    @Override
    public void delete(Integer integer) {}

    @Override
    public Optional<Pojo> get(Integer integer) {
        return Optional.empty();
    }

    @Override
    public List<Pojo> list(Pageable pageable, @Nullable Filter filter) {
        return null;
    }
}
