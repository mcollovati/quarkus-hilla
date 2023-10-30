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
package com.example.application.autogrid;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.BrowserCallable;
import dev.hilla.Nullable;
import dev.hilla.crud.CountService;
import dev.hilla.crud.CrudService;
import dev.hilla.crud.filter.Filter;
import org.springframework.data.domain.Pageable;

@BrowserCallable
@ApplicationScoped
@AnonymousAllowed
@Nonnull
public class UserService implements CrudService<User, Long>, CountService {

    private final UserRepository repository;

    UserService(UserRepository userRepository) {
        this.repository = userRepository;
    }

    @Override
    public long count(@Nullable Filter filter) {
        return repository.count(filter);
    }

    @Override
    public User get(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public boolean exists(Long id) {
        return repository.existsById(id);
    }

    @Override
    @Nonnull
    public List<User> list(Pageable pageable, @Nullable Filter filter) {
        return repository.list(pageable, filter);
    }

    @Override
    public User save(User value) {
        return repository.save(value);
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
