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

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuarkusSecurityIdentityAugmentorTest {

    private final QuarkusSecurityIdentityAugmentor augmentor = new QuarkusSecurityIdentityAugmentor();

    @Test
    void augment_capturesBaseIdentityAtLowestPriority() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");

        SecurityIdentity augmented = augmentor.augment(identity, null).await().indefinitely();

        assertEquals(Integer.MIN_VALUE, augmentor.priority());
        assertSame(identity, QuarkusSecurityIdentityAugmentor.baseIdentity(augmented));
    }

    @Test
    void augment_repeatedInvocationIsIdempotent() {
        SecurityIdentity identity = TestSecurityIdentity.authenticated("user", "USER");
        SecurityIdentity first = augmentor.augment(identity, null).await().indefinitely();

        SecurityIdentity second = augmentor.augment(first, null).await().indefinitely();

        assertSame(first, second);
        assertSame(identity, QuarkusSecurityIdentityAugmentor.baseIdentity(second));
    }

    @Test
    void augment_anonymousIdentityIsUnchanged() {
        SecurityIdentity identity = TestSecurityIdentity.anonymous();

        assertSame(identity, augmentor.augment(identity, null).await().indefinitely());
    }
}
