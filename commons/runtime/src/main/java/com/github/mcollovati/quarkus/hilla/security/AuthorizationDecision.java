/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.security;

/**
 * Result of evaluating one authorization source.
 *
 * <p>{@link #NO_MATCH} means that the source does not apply and allows the
 * caller to consult another source. It must not be treated as an allow or a
 * deny decision.
 */
enum AuthorizationDecision {
    /** Authorization source does not apply to the evaluated target. */
    NO_MATCH,

    /** Authorization source explicitly allows access. */
    ALLOW,

    /** Authorization source explicitly denies access. */
    DENY
}
