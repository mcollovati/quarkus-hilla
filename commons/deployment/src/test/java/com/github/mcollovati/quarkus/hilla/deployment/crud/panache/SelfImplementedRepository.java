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
package com.github.mcollovati.quarkus.hilla.deployment.crud.panache;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

import com.vaadin.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;

import com.github.mcollovati.quarkus.hilla.crud.panache.FilterableRepository;

@ApplicationScoped
public class SelfImplementedRepository implements FilterableRepository<TestEntity, Long> {

    @Override
    public long count(Filter filter) {
        return -99;
    }

    @Override
    public List<TestEntity> list(Pageable pageable, Filter filter) {
        TestEntity entity = new TestEntity();
        entity.id = 0L;
        entity.setText("Fixed");
        entity.setNumber(-99);
        return List.of(entity);
    }

    @Override
    public boolean isNew(TestEntity value) {
        return value.id == null || value.id < 0;
    }
}
