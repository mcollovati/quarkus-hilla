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

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkusHandlerHelperTest {

    @Test
    void frameworkInternalRequest_acceptsValidUploadPath() {
        RoutingContext context = context("/app/VAADIN/dynamic/resource/42/abc-123/upload");

        assertTrue(QuarkusHandlerHelper.isFrameworkInternalRequest("/app/*", context));
    }

    @Test
    void frameworkInternalRequest_rejectsInvalidUploadPath() {
        RoutingContext context = context("/app/VAADIN/dynamic/resource/not-a-ui/abc-123/upload");

        assertFalse(QuarkusHandlerHelper.isFrameworkInternalRequest("/app/*", context));
    }

    private static RoutingContext context(String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn(path);
        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(request);
        return context;
    }
}
