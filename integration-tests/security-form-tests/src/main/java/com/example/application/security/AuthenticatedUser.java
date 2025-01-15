/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
package com.example.application.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

import com.example.application.data.User;
import com.example.application.data.UserRepository;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class AuthenticatedUser {

    private final UserRepository userRepository;
    private final SecurityIdentity authenticationContext;

    public AuthenticatedUser(SecurityIdentity authenticationContext, UserRepository userRepository) {
        this.userRepository = userRepository;
        this.authenticationContext = authenticationContext;
    }

    public Optional<User> get() {
        return Optional.ofNullable(authenticationContext.getPrincipal())
                .map(userDetails -> userRepository.findByUsername(userDetails.getName()));
    }

    public void logout() {
        // authenticationContext.logout();
        throw new UnsupportedOperationException("logout not supported");
    }
}
